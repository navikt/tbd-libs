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

class DefaultUtsettelser: Iterator<Duration> {
    private val utsettelser = mutableListOf(ofMillis(200), ofMillis(600), ofMillis(1200))
    override fun hasNext() = utsettelser.isNotEmpty()
    override fun next(): Duration = utsettelser.removeAt(0)
}