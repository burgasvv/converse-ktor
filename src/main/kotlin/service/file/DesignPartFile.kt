package org.burgas.service.file

import io.ktor.http.content.PartData
import org.burgas.dao.File

interface DesignPartFile<ID, F : File> {

    suspend fun create(partData: PartData): F

    suspend fun delete(id: ID)
}