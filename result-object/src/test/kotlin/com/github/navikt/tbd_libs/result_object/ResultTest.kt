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

    @Test
    fun map() {
        testfun(false).map {
            "Hei, ${it.name}!".ok()
        }.also { result ->
            result as Result.Ok
            assertEquals("Hei, foo!", result.value)
        }
        testfun(true).map {
            "Hei, ${it.name}!".ok()
        }.also { result ->
            result as Result.Error
            assertEquals("Denne gir feil altså!", result.error)
        }
    }

    @Test
    fun fold() {
        testfun(false).fold(
            whenOk = { "Hei, ${it.name}!".ok() },
            whenError = { msg, cause -> "Error: $msg".error(cause) }
        ).also { result ->
            result as Result.Ok
            assertEquals("Hei, foo!", result.value)
        }
        testfun(true).fold(
            whenOk = { "Hei, ${it.name}!".ok() },
            whenError = { msg, cause -> "Error: $msg".error(cause) }
        ).also { result ->
            result as Result.Error
            assertEquals("Error: Denne gir feil altså!", result.error)
        }
        "A".ok().fold(
            whenOk = { "Hei, $it" },
            whenError = { msg, cause -> "Error: $msg" }
        ).also {
            assertEquals("Hei, A", it)
        }
    }

    @Test
    fun flatten() {
        listOf(
            "A".ok(),
            "B".ok(),
            "C".error()
        ).flatten().also { result ->
            result as Result.Error
            assertEquals("C", result.error)
        }
        listOf(
            "A".ok(),
            "B".ok(),
            "C".ok()
        ).flatten().also { result ->
            result as Result.Ok
            assertEquals(listOf("A", "B", "C"), result.value)
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