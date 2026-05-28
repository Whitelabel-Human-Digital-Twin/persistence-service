package io.github.whdt

import com.mongodb.client.MongoClients
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Base class for MongoDB integration tests. Requires Docker to be running.
 * Starts a shared MongoDBContainer once per test class and creates a fresh database per instance.
 */
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class MongoIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val container = MongoDBContainer("mongo:7.0")
    }

    protected val client by lazy {
        MongoClients.create(container.connectionString)
    }

    protected val database by lazy {
        client.getDatabase("test")
    }
}
