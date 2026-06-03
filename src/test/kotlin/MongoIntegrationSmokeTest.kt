import org.bson.Document
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MongoIntegrationSmokeTest : MongoIntegrationTest() {
    @Test
    fun `container starts and database is reachable`() {
        val pingResult = database.runCommand(Document("ping", 1))
        assertEquals(1.0, pingResult["ok"])
    }
}