package com.personal.expensetracker.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.personal.expensetracker.data.local.entity.Category
import com.personal.expensetracker.data.local.entity.Transaction
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
            val ref = txn.meta?.let {
                Regex("\"ref\":\"([^\"]+)\"").find(it)?.groupValues?.get(1)
            } ?: ""
            "\"$date\",${txn.type},${amount},\"${cat?.name ?: "Uncategorized"}\",\"${txn.description ?: ""}\",${txn.status},\"$ref\""
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    fun shareAsCsv(context: Context, csvContent: String, filename: String = "expenses.csv") {
        val file = File(context.cacheDir, filename)
        file.writeText(csvContent)

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Expenses"))
    }
}
