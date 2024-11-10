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
        val topics = (1.. MAX_TOPICS_SIZE).map { kafkaContainer.nyTopic() }
        assertThrows<RuntimeException> { kafkaContainer.nyTopic(Duration.ofMillis(10)) }
        topics.forEach { kafkaContainer.droppTopic(it) }
    }

    @Test
    fun `returnerer topics hvis gitt antall ikke kan oppnås`() {
        runBlocking {
            // lar det være 1 ledig topic igjen
            val topics = kafkaContainer.nyeTopics(MAX_TOPICS_SIZE - 1)
            // kan umulig gå OK siden det skal kun være én ledig tilkobling
            // (på grunn av andre tester i samme modul kan i teorien okkupere den siste tilkoblingen må vi ha litt timeout.
            //      men siden vi vet at timeout vil inntreffe (siden det ikke finnes to ledige tilkoblinger) så unngår vi å sette
            //       et så høyt tall at testen tar evigheter å fullføre..)
            assertThrows<RuntimeException> { kafkaContainer.nyeTopics(2, timeout = Duration.ofSeconds(10)) }
            val sisteTopic = assertDoesNotThrow { kafkaContainer.nyeTopics(1) }
            kafkaContainer.droppTopics(topics + sisteTopic)
        }
    }
}