package com.github.navikt.tbd_libs.rapids_and_rivers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MessageValidationTest {
    @Test
    fun `key can exist`() {
        val validation = validate {
            "@event_name" can exist
        }

        validation.test("""{ "event_name": "mitt_eventnavn" }""") { result, problems ->
            assertEquals(setOf("@event_name"), result)
            assertFalse(problems.hasErrors())
        }

        validation.test("""{ "@event_name": null }""") { result, problems ->
            assertEquals(setOf("@event_name"), result)
            assertFalse(problems.hasErrors())
        }

        validation.test("""{ "@event_name": "mitt_eventnavn" }""") { result, problems ->
            assertEquals(setOf("@event_name"), result)
            assertFalse(problems.hasErrors())
        }

        val multipleKeys = validate {
            setOf("fnr", "aktørId") can exist
        }
        multipleKeys.test("""{ "event_name": "mitt_eventnavn" }""") { result, problems ->
            assertEquals(setOf("fnr", "aktørId"), result)
            assertFalse(problems.hasErrors())
        }
        multipleKeys.test("""{ "fnr": "1", "aktørId": "2" }""") { result, problems ->
            assertEquals(setOf("fnr", "aktørId"), result)
            assertFalse(problems.hasErrors())
        }
    }

    @Test
    fun `key should exist`() {
        val validation = validate {
            "@event_name" should exist
        }

        validation.test("""{ "event_name": "mitt_eventnavn" }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("@event_name: Feltet finnes ikke", problems.toString())
        }

        validation.test("""{ "@event_name": null }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("@event_name: Feltet finnes ikke", problems.toString())
        }

        validation.test("""{ "@event_name": "mitt_eventnavn" }""") { result, problems ->
            assertEquals(setOf("@event_name"), result)
            assertFalse(problems.hasErrors())
        }

        val multipleKeys = validate {
            setOf("fnr", "aktørId") should exist
        }
        multipleKeys.test("""{ "event_name": "mitt_eventnavn" }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("fnr: Feltet finnes ikke", problems.toString())
            assertContains("aktørId: Feltet finnes ikke", problems.toString())
        }
        multipleKeys.test("""{ "fnr": "1" }""") { result, problems ->
            assertEquals(setOf("fnr"), result)
            assertTrue(problems.hasErrors())
            assertContains("aktørId: Feltet finnes ikke", problems.toString())
        }
        multipleKeys.test("""{ "fnr": "1", "aktørId": "2" }""") { result, problems ->
            assertEquals(setOf("fnr", "aktørId"), result)
            assertFalse(problems.hasErrors())
        }
    }

    @Test
    fun `key should not exist`() {
        val validation = validate {
            "@event_name" should notExist
        }

        validation.test("""{ "@event_name": "mitt_eventnavn" }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("@event_name: Feltet finnes", problems.toString())
        }

        validation.test("""{ "@event_name": null }""") { result, problems ->
            assertEquals(setOf("@event_name"), result)
            assertFalse(problems.hasErrors())
        }

        validation.test("""{ "event_name": "mitt_eventnavn" }""") { result, problems ->
            assertEquals(setOf("@event_name"), result)
            assertFalse(problems.hasErrors())
        }
    }

    @Test
    fun `key should be`() {
        val validation = validate {
            "@event_name" should be("mitt_eventnavn")
        }

        validation.test("""{ "event_name": "mitt_eventnavn" }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("@event_name: Feltet har ikke forventet verdi mitt_eventnavn", problems.toString())
        }

        validation.test("""{ "@event_name": "uventet_verdi" }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("@event_name: Feltet har ikke forventet verdi mitt_eventnavn", problems.toString())
        }

        validation.test("""{ "@event_name": "mitt_eventnavn" }""") { result, problems ->
            assertEquals(setOf("@event_name"), result)
            assertFalse(problems.hasErrors())
        }
    }

    @Test
    fun `key should be boolean`() {
        val validation = validate {
            "er_sendt" should be(true)
        }

        validation.test("""{ "er_fullstendig": true }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("er_sendt: Feltet har ikke forventet verdi true", problems.toString())
        }

        validation.test("""{ "er_sendt": "uventet_verdi" }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("er_sendt: Feltet har ikke forventet verdi true", problems.toString())
        }

        validation.test("""{ "er_sendt": "true" }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("er_sendt: Feltet har ikke forventet verdi true", problems.toString())
        }

        validation.test("""{ "er_sendt": false }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("er_sendt: Feltet har ikke forventet verdi true", problems.toString())
        }

        validation.test("""{ "er_sendt": true }""") { result, problems ->
            assertEquals(setOf("er_sendt"), result)
            assertFalse(problems.hasErrors())
        }
    }

    @Test
    fun `key should be number`() {
        val validation = validate {
            "antall" should be(100)
        }

        validation.test("""{ "størrelse": 0 }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("antall: Feltet har ikke forventet verdi 100", problems.toString())
        }

        validation.test("""{ "antall": "mange" }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("antall: Feltet har ikke forventet verdi 100", problems.toString())
        }

        validation.test("""{ "antall": "100" }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("antall: Feltet har ikke forventet verdi 100", problems.toString())
        }

        validation.test("""{ "antall": 100.0 }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("antall: Feltet har ikke forventet verdi 100", problems.toString())
        }

        validation.test("""{ "antall": 100 }""") { result, problems ->
            assertEquals(setOf("antall"), result)
            assertFalse(problems.hasErrors())
        }
    }

    @Test
    fun `key should be array`() {
        val validation = validate {
            "vedlegg" should be (array {
                "type" should be("papir")
            })
        }

        validation.test("""{ }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("vedlegg: Forventet felt er ikke array", problems.toString())
        }

        validation.test("""{ "vedlegg": [] }""") { result, problems ->
            assertEquals(setOf("vedlegg"), result)
            assertFalse(problems.hasErrors())
        }

        validation.test("""{ "vedlegg": [ { "type": "pdf" } ] }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("vedlegg.0: Array element did not pass validation: E: type: Feltet har ikke forventet verdi papir", problems.toString())
        }

        validation.test("""{ "vedlegg": [ { "type": "papir" } ] }""") { result, problems ->
            assertEquals(setOf("vedlegg"), result)
            assertFalse(problems.hasErrors())
        }
    }

    @Test
    fun `key should be one of`() {
        val validation = validate {
            "@event_name" should be(setOf("ny_søknad", "sendt_søknad"))
        }

        validation.test("""{ "event_name": "mitt_eventnavn" }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("@event_name: Feltet har ikke en av forventet verdier ny_søknad, sendt_søknad", problems.toString())
        }

        validation.test("""{ "@event_name": "uventet_verdi" }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("@event_name: Feltet har ikke en av forventet verdier ny_søknad, sendt_søknad", problems.toString())
        }

        validation.test("""{ "@event_name": "ny_søknad" }""") { result, problems ->
            assertEquals(setOf("@event_name"), result)
            assertFalse(problems.hasErrors())
        }

        validation.test("""{ "@event_name": "sendt_søknad" }""") { result, problems ->
            assertEquals(setOf("@event_name"), result)
            assertFalse(problems.hasErrors())
        }
    }

    @Test
    fun `key should not be`() {
        val validation = validate {
            "@event_name" should notBe("mitt_eventnavn")
        }

        validation.test("""{ "event_name": "mitt_eventnavn" }""") { result, problems ->
            assertEquals(setOf("@event_name"), result)
            assertFalse(problems.hasErrors())
        }

        validation.test("""{ "@event_name": "mitt_eventnavn" }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("@event_name: Feltet har uventet verdi mitt_eventnavn", problems.toString())
        }

        validation.test("""{ "@event_name": "ok_navn" }""") { result, problems ->
            assertEquals(setOf("@event_name"), result)
            assertFalse(problems.hasErrors())
        }
    }

    @Test
    fun `key should not be one of`() {
        val validation = validate {
            "@event_name" should notBe(setOf("ny_søknad", "sendt_søknad"))
        }

        validation.test("""{ "event_name": "mitt_eventnavn" }""") { result, problems ->
            assertEquals(setOf("@event_name"), result)
            assertFalse(problems.hasErrors())
        }

        validation.test("""{ "@event_name": "ny_søknad" }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("@event_name: Feltet har en av uventet verdier ny_søknad, sendt_søknad", problems.toString())
        }

        validation.test("""{ "@event_name": "sendt_søknad" }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("@event_name: Feltet har en av uventet verdier ny_søknad, sendt_søknad", problems.toString())
        }

        validation.test("""{ "@event_name": "ok_navn" }""") { result, problems ->
            assertEquals(setOf("@event_name"), result)
            assertFalse(problems.hasErrors())
        }
    }



    @Test
    fun `key should not be boolean`() {
        val validation = validate {
            "er_sendt" should notBe(true)
        }

        validation.test("""{ "er_fullstendig": true }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("er_sendt: Feltet har ikke forventet verdi false", problems.toString())
        }

        validation.test("""{ "er_sendt": "uventet_verdi" }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("er_sendt: Feltet har ikke forventet verdi false", problems.toString())
        }

        validation.test("""{ "er_sendt": "true" }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("er_sendt: Feltet har ikke forventet verdi false", problems.toString())
        }

        validation.test("""{ "er_sendt": true }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("er_sendt: Feltet har ikke forventet verdi false", problems.toString())
        }

        validation.test("""{ "er_sendt": false }""") { result, problems ->
            assertEquals(setOf("er_sendt"), result)
            assertFalse(problems.hasErrors())
        }
    }

    @Test
    fun `key can be`() {
        val validation = validate {
            "@event_name" can be("mitt_eventnavn")
        }

        validation.test("""{ "event_name": "mitt_eventnavn" }""") { result, problems ->
            assertEquals(setOf("@event_name"), result)
            assertFalse(problems.hasErrors())
        }

        validation.test("""{ "@event_name": "uventet_verdi" }""") { result, problems ->
            assertEquals(emptySet<String>(), result)
            assertTrue(problems.hasErrors())
            assertContains("@event_name: Feltet har ikke forventet verdi mitt_eventnavn", problems.toString())
        }

        validation.test("""{ "@event_name": "mitt_eventnavn" }""") { result, problems ->
            assertEquals(setOf("@event_name"), result)
            assertFalse(problems.hasErrors())
        }
    }

    private fun MessageValidation.test(@Language("JSON") testMessage: String, assertBlock: (Set<String>, MessageProblems) -> Unit) {
        val problems = MessageProblems(testMessage)
        val node = jacksonObjectMapper().readTree(testMessage)
        val result = try {
            validatedKeys(node, problems)
        } catch (err: MessageProblems.MessageException) {
            emptySet()
        }
        assertBlock(result, problems)
    }

    private fun assertContains(expected: String, actual: String) {
        assertTrue(expected in actual) { "<$actual> does not contain <$expected>" }
    }
}