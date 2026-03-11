package io.github.whdt.db.property

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.CreateCollectionOptions
import com.mongodb.client.model.TimeSeriesGranularity
import com.mongodb.client.model.TimeSeriesOptions
import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.property.Property
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document

class PropertyEventService(val db: MongoDatabase) {
    private var collection: MongoCollection<Document>

    init {
        val tsOptions = TimeSeriesOptions("timeField")
            .metaField("metaField")
            .granularity(TimeSeriesGranularity.SECONDS)
        val ccOptions = CreateCollectionOptions().timeSeriesOptions(tsOptions)
        db.createCollection("property_events", ccOptions)
        collection = db.getCollection("property_events")
    }

    suspend fun insertMany(hdtId: HdtId, properties: List<Property>): Boolean = withContext(Dispatchers.IO) {
        val docs = properties
            .map { PropertyEventDocument.fromWhdtProperty(hdtId, it) }
            .map(PropertyEventDocument::toDocument)
        val res = collection.insertMany(docs)
        res.wasAcknowledged()
    }
}