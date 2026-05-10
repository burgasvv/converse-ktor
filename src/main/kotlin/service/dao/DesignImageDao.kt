package org.burgas.service.dao

import io.ktor.http.content.*
import org.burgas.dao.Dao

interface DesignImageDao<ID, D : Dao> {

    suspend fun createPreviewImage(entity: D, multiPartData: MultiPartData)

    suspend fun makePreviewImage(entity: D, imageId: ID)
}