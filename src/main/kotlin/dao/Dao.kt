package org.burgas.dao

import org.burgas.database.*
import org.jetbrains.exposed.dao.CompositeEntity
import org.jetbrains.exposed.dao.CompositeEntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

interface File

class FileEntity(id: EntityID<UUID>) : UUIDEntity(id), File {
    companion object : UUIDEntityClass<FileEntity>(FileTable)

    val name by FileTable.name
    val contentType by FileTable.contentType
    val preview by FileTable.preview
    val data by FileTable.data
}

class IdentityEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<IdentityEntity>(IdentityTable)

    val authority by IdentityTable.authority
    val username by IdentityTable.username
    val password by IdentityTable.password
    val email by IdentityTable.email
    val phone by IdentityTable.phone
    val status by IdentityTable.status
    val firstname by IdentityTable.firstname
    val lastname by IdentityTable.lastname
    val patronymic by IdentityTable.patronymic

    val files by FileEntity via IdentityFileTable
    val contacts by IdentityEntity via IdentityContactTable
}

class DialogueEntity(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : CompositeEntityClass<DialogueEntity>(DialogueTable)

    val created by DialogueTable.created
}

class DialogueMessageEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DialogueMessageEntity>(DialogueMessageTable)

    var dialogue by DialogueEntity referencedOn DialogueMessageTable
    val sender by IdentityEntity optionalReferencedOn DialogueMessageTable.senderId

    val text by DialogueMessageTable.text
    val created by DialogueMessageTable.created
}