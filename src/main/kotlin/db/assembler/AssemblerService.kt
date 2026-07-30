package db.assembler

import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.interfaces.digital.DigitalInterface
import io.github.ktwinx.core.hdt.interfaces.physical.PhysicalInterface
import io.github.ktwinx.core.hdt.model.Format
import io.github.ktwinx.core.hdt.model.WellKnownFormats
import io.github.ktwinx.core.hdt.model.property.Coding
import io.github.ktwinx.core.hdt.model.property.PropertyValue
import io.github.ktwinx.core.hdt.storage.Storage
import db.hdt.HdtService
import db.model.ModelService
import db.property.PropertyObservationService
import db.property.PropertyService
import db.property.propertyOrderComparator
import db.util.Err
import db.util.Ok
import db.util.OperationResult
import kotlinx.serialization.Serializable
import kotlin.text.get
import kotlin.time.Instant

@Serializable
data class HdtSpecResponse(
    val hdtId: String,
    val physicalInterfaces: List<PhysicalInterface>,
    val digitalInterfaces: List<DigitalInterface>,
    val storages: List<Storage>,
    val models: List<ModelSpecEntry>,
    val tags: Map<String, String>,
)

@Serializable
data class ModelSpecEntry(
    val modelId: String,
    val modelName: String,
    val properties: List<PropertySpecEntry>,
    val tags: Map<String, String> = emptyMap(),
    val format: Format = WellKnownFormats.UNSPECIFIED,
)

@Serializable
data class PropertySpecEntry(
    val propertyId: String,
    val propertyName: String,
    val description: String,
    val declaredType: String,
    val initialValue: PropertyValue? = null,
    val tags: Map<String, String> = emptyMap(),
    val coding: Coding? = null,
    val ordinal: Int = -1,
)

@Serializable
data class PropertySnapshotEntry(
    val propertyId: String,
    val propertyName: String,
    val value: PropertyValue?,
    val timestamp: Instant?,
    val source: String,
)

@Serializable
data class TaskPropertySnapshotEntry(
    val task: String?,
    val propertyId: String,
    val propertyName: String,
    val modelName: String,
    val value: PropertyValue,
    val timestamp: Instant,
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
                        tags = prop.tags,
                        coding = prop.coding,
                        ordinal = prop.ordinal,
                    )
                },
                tags = model.tags,
                format = model.format,
            )
        }

        return Ok(
            HdtSpecResponse(
                hdtId = hdt.hdtId.id,
                physicalInterfaces = hdt.physicalInterfaces,
                digitalInterfaces = hdt.digitalInterfaces,
                storages = hdt.storages,
                models = modelEntries,
                tags = hdt.tags,
            )
        )
    }

    suspend fun getSnapshotByTask(hdtId: HdtId): OperationResult<List<TaskPropertySnapshotEntry>> {
        val latest = observationService.latestPerPropertyAndTask(hdtId)
        val entries = latest.map { (task, obs) ->
            TaskPropertySnapshotEntry(
                task = task,
                propertyId = obs.metaField.propertyId.value,
                propertyName = obs.metaField.propertyName.value,
                modelName = obs.metaField.modelName.value,
                value = obs.value,
                timestamp = obs.timeField,
            )
        }
        return Ok(entries)
    }

    suspend fun getSnapshot(hdtId: HdtId): OperationResult<List<PropertySnapshotEntry>> {
        val properties = propertyService.findByHdtId(hdtId)
        if (properties.isEmpty()) {
            return Err("No property specs found for HDT: ${hdtId.id}")
        }

        val latestObservations = observationService.latestPerProperty(hdtId)

        // Each property's own ordinal is authoritative here: this is a single HDT, so there's
        // no cross-DT ambiguity to resolve (unlike the canonical order used by cohort responses).
        val order = properties.filter { it.ordinal >= 0 }.associate { it.propertyName to it.ordinal }
        val orderedProperties = properties.sortedWith(compareBy(propertyOrderComparator(order)) { it.propertyName })

        val snapshot = orderedProperties.map { prop ->
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
