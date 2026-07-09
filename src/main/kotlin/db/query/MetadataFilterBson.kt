package db.query

import com.mongodb.client.model.Filters
import org.bson.conversions.Bson

/**
 * Translates a metadata filter map (key -> allowed values) into a conjunction of `$in`
 * predicates over `<metadataField>.<key>`, one per key. Mirrors the `$in` case of
 * [io.github.ktwinx.core.hdt.query.TagPredicate.toBson] but over a flat `Map` instead of a
 * predicate AST, since `metadataFilters` is already interpreted as an implicit AND of `$in`s.
 *
 * An empty map means "no filter"; returns null so callers can omit it from their filter list.
 */
fun Map<String, List<String>>.toMetadataBson(metadataField: String = "metadata"): Bson? {
    if (isEmpty()) return null
    val filters = map { (key, values) -> Filters.`in`("$metadataField.$key", values) }
    return if (filters.size == 1) filters.first() else Filters.and(filters)
}
