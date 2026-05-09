package org.burgas.service

import org.burgas.dao.File

interface ReadPartFile<ID, F : File> {

    suspend fun findEntity(id: ID): F
}