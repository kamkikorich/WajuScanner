package com.example.wajuscanner.core.di

import android.content.Context
import androidx.room.Room
import com.example.wajuscanner.data.local.db.AppDatabase
import com.example.wajuscanner.data.local.db.dao.DocumentDao
import com.example.wajuscanner.data.local.db.dao.OcrResultDao
import com.example.wajuscanner.data.local.db.dao.PageDao
import com.example.wajuscanner.data.repository.DocumentRepositoryImpl
import com.example.wajuscanner.domain.repository.DocumentRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "smart_scanner_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideDocumentDao(database: AppDatabase): DocumentDao =
        database.documentDao()

    @Provides
    @Singleton
    fun providePageDao(database: AppDatabase): PageDao =
        database.pageDao()

    @Provides
    @Singleton
    fun provideOcrResultDao(database: AppDatabase): OcrResultDao =
        database.ocrResultDao()

    @Provides
    @Singleton
    fun provideDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository = impl
}
