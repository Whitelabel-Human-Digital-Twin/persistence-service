package db.model

import MongoIntegrationTest
import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.model.Model
import io.github.ktwinx.core.hdt.model.ModelDescription
import io.github.ktwinx.core.hdt.model.ModelName
import io.github.ktwinx.core.hdt.model.property.Property
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ModelServiceTest : MongoIntegrationTest() {

    private lateinit var service: ModelService

    private fun makeModel(hdtId: String, name: String) = Model(
        hdtId = HdtId(hdtId),
        name = ModelName(name),
        description = ModelDescription("Test model $name"),
        properties = listOf<Property>(),
    )

    @BeforeAll
    fun setup() = runBlocking {
        service = ModelService(database)

        service.insertMany(
            listOf(
                makeModel("hdt-model-a", "acc"),
                makeModel("hdt-model-b", "acc"),
                makeModel("hdt-model-c", "gyro"),
            )
        )
        Unit
    }

    @Test
    fun `findByName returns exactly the models sharing the given name across HDTs`() = runBlocking {
        val result = service.findByName(ModelName("acc"))

        assertEquals(setOf("hdt-model-a", "hdt-model-b"), result.map { it.hdtId.id }.toSet())
        assertEquals(2, result.size)
        result.forEach { assertEquals(ModelName("acc"), it.modelName) }
    }
}
