package io.github.whdt.db.property

import io.github.whdt.MongoIntegrationTest
import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.ModelId
import io.github.whdt.core.hdt.model.property.PropertyId
import io.github.whdt.core.hdt.model.property.PropertyName
import io.github.whdt.core.hdt.query.TagPredicate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PropertyServiceTagPredicateTest : MongoIntegrationTest() {

    private lateinit var service: PropertyService

    @BeforeAll
    fun setup() {
        service = PropertyService(database)
        val docs = listOf(
            makeProperty("sp1", mapOf("domain" to "motor", "task" to "grasp")),
            makeProperty("sp2", mapOf("domain" to "neural")),
            makeProperty("sp3", mapOf("domain" to "motor", "draft" to "true")),
            makeProperty("sp4", emptyMap()),
        )
        service.collection.insertMany(docs.map { it.toDocument() })
    }

    private fun makeProperty(id: String, tags: Map<String, String>) = PropertyDocument(
        hdtId = HdtId("hdt-svc-test"),
        modelId = ModelId("model-svc-test"),
        propertyId = PropertyId(id),
        propertyName = PropertyName(id),
        description = "test",
        declaredType = "STRING",
        tags = tags,
    )

    @Test
    fun `findByTagPredicate returns only matching properties`() = runBlocking {
        val results = service.findByTagPredicate(TagPredicate.Eq("domain", "motor"))
        assertEquals(2, results.size)
        assertTrue(results.all { it.tags["domain"] == "motor" })
    }

    @Test
    fun `findByTagPredicate with And(emptyList) returns all properties`() = runBlocking {
        val results = service.findByTagPredicate(TagPredicate.And(emptyList()))
        assertEquals(4, results.size)
    }

    @Test
    fun `findByTagPredicate with Or(emptyList) returns no properties`() = runBlocking {
        val results = service.findByTagPredicate(TagPredicate.Or(emptyList()))
        assertTrue(results.isEmpty())
    }
}
