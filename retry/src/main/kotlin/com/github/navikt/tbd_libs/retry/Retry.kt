package com.github.navikt.tbd_libs.retry

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.Duration.ofMillis

suspend inline fun <T> retry(
    utsettelser: Iterator<Duration> = DefaultUtsettelser(),
    avbryt: (throwable: Throwable) -> Boolean = { false },
    block: () -> T
): T {
    while (utsettelser.hasNext()) {
        try { return block() } catch (t: Throwable) {
            if (t is CancellationException || avbryt(t)) throw t
        }
        delay(utsettelser.next().toMillis())
    }
    return block()
}

inline fun <T> retryBlocking(
    utsettelser: Iterator<Duration> = DefaultUtsettelser(),
    crossinline avbryt: (throwable: Throwable) -> Boolean = { false },
    crossinline block: () -> T
) = runBlocking { retry(utsettelser, avbryt, block) }

open class PredefinerteUtsettelser(vararg utsettelser: Duration): Iterator<Duration> {
    private val utsettelser = utsettelser.toMutableList()
    init { require(utsettelser.isNotEmpty()) { "Må sette minst en utsettelse!"} }
    override fun hasNext() = utsettelser.isNotEmpty()
    override fun next(): Duration = utsettelser.removeAt(0)
}

class DefaultUtsettelser: PredefinerteUtsettelser(ofMillis(200), ofMillis(600), ofMillis(1200))
