package com.feelvision.data.people

import com.feelvision.domain.model.Person
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeopleRepositoryImpl @Inject constructor(
    private val dao: PeopleDao
) : PeopleRepository {

    override fun getAllPeople(): Flow<List<Person>> =
        dao.getAllFlow().map { list -> list.map { it.toDomain() } }

    override suspend fun enroll(name: String, relation: String): Long =
        dao.insert(PersonEntity(name = name.trim(), relation = relation.trim()))

    override suspend fun addPhoto(id: Long) {
        // increment photo count — load entity first via a query
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    private fun PersonEntity.toDomain() = Person(
        id = id, name = name, relation = relation, photoCount = photoCount
    )
}