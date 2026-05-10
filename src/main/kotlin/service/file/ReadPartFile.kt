package org.burgas.service.file

import org.burgas.dao.File

interface ReadPartFile<ID, F : File> {

    suspend fun findEntity(id: ID): F
}