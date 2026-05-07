package com.personal.expensetracker.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.personal.expensetracker.data.local.entity.Category
import com.personal.expensetracker.data.local.entity.Transaction
import com.personal.expensetracker.data.local.entity.decodeMeta
import java.io.File

object CsvExporter {

    fun export(
        transactions: List<Transaction>,
        categories: Map<Int, Category>
    ): String {
        val header = "Date,Type,Amount (KES),Category,Description,Status,Reference"
        val rows = transactions.map { txn ->
            val cat = txn.categoryId?.let { categories[it] }
            val date = FormatUtils.formatDate(txn.transactedAt)
            val amount = String.format("%.2f", txn.amount / 100.0)
            val ref = txn.reference ?: decodeMeta(txn.meta)?.ref.orEmpty()
            "\"$date\",${txn.type},${amount},\"${cat?.name ?: "Uncategorized"}\",\"${txn.description ?: ""}\",${txn.status},\"$ref\""
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    /**
     * Writes the CSV to the public Downloads directory and opens a share sheet.
     * On API 29+ uses MediaStore; below uses scoped external files dir.
     */
    fun shareAsCsv(context: Context, csvContent: String, filename: String = "expenses.csv") {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeToDownloadsViaMediaStore(context, csvContent, filename)
        } else {
            writeToExternalFilesDir(context, csvContent, filename)
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Expenses"))
    }

    private fun writeToDownloadsViaMediaStore(
        context: Context,
        csvContent: String,
        filename: String
    ): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore.insert returned null for $filename")
        resolver.openOutputStream(uri)?.use { it.write(csvContent.toByteArray()) }
            ?: error("Could not open output stream for $uri")
        return uri
    }

    private fun writeToExternalFilesDir(
        context: Context,
        csvContent: String,
        filename: String
    ): Uri {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.cacheDir
        val file = File(dir, filename)
        file.writeText(csvContent)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
