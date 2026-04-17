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
            .addMigrations(
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_13_14,
                AppDatabase.MIGRATION_14_15,
                AppDatabase.MIGRATION_15_16,
                AppDatabase.MIGRATION_16_17
            )
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
