package com.personal.financetracker.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.personal.financetracker.data.local.entity.Budget
import com.personal.financetracker.data.local.entity.Category
import com.personal.financetracker.data.local.entity.Debt
import com.personal.financetracker.data.local.entity.Investment
import com.personal.financetracker.data.local.entity.SavingsGoal
import com.personal.financetracker.data.local.entity.Transaction
import com.personal.financetracker.data.local.entity.decodeMeta
import java.io.File

/**
 * Multi-section CSV export. One file with one section per table, each
 * separated by a `# <table>` marker. Spreadsheet apps treat the markers as
 * blank rows, so the file opens cleanly and the user can split as needed.
 */
object CsvExporter {

    enum class DurationUnit { WEEKS, MONTHS, YEARS, ALL }

    data class ExportPayload(
        val transactions: List<Transaction>,
        val categories: List<Category>,
        val budgets: List<Budget>,
        val savingsGoals: List<SavingsGoal>,
        val investments: List<Investment>,
        val debts: List<Debt>
    )

    /**
     * Compute the cutoff timestamp (millis since epoch) for a duration window.
     * Returns 0L for [DurationUnit.ALL] so every row passes the filter.
     */
    fun cutoffMillis(unit: DurationUnit, count: Int): Long {
        if (unit == DurationUnit.ALL || count <= 0) return 0L
        val days = when (unit) {
            DurationUnit.WEEKS -> count * 7L
            DurationUnit.MONTHS -> count * 30L
            DurationUnit.YEARS -> count * 365L
            DurationUnit.ALL -> 0L
        }
        return System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
    }

    fun buildAll(payload: ExportPayload): String {
        val categoriesById = payload.categories.associateBy { it.id }
        val sb = StringBuilder()

        // ── Transactions ──
        sb.appendLine("# Transactions")
        sb.appendLine("Date,Type,Amount (KES),Category,Counterparty,Description,Status,Reference")
        payload.transactions.forEach { txn ->
            val cat = txn.categoryId?.let { categoriesById[it]?.name } ?: "Uncategorized"
            val cp = txn.counterparty ?: decodeMeta(txn.meta)?.counterparty.orEmpty()
            val ref = txn.reference ?: decodeMeta(txn.meta)?.ref.orEmpty()
            sb.appendLine(
                row(
                    FormatUtils.formatDate(txn.transactedAt),
                    txn.type.name,
                    money(txn.amount),
                    cat,
                    cp,
                    txn.description.orEmpty(),
                    txn.status.name,
                    ref
                )
            )
        }

        // ── Categories ──
        sb.appendLine()
        sb.appendLine("# Categories")
        sb.appendLine("Name,Type,Icon,Color")
        payload.categories.forEach { c ->
            sb.appendLine(row(c.name, if (c.isIncome) "Income" else "Expense", c.icon.orEmpty(), c.color.orEmpty()))
        }

        // ── Budgets ──
        sb.appendLine()
        sb.appendLine("# Budgets")
        sb.appendLine("Month,Category,Amount (KES)")
        payload.budgets.forEach { b ->
            val cat = categoriesById[b.categoryId]?.name ?: "?"
            sb.appendLine(row(FormatUtils.formatDate(b.month), cat, money(b.amount)))
        }

        // ── Savings goals ──
        sb.appendLine()
        sb.appendLine("# Savings Goals")
        sb.appendLine("Name,Target (KES),Current (KES),Deadline,Completed")
        payload.savingsGoals.forEach { g ->
            sb.appendLine(
                row(
                    g.name,
                    money(g.targetAmount),
                    money(g.currentAmount),
                    g.deadline?.let { FormatUtils.formatDate(it) } ?: "",
                    if (g.isCompleted) "Yes" else "No"
                )
            )
        }

        // ── Investments ──
        sb.appendLine()
        sb.appendLine("# Investments")
        sb.appendLine("Name,Type,Buy-in (KES),Current Value (KES),Notes,Updated")
        payload.investments.forEach { i ->
            sb.appendLine(
                row(
                    i.name,
                    i.type.orEmpty(),
                    money(i.buyInAmount),
                    money(i.currentValue),
                    i.notes.orEmpty(),
                    FormatUtils.formatDate(i.updatedAt)
                )
            )
        }

        // ── Debts ──
        sb.appendLine()
        sb.appendLine("# Debts")
        sb.appendLine("Direction,Person,Amount (KES),Description,Settled,Due Date")
        payload.debts.forEach { d ->
            sb.appendLine(
                row(
                    d.direction.name,
                    d.person,
                    money(d.amount),
                    d.description.orEmpty(),
                    if (d.isSettled) "Yes" else "No",
                    d.dueDate?.let { FormatUtils.formatDate(it) } ?: ""
                )
            )
        }

        return sb.toString()
    }

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
        context.startActivity(Intent.createChooser(intent, "Export Expense Data"))
    }

    private fun money(cents: Int): String = String.format("%.2f", cents / 100.0)

    private fun row(vararg cells: String): String =
        cells.joinToString(",") { cell ->
            // Escape any cell that contains a delimiter, quote or newline.
            if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
                "\"${cell.replace("\"", "\"\"")}\""
            } else cell
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
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        val file = File(dir, filename)
        file.writeText(csvContent)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
