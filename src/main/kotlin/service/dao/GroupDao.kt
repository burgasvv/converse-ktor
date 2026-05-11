package org.burgas.service.dao

import org.burgas.dto.GroupRequest

interface GroupDao {

    suspend fun join(groupRequest: GroupRequest)

    suspend fun out(groupRequest: GroupRequest)

    suspend fun removeAdminStatus(groupRequest: GroupRequest)
}