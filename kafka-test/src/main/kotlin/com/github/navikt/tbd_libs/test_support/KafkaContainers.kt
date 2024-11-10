package com.github.navikt.tbd_libs.test_support

import java.util.concurrent.ConcurrentHashMap

object KafkaContainers {
    private val JUNIT_PARALLELISM = System.getProperty("junit.jupiter.execution.parallel.config.fixed.parallelism")?.toInt() ?: 1

    private const val AT_LEAST_ONE = 1
    private val MIN_POOL_SIZE = maxOf(AT_LEAST_ONE, JUNIT_PARALLELISM)
    private val MAX_POOL_SIZE = Runtime.getRuntime().availableProcessors()
    private val DEFAULT_POOL_SIZE = minOf(MAX_POOL_SIZE, MIN_POOL_SIZE)

    private val instances = ConcurrentHashMap<String, KafkaContainer>()

    // gjenbruker containers med samme navn for å unngå
    // å spinne opp mange containers
    fun container(appnavn: String, numberOfTopics: Int = DEFAULT_POOL_SIZE, minPoolSize: Int = MIN_POOL_SIZE): KafkaContainer {
        val poolSize = maxOf(numberOfTopics, minPoolSize)
        println("Pool size: $poolSize\nNumber of topics: $numberOfTopics\nMin pool size: $minPoolSize\nJUnit parallelism: $JUNIT_PARALLELISM\nMax pool size: $MAX_POOL_SIZE")
        return instances.getOrPut(appnavn) {
            KafkaContainer(appnavn, poolSize)
        }
    }
}