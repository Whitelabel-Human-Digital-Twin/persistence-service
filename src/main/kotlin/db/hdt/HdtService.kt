package io.github.whdt.db.hdt

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters.eq
import io.github.whdt.core.hdt.HumanDigitalTwin
import io.github.whdt.core.hdt.interfaces.digital.DigitalInterface
import io.github.whdt.core.hdt.interfaces.physical.PhysicalInterface
import io.github.whdt.core.hdt.model.id.HdtId
import io.github.whdt.core.hdt.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bson.Document
import org.bson.types.ObjectId

@Serializable
data class HumanDigitalTwinDocument(
    val hdtId: HdtId,
    val physicalInterfaces: List<PhysicalInterface>,
    val digitalInterfaces: List<DigitalInterface>,
    val storages: List<Storage>,
    ) {

    fun toDocument(): Document = Document.parse(Json.encodeToString(serializer(), this))

    companion object {
        fun fromHumanDigitalTwin(hdt: HumanDigitalTwin): HumanDigitalTwinDocument =
            HumanDigitalTwinDocument(
                hdtId = hdt.hdtId,
                physicalInterfaces = hdt.physicalInterfaces,
                digitalInterfaces = hdt.digitalInterfaces,
                storages = hdt.storages,
            )
        fun fromDocument(document: Document): HumanDigitalTwinDocument {
            val copy = Document(document)
            copy.remove("_id")
            return Json.decodeFromString(serializer(), copy.toJson())
        }
    }
}

class HdtService(private val database: MongoDatabase) {
    var collection: MongoCollection<Document>

    init {
        database.createCollection("hdt")
        collection = database.getCollection("hdt")
    }

    suspend fun create(hdt: HumanDigitalTwin): String = withContext(Dispatchers.IO) {
        val hdtDocument = HumanDigitalTwinDocument.fromHumanDigitalTwin(hdt)
        val doc = hdtDocument.toDocument()
        doc["_id"] = hdtDocument.hdtId.id
        collection.insertOne(doc)
        doc["_id"].toString()
    }

    suspend fun read(id: String): HumanDigitalTwinDocument? = withContext(Dispatchers.IO) {
        collection.find(eq("_id", ObjectId(id))).first()?.let(HumanDigitalTwinDocument::fromDocument)
    }

    suspend fun update(id: String, hdt: HumanDigitalTwin): Document? = withContext(Dispatchers.IO) {
        val hdtDocument = HumanDigitalTwinDocument.fromHumanDigitalTwin(hdt)
        collection.findOneAndReplace(eq("_id", ObjectId(id)), hdtDocument.toDocument())
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