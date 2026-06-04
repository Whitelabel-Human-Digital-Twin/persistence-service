package db.hdt

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.ReplaceOneModel
import com.mongodb.client.model.ReplaceOptions
import io.github.ktwinx.core.hdt.HumanDigitalTwin
import db.util.OperationResult
import db.util.runCatchingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.types.ObjectId

class HdtService(private val database: MongoDatabase) {
    var collection: MongoCollection<Document>

    init {
        val exists = database.listCollectionNames().contains("hdt")
        if(!exists) {
            database.createCollection("hdt")
        }
        collection = database.getCollection("hdt")
    }

    suspend fun create(hdt: HumanDigitalTwin): HumanDigitalTwinDocument = withContext(Dispatchers.IO) {
        val hdtDocument = HumanDigitalTwinDocument.fromHumanDigitalTwin(hdt)
        val doc = hdtDocument.toDocument()
        doc["_id"] = hdtDocument.hdtId.id
        collection.insertOne(doc)
        HumanDigitalTwinDocument.fromDocument(doc)
    }

    suspend fun upsert(hdt: HumanDigitalTwin): Boolean = withContext(Dispatchers.IO) {
        val filter = eq("hdtId", hdt.hdtId.id)
        val options = ReplaceOptions().upsert(true)
        val hdtDoc = HumanDigitalTwinDocument.fromHumanDigitalTwin(hdt)
        val doc = hdtDoc.toDocument()
        val res = collection.replaceOne(filter, doc,options)
        res.wasAcknowledged()
    }

    suspend fun insertMany(hdts: List<HumanDigitalTwin>): OperationResult<Int> = withContext(Dispatchers.IO) {
        runCatchingResult {
            val docs = hdts.map { HumanDigitalTwinDocument.fromHumanDigitalTwin(it) }.map { it.toDocument() }
            val res = collection.insertMany(docs)
            res.insertedIds.size
        }
    }

    suspend fun upsertMany(hdts: List<HumanDigitalTwin>): OperationResult<Map<String, Int>> = withContext(Dispatchers.IO) {
        val operations = hdts.map { hdt ->
            val doc = HumanDigitalTwinDocument.fromHumanDigitalTwin(hdt).toDocument()
            ReplaceOneModel(
                eq("hdtId", hdt.hdtId.id),
                doc,
                ReplaceOptions().upsert(true)
            )
        }

        runCatchingResult {
            val res = collection.bulkWrite(operations)
            mapOf(
                "inserts" to res.inserts.size,
                "upserts" to res.upserts.size,
            )
        }
    }

    suspend fun read(id: String): HumanDigitalTwinDocument? = withContext(Dispatchers.IO) {
        collection.find(eq("_id", ObjectId(id))).first()?.let(HumanDigitalTwinDocument.Companion::fromDocument)
    }

    suspend fun update(id: String, hdt: HumanDigitalTwin): HumanDigitalTwinDocument? = withContext(Dispatchers.IO) {
        val hdtDocument = HumanDigitalTwinDocument.fromHumanDigitalTwin(hdt)
        val res = collection.findOneAndReplace(eq("_id", ObjectId(id)), hdtDocument.toDocument())
        res?.let { HumanDigitalTwinDocument.fromDocument(it) }
    }

    suspend fun delete(id: String): Document? = withContext(Dispatchers.IO) {
        collection.findOneAndDelete(eq("_id", ObjectId(id)))
    }

    suspend fun findAll(): List<HumanDigitalTwinDocument> = withContext(Dispatchers.IO) {
        collection.find().toList().map { HumanDigitalTwinDocument.fromDocument(it) }.toList()
    }

    suspend fun findByIds(ids: List<String>): List<HumanDigitalTwinDocument> = withContext(Dispatchers.IO) {
        findAll().filter { it.hdtId.id in ids }
    }

    /*


    /**
     * Find HDTs where property name = X AND numeric value > threshold
     * Ensures name + value belong to same property using elemMatch
     */
    suspend fun findByPropertyComparison(
        propertyName: String,
        valueKey: String,
        operator: ComparisonOperator,
        threshold: PropertyValue
    ): List<HumanDigitalTwin> = withContext(Dispatchers.IO) {

        val valueFilter = buildValueFilter(valueKey, operator, threshold)

        val propertyFilter = elemMatch(
            "models.properties",
            and(
                eq("name", propertyName),
                valueFilter
            )
        )

        collection.find(propertyFilter)
            .toList()
            .map(HumanDigitalTwin::fromDocument)
    }
     */
}