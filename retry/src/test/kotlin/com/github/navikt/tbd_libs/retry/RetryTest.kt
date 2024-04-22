package com.github.navikt.tbd_libs.retry

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalArgumentException
import java.time.Duration

internal class RetryTest {

    @Test
    fun `Først en test av dingsen`() {
        val dings = DingsSomFunkerPåForsøk(4)
        assertEquals("En feil på forsøk 1", assertThrows<IllegalStateException> { dings.gjørtingBlocking() }.message)
        assertEquals("En feil på forsøk 2", assertThrows<IllegalStateException> { dings.gjørtingBlocking() }.message)
        assertEquals("En feil på forsøk 3", assertThrows<IllegalStateException> { dings.gjørtingBlocking() }.message)
        assertEquals("Det gikk jo bra på førsøk 4 da", dings.gjørtingBlocking())
    }

    @Test
    fun `Når noe funker med en gang`() {
        val dings = DingsSomFunkerPåForsøk(1)
        assertEquals("Det gikk jo bra på førsøk 1 da", retryBlocking { dings.gjørtingBlocking() })
    }

    @Test
    fun `Når noe funker med på førsøk 3`() {
        val dings = DingsSomFunkerPåForsøk(3)
        assertEquals("Det gikk jo bra på førsøk 3 da", retryBlocking { dings.gjørtingBlocking() })
    }

    @Test
    fun `Når noe feiler 4 ganger og vi ikke prøver noe mer`() {
        val dings = DingsSomFunkerPåForsøk(5)
        assertEquals("En feil på forsøk 4", assertThrows<IllegalStateException> { retryBlocking { dings.gjørtingBlocking() } }.message)
    }

    @Test
    fun `Om det er en spesiell feil som vi ikke vil retrye på så fortsetter vi ei`() {
        val dings = DingsSomFunkerPåForsøk(10, feil = { EnfeilViIkkeVilRetrye(it) })
        assertEquals(
            "For dette her fikses ikke av en retry. Så derfor feiler vi på forsøk 1",
            assertThrows<IllegalStateException> { retryBlocking(avbryt = { it is EnfeilViIkkeVilRetrye} ) { dings.gjørtingBlocking() } }.message
        )
    }

    @Test
    fun `Når noe funker med på førsøk 3 suspendable`() = runBlocking {
        val dings = DingsSomFunkerPåForsøk(3)
        assertEquals("Det gikk jo bra på førsøk 3 da", retry { dings.gjørting() })
    }

    @Test
    fun `kan sette sine egne predefinerte utsettelser`() {
        val default = DefaultUtsettelser()
        assertEquals(Duration.ofMillis(200), default.next())
        assertEquals(Duration.ofMillis(600), default.next())
        assertEquals(Duration.ofMillis(1200), default.next())
        assertFalse(default.hasNext())

        val custom = PredefinerteUtsettelser(Duration.ofMillis(200), Duration.ofMinutes(1), Duration.ofHours(1), Duration.ofMillis(2))
        assertEquals(Duration.ofMillis(200), custom.next())
        assertEquals(Duration.ofMinutes(1), custom.next())
        assertEquals(Duration.ofHours(1), custom.next())
        assertEquals(Duration.ofMillis(2), custom.next())
        assertFalse(custom.hasNext())

        assertThrows<IllegalArgumentException> { PredefinerteUtsettelser() }
    }

    private class DingsSomFunkerPåForsøk(
        private val forsøk: Int,
        private val feil: (nåværendeForsøk: Int) -> Throwable = { IllegalStateException("En feil på forsøk $it") }
    ) {
        init { check(forsøk >= 1) }
        private var nåværendeForsøk = 1

        fun gjørtingBlocking(): String {
            if (nåværendeForsøk == forsøk) return "Det gikk jo bra på førsøk $nåværendeForsøk da"
            throw feil(nåværendeForsøk++)
        }
        suspend fun gjørting(): String {
            delay(1)
            return gjørtingBlocking()
        }

    }

    private class EnfeilViIkkeVilRetrye(nåværendeForsøk: Int): IllegalStateException("For dette her fikses ikke av en retry. Så derfor feiler vi på forsøk $nåværendeForsøk")
}