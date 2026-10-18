package com.example.aplikacjatypumessenger

import com.example.aplikacjatypumessenger.utils.Validators
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {

    @Test
    fun `accepts typical email addresses`() {
        assertTrue(Validators.isValidEmail("jan.kowalski@example.com"))
        assertTrue(Validators.isValidEmail("a+b@sub.domena.pl"))
    }

    @Test
    fun `rejects malformed email addresses`() {
        assertFalse(Validators.isValidEmail(""))
        assertFalse(Validators.isValidEmail("jan"))
        assertFalse(Validators.isValidEmail("jan@"))
        assertFalse(Validators.isValidEmail("jan@example"))
        assertFalse(Validators.isValidEmail("jan @example.com"))
    }

    @Test
    fun `password needs at least six characters`() {
        assertFalse(Validators.isValidPassword("12345"))
        assertTrue(Validators.isValidPassword("123456"))
    }

    @Test
    fun `username is trimmed before length check`() {
        assertFalse(Validators.isValidUsername("  ab  "))
        assertTrue(Validators.isValidUsername("abc"))
    }
}
