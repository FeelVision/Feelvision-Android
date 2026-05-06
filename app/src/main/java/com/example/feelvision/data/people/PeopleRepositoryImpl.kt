package com.feelvision.data.people

import android.graphics.Bitmap
import com.feelvision.domain.model.Person
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeopleRepositoryImpl @Inject constructor(
    private val store: PeopleJsonStore
) : PeopleRepository {

    // ── Bootstrap ─────────────────────────────────────────────────────

    override suspend fun load() = store.load()

    // ── Observe ───────────────────────────────────────────────────────

    override fun getAllPeople(): Flow<List<Person>> = store.people

    // ── Read ──────────────────────────────────────────────────────────

    override suspend fun getById(id: Long): Person? = store.getById(id)

    // ── Write ─────────────────────────────────────────────────────────

    override suspend fun enroll(
        name     : String,
        relation : String,
        notes    : String
    ): Person {
        val person = Person(
            id        = System.currentTimeMillis(),
            name      = name.trim(),
            relation  = relation.trim(),
            notes     = notes.trim(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        return store.insert(person)
    }

    override suspend fun updateInfo(
        id       : Long,
        name     : String,
        relation : String,
        notes    : String
    ) {
        val existing = store.getById(id) ?: return
        store.update(
            existing.copy(
                name      = name.trim(),
                relation  = relation.trim(),
                notes     = notes.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun addPhoto(
        personId : Long,
        bitmap   : Bitmap,
        index    : Int
    ): Boolean {
        val existing = store.getById(personId) ?: return false
        // Save photo file to disk
        store.savePhoto(personId, bitmap, index)
        // Increment photo count on the person record
        store.update(
            existing.copy(
                photoCount = existing.photoCount + 1,
                updatedAt  = System.currentTimeMillis()
            )
        )
        return true
    }

    override suspend fun updateEmbedding(
        id        : Long,
        embedding : List<Float>
    ) {
        val existing = store.getById(id) ?: return
        store.update(
            existing.copy(
                embedding = embedding,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun delete(id: Long): Boolean {
        store.delete(id)
        return true
    }

    // ── Photos ────────────────────────────────────────────────────────

    override fun getPhotos(personId: Long): List<File> =
        store.getPhotosForPerson(personId)
}