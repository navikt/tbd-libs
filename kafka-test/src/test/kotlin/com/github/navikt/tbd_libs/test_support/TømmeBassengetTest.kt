package com.github.navikt.tbd_libs.test_support

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD
import java.time.Duration

// må kjøre testene i samme tråd fordi begge testene forventer å tømme _hele_ bassenget for topics,
// og det gir ikke mening å kjøre testene samtidig
@Execution(SAME_THREAD)
class TømmeBassengetTest {
    @Test
    fun `får exception om topic ikke er tilgjengelig innen timeout`() = runBlocking {
        val topics = (1.. MAX_TOPICS_SIZE).map { kafkaContainer.nyTopic(Duration.ofSeconds(1)) }
        assertThrows<RuntimeException> { kafkaContainer.nyTopic(Duration.ofMillis(10)) }
        topics.forEach { kafkaContainer.droppTopic(it) }
    }

    @Test
    fun `returnerer topics hvis gitt antall ikke kan oppnås`() {
        runBlocking {
            assertThrows<RuntimeException> { kafkaContainer.nyeTopics(MAX_TOPICS_SIZE + 1, Duration.ofSeconds(1)) }
            val topics = assertDoesNotThrow { kafkaContainer.nyeTopics(MAX_TOPICS_SIZE) }
            topics.forEach { kafkaContainer.droppTopic(it) }
        }
    }
}