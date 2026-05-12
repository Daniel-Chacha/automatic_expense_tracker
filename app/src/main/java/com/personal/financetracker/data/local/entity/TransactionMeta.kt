package com.personal.financetracker.data.local.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TransactionMeta(
    val ref: String? = null,
    val balance: Int? = null,
    val counterparty: String? = null,
    val raw: String = ""
)

val MetaJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun TransactionMeta.encode(): String = MetaJson.encodeToString(TransactionMeta.serializer(), this)

fun decodeMeta(meta: String?): TransactionMeta? {
    if (meta.isNullOrBlank()) return null
    return runCatching { MetaJson.decodeFromString<TransactionMeta>(meta) }.getOrNull()
}
