package io.github.whdt.db.model

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.ReplaceOneModel
import com.mongodb.client.model.ReplaceOptions
import io.github.whdt.core.hdt.model.Model
import io.github.whdt.core.hdt.model.ModelName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.types.ObjectId

class ModelService(private val database: MongoDatabase) {
    var collection: MongoCollection<Document>

    init {
        database.createCollection("models")
        collection = database.getCollection("models")
    }

    suspend fun create(model: Model): String = withContext(Dispatchers.IO) {
        val modelDoc = ModelDocument.fromWhdtModel(model)
        val doc = modelDoc.toDocument()
        collection.insertOne(doc)
        doc["_id"].toString()
    }

    suspend fun insertMany(models: List<Model>): Boolean = withContext(Dispatchers.IO) {
        val docs = models.map { ModelDocument.fromWhdtModel(it) }.map { it.toDocument() }
        val res = collection.insertMany(docs)
        res.wasAcknowledged()
    }

    suspend fun upsert(model: Model): Boolean = withContext(Dispatchers.IO) {
        val filter = eq("modelId", model.id.value)
        val options = ReplaceOptions().upsert(true)
        val modelDoc = ModelDocument.fromWhdtModel(model)
        val doc = modelDoc.toDocument()
        val res = collection.replaceOne(filter, doc,options)
        res.wasAcknowledged()
    }

    suspend fun upsertMany(models: List<Model>): Boolean = withContext(Dispatchers.IO) {
        val docs = models.map { ModelDocument.fromWhdtModel(it) }.map { it.toDocument() }
        val operations = docs.map {
            val id = it["modelId"]
            ReplaceOneModel(
                eq("modelId", id),
                it,
                ReplaceOptions().upsert(true)
            )
        }
        val res = collection.bulkWrite(operations)
        res.wasAcknowledged()
    }

    suspend fun read(id: String): ModelDocument? = withContext(Dispatchers.IO) {
        collection.find(eq("_id", ObjectId(id))).first()?.let(ModelDocument::fromDocument)
    }

    suspend fun delete(id: String): Document? = withContext(Dispatchers.IO) {
        collection.findOneAndDelete(eq("_id", ObjectId(id)))
    }

    suspend fun findAll(): List<ModelDocument> = withContext(Dispatchers.IO) {
        collection.find().toList().map { ModelDocument.fromDocument(it) }.toList()
    }

    suspend fun findByName(modelName: ModelName): List<ModelDocument> = withContext(Dispatchers.IO) {
        findAll().filter { it.modelName == modelName }
    }
}