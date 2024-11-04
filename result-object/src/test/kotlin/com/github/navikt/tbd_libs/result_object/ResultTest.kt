package com.github.navikt.tbd_libs.result_object

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResultTest {

    @Test
    fun test1() {
        testfun(true).also {
            it as Result.Error
            assertEquals("Denne gir feil altså!", it.error)
        }
        testfun(false).also {
            it as Result.Ok
            assertEquals("foo", it.value.name)
        }
    }

    @Test
    fun test2() {
        testfunShortcuts(true).also {
            it as Result.Error
            assertEquals("Denne gir feil altså!", it.error)
        }
        testfunShortcuts(false).also {
            it as Result.Ok
            assertEquals("foo", it.value.name)
        }
    }

    private fun testfun(feil: Boolean): Result<Testobject> {
        return when (feil) {
            true -> Result.Error("Denne gir feil altså!")
            false -> Result.Ok(Testobject())
        }
    }

    private fun testfunShortcuts(feil: Boolean): Result<Testobject> {
        return when (feil) {
            true -> "Denne gir feil altså!".error()
            false -> Testobject().ok()
        }
    }

    class Testobject {
        val name = "foo"
    }
}