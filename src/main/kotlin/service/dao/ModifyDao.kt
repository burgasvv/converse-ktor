package org.burgas.service.dao

import org.burgas.dto.Request
import org.burgas.dto.Response

interface ModifyDao<R : Request, F : Response> {

    suspend fun update(request: R): F
}