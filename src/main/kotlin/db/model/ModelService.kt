package io.github.whdt.db.model

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReplaceOneModel
import com.mongodb.client.model.ReplaceOptions
import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.Model
import io.github.whdt.core.hdt.model.ModelName
import io.github.whdt.db.util.OperationResult
import io.github.whdt.db.util.runCatchingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.types.ObjectId

class ModelService(private val database: MongoDatabase) {
    var collection: MongoCollection<Document>

    init {
        database.createCollection("models")
        collection = database.getCollection("models")
        collection.createIndex(Indexes.ascending("modelId"), IndexOptions().unique(true))
    }

    suspend fun create(model: Model): ModelDocument = withContext(Dispatchers.IO) {
        val modelDoc = ModelDocument.fromWhdtModel(model)
        val doc = modelDoc.toDocument()
        collection.insertOne(doc)
        ModelDocument.fromDocument(doc)
    }

    suspend fun insertMany(models: List<Model>): OperationResult<Int> = withContext(Dispatchers.IO) {
        val docs = models.map { ModelDocument.fromWhdtModel(it) }.map { it.toDocument() }
        runCatchingResult {
            val res = collection.insertMany(docs)
            res.insertedIds.size
        }
    }

    suspend fun upsert(model: Model): Boolean = withContext(Dispatchers.IO) {
        val filter = eq("modelId", model.id.value)
        val options = ReplaceOptions().upsert(true)
        val modelDoc = ModelDocument.fromWhdtModel(model)
        val doc = modelDoc.toDocument()
        val res = collection.replaceOne(filter, doc,options)
        res.wasAcknowledged()
    }

    suspend fun upsertMany(models: List<Model>): OperationResult<Map<String, Int>> = withContext(Dispatchers.IO) {
        val operations = models.map { model ->
            val doc = ModelDocument.fromWhdtModel(model).toDocument()
            ReplaceOneModel(
                eq("modelId", model.id.value),
                doc,
                ReplaceOptions().upsert(true)
            )
        }

        runCatchingResult {
            val res = collection.bulkWrite(operations)
            mapOf(
                "inserted" to res.inserts.size,
                "upserted" to res.upserts.size,
            )
        }
    }

    suspend fun read(id: String): ModelDocument? = withContext(Dispatchers.IO) {
        collection.find(eq("_id", ObjectId(id))).first()?.let(ModelDocument::fromDocument)
    }

    suspend fun delete(id: String): Document? = withContext(Dispatchers.IO) {
        collection.findOneAndDelete(eq("_id", ObjectId(id)))
    }

    suspend fun findAll(): List<ModelDocument> = withContext(Dispatchers.IO) {
        collection.find().toList().map(ModelDocument::fromDocument)
    }

    suspend fun findByName(modelName: ModelName): List<ModelDocument> = withContext(Dispatchers.IO) {
        findAll().filter { it.modelName == modelName }
    }

    suspend fun findByHdtId(hdtId: HdtId): List<ModelDocument> = withContext(Dispatchers.IO) {
        collection.find(eq("hdtId", hdtId.id)).toList().map(ModelDocument::fromDocument)
    }
}