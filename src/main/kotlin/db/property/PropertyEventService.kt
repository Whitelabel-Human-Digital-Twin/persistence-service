package io.github.whdt.db.property

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Accumulators
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.CreateCollectionOptions
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Projections.fields
import com.mongodb.client.model.Projections.include
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.TimeSeriesGranularity
import com.mongodb.client.model.TimeSeriesOptions
import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.ModelId
import io.github.whdt.core.hdt.model.property.Property
import io.github.whdt.core.hdt.model.property.PropertyId
import io.github.whdt.core.hdt.model.property.PropertyName
import io.github.whdt.db.property.query.PropertyStatsPerHdt
import io.github.whdt.db.property.query.PropertyValuesById
import io.github.whdt.db.property.query.PropertyValuesByName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.conversions.Bson
import java.time.Instant
import java.util.Date
import kotlin.time.toKotlinInstant

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
        propertyId: String? = null,
        propertyName: String? = null,
        from: Instant? = null,
        to: Instant? = null
    ): Bson {
        val filters = mutableListOf<Bson>()
        if (hdtId != null) filters += eq("metaField.hdtId", hdtId)
        if (modelId != null) filters += eq("metaField.modelId", modelId)
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

    suspend fun propertyValuesById(
        propertyId: PropertyId,
        from: Instant,
        to: Instant
    ): List<PropertyValuesById> = withContext(Dispatchers.IO) {
        val filters = baseMatch(propertyId = propertyId.value, from = from, to = to)

        collection.find(filters)
            .projection(fields(include("metaField", "timeField", "value")))
            .toList()
            .mapNotNull { doc ->
                val meta = doc.get("metaField", Document::class.java) ?: return@mapNotNull null
                val pid = meta.getString("propertyId") ?: return@mapNotNull null
                val value = doc["value"]?.pv() ?: return@mapNotNull null
                val time = doc.getDate("timeField")?.toInstant() ?: return@mapNotNull null

                PropertyValuesById(
                    PropertyId(pid),
                    value,
                    time.toKotlinInstant()
                )
            }
    }

    suspend fun propertyValuesByName(
        hdtId: HdtId,
        propertyName: PropertyName,
        from: Instant,
        to: Instant,
    ): List<PropertyValuesByName> = withContext(Dispatchers.IO) {
        val filters = baseMatch(hdtId = hdtId.id, propertyName = propertyName.value, from = from, to = to)
        collection.find(filters)
            .projection(fields(include("metaField", "timeField", "value")))
            .toList()
            .mapNotNull { doc ->
                val meta = doc.get("metaField", Document::class.java) ?: return@mapNotNull null
                val mid = meta.getString("modelId") ?: return@mapNotNull null
                val pName = meta.getString("propertyName") ?: return@mapNotNull null
                val value = doc["value"]?.pv() ?: return@mapNotNull null
                val time = doc.getDate("timeField")?.toInstant() ?: return@mapNotNull null

                PropertyValuesByName(
                    ModelId(mid),
                    PropertyName(pName),
                    value,
                    time.toKotlinInstant()
                )
            }
    }

    suspend fun avgMinMaxForPropertyByHdt(
        hdtIds: List<String>,
        modelId: String,
        propertyId: String,
        from: Instant? = null,
        to: Instant? = null
    ): List<PropertyStatsPerHdt> = withContext(Dispatchers.IO) {
        val filters = mutableListOf<Bson>(
            `in`("metaField.hdtId", hdtIds),
            eq("metaField.modelId", modelId),
            eq("metaField.propertyId", propertyId)
        )
        if (from != null) filters += gte("timeField", Date.from(from))
        if (to != null) filters += lt("timeField", Date.from(to))

        val pipeline = listOf(
            Aggregates.match(and(filters)),
            Aggregates.group(
                $$"$metaField.hdtId",
                Accumulators.sum("count", 1),
                Accumulators.avg("avg", $$"$value"),
                Accumulators.min("min", $$"$value"),
                Accumulators.max("max", $$"$value")
            ),
            Aggregates.sort(Sorts.ascending("_id"))
        )

        collection.aggregate(pipeline).map { doc ->
            PropertyStatsPerHdt(
                hdtId = doc.getString("_id"),
                count = (doc["count"] as Number).toLong(),
                avg = (doc["avg"] as? Number)?.toDouble(),
                min = (doc["min"] as? Number)?.toDouble(),
                max = (doc["max"] as? Number)?.toDouble()
            )
        }.toList()
    }
}