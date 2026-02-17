package io.github.whdt.db.hdt

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters.*
import io.github.whdt.core.hdt.HumanDigitalTwin
import io.github.whdt.core.hdt.model.property.Property
import io.github.whdt.core.hdt.model.property.PropertyValue
import io.github.whdt.distributed.serde.Stub
import io.github.whdt.query.ComparisonOperator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId

fun HumanDigitalTwin.toDocument(): Document = Document.parse(Stub.hdtJsonSerDe().serialize(this))

fun HumanDigitalTwin.Companion.fromDocument(doc: Document): HumanDigitalTwin {
    val copy = Document(doc)
    copy.remove("_id")
    return Stub.hdtJsonSerDe().deserialize(copy.toJson())
}

class HdtService(private val database: MongoDatabase) {
    var collection: MongoCollection<Document>

    init {
        database.createCollection("hdt")
        collection = database.getCollection("hdt")
    }

    suspend fun create(hdt: HumanDigitalTwin): String = withContext(Dispatchers.IO) {
        val doc = hdt.toDocument()
        doc["_id"] = hdt.hdtId.id
        collection.insertOne(doc)
        doc["_id"].toString()
    }

    suspend fun read(id: String): HumanDigitalTwin? = withContext(Dispatchers.IO) {
        collection.find(eq("_id", ObjectId(id))).first()?.let(HumanDigitalTwin::fromDocument)
    }

    suspend fun update(id: String, hdt: HumanDigitalTwin): Document? = withContext(Dispatchers.IO) {
        collection.findOneAndReplace(eq("_id", ObjectId(id)), hdt.toDocument())
    }

    suspend fun delete(id: String): Document? = withContext(Dispatchers.IO) {
        collection.findOneAndDelete(eq("_id", ObjectId(id)))
    }

    suspend fun findAll(): List<HumanDigitalTwin> = withContext(Dispatchers.IO) {
        collection.find().toList().map { HumanDigitalTwin.fromDocument(it) }.toList()
    }

    /**
     * Find HDTs that contain a property with given name
     */
    suspend fun findByPropertyName(propertyName: String): List<HumanDigitalTwin> =
        withContext(Dispatchers.IO) {
            collection.find(
                eq("models.properties.name", propertyName)
            ).toList().map(HumanDigitalTwin::fromDocument)
        }

    private fun buildValueFilter(
        valueKey: String,
        operator: ComparisonOperator,
        value: PropertyValue
    ): Bson {

        fun applyOperator(field: String, v: Any): Bson =
            when (operator) {
                ComparisonOperator.GT  -> gt(field, v)
                ComparisonOperator.GTE -> gte(field, v)
                ComparisonOperator.LT  -> lt(field, v)
                ComparisonOperator.LTE -> lte(field, v)
                ComparisonOperator.EQ  -> eq(field, v)
            }

        return when (value) {

            is PropertyValue.IntPropertyValue ->
                applyOperator(
                    "valueMap.$valueKey.value",
                    value.value
                )

            is PropertyValue.DoublePropertyValue ->
                applyOperator(
                    "valueMap.$valueKey.value",
                    value.value
                )

            is PropertyValue.FloatPropertyValue ->
                applyOperator(
                    "valueMap.$valueKey.value",
                    value.value
                )

            is PropertyValue.LongPropertyValue ->
                applyOperator(
                    "valueMap.$valueKey.value",
                    value.value
                )

            is PropertyValue.StringPropertyValue ->
                applyOperator(
                    "valueMap.$valueKey.value",
                    value.value
                )

            is PropertyValue.BooleanPropertyValue ->
                applyOperator(
                    "valueMap.$valueKey.value",
                    value.value
                )

            PropertyValue.EmptyPropertyValue ->
                throw IllegalArgumentException("Cannot compare empty property value")
        }
    }

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
}