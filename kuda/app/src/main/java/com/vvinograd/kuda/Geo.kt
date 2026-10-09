package com.vvinograd.kuda

import kotlin.math.abs
import kotlin.math.roundToInt

/** Сохранённое место. */
data class Spot(val id: Long, val name: String, val lat: Double, val lon: Double, val savedAt: Long)

object Geo {

    /** Приводит угол к диапазону [0, 360). */
    fun norm360(deg: Float): Float {
        val r = deg % 360f
        return if (r < 0) r + 360f else r
    }

    /** Кратчайшая разница углов to - from в диапазоне (-180, 180]. */
    fun diff(from: Float, to: Float): Float {
        var d = norm360(to) - norm360(from)
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }

    /** Плавное приближение угла, чтобы стрелка не дёргалась. Учитывает переход 359° → 0°. */
    fun smooth(current: Float?, target: Float, factor: Float): Float {
        if (current == null) return norm360(target)
        return norm360(current + diff(current, target) * factor)
    }

    /** На какой угол повернуть стрелку относительно верха телефона. */
    fun arrowAngle(bearingToTarget: Float, heading: Float): Float = norm360(bearingToTarget - heading)

    fun formatDistance(meters: Float): String = when {
        meters < 1000f -> "${meters.roundToInt()} м"
        meters < 100_000f -> {
            val tenths = (meters / 100f).roundToInt()
            "${tenths / 10},${tenths % 10} км"
        }
        else -> "${(meters / 1000f).roundToInt()} км"
    }

    /** Подсказка, в какую сторону повернуть. */
    fun turnHint(arrow: Float): String {
        val d = diff(0f, arrow)
        return when {
            abs(d) <= 20f -> "Прямо ⬆"
            abs(d) >= 160f -> "Развернитесь ⬇"
            d > 0 -> "Направо ↗"
            else -> "Налево ↖"
        }
    }

    /** Эмодзи по названию места, для красоты списка. */
    fun emojiFor(name: String): String {
        val n = name.lowercase()
        return when {
            listOf("машин", "авто", "парков").any { it in n } -> "🚗"
            listOf("отел", "гостин", "дом", "кварт").any { it in n } -> "🏨"
            listOf("палат", "лагер", "костр").any { it in n } -> "⛺"
            listOf("пляж", "море").any { it in n } -> "🏖"
            listOf("кафе", "ресторан").any { it in n } -> "☕"
            listOf("встреч", "друз").any { it in n } -> "🤝"
            else -> "📍"
        }
    }
}
