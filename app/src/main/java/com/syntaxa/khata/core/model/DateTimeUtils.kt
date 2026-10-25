package com.syntaxa.khata.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateTimeUtils {
    private val zoneId = ZoneId.systemDefault()
    private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
    private val receiptStampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ENGLISH)

    fun formatDate(epochMillis: Long): String {
        val dt = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
        return dt.format(dateFormatter)
    }

    fun formatTime(epochMillis: Long): String {
        val dt = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalTime()
        return dt.format(timeFormatter)
    }

    fun formatDateTime(epochMillis: Long): String {
        val dt = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDateTime()
        return dt.format(dateTimeFormatter)
    }

    fun formatReceiptTimestamp(epochMillis: Long): String {
        val dt = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDateTime()
        return dt.format(receiptStampFormatter)
    }

    fun getStartOfDay(epochMillis: Long = System.currentTimeMillis()): Long {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(zoneId)
            .toLocalDate()
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    fun getEndOfDay(epochMillis: Long = System.currentTimeMillis()): Long {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(zoneId)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli() - 1
    }

    fun getDaysAgoStart(days: Int): Long {
        return LocalDate.now(zoneId)
            .minusDays(days.toLong())
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    fun getStartOfMonth(): Long {
        val now = LocalDate.now(zoneId)
        return now.withDayOfMonth(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }
}
