package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class DocumentWithPages(
    @Embedded val document: DocumentEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "documentId"
    )
    val pages: List<DocumentPageEntity>
)

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :documentId LIMIT 1")
    fun getDocumentWithPages(documentId: Long): Flow<DocumentWithPages?>

    @Query("SELECT * FROM documents WHERE id = :documentId LIMIT 1")
    suspend fun getDocumentById(documentId: Long): DocumentEntity?

    @Query("SELECT * FROM document_pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    suspend fun getPagesForDocument(documentId: Long): List<DocumentPageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: DocumentPageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<DocumentPageEntity>)

    @Update
    suspend fun updatePage(page: DocumentPageEntity)

    @Query("DELETE FROM document_pages WHERE id = :pageId")
    suspend fun deletePageById(pageId: Long)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteDocumentById(documentId: Long)

    @Query("UPDATE documents SET title = :newTitle, updatedAt = :updatedAt WHERE id = :documentId")
    suspend fun renameDocument(documentId: Long, newTitle: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET pageCount = :pageCount, coverImagePath = :coverPath, updatedAt = :updatedAt WHERE id = :documentId")
    suspend fun updateDocumentMeta(documentId: Long, pageCount: Int, coverPath: String, updatedAt: Long = System.currentTimeMillis())
}
