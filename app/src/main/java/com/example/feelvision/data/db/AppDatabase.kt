package com.feelvision.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.feelvision.data.people.PeopleDao
import com.feelvision.data.people.PersonEntity

@Database(entities = [PersonEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun peopleDao(): PeopleDao
}