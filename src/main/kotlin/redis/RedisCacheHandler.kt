package org.burgas.redis

import org.burgas.dao.Dao

interface RedisCacheHandler<D : Dao> {

    suspend fun handleCache(entity: D)
}