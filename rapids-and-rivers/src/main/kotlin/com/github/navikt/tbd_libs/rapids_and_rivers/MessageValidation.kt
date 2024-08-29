package com.github.navikt.tbd_libs.rapids_and_rivers

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.ValidationResult.Companion.createRule
import com.github.navikt.tbd_libs.rapids_and_rivers.ValidationResult.Invalid
import com.github.navikt.tbd_libs.rapids_and_rivers.ValidationResult.Valid
import com.github.navikt.tbd_libs.rapids_and_rivers.ValueValidation.Companion.allAreOK
import com.github.navikt.tbd_libs.rapids_and_rivers.ValueValidation.Companion.optional
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems

val exist = createRule("Feltet finnes ikke") { node -> !node.isMissingOrNull() }
val notExist = createRule("Feltet finnes") { node -> node.isMissingOrNull() }
fun be(expectedValue: String) = createRule("Feltet har ikke forventet verdi $expectedValue") { node -> node.isTextual && node.asText() == expectedValue }
fun be(expectedValues: Collection<String>) = createRule("Feltet har ikke en av forventet verdier ${expectedValues.joinToString()}") { node -> node.isTextual && node.asText() in expectedValues }
fun be(expectedValue: Boolean) = createRule("Feltet har ikke forventet verdi $expectedValue") { node -> node.isBoolean && node.asBoolean() == expectedValue }
fun be(expectedValue: Number) = createRule("Feltet har ikke forventet verdi $expectedValue") { node -> node.isNumber && node.numberValue() == expectedValue }
fun be(expectedValue: MessageValidation) = ValueValidation { key, node, problems ->
    if (!node.isArray) Invalid("Forventet felt er ikke array").also { problems.error(key, "Forventet felt er ikke array") }
    else {
        node.forEachIndexed { index, child ->
            val childProblems = MessageProblems(child.toString())
            expectedValue.validate(child, childProblems)
            if (childProblems.hasErrors()) problems.error("$key.$index", "Array element did not pass validation: $childProblems")
        }
        if (problems.hasErrors()) Invalid("Array did not pass validation") else Valid
    }
}
fun be(customParser: (JsonNode) -> Any) = createRule("Feltet kan ikke parses") { node ->
    try {
        customParser(node)
        true
    } catch (err: Exception) {
        false
    }
}
fun beAllOrAny(expectedValues: Collection<String>) = createRule("Feltet har ikke minst en av forventet verdier ${expectedValues.joinToString()}") { node -> node.isArray && node.any { it.asText() in expectedValues } }
fun beAll(expectedValues: Collection<String>) = createRule("Feltet har ikke alle forventet verdier ${expectedValues.joinToString()}") { node -> node.isArray && node.map(JsonNode::asText).containsAll(expectedValues) }
fun notBe(expectedValue: String) = createRule("Feltet har uventet verdi $expectedValue") { node -> node.isMissingOrNull() || (node.isTextual && node.asText() != expectedValue) }
fun notBe(expectedValues: Collection<String>) = createRule("Feltet har en av uventet verdier ${expectedValues.joinToString()}") { node -> node.isMissingOrNull() || (node.isTextual && node.asText() !in expectedValues) }
fun notBe(expectedValue: Boolean) = be(!expectedValue)

fun array(arrayValidation: MessageValidation.() -> Unit) = validate(arrayValidation)

fun validate(validationSpec: MessageValidation.() -> Unit): MessageValidation {
    val spec = MessageValidation()
    spec.validationSpec()
    return spec
}

/**
 * validation = MessageValidation {
 *      "key" should exist
 *      "key" must be("value")
 * }
 */
fun interface ValueValidation {
    fun validate(key: String, node: JsonNode, problems: MessageProblems): ValidationResult

    companion object {
        fun ValueValidation.optional() = ValueValidation { key, node, problems ->
            if (node.isMissingOrNull()) Valid else validate(key, node, problems)
        }
        fun Collection<ValueValidation>.allAreOK(key: String, valueToBeEvaluated: JsonNode, problems: MessageProblems): Boolean {
            return all { spec -> spec.validate(key, valueToBeEvaluated, problems) is Valid }
        }
    }
}

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val message: String) : ValidationResult()
    companion object {
        fun createRule(message: String, validation: (JsonNode) -> Boolean) = ValueValidation { key, node, problems ->
            when (validation(node)) {
                true -> Valid
                false -> Invalid(message).also {
                    problems.error(key, message)
                }
            }

        }
    }
}

class MessageValidation {
    private companion object {
        private const val nestedKeySeparator = '.'
    }
    private val fields = mutableMapOf<String, MutableList<ValueValidation>>()

    fun validate(node: JsonNode, problems: MessageProblems) =
        fields
            .mapValues { (key, validations) ->
                val valueToBeEvaluated = node.node(key)
                validations.allAreOK(key, valueToBeEvaluated, problems)
            }

    fun validatedKeys(node: JsonNode, problems: MessageProblems): Set<String> {
        return validate(node, problems).filter { (_, result) -> result }.keys
    }

    private fun JsonNode.node(path: String): JsonNode {
        if (!path.contains(nestedKeySeparator)) return path(path)
        return path.split(nestedKeySeparator).fold(this) { result: JsonNode, key ->
            result.path(key)
        }
    }

    infix fun Collection<String>.should(what: ValueValidation) {
        forEach { it.should(what) }
    }
    infix fun String.should(what: ValueValidation) =
        addValidation(this, what)

    infix fun String.must(what: ValueValidation) =
        addValidation(this, what)

    infix fun Collection<String>.can(what: ValueValidation) {
        forEach { it.can(what) }
    }
    infix fun String.can(what: ValueValidation) =
        should(what.optional())

    private fun addValidation(key: String, validation: ValueValidation) {
        fields.getOrPut(key) { mutableListOf() }.add(validation)
    }
}