package com.example

import com.example.data.DocumentHelper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ExampleRobolectricTest {

    @Test
    fun `test formatFileSize utility`() {
        assertEquals("500 B", DocumentHelper.formatFileSize(500L))
        assertEquals("1.5 KB", DocumentHelper.formatFileSize(1536L))
        assertEquals("2.0 MB", DocumentHelper.formatFileSize(2 * 1024 * 1024L))
    }
}
