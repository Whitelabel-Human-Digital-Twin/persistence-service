package db.property

import io.github.ktwinx.core.hdt.model.property.PropertyName

/**
 * Comparator for property-keyed responses. Names present in [order] sort by their assigned
 * ordinal; names absent from it (no HDT has assigned one) sort last, with an alphabetical
 * tiebreak shared by both groups.
 */
fun propertyOrderComparator(order: Map<PropertyName, Int>): Comparator<PropertyName> =
    compareBy({ order[it] ?: Int.MAX_VALUE }, { it.value })
