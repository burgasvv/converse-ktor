package org.burgas.service.dao

import org.burgas.dto.Request
import org.burgas.dto.Response

interface DesignDao<ID, R : Request, F : Response> {

    suspend fun create(request: R): F

    suspend fun delete(id: ID)
}