package io.github.whdt.db.view

import io.github.whdt.core.hdt.query.TagPredicate
import io.github.whdt.core.hdt.view.View
import io.github.whdt.core.hdt.view.ViewName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bson.Document

@Serializable
data class ViewDocument(
    val name: ViewName,
    val predicate: TagPredicate? = null,
    val groupByKeys: List<String> = emptyList(),
) {
    fun toView(): View = View(name = name, predicate = predicate, groupByKeys = groupByKeys)

    fun toDocument(): Document = Document.parse(Json.encodeToString(serializer(), this))

    companion object {
        fun fromView(view: View): ViewDocument =
            ViewDocument(name = view.name, predicate = view.predicate, groupByKeys = view.groupByKeys)

        fun fromDocument(doc: Document): ViewDocument {
            val copy = Document(doc)
            copy.remove("_id")
            return Json.decodeFromString(serializer(), copy.toJson())
        }
    }
}
