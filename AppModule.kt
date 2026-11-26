package com.maherma.app.di

import android.content.Context
import androidx.room.Room
import com.maherma.app.data.db.AuditDao
import com.maherma.app.data.db.ChatDao
import com.maherma.app.data.db.MahermaDb
import com.maherma.app.data.db.MemoryDao
import com.maherma.app.data.db.NoteDao
import com.maherma.app.data.db.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    @Provides @Singleton
    fun http(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    @Provides @Singleton
    fun db(@ApplicationContext c: Context): MahermaDb = Room.databaseBuilder(c, MahermaDb::class.java, "maherma.db").build()

    @Provides fun chatDao(db: MahermaDb): ChatDao = db.chatDao()
    @Provides fun taskDao(db: MahermaDb): TaskDao = db.taskDao()
    @Provides fun noteDao(db: MahermaDb): NoteDao = db.noteDao()
    @Provides fun memoryDao(db: MahermaDb): MemoryDao = db.memoryDao()
    @Provides fun auditDao(db: MahermaDb): AuditDao = db.auditDao()
}
