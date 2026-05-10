package org.burgas.service.dao

import org.burgas.dao.Dao
import org.burgas.dto.Response

interface ReadDao<ID, E : Dao, F : Response> {

    suspend fun findEntity(id: ID): E

    suspend fun findById(id: ID): F
}