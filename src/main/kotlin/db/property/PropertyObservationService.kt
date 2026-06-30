package db.property

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.*
import com.mongodb.client.model.Accumulators.addToSet
import com.mongodb.client.model.Accumulators.push
import com.mongodb.client.model.Aggregates.*
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Projections.fields
import com.mongodb.client.model.Projections.include
import db.util.OperationResult
import db.util.runCatchingResult
import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.model.ModelId
import io.github.ktwinx.core.hdt.model.ModelName
import io.github.ktwinx.core.hdt.model.property.PropertyId
import io.github.ktwinx.core.hdt.model.property.PropertyName
import io.github.ktwinx.core.hdt.model.property.PropertyObservation
import io.github.ktwinx.core.hdt.model.property.PropertyValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.conversions.Bson
import routing.query.event.comparison.Comparison
import routing.query.event.comparison.ComparisonOperator
import routing.query.event.comparison.PropertiesByComparisonsAggregateResponse
import routing.query.event.comparison.PropertyComparison
import routing.query.event.stats.PropertyStatsPerHdt
import java.time.Instant
import java.util.*

class PropertyObservationService(val db: MongoDatabase) {
    var collection: MongoCollection<Document>

    init {
        val exists = db.listCollectionNames().contains("observations")
        if (!exists) {
            val tsOptions = TimeSeriesOptions("timeField")
                .metaField("metaField")
                .granularity(TimeSeriesGranularity.SECONDS)
            val ccOptions = CreateCollectionOptions().timeSeriesOptions(tsOptions)
            db.createCollection("observations", ccOptions)
        }
        collection = db.getCollection("observations")
    }

    /** CRUD OPERATIONS **/

    suspend fun insertMany(observations: List<PropertyObservation>): OperationResult<Int> = withContext(Dispatchers.IO) {
        val docs = observations
            .map { PropertyObservationDocument.fromObservation(it) }
            .map(PropertyObservationDocument::toDocument)
        runCatchingResult {
            val res = collection.insertMany(docs)
            res.insertedIds.size
        }
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

    private suspend fun findObservationsWithFilter(
        filter: Bson,
    ): List<PropertyObservationDocument> = withContext(Dispatchers.IO) {
        collection.find(filter)
            .projection(fields(include("metaField", "timeField", "value")))
            .toList()
            .mapNotNull(PropertyObservationDocument::fromDocument)
    }

    private suspend fun findObservationsWithBaseMatch(
        hdtId: String? = null,
        modelId: String? = null,
        modelName: String? = null,
        propertyId: String? = null,
        propertyName: String? = null,
        from: Instant? = null,
        to: Instant? = null
    ): List<PropertyObservationDocument> = withContext(Dispatchers.IO) {
        val filters = baseMatch(hdtId, modelId, modelName, propertyId, propertyName, from, to)
        findObservationsWithFilter(filters)
    }

    suspend fun observationsById(
        propertyId: PropertyId,
        from: Instant,
        to: Instant
    ): List<PropertyObservationDocument> = withContext(Dispatchers.IO) {
        findObservationsWithBaseMatch(propertyId = propertyId.value, from = from, to = to)
    }

    suspend fun observationsByName(
        hdtId: HdtId,
        propertyName: PropertyName,
        from: Instant,
        to: Instant,
    ): List<PropertyObservationDocument> = withContext(Dispatchers.IO) {
        findObservationsWithBaseMatch(hdtId = hdtId.id, propertyName = propertyName.value, from = from, to = to)
    }

    suspend fun observationsByHdtId(
        hdtId: HdtId,
    ): List<PropertyObservationDocument> = withContext(Dispatchers.IO) {
        findObservationsWithBaseMatch(hdtId = hdtId.id)
    }

    suspend fun observationHistory(
        hdtId: HdtId,
        propertyName: PropertyName,
    ): List<PropertyObservationDocument> = withContext(Dispatchers.IO) {
        val filters = baseMatch(hdtId = hdtId.id, propertyName = propertyName.value)
        collection.find(filters)
            .projection(fields(include("metaField", "timeField", "value")))
            .toList()
            .mapNotNull(PropertyObservationDocument::fromDocument)
    }

    suspend fun latestPerProperty(
        hdtId: HdtId
    ): Map<PropertyId, PropertyObservationDocument> = withContext(Dispatchers.IO) {
        val filter = baseMatch(hdtId = hdtId.id)
        val pipeline = listOf(
            match(filter),
            sort(Sorts.descending("timeField")),
            group(
                "\$metaField.propertyId",
                Accumulators.first("doc", "\$\$ROOT")
            )
        )
        collection.aggregate(pipeline)
            .mapNotNull { doc ->
                val inner = doc.get("doc", Document::class.java) ?: return@mapNotNull null
                val obs = PropertyObservationDocument.fromDocument(inner) ?: return@mapNotNull null
                obs.metaField.propertyId to obs
            }
            .toList()
            .toMap()
    }

    data class LatestByTask(val task: String?, val observation: PropertyObservationDocument)

    suspend fun latestPerPropertyAndTask(
        hdtId: HdtId
    ): List<LatestByTask> = withContext(Dispatchers.IO) {
        val filter = baseMatch(hdtId = hdtId.id)
        val idDoc = Document("propertyId", "\$metaField.propertyId")
            .append("task", "\$metadata.task")
        val pipeline = listOf(
            match(filter),
            sort(Sorts.descending("timeField")),
            group(idDoc, Accumulators.first("doc", "\$\$ROOT"))
        )
        collection.aggregate(pipeline)
            .mapNotNull { doc ->
                val id = doc.get("_id", Document::class.java) ?: return@mapNotNull null
                val task = id.getString("task")
                val inner = doc.get("doc", Document::class.java) ?: return@mapNotNull null
                val obs = PropertyObservationDocument.fromDocument(inner) ?: return@mapNotNull null
                LatestByTask(task, obs)
            }
            .toList()
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
                "\$metaField.hdtId",
                Accumulators.sum("count", 1),
                Accumulators.avg("avg", "\$value"),
                Accumulators.min("min", "\$value"),
                Accumulators.max("max", "\$value")
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

    suspend fun observationsByComparison(
        propertyName: PropertyName,
        comparisons: List<Comparison>,
    ): List<PropertyObservationDocument> = withContext(Dispatchers.IO) {
        val valueFilter = and(
            comparisons.map { buildValueFilter(it.comparison, it.value) }.toList()
        )
        val filter = and(
            valueFilter,
            baseMatch(propertyName = propertyName.value),
        )
        findObservationsWithFilter(filter)
    }

    suspend fun observationsByComparisonsAggregate(
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
                "\$metaField.hdtId",
                addToSet("matchedProperties", "\$metaField.propertyName"),
                push(
                    "matchedEvents",
                    Document()
                        .append("propertyName", "\$metaField.propertyName")
                        .append("value", "\$value")
                        .append("timeField", "\$timeField")
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
