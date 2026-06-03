package db.query

import com.mongodb.client.MongoCollection
import MongoIntegrationTest
import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.model.ModelId
import io.github.ktwinx.core.hdt.model.property.PropertyId
import io.github.ktwinx.core.hdt.model.property.PropertyName
import io.github.ktwinx.core.hdt.query.TagPredicate
import db.property.PropertyDocument
import io.github.ktwinx.core.hdt.query.matches
import org.bson.Document
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TagPredicateBsonTest : MongoIntegrationTest() {

    private lateinit var collection: MongoCollection<Document>
    private lateinit var fixtures: List<PropertyDocument>

    @BeforeAll
    fun setup() {
        collection = database.getCollection("tag_predicate_test_properties")
        fixtures = listOf(
            makeProperty("p1", mapOf("domain" to "motor", "task" to "grasp")),
            makeProperty("p2", mapOf("domain" to "motor", "task" to "push")),
            makeProperty("p3", mapOf("domain" to "neural")),
            makeProperty("p4", mapOf("domain" to "neural", "draft" to "true")),
            makeProperty("p5", mapOf("task" to "grasp", "visit" to "1")),
            makeProperty("p6", mapOf("visit" to "2")),
            makeProperty("p7", emptyMap()),
        )
        collection.insertMany(fixtures.map { it.toDocument() })
    }

    private fun makeProperty(id: String, tags: Map<String, String>) = PropertyDocument(
        hdtId = HdtId("hdt-test"),
        modelId = ModelId("model-test"),
        propertyId = PropertyId(id),
        propertyName = PropertyName(id),
        description = "test",
        declaredType = "STRING",
        tags = tags,
    )

    private fun assertResultsAgree(p: TagPredicate) {
        val mongoIds = collection.find(p.toBson()).toList()
            .mapNotNull { PropertyDocument.fromDocument(it)?.propertyId?.value }.toSet()
        val inMemoryIds = fixtures.filter { p.matches(it.tags) }.map { it.propertyId.value }.toSet()
        assertEquals(inMemoryIds, mongoIds, "Mongo and matches() disagreed for: $p")
    }

    @Test
    fun `Eq matches only properties with that exact tag value`() = assertResultsAgree(
        TagPredicate.Eq("domain", "motor"),
    )

    @Test
    fun `In matches properties whose tag value is in the set`() = assertResultsAgree(
        TagPredicate.In("task", setOf("grasp", "push")),
    )

    @Test
    fun `Has matches properties that carry the tag at all`() = assertResultsAgree(
        TagPredicate.Has("visit"),
    )

    @Test
    fun `And of two terms matches the intersection`() = assertResultsAgree(
        TagPredicate.And(listOf(TagPredicate.Eq("domain", "motor"), TagPredicate.Has("task"))),
    )

    @Test
    fun `Or of two terms matches the union`() = assertResultsAgree(
        TagPredicate.Or(listOf(TagPredicate.Eq("domain", "motor"), TagPredicate.Eq("domain", "neural"))),
    )

    @Test
    fun `Not matches properties NOT satisfying the inner predicate including missing field`() = assertResultsAgree(
        TagPredicate.Not(TagPredicate.Eq("domain", "motor")),
    )

    @Test
    fun `Not(Has) matches properties missing the tag entirely`() = assertResultsAgree(
        TagPredicate.Not(TagPredicate.Has("domain")),
    )

    @Test
    fun `And(emptyList) matches every document`() = assertResultsAgree(
        TagPredicate.And(emptyList()),
    )

    @Test
    fun `Or(emptyList) matches no documents`() = assertResultsAgree(
        TagPredicate.Or(emptyList()),
    )

    @Test
    fun `nested And(Or(Eq, Eq), Not(Has)) matches expected subset`() = assertResultsAgree(
        TagPredicate.And(listOf(
            TagPredicate.Or(listOf(
                TagPredicate.Eq("domain", "motor"),
                TagPredicate.Eq("domain", "neural"),
            )),
            TagPredicate.Not(TagPredicate.Has("draft")),
        )),
    )

    @Test
    fun `Has matches property with tag regardless of value`() = assertResultsAgree(
        TagPredicate.Has("task"),
    )
}
