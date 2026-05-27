package io.github.whdt.db.property

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReplaceOneModel
import com.mongodb.client.model.ReplaceOptions
import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.ModelId
import io.github.whdt.core.hdt.model.property.Property
import io.github.whdt.core.hdt.query.TagPredicate
import io.github.whdt.db.query.toBson
import io.github.whdt.db.util.OperationResult
import io.github.whdt.db.util.runCatchingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document

class PropertyService(private val database: MongoDatabase) {
    var collection: MongoCollection<Document>

    init {
        database.createCollection("properties")
        collection = database.getCollection("properties")
        collection.createIndex(
            Indexes.ascending("hdtId", "modelId", "propertyId"),
            IndexOptions().unique(true)
        )
    }

    suspend fun findByHdtId(
        hdtId: HdtId,
        modelId: ModelId? = null
    ): List<PropertyDocument> = withContext(Dispatchers.IO) {
        val filter = if (modelId != null) {
            and(eq("hdtId", hdtId.id), eq("modelId", modelId.value))
        } else {
            eq("hdtId", hdtId.id)
        }
        collection.find(filter).toList().mapNotNull(PropertyDocument::fromDocument)
    }

    suspend fun findByModelId(modelId: ModelId): List<PropertyDocument> = withContext(Dispatchers.IO) {
        collection.find(eq("modelId", modelId.value)).toList().mapNotNull(PropertyDocument::fromDocument)
    }

    suspend fun findByTagPredicate(predicate: TagPredicate): List<PropertyDocument> =
        withContext(Dispatchers.IO) {
            collection.find(predicate.toBson()).toList().mapNotNull(PropertyDocument::fromDocument)
        }

    suspend fun batchUpsert(hdtId: HdtId, properties: List<Property>): OperationResult<Map<String, Int>> =
        withContext(Dispatchers.IO) {
            val operations = properties.map { property ->
                val doc = PropertyDocument.fromWhdtProperty(hdtId, property).toDocument()
                ReplaceOneModel(
                    and(
                        eq("hdtId", hdtId.id),
                        eq("modelId", property.modelId.value),
                        eq("propertyId", property.id.value)
                    ),
                    doc,
                    ReplaceOptions().upsert(true)
                )
            }
            runCatchingResult {
                if (operations.isEmpty()) return@runCatchingResult mapOf("inserted" to 0, "upserted" to 0)
                val res = collection.bulkWrite(operations)
                mapOf(
                    "inserted" to res.inserts.size,
                    "upserted" to res.upserts.size,
                )
            }
        }
}
