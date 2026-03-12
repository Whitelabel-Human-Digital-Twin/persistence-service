package io.github.whdt.db.property

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Accumulators
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.CreateCollectionOptions
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.TimeSeriesGranularity
import com.mongodb.client.model.TimeSeriesOptions
import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.property.Property
import io.github.whdt.db.property.query.PropertyStatsPerHdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.conversions.Bson
import java.time.Instant
import java.util.Date

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
        propertyId: String? = null,
        from: Instant? = null,
        to: Instant? = null
    ): Bson {
        val filters = mutableListOf<Bson>()
        if (hdtId != null) filters += eq("metaField.hdtId", hdtId)
        if (propertyId != null) filters += eq("metaField.propertyId", propertyId)
        if (from != null) filters += gte("timeField", Date.from(from))
        if (to != null) filters += lt("timeField", Date.from(to))

        return and(filters)
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