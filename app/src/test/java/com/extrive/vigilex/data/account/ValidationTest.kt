package com.extrive.vigilex.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidationTest {
    @Test
    fun emailRequiresAddressShape() {
        assertNotNull(emailError(""))
        assertNotNull(emailError("name"))
        assertNotNull(emailError("name@company"))
        assertNotNull(emailError("na me@company.com"))
        assertNull(emailError("name@company.com"))
        assertNull(emailError("  name@company.co.uk "))
    }

    @Test
    fun newPasswordEnforcesMinimumLength() {
        assertNotNull(newPasswordError(""))
        assertNotNull(newPasswordError("a".repeat(MIN_PASSWORD_LENGTH - 1)))
        assertNull(newPasswordError("a".repeat(MIN_PASSWORD_LENGTH)))
    }

    @Test
    fun signInPasswordOnlyChecksPresence() {
        assertNotNull(signInPasswordError(""))
        assertNull(signInPasswordError("x"))
    }

    @Test
    fun confirmationMustMatch() {
        assertNotNull(confirmPasswordError("password1", ""))
        assertEquals("Passwords do not match.", confirmPasswordError("password1", "password2"))
        assertNull(confirmPasswordError("password1", "password1"))
    }

    @Test
    fun nameMustNotBeBlank() {
        assertNotNull(nameError("   "))
        assertNull(nameError("Alex"))
    }
}
