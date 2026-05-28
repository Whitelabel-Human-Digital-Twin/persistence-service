package io.github.whdt.db.view

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReplaceOptions
import io.github.whdt.core.hdt.view.View
import io.github.whdt.core.hdt.view.ViewName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document

class ViewService(database: MongoDatabase) {
    var collection: MongoCollection<Document>

    init {
        runCatching { database.createCollection("views") }
        collection = database.getCollection("views")
        collection.createIndex(Indexes.ascending("name"), IndexOptions().unique(true))
    }

    suspend fun findAll(): List<ViewDocument> = withContext(Dispatchers.IO) {
        collection.find().toList().map(ViewDocument::fromDocument)
    }

    suspend fun findByName(name: ViewName): ViewDocument? = withContext(Dispatchers.IO) {
        collection.find(Filters.eq("name", name.value)).first()?.let(ViewDocument::fromDocument)
    }

    suspend fun upsert(view: View): ViewDocument = withContext(Dispatchers.IO) {
        val doc = ViewDocument.fromView(view).toDocument()
        collection.replaceOne(Filters.eq("name", view.name.value), doc, ReplaceOptions().upsert(true))
        ViewDocument.fromView(view)
    }

    suspend fun delete(name: ViewName): Boolean = withContext(Dispatchers.IO) {
        collection.findOneAndDelete(Filters.eq("name", name.value)) != null
    }
}
