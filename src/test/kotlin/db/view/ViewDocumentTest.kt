package db.view

import io.github.ktwinx.core.hdt.query.TagPredicate
import io.github.ktwinx.core.hdt.view.View
import io.github.ktwinx.core.hdt.view.ViewName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ViewDocumentTest {

    @Test
    fun `fromView and toView round-trip preserves name and empty fields`() {
        val view = View(name = ViewName("simple"), predicate = null, groupByKeys = emptyList())
        val doc = ViewDocument.fromView(view)
        val restored = doc.toView()
        assertEquals(view.name, restored.name)
        assertNull(restored.predicate)
        assertEquals(emptyList(), restored.groupByKeys)
    }

    @Test
    fun `fromView and toView round-trip preserves non-null predicate`() {
        val predicate = TagPredicate.Eq("domain", "motor")
        val view = View(name = ViewName("with-predicate"), predicate = predicate, groupByKeys = emptyList())
        val doc = ViewDocument.fromView(view)
        val restored = doc.toView()
        assertEquals(predicate, restored.predicate)
    }

    @Test
    fun `fromView and toView round-trip preserves multi-key groupByKeys`() {
        val view = View(
            name = ViewName("grouped"),
            predicate = null,
            groupByKeys = listOf("domain", "task", "unit"),
        )
        val doc = ViewDocument.fromView(view)
        val restored = doc.toView()
        assertEquals(listOf("domain", "task", "unit"), restored.groupByKeys)
    }

    @Test
    fun `toDocument and fromDocument round-trip preserves all fields`() {
        val predicate = TagPredicate.And(
            listOf(TagPredicate.Eq("domain", "neural"), TagPredicate.Has("task"))
        )
        val original = ViewDocument(
            name = ViewName("complex"),
            predicate = predicate,
            groupByKeys = listOf("domain", "task"),
        )
        val bson = original.toDocument()
        val restored = ViewDocument.fromDocument(bson)
        assertEquals(original.name, restored.name)
        assertEquals(original.predicate, restored.predicate)
        assertEquals(original.groupByKeys, restored.groupByKeys)
    }

    @Test
    fun `toDocument and fromDocument round-trip with null predicate`() {
        val original = ViewDocument(name = ViewName("flat-view"), predicate = null, groupByKeys = emptyList())
        val bson = original.toDocument()
        val restored = ViewDocument.fromDocument(bson)
        assertEquals(original, restored)
    }

    @Test
    fun `fromDocument strips _id field`() {
        val original = ViewDocument(name = ViewName("test"), predicate = null, groupByKeys = emptyList())
        val bson = original.toDocument()
        bson["_id"] = "some-id"
        val restored = ViewDocument.fromDocument(bson)
        assertEquals(original, restored)
    }
}
