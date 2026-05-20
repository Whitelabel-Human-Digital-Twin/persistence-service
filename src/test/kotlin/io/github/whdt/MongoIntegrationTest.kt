package io.github.whdt

import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Base class for MongoDB integration tests. Requires Docker to be running.
 * Starts a shared MongoDBContainer once per test class and creates a fresh database per instance.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class MongoIntegrationTest {

    protected lateinit var database: MongoDatabase
    private lateinit var client: com.mongodb.client.MongoClient

    companion object {
        @JvmStatic
        val container: MongoDBContainer = MongoDBContainer("mongo:7.0").apply { start() }
    }

    @BeforeAll
    fun setupDatabase() {
        client = MongoClients.create(container.connectionString)
        database = client.getDatabase("test-${System.currentTimeMillis()}")
    }

    @AfterAll
    fun tearDownDatabase() {
        client.close()
    }
}
