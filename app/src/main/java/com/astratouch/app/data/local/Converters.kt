package com.astratouch.app.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

/**
 * Room type converters.
 *
 * Colour values are stored as 64-bit ints so ARGB values survive unsigned
 * conversion on all API levels; booleans are stored as integers by SQLite
 * natively and need no converter.
 */
class Converters {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @TypeConverter
    fun stringListToJson(value: List<String>?): String = json.encodeToString(value ?: emptyList())

    @TypeConverter
    fun jsonToStringList(value: String?): List<String> = runCatching {
        json.decodeFromString<List<String>>(value ?: "[]")
    }.getOrDefault(emptyList())
}
