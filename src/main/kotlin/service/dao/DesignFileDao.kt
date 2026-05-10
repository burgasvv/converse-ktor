package org.burgas.service.dao

import io.ktor.http.content.MultiPartData
import org.burgas.dao.Dao
import org.burgas.dto.FileRequest

interface DesignFileDao<D : Dao> {

    suspend fun uploadFiles(entity: D, multiPartData: MultiPartData)

    suspend fun removeFiles(entity: D, fileRequest: FileRequest)
}