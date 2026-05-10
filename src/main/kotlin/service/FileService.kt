package org.burgas.service

import io.ktor.http.content.PartData
import kotlinx.coroutines.Dispatchers
import org.burgas.dao.FileEntity
import org.burgas.database.DatabaseConnection
import org.burgas.service.file.DesignPartFile
import org.burgas.service.file.DesignPartImage
import org.burgas.service.file.ReadPartFile
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.UUID

class FileService : ReadPartFile<UUID, FileEntity>, DesignPartFile<UUID, FileEntity>, DesignPartImage<FileEntity> {

    override suspend fun findEntity(id: UUID): FileEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        FileEntity.findById(id)!!
    }

    override suspend fun create(partData: PartData): FileEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        FileEntity.new { this.upload(partData) }
    }

    override suspend fun delete(id: UUID) = newSuspendedTransaction {
        val fileEntity = findEntity(id)
        fileEntity.delete()
    }

    override suspend fun createPreview(partData: PartData): FileEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        if (partData.contentType!!.contentType.startsWith("image")) {
            val fileEntity = create(partData)
            fileEntity.apply { this.preview = true }
            fileEntity
        } else {
            throw IllegalArgumentException("Wrong file content type, must be image")
        }
    }

    override suspend fun makePreview(file: FileEntity): FileEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        if (file.contentType.startsWith("image")) {
            file.preview = true
            file
        } else {
            throw IllegalArgumentException("Wrong file content type, must be image")
        }
    }
}