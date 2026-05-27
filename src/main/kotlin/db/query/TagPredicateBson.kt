package io.github.whdt.db.query

import com.mongodb.client.model.Filters
import io.github.whdt.core.hdt.query.TagPredicate
import org.bson.Document
import org.bson.conversions.Bson

/**
 * Match-all filter: an empty BSON document. MongoDB treats this as "no constraints".
 * Used to translate `TagPredicate.And(emptyList())` (vacuous AND ≡ true).
 */
private val MATCH_ALL: Bson = Document()

/**
 * Match-none filter: `$expr: { $literal: false }`. Evaluates to false on every document
 * without relying on a sentinel field name. Used to translate `TagPredicate.Or(emptyList())`
 * (vacuous OR ≡ false).
 */
private val MATCH_NONE: Bson = Document("\$expr", Document("\$literal", false))

/**
 * Translates a [TagPredicate] (whdt-core boolean algebra over tags) into a MongoDB [Bson] filter
 * that, applied to a collection of [io.github.whdt.db.property.PropertyDocument]s, returns exactly
 * those whose `tags` map satisfies the predicate.
 *
 * Correctness: for any predicate `p` and any document `d`,
 *   `p.matches(d.tags) == (d ∈ collection.find(p.toBson()))`.
 *
 * @param tagsField the BSON document field that holds the tag map. Defaults to `"tags"`;
 *                  callers using a different field name (e.g. embedded in a sub-document) override.
 */
fun TagPredicate.toBson(tagsField: String = "tags"): Bson = when (this) {
    is TagPredicate.Eq  -> Filters.eq("$tagsField.$key", value)
    is TagPredicate.In  -> Filters.`in`("$tagsField.$key", values)
    is TagPredicate.Has -> Filters.exists("$tagsField.$key", true)
    is TagPredicate.And -> if (terms.isEmpty()) MATCH_ALL  else Filters.and(terms.map { it.toBson(tagsField) })
    is TagPredicate.Or  -> if (terms.isEmpty()) MATCH_NONE else Filters.or(terms.map { it.toBson(tagsField) })
    is TagPredicate.Not -> Filters.not(term.toBson(tagsField))
}
