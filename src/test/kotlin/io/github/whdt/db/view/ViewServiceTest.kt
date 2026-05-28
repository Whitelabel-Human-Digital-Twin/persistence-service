package io.github.whdt.db.view

import io.github.whdt.MongoIntegrationTest
import io.github.whdt.core.hdt.query.TagPredicate
import io.github.whdt.core.hdt.view.View
import io.github.whdt.core.hdt.view.ViewName
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ViewServiceTest : MongoIntegrationTest() {

    private lateinit var service: ViewService

    @BeforeAll
    fun setup() {
        service = ViewService(database)
    }

    @Test
    fun `findAll includes a newly upserted view`() = runBlocking {
        val uniqueName = "findall-check-${System.currentTimeMillis()}"
        val before = service.findAll().size
        service.upsert(View(name = ViewName(uniqueName), predicate = null, groupByKeys = emptyList()))
        val after = service.findAll()
        assertEquals(before + 1, after.size)
        assertTrue(after.any { it.name.value == uniqueName })
    }

    @Test
    fun `upsert then findByName returns the stored view`() = runBlocking {
        val view = View(name = ViewName("my-view"), predicate = null, groupByKeys = emptyList())
        service.upsert(view)
        val found = service.findByName(ViewName("my-view"))
        assertNotNull(found)
        assertEquals(ViewName("my-view"), found.name)
        assertNull(found.predicate)
        assertEquals(emptyList(), found.groupByKeys)
    }

    @Test
    fun `upsert with predicate and groupByKeys round-trips correctly`() = runBlocking {
        val predicate = TagPredicate.Eq("domain", "motor")
        val view = View(
            name = ViewName("motor-view"),
            predicate = predicate,
            groupByKeys = listOf("domain", "task"),
        )
        service.upsert(view)
        val found = service.findByName(ViewName("motor-view"))
        assertNotNull(found)
        assertEquals(predicate, found.predicate)
        assertEquals(listOf("domain", "task"), found.groupByKeys)
    }

    @Test
    fun `upsert overwrites an existing view with the same name`() = runBlocking {
        val name = ViewName("overwrite-test")
        service.upsert(View(name = name, predicate = null, groupByKeys = listOf("old-key")))
        service.upsert(View(name = name, predicate = TagPredicate.Has("x"), groupByKeys = listOf("new-key")))

        val found = service.findByName(name)
        assertNotNull(found)
        assertEquals(listOf("new-key"), found.groupByKeys)
        assertEquals(TagPredicate.Has("x"), found.predicate)
    }

    @Test
    fun `findAll returns all stored views`() = runBlocking {
        service.upsert(View(name = ViewName("list-a"), predicate = null, groupByKeys = emptyList()))
        service.upsert(View(name = ViewName("list-b"), predicate = null, groupByKeys = emptyList()))
        val all = service.findAll()
        val names = all.map { it.name.value }
        assertTrue(names.contains("list-a"))
        assertTrue(names.contains("list-b"))
    }

    @Test
    fun `findByName returns null for unknown name`() = runBlocking {
        val result = service.findByName(ViewName("does-not-exist"))
        assertNull(result)
    }

    @Test
    fun `delete returns true then false on second call`() = runBlocking {
        val name = ViewName("to-delete")
        service.upsert(View(name = name, predicate = null, groupByKeys = emptyList()))
        assertTrue(service.delete(name))
        assertFalse(service.delete(name))
        assertNull(service.findByName(name))
    }
}
