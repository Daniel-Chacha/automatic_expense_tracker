package com.personal.financetracker.data.local

import androidx.room.TypeConverter
import com.personal.financetracker.data.local.entity.DebtDirection
import com.personal.financetracker.data.local.entity.TransactionStatus
import com.personal.financetracker.data.local.entity.TransactionType

class Converters {

    @TypeConverter
    fun fromTransactionType(type: TransactionType): String = type.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromTransactionStatus(status: TransactionStatus): String = status.name

    @TypeConverter
    fun toTransactionStatus(value: String): TransactionStatus = TransactionStatus.valueOf(value)

    @TypeConverter
    fun fromDebtDirection(direction: DebtDirection): String = direction.name

    @TypeConverter
    fun toDebtDirection(value: String): DebtDirection = DebtDirection.valueOf(value)
}
