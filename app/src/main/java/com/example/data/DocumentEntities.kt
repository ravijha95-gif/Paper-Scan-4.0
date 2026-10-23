package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pageCount: Int = 0,
    val coverImagePath: String = ""
)

@Entity(
    tableName = "document_pages",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index("documentId")]
)
data class DocumentPageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val originalImagePath: String,
    val processedImagePath: String,
    val filterMode: String = "ENHANCED", // ORIGINAL, ENHANCED, GRAYSCALE, BW
    val brightness: Float = 0f,           // -1.0f to 1.0f
    val contrast: Float = 1.0f,           // 0.5f to 2.0f
    val rotationDegrees: Int = 0,         // 0, 90, 180, 270
    // Normalized 4 corners (0.0 to 1.0) stored as comma-separated: "tlx,tly,trx,try,brx,bry,blx,bly"
    val cropCorners: String = ""
)
