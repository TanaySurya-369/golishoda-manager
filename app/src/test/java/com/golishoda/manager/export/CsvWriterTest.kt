package com.golishoda.manager.export

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvWriterTest {
    @Test fun escapesCommasQuotesAndNewlines() {
        assertEquals("\"Market, North\"", CsvWriter.escape("Market, North"))
        assertEquals("\"He said \"\"hi\"\"\"", CsvWriter.escape("He said \"hi\""))
        assertEquals("\"two\nlines\"", CsvWriter.escape("two\nlines"))
    }
}
