package com.personal.expensetracker.data.remote

import com.personal.expensetracker.data.local.entity.Transaction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.time.Instant

/**
 * DTO for syncing transactions to Supabase.
 * Matches the `transactions` table schema.
 */
@Serializable
data class TransactionDto(
    val id: Int,
    val type: String,
    val status: String,
    val amount: Int,
    @SerialName("category_id") val categoryId: Int? = null,
    @SerialName("account_id") val accountId: Int? = null,
    val description: String? = null,
    @SerialName("transacted_at") val transactedAt: String,
    val meta: JsonElement? = null,
    @SerialName("created_at") val createdAt: String,
)

fun Transaction.toDto(): TransactionDto = TransactionDto(
    id = id,
    type = type.name,
    status = status.name,
    amount = amount,
    categoryId = categoryId,
    accountId = accountId,
    description = description,
    transactedAt = Instant.ofEpochMilli(transactedAt).toString(),
    meta = meta?.let {
        try { Json.parseToJsonElement(it) }
        catch (_: Exception) { null }
    },
    createdAt = Instant.ofEpochMilli(createdAt).toString()
)
