package com.golishoda.manager.export

object CsvWriter {
    fun row(values: Iterable<String>): String = values.joinToString(",", transform = ::escape)
    fun escape(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${value.replace("\"", "\"\"")}\"" else value
}
