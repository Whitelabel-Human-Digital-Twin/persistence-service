package io.github.whdt.db

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import io.github.whdt.core.hdt.HumanDigitalTwin
import io.github.whdt.distributed.serde.Stub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.types.ObjectId

fun HumanDigitalTwin.toDocument(): Document = Document.parse(Stub.hdtJsonSerDe().serialize(this))

fun HumanDigitalTwin.Companion.fromDocument(doc: Document): HumanDigitalTwin = Stub.hdtJsonSerDe().deserialize(doc.toJson())

class HdtService(private val database: MongoDatabase) {
    var collection: MongoCollection<Document>

    init {
        database.createCollection("hdt")
        collection = database.getCollection("hdt")
    }

    suspend fun create(hdt: HumanDigitalTwin): String = withContext(Dispatchers.IO) {
        val doc = hdt.toDocument()
        collection.insertOne(doc)
        doc["_id"].toString()
    }

    suspend fun read(id: String): HumanDigitalTwin? = withContext(Dispatchers.IO) {
        collection.find(Filters.eq("_id", ObjectId(id))).first()?.let(HumanDigitalTwin::fromDocument)
    }

    suspend fun update(id: String, hdt: HumanDigitalTwin): Document? = withContext(Dispatchers.IO) {
        collection.findOneAndReplace(Filters.eq("_id", ObjectId(id)), hdt.toDocument())
    }

    suspend fun delete(id: String): Document? = withContext(Dispatchers.IO) {
        collection.findOneAndDelete(Filters.eq("_id", ObjectId(id)))
    }
}