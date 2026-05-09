package org.burgas.service

import kotlinx.coroutines.Dispatchers
import org.burgas.dao.FileEntity
import org.burgas.database.DatabaseConnection
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class FileService : ReadPartFile<UUID, FileEntity> {

    override suspend fun findEntity(id: UUID): FileEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        FileEntity.findById(id)!!
    }
}