package com.audiokit.catchphrase.util

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

object TimeUtils {

    private fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun todayStart(): Long = startOfDay(LocalDate.now())

    fun todayEnd(): Long = startOfDay(LocalDate.now().plusDays(1))

    fun weekStart(): Long = startOfDay(LocalDate.now().minusDays(6))

    fun lastSevenDays(): List<LocalDate> = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }

    fun dayKey(date: LocalDate): String = date.toString()

    fun dateOf(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    fun formatClock(millis: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(millis))

    fun dayHeader(millis: Long): String {
        val date = dateOf(millis)
        return when (date) {
            LocalDate.now() -> "今天"
            LocalDate.now().minusDays(1) -> "昨天"
            else -> "${date.monthValue} 月 ${date.dayOfMonth} 日"
        }
    }

    fun dayShort(date: LocalDate): String = "${date.monthValue}/${date.dayOfMonth}"

    fun weekdayShort(date: LocalDate): String = when (date.dayOfWeek.value) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        else -> "日"
    }
}
