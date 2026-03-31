package com.example.docscanner.di

import android.content.Context
import androidx.room.Room
import com.example.docscanner.data.classifier.DocumentClassifier
import com.example.docscanner.data.local.dao.DocumentDao
import com.example.docscanner.data.local.dao.FolderDao
import com.example.docscanner.data.local.dao.AppDatabase
import com.example.docscanner.data.local.dao.ApplicationDocumentDao
import com.example.docscanner.data.local.dao.ApplicationSessionDao
import com.example.docscanner.data.local.dao.DocGroupDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "docscanner_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideFolderDao(db: AppDatabase): FolderDao = db.folderDao()

    @Provides
    fun provideDocumentDao(db: AppDatabase): DocumentDao = db.documentDao()


    @Provides
    @Singleton
    fun provideDocumentClassifier(@ApplicationContext context: Context): DocumentClassifier =
        DocumentClassifier(context)

    @Provides
    fun provideApplicationSessionDao(db: AppDatabase): ApplicationSessionDao =
        db.applicationSessionDao()

    @Provides
    fun provideApplicationDocumentDao(db: AppDatabase): ApplicationDocumentDao =
        db.applicationDocumentDao()

    @Provides
    fun provideDocGroupDao(db: AppDatabase): DocGroupDao = db.docGroupDao()


}