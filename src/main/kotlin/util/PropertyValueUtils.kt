package io.github.whdt.util

import io.github.whdt.core.hdt.model.property.PropertyValue

fun PropertyValue.unwrapAndStringify(): String {
    return when (this) {
        is PropertyValue.FloatPropertyValue -> value.toString()
        is PropertyValue.DoublePropertyValue -> value.toString()
        is PropertyValue.IntPropertyValue -> value.toString()
        is PropertyValue.LongPropertyValue -> value.toString()
        is PropertyValue.StringPropertyValue -> value
        is PropertyValue.BooleanPropertyValue -> value.toString()
        is PropertyValue.EmptyPropertyValue -> "empty-property-value"
    }
}