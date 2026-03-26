package io.github.whdt.db.property

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.*
import com.mongodb.client.model.Accumulators.addToSet
import com.mongodb.client.model.Accumulators.push
import com.mongodb.client.model.Aggregates.group
import com.mongodb.client.model.Aggregates.match
import com.mongodb.client.model.Aggregates.sort
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Projections.fields
import com.mongodb.client.model.Projections.include
import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.ModelId
import io.github.whdt.core.hdt.model.ModelName
import io.github.whdt.core.hdt.model.property.Property
import io.github.whdt.core.hdt.model.property.PropertyId
import io.github.whdt.core.hdt.model.property.PropertyName
import io.github.whdt.core.hdt.model.property.PropertyValue
import io.github.whdt.routing.query.event.comparison.ComparisonOperator
import io.github.whdt.routing.query.event.comparison.Comparison
import io.github.whdt.routing.query.event.comparison.PropertyComparison
import io.github.whdt.routing.query.event.stats.PropertyStatsPerHdt
import io.github.whdt.request.PropertiesByComparisonsAggregateResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.conversions.Bson
import java.time.Instant
import java.util.*
import kotlin.collections.mutableListOf

class PropertyEventService(val db: MongoDatabase) {
    var collection: MongoCollection<Document>

    init {
        val tsOptions = TimeSeriesOptions("timeField")
            .metaField("metaField")
            .granularity(TimeSeriesGranularity.SECONDS)
        val ccOptions = CreateCollectionOptions().timeSeriesOptions(tsOptions)
        db.createCollection("property_events", ccOptions)
        collection = db.getCollection("property_events")
    }

    /** CRUD OPERATIONS **/

    suspend fun insertMany(hdtId: HdtId, properties: List<Property>): Boolean = withContext(Dispatchers.IO) {
        val docs = properties
            .map { PropertyEventDocument.fromWhdtProperty(hdtId, it) }
            .map(PropertyEventDocument::toDocument)
        val res = collection.insertMany(docs)
        res.wasAcknowledged()
    }

    /** AGGREGATE OPERATIONS **/

    private fun baseMatch(
        hdtId: String? = null,
        modelId: String? = null,
        modelName: String? = null,
        propertyId: String? = null,
        propertyName: String? = null,
        from: Instant? = null,
        to: Instant? = null
    ): Bson {
        val filters = mutableListOf<Bson>()
        if (hdtId != null) filters += eq("metaField.hdtId", hdtId)
        if (modelId != null) filters += eq("metaField.modelId", modelId)
        if (modelName != null) filters += eq("metaField.modelName", modelName)
        if (propertyId != null) filters += eq("metaField.propertyId", propertyId)
        if (propertyName != null) filters += eq("metaField.propertyName", propertyName)
        if (from != null) filters += gte("timeField", Date.from(from))
        if (to != null) filters += lt("timeField", Date.from(to))

        return when (filters.size) {
            0 -> Document()
            1 -> filters.first()
            else -> and(filters)
        }
    }

    private suspend fun findPropertiesWithFilter(
        filter: Bson,
    ): List<PropertyEventDocument> = withContext(Dispatchers.IO) {
        collection.find(filter)
            .projection(fields(include("metaField", "timeField", "value")))
            .toList()
            .mapNotNull(PropertyEventDocument::fromDocument)
    }

    private suspend fun findPropertiesWithBaseMatch(
        hdtId: String? = null,
        modelId: String? = null,
        modelName: String? = null,
        propertyId: String? = null,
        propertyName: String? = null,
        from: Instant? = null,
        to: Instant? = null
    ): List<PropertyEventDocument> = withContext(Dispatchers.IO) {
        val filters = baseMatch(hdtId, modelId, modelName, propertyId, propertyName, from, to)
        findPropertiesWithFilter(filters)
    }

    suspend fun propertiesById(
        propertyId: PropertyId,
        from: Instant,
        to: Instant
    ): List<PropertyEventDocument> = withContext(Dispatchers.IO) {
        findPropertiesWithBaseMatch(propertyId = propertyId.value, from = from, to = to)
    }

    suspend fun propertiesByName(
        hdtId: HdtId,
        propertyName: PropertyName,
        from: Instant,
        to: Instant,
    ): List<PropertyEventDocument> = withContext(Dispatchers.IO) {
        findPropertiesWithBaseMatch(hdtId = hdtId.id, propertyName = propertyName.value, from = from, to = to)
    }

    suspend fun propertiesByHdtId(
        hdtId: HdtId,
    ): List<PropertyEventDocument> = withContext(Dispatchers.IO) {
        findPropertiesWithBaseMatch(hdtId = hdtId.id)
    }

    suspend fun propertyHistory(
        hdtId: HdtId,
        propertyName: PropertyName,
    ): List<PropertyEventDocument> = withContext(Dispatchers.IO) {
        val filters = baseMatch(hdtId = hdtId.id, propertyName = propertyName.value)
        collection.find(filters)
            .projection(fields(include("metaField", "timeField", "value")))
            .toList()
            .mapNotNull(PropertyEventDocument::fromDocument)
    }

    suspend fun propertyAggregateStats(
        hdtIds: List<HdtId>,
        modelIds: List<ModelId>,
        modelNames: List<ModelName>,
        propertyName: PropertyName,
        from: Instant? = null,
        to: Instant? = null
    ): List<PropertyStatsPerHdt> = withContext(Dispatchers.IO) {
        val filters = mutableListOf<Bson>(
            eq("metaField.propertyName", propertyName.value),
        )
        if (hdtIds.isNotEmpty()) filters += `in`("metaField.hdtId", hdtIds.map { it.id })
        if (modelIds.isNotEmpty()) filters += `in`("metaField.modelId", modelIds.map { it.value })
        if (modelNames.isNotEmpty()) filters += `in`("metaField.modelName", modelNames.map { it.value })
        if (from != null) filters += gte("timeField", Date.from(from))
        if (to != null) filters += lt("timeField", Date.from(to))

        val pipeline = listOf(
            match(and(filters)),
            group(
                $$"$metaField.hdtId",
                Accumulators.sum("count", 1),
                Accumulators.avg("avg", $$"$value"),
                Accumulators.min("min", $$"$value"),
                Accumulators.max("max", $$"$value")
            ),
            sort(Sorts.ascending("_id"))
        )

        collection.aggregate(pipeline).map { doc ->
            PropertyStatsPerHdt(
                hdtId = doc.getString("_id"),
                count = (doc["count"] as Number).toLong(),
                avg = (doc["avg"] as? Number)?.toDouble(),
                min = (doc["min"] as? Number)?.toDouble(),
                max = (doc["max"] as? Number)?.toDouble()
            )
        }.toList().sortedBy { it.hdtId }
    }

    private fun buildValueFilter(
        operator: ComparisonOperator,
        value: PropertyValue
    ): Bson {
        fun applyOperator(field: String, v: Any): Bson =
            when (operator) {
                ComparisonOperator.GT  -> gt(field, v)
                ComparisonOperator.GTE -> gte(field, v)
                ComparisonOperator.LT  -> lt(field, v)
                ComparisonOperator.LTE -> lte(field, v)
                ComparisonOperator.EQ  -> eq(field, v)
            }
        return when (value) {
            is PropertyValue.IntPropertyValue -> applyOperator("value", value.value)
            is PropertyValue.DoublePropertyValue -> applyOperator("value", value.value)
            is PropertyValue.FloatPropertyValue -> applyOperator("value", value.value)
            is PropertyValue.LongPropertyValue -> applyOperator("value", value.value)
            is PropertyValue.StringPropertyValue -> applyOperator("value", value.value)
            is PropertyValue.BooleanPropertyValue -> applyOperator("value", value.value)
            PropertyValue.EmptyPropertyValue ->
                throw IllegalArgumentException("Cannot compare empty property value")
        }
    }

    suspend fun propertiesByComparison(
        propertyName: PropertyName,
        comparisons: List<Comparison>,
    ): List<PropertyEventDocument> = withContext(Dispatchers.IO) {
        val valueFilter = and(
            comparisons.map { buildValueFilter(it.comparison, it.value) }.toList()
        )
        val filter = and(
            valueFilter,
            baseMatch(propertyName = propertyName.value),
        )
        findPropertiesWithFilter(filter)
    }

    suspend fun propertiesByComparisonsAggregate(
        propertyComparisons: List<PropertyComparison>,
        modelNames: List<ModelName>? = null,
        from: Instant? = null,
        to: Instant? = null
    ): List<PropertiesByComparisonsAggregateResponse> = withContext(Dispatchers.IO) {
        fun buildPropertyComparisonFilter(pc: PropertyComparison): Bson =
            and(
                eq("metaField.propertyName", pc.propertyName.value),
                buildValueFilter(pc.comparison, pc.value)
            )
        val propertyNames = propertyComparisons.map { it.propertyName.value }.distinct()
        val outerFilters = mutableListOf(baseMatch(from = from, to = to))
        if (!modelNames.isNullOrEmpty())
            outerFilters += `in`("metaField.modelName", modelNames.map { it.value })
        val comparisonOrFilter = or(propertyComparisons.map(::buildPropertyComparisonFilter))
        val finalMatch = and(
            outerFilters + comparisonOrFilter
        )
        val pipeline = listOf(
            match(finalMatch),
            group(
                $$"$metaField.hdtId",
                addToSet("matchedProperties", $$"$metaField.propertyName"),
                push(
                    "matchedEvents",
                    Document()
                        .append("propertyName", $$"$metaField.propertyName")
                        .append("value", $$"$value")
                        .append("timeField", $$"$timeField")
                )
            ),
            match(all("matchedProperties", propertyNames))
        )
        collection.aggregate(pipeline)
            .mapNotNull {
               PropertiesByComparisonsAggregateResponse.fromDocument(it)
           }
           .toList()
           .sortedBy { it.hdtId.id }
    }
}