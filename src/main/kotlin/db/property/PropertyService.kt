package io.github.whdt.db.property

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.ReplaceOptions
import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.property.Property
import io.github.whdt.core.hdt.model.property.PropertyName
import io.github.whdt.core.hdt.model.property.PropertyValue
import io.github.whdt.request.ComparisonOperator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId

class PropertyService(private val database: MongoDatabase) {
    var collection: MongoCollection<Document>

    init {
        database.createCollection("properties")
        collection = database.getCollection("properties")
    }

    suspend fun create(hdtId: HdtId, property: Property): String = withContext(Dispatchers.IO) {
        val propertyDoc = PropertyDocument.fromWhdtProperty(hdtId, property)
        val doc = propertyDoc.toDocument()
        collection.insertOne(doc)
        doc["_id"].toString()
    }

    suspend fun upsert(hdtId: HdtId, property: Property): Boolean = withContext(Dispatchers.IO) {
        val filter = eq("propertyId", property.id.value)
        val options = ReplaceOptions().upsert(true)
        val propertyDoc = PropertyDocument.fromWhdtProperty(hdtId, property)
        val doc = propertyDoc.toDocument()
        val res = collection.replaceOne(filter, doc,options)
        res.wasAcknowledged()
    }

    suspend fun read(id: String): PropertyDocument? = withContext(Dispatchers.IO) {
        collection.find(eq("_id", ObjectId(id))).first()?.let(PropertyDocument::fromDocument)
    }

    suspend fun delete(id: String): Document? = withContext(Dispatchers.IO) {
        collection.findOneAndDelete(eq("_id", ObjectId(id)))
    }

    suspend fun findAll(): List<PropertyDocument> = withContext(Dispatchers.IO) {
        collection.find().toList().map { PropertyDocument.fromDocument(it) }.toList()
    }

    suspend fun findByName(propertyName: PropertyName): List<PropertyDocument> = withContext(Dispatchers.IO) {
        findAll().filter { it.propertyName == propertyName }
    }

    private fun buildValueFilter(
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
                    "value.value",
                    value.value
                )

            is PropertyValue.DoublePropertyValue ->
                applyOperator(
                    "value.value",
                    value.value
                )

            is PropertyValue.FloatPropertyValue ->
                applyOperator(
                    "value.value",
                    value.value
                )

            is PropertyValue.LongPropertyValue ->
                applyOperator(
                    "value.value",
                    value.value
                )

            is PropertyValue.StringPropertyValue ->
                applyOperator(
                    "value.value",
                    value.value
                )

            is PropertyValue.BooleanPropertyValue ->
                applyOperator(
                    "value.value",
                    value.value
                )

            PropertyValue.EmptyPropertyValue ->
                throw IllegalArgumentException("Cannot compare empty property value")
        }
    }

    suspend fun findByComparison(
        propertyName: PropertyName,
        other: PropertyValue,
        operator: ComparisonOperator
    ): List<PropertyDocument> = withContext(Dispatchers.IO) {
        val valueFilter = buildValueFilter(operator, other)
        val propertyFilter = and(
            eq("propertyName", propertyName.value),
            valueFilter
        )
        collection.find(propertyFilter).toList().map { PropertyDocument.fromDocument(it) }
    }
}