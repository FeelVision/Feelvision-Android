package com.feelvision.data.people

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import com.feelvision.domain.model.Person
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeopleJsonStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val peopleDir  = File(context.filesDir, "people")
    private val jsonFile   = File(peopleDir, "people.json")
    private val photosDir  = File(peopleDir, "photos")

    // Permanent external backup directory that survives app uninstallation
    private val backupDir  = File(Environment.getExternalStorageDirectory(), "FeelVision/people")
    private val backupJson = File(backupDir, "people.json")
    private val backupPhotos = File(backupDir, "photos")

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val mutex      = Mutex()

    // In-memory cache — single source of truth
    private val _people = MutableStateFlow<List<Person>>(emptyList())
    val people: Flow<List<Person>> = _people.asStateFlow()

    // ── Bootstrap ─────────────────────────────────────────────────────

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            peopleDir.mkdirs()
            photosDir.mkdirs()

            // Auto-restore: If local database doesn't exist but backup does, copy it over!
            if (!jsonFile.exists() && backupJson.exists()) {
                restoreFromBackup()
            }

            _people.value = readFromDisk()
        }
    }

    // ── Backup & Restore ──────────────────────────────────────────────

    private fun backupToDisk() {
        try {
            // Only proceed if permission is granted, otherwise return gracefully to avoid spamming exceptions
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                if (!android.os.Environment.isExternalStorageManager()) {
                    Log.w("PeopleJsonStore", "Skipping backup: MANAGE_EXTERNAL_STORAGE permission not granted yet.")
                    return
                }
            }

            if (!backupDir.exists() && !backupDir.mkdirs()) {
                Log.w("PeopleJsonStore", "Failed to create backup directory: ${backupDir.absolutePath}")
                return
            }
            if (!backupPhotos.exists() && !backupPhotos.mkdirs()) {
                Log.w("PeopleJsonStore", "Failed to create backup photos directory: ${backupPhotos.absolutePath}")
                return
            }

            if (jsonFile.exists()) {
                jsonFile.copyTo(backupJson, overwrite = true)
            }
            if (photosDir.exists()) {
                photosDir.copyRecursively(backupPhotos, overwrite = true)
            }
        } catch (e: Exception) {
            Log.e("PeopleJsonStore", "Failed to backup data to external storage: ${e.message}", e)
        }
    }

    private fun restoreFromBackup() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                if (!android.os.Environment.isExternalStorageManager()) {
                    Log.w("PeopleJsonStore", "Skipping restore: MANAGE_EXTERNAL_STORAGE permission not granted yet.")
                    return
                }
            }

            if (backupJson.exists()) {
                backupJson.copyTo(jsonFile, overwrite = true)
            }
            if (backupPhotos.exists()) {
                backupPhotos.copyRecursively(photosDir, overwrite = true)
            }
            Log.d("PeopleJsonStore", "Successfully restored people database from external backup!")
        } catch (e: Exception) {
            Log.e("PeopleJsonStore", "Failed to restore data from external storage: ${e.message}", e)
        }
    }

    // ── Read ──────────────────────────────────────────────────────────

    private fun readFromDisk(): List<Person> {
        if (!jsonFile.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<Person>>() {}.type
            gson.fromJson<List<Person>>(jsonFile.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            // Corrupted file — back it up and start fresh
            jsonFile.copyTo(File(peopleDir, "people.json.bak"), overwrite = true)
            emptyList()
        }
    }

    // ── Write ─────────────────────────────────────────────────────────

    private fun writeToDisk(people: List<Person>) {
        peopleDir.mkdirs()
        // Write to temp file first, then rename — prevents corruption on crash
        val tmp = File(peopleDir, "people.json.tmp")
        tmp.writeText(gson.toJson(people))
        tmp.renameTo(jsonFile)
    }

    // ── CRUD ──────────────────────────────────────────────────────────

    suspend fun getAll(): List<Person> = mutex.withLock { _people.value }

    suspend fun getById(id: Long): Person? = mutex.withLock {
        _people.value.find { it.id == id }
    }

    suspend fun insert(person: Person): Person = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = _people.value + person
            writeToDisk(updated)
            _people.value = updated
            backupToDisk() // Sync to external backup
            person
        }
    }

    suspend fun update(person: Person) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = _people.value.map {
                if (it.id == person.id) person.copy(updatedAt = System.currentTimeMillis())
                else it
            }
            writeToDisk(updated)
            _people.value = updated
            backupToDisk() // Sync to external backup
        }
    }

    suspend fun delete(id: Long): Long = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = _people.value.filter { it.id != id }
            writeToDisk(updated)
            _people.value = updated
            // Clean up internal photos
            File(photosDir, id.toString()).deleteRecursively()
            // Clean up backup photos
            File(backupPhotos, id.toString()).deleteRecursively()
            backupToDisk() // Sync updated list
            id
        }
    }

    // ── Photos ────────────────────────────────────────────────────────

    suspend fun savePhoto(
        personId : Long,
        bitmap   : Bitmap,
        index    : Int
    ): String = withContext(Dispatchers.IO) {
        val personPhotoDir = File(photosDir, personId.toString())
        personPhotoDir.mkdirs()
        val file = File(personPhotoDir, "photo_$index.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        // Instantly save to external backup directory as well
        try {
            var permissionGranted = true
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                if (!android.os.Environment.isExternalStorageManager()) {
                    permissionGranted = false
                }
            }

            if (permissionGranted) {
                val backupPersonPhotoDir = File(backupPhotos, personId.toString())
                if (backupPersonPhotoDir.exists() || backupPersonPhotoDir.mkdirs()) {
                    val backupFile = File(backupPersonPhotoDir, "photo_$index.jpg")
                    FileOutputStream(backupFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                } else {
                    Log.w("PeopleJsonStore", "Failed to create backup directory: ${backupPersonPhotoDir.absolutePath}")
                }
            } else {
                Log.w("PeopleJsonStore", "Skipping photo backup: MANAGE_EXTERNAL_STORAGE permission not granted yet.")
            }
        } catch (e: Exception) {
            Log.e("PeopleJsonStore", "Failed to backup photo: ${e.message}", e)
        }

        file.absolutePath
    }

    fun getPhotosForPerson(personId: Long): List<File> {
        val dir = File(photosDir, personId.toString())
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.extension == "jpg" }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun getPhotoDir(personId: Long): File = File(photosDir, personId.toString())
}