package com.feelvision.data.people

import android.graphics.Bitmap
import com.feelvision.domain.model.Person
import kotlinx.coroutines.flow.Flow

interface PeopleRepository {

    // ── Observe ───────────────────────────────────────────────────────
    fun getAllPeople(): Flow<List<Person>>

    // ── Read ──────────────────────────────────────────────────────────
    suspend fun getById(id: Long): Person?

    // ── Write ─────────────────────────────────────────────────────────
    suspend fun enroll(
        name     : String,
        relation : String,
        notes    : String = ""
    ): Person

    suspend fun updateInfo(
        id       : Long,
        name     : String,
        relation : String,
        notes    : String
    )

    suspend fun addPhoto(
        personId : Long,
        bitmap   : Bitmap,
        index    : Int
    ): Boolean

    suspend fun updateEmbedding(
        id        : Long,
        embedding : List<Float>
    )

    suspend fun delete(id: Long): Boolean

    // ── Photos ────────────────────────────────────────────────────────
    fun getPhotos(personId: Long): List<java.io.File>

    // ── Bootstrap ─────────────────────────────────────────────────────
    suspend fun load()
}