package db.property

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReplaceOneModel
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Updates
import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.model.ModelId
import io.github.ktwinx.core.hdt.model.property.Property
import io.github.ktwinx.core.hdt.model.property.PropertyId
import io.github.ktwinx.core.hdt.query.TagPredicate
import db.query.toBson
import db.util.OperationResult
import db.util.runCatchingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import java.util.Date
import kotlin.time.Clock
import kotlin.time.toJavaInstant

class PropertyService(private val database: MongoDatabase) {
    var collection: MongoCollection<Document>

    init {
        val exists = database.listCollectionNames().contains("properties")
        if(!exists) {
            database.createCollection("properties")
        }
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

    suspend fun replaceTags(
        hdtId: HdtId,
        propertyId: PropertyId,
        tags: Map<String, String>,
    ): OperationResult<Boolean> = withContext(Dispatchers.IO) {
        runCatchingResult {
            val res = collection.updateOne(
                and(eq("hdtId", hdtId.id), eq("propertyId", propertyId.value)),
                Updates.combine(
                    Updates.set("tags", Document(tags)),
                    Updates.set("lastUpdated", Date.from(Clock.System.now().toJavaInstant())),
                ),
            )
            res.matchedCount > 0
        }
    }

    suspend fun batchInsert(hdtId: HdtId, properties: List<Property>): OperationResult<Int> =
        withContext(Dispatchers.IO) {
            if (properties.isEmpty()) return@withContext db.util.Ok(0)
            runCatchingResult {
                val res = collection.insertMany(properties.map {
                    PropertyDocument.fromktwinxProperty(hdtId, it)
                        .toDocument()
                })
                res.insertedIds.size
            }
        }

    suspend fun batchUpsert(hdtId: HdtId, properties: List<Property>): OperationResult<Map<String, Int>> =
        withContext(Dispatchers.IO) {
            val operations = properties.map { property ->
                val doc = PropertyDocument.fromktwinxProperty(hdtId, property).toDocument()
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
