package io.github.whdt.db.model

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters.eq
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