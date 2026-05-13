package io.github.whdt.db.assembler

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.interfaces.digital.DigitalInterface
import io.github.whdt.core.hdt.interfaces.physical.PhysicalInterface
import io.github.whdt.core.hdt.model.property.PropertyValue
import io.github.whdt.core.hdt.storage.Storage
import io.github.whdt.db.hdt.HdtService
import io.github.whdt.db.model.ModelService
import io.github.whdt.db.property.PropertyObservationService
import io.github.whdt.db.property.PropertyService
import io.github.whdt.db.util.Err
import io.github.whdt.db.util.Ok
import io.github.whdt.db.util.OperationResult
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class HdtSpecResponse(
    val hdtId: String,
    val physicalInterfaces: List<PhysicalInterface>,
    val digitalInterfaces: List<DigitalInterface>,
    val storages: List<Storage>,
    val models: List<ModelSpecEntry>,
    val metadata: Map<String, String>,
)

@Serializable
data class ModelSpecEntry(
    val modelId: String,
    val modelName: String,
    val properties: List<PropertySpecEntry>,
)

@Serializable
data class PropertySpecEntry(
    val propertyId: String,
    val propertyName: String,
    val description: String,
    val declaredType: String,
    val initialValue: PropertyValue? = null,
    val metadata: Map<String, String>,
)

@Serializable
data class PropertySnapshotEntry(
    val propertyId: String,
    val propertyName: String,
    val value: PropertyValue?,
    val timestamp: Instant?,
    val source: String,
)

class AssemblerService(
    private val hdtService: HdtService,
    private val modelService: ModelService,
    private val propertyService: PropertyService,
    private val observationService: PropertyObservationService,
) {
    suspend fun getFullSpec(hdtId: HdtId): OperationResult<HdtSpecResponse> {
        val hdt = hdtService.findAll().firstOrNull { it.hdtId.id == hdtId.id }
            ?: return Err("HDT not found: ${hdtId.id}")

        val models = modelService.findByHdtId(hdtId)
        val properties = propertyService.findByHdtId(hdtId)
        val propertiesByModel = properties.groupBy { it.modelId.value }

        val modelEntries = models.map { model ->
            val modelProps = propertiesByModel[model.modelId.value] ?: emptyList()
            ModelSpecEntry(
                modelId = model.modelId.value,
                modelName = model.modelName.value,
                properties = modelProps.map { prop ->
                    PropertySpecEntry(
                        propertyId = prop.propertyId.value,
                        propertyName = prop.propertyName.value,
                        description = prop.description,
                        declaredType = prop.declaredType,
                        initialValue = prop.initialValue,
                        metadata = prop.metadata,
                    )
                }
            )
        }

        return Ok(
            HdtSpecResponse(
                hdtId = hdt.hdtId.id,
                physicalInterfaces = hdt.physicalInterfaces,
                digitalInterfaces = hdt.digitalInterfaces,
                storages = hdt.storages,
                models = modelEntries,
                metadata = hdt.metadata,
            )
        )
    }

    suspend fun getSnapshot(hdtId: HdtId): OperationResult<List<PropertySnapshotEntry>> {
        val properties = propertyService.findByHdtId(hdtId)
        if (properties.isEmpty()) {
            return Err("No property specs found for HDT: ${hdtId.id}")
        }

        val latestObservations = observationService.latestPerProperty(hdtId)

        val snapshot = properties.map { prop ->
            val obs = latestObservations[prop.propertyId]
            PropertySnapshotEntry(
                propertyId = prop.propertyId.value,
                propertyName = prop.propertyName.value,
                value = obs?.value ?: prop.initialValue,
                timestamp = obs?.timeField,
                source = if (obs != null) "observation" else "initial_value",
            )
        }

        return Ok(snapshot)
    }
}
