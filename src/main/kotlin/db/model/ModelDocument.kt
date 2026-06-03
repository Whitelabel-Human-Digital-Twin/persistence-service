package db.model

import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.model.Format
import io.github.ktwinx.core.hdt.model.Model
import io.github.ktwinx.core.hdt.model.ModelDescription
import io.github.ktwinx.core.hdt.model.ModelId
import io.github.ktwinx.core.hdt.model.ModelName
import io.github.ktwinx.core.hdt.model.WellKnownFormats
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bson.Document
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
data class ModelDocument(
    val hdtId: HdtId,
    val modelId: ModelId,
    val modelName: ModelName,
    val modelDescription: ModelDescription,
    val tags: Map<String, String> = emptyMap(),
    val format: Format = WellKnownFormats.UNSPECIFIED,
    val lastUpdated: Instant = Clock.System.now(),
) {
    fun toDocument(): Document = Document.parse(Json.encodeToString(serializer(), this))

    companion object {
        fun fromktwinxModel(model: Model): ModelDocument = ModelDocument(
            hdtId = model.hdtId,
            modelId = model.id,
            modelName = model.name,
            modelDescription = model.description,
            tags = model.tags,
            format = model.format,
        )

        fun fromDocument(document: Document): ModelDocument {
            val copy = Document(document)
            copy.remove("_id")
            return Json.decodeFromString(serializer(), copy.toJson())
        }
    }
}
