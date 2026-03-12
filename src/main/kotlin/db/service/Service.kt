package io.github.whdt.db.service

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.ReplaceOptions
import io.github.whdt.db.record.MongoRecord
import io.github.whdt.db.record.Record
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document

interface Service<R: Record> {
    suspend fun create(entity: R): String
    suspend fun upsert(idField: String, entity: R): Boolean
    suspend fun read(id: String): R?
    suspend fun delete(entity: R)
    suspend fun findById(id: String): R?
    suspend fun findAll(): List<R>
}

abstract class MongoService<R: MongoRecord>(collectionName: String, database: MongoDatabase) : Service<R> {
    var collection: MongoCollection<Document>

    init {
        setupCollection(database)
        database.createCollection(collectionName)
        collection = database.getCollection(collectionName)
    }

    abstract fun setupCollection(database: MongoDatabase)

    override suspend fun create(entity: R): String = withContext(Dispatchers.IO) {
        val doc = entity.toDocument()
        collection.insertOne(doc)
        doc["_id"].toString()
    }

    override suspend fun upsert(idField: String, entity: R): Boolean = withContext(Dispatchers.IO) {
        val filter = eq(idField, entity.id)
        val options = ReplaceOptions().upsert(true)
        val doc = entity.toDocument()
        val res = collection.replaceOne(filter, doc,options)
        res.wasAcknowledged()
    }
}