package routing.query.event.comparison.dto

import io.github.ktwinx.core.hdt.model.property.PropertyName
import io.github.ktwinx.core.hdt.model.property.PropertyValue
import routing.query.event.comparison.PropertyComparison
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class PropertyType {
    INT,
    STRING,
    BOOLEAN,
    DOUBLE,
}

fun PropertyComparisonDto.toDomain(
    propertyType: PropertyType
): PropertyComparison {

    val json = value.jsonPrimitive

    val typedValue = when (propertyType) {
        PropertyType.DOUBLE -> PropertyValue.DoublePropertyValue(json.double)
        PropertyType.INT -> PropertyValue.IntPropertyValue(json.int)
        PropertyType.BOOLEAN -> PropertyValue.BooleanPropertyValue(json.boolean)
        PropertyType.STRING -> PropertyValue.StringPropertyValue(json.content)
    }

    return PropertyComparison(
        propertyName = PropertyName(propertyName),
        comparison = comparison,
        value = typedValue
    )
}

fun inferPropertyType(value: JsonElement): PropertyType {
    val primitive = value.jsonPrimitive

    return when {
        primitive.isString -> PropertyType.STRING
        primitive.booleanOrNull != null -> PropertyType.BOOLEAN
        primitive.intOrNull != null -> PropertyType.INT
        primitive.doubleOrNull != null -> PropertyType.DOUBLE
        else -> PropertyType.STRING
    }
}