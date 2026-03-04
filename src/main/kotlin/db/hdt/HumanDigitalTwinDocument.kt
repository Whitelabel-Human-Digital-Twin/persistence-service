package io.github.whdt.db.hdt

import io.github.whdt.core.hdt.HumanDigitalTwin
import io.github.whdt.core.hdt.interfaces.digital.DigitalInterface
import io.github.whdt.core.hdt.interfaces.physical.PhysicalInterface
import io.github.whdt.core.hdt.model.id.HdtId
import io.github.whdt.core.hdt.storage.Storage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bson.Document


@Serializable
data class HumanDigitalTwinDocument(
    val hdtId: HdtId,
    val physicalInterfaces: List<PhysicalInterface>,
    val digitalInterfaces: List<DigitalInterface>,
    val storages: List<Storage>,
) {

    fun toDocument(): Document = Document.parse(Json.encodeToString(serializer(), this))

    companion object {
        fun fromHumanDigitalTwin(hdt: HumanDigitalTwin): HumanDigitalTwinDocument =
            HumanDigitalTwinDocument(
                hdtId = hdt.hdtId,
                physicalInterfaces = hdt.physicalInterfaces,
                digitalInterfaces = hdt.digitalInterfaces,
                storages = hdt.storages,
            )
        fun fromDocument(document: Document): HumanDigitalTwinDocument {
            val copy = Document(document)
            copy.remove("_id")
            return Json.decodeFromString(serializer(), copy.toJson())
        }
    }
}