package org.burgas.service.file

import io.ktor.http.content.PartData
import org.burgas.dao.File

interface DesignPartImage<F : File> {

    suspend fun createPreview(partData: PartData): F

    suspend fun makePreview(file: F): F
}