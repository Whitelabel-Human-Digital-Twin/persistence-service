package io.github.whdt.db.model

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.Model
import io.github.whdt.core.hdt.model.ModelDescription
import io.github.whdt.core.hdt.model.ModelId
import io.github.whdt.core.hdt.model.ModelName
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
    val lastUpdated: Instant = Clock.System.now(),
) {
    fun toDocument(): Document = Document.parse(Json.encodeToString(serializer(), this))

    companion object {
        fun fromWhdtModel(model: Model): ModelDocument {
            return ModelDocument(
                hdtId = model.hdtId,
                modelId = model.id,
                modelName = model.name,
                modelDescription = model.description,
            )
        }
        fun fromDocument(document: Document): ModelDocument {
            val copy = Document(document)
            copy.remove("_id")
            return Json.decodeFromString(serializer(), copy.toJson())
        }
    }
}