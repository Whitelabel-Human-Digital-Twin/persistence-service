package io.github.whdt.db.query

import com.mongodb.client.model.Filters
import io.github.whdt.core.hdt.query.TagPredicate
import org.bson.Document
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TagPredicateBsonStructureTest {

    @Test
    fun `Eq produces field-value equality filter on tags subpath`() {
        val bson = TagPredicate.Eq("k", "v").toBson()
        assertNotNull(bson)
        assertEquals(Filters.eq("tags.k", "v").toBsonDocument(), bson.toBsonDocument())
    }

    @Test
    fun `And(emptyList) produces match-all empty Document`() {
        val bson = TagPredicate.And(emptyList()).toBson()
        assertEquals(Document(), bson)
    }

    @Test
    fun `Or(emptyList) produces match-none document with dollar-expr key`() {
        val bson = TagPredicate.Or(emptyList()).toBson() as Document
        assertTrue(bson.containsKey("\$expr"), "Expected \$expr key in match-none filter")
    }

    @Test
    fun `tagsField parameter is forwarded into nested subpath`() {
        val bson = TagPredicate.Eq("x", "y").toBson(tagsField = "meta")
        assertEquals(Filters.eq("meta.x", "y").toBsonDocument(), bson.toBsonDocument())
    }
}
