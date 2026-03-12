package io.github.whdt.db.record

import org.bson.Document

interface Record {
    val id: String
}

interface MongoRecord : Record {
    fun fromDocument(document: Document): Record
    fun toDocument(): Document
}