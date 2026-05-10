package org.burgas.service.dao

import org.burgas.dto.Response

interface ListDao<F : Response> {

    suspend fun findAll(): List<F>
}