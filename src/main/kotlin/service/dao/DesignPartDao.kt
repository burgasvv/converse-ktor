package org.burgas.service.dao

import io.ktor.http.content.*
import org.burgas.dto.Request
import org.burgas.dto.Response

interface DesignPartDao<ID, R : Request, F : Response> {

    suspend fun create(entityRequest: R, parts: List<PartData>): F

    suspend fun delete(entityId: ID)
}