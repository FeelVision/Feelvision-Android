package com.feelvision.data.people

import com.feelvision.domain.model.Person
import kotlinx.coroutines.flow.Flow

interface PeopleRepository {
    fun getAllPeople(): Flow<List<Person>>
    suspend fun enroll(name: String, relation: String): Long
    suspend fun addPhoto(id: Long)
    suspend fun delete(id: Long)
}