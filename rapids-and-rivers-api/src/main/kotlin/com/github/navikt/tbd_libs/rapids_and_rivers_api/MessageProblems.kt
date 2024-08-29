package com.github.navikt.tbd_libs.rapids_and_rivers_api

class MessageProblems(private val originalMessage: String) {
    private val errors = mutableMapOf<String, MutableList<String>>()
    private val severe = mutableListOf<String>()

    fun error(key: String, melding: String) {
        errors.getOrPut(key) { mutableListOf() }.add(melding)
    }

    @Deprecated("ta i bruk den andre")
    fun error(melding: String) {
        errors.getOrPut("") { mutableListOf() }.add(melding)
    }

    fun severe(melding: String, vararg params: Any): Nothing {
        severe.add(String.format(melding, *params))
        throw MessageException(this)
    }

    fun hasErrors() = severe.isNotEmpty() || errors.isNotEmpty()

    fun hasProblemsWith(key: String) = key in errors

    fun toExtendedReport(): String {
        if (!hasErrors()) return "No errors in message\n"
        val results = StringBuffer()
        results.append("Message has errors:\n\t")
        append("Severe errors", severe, results)
        append("Errors", errors.map { (key, value) -> "$key: $value" }, results)
        results.append("\n")
        results.append("Original message: $originalMessage\n")
        return results.toString()
    }

    override fun toString(): String {
        return (severe.map { "S: $it" } + errors.flatMap { (k, v) -> v.map { "E: $k: $it" } })
            .joinToString(separator = "\n")
    }

    private fun append(label: String, messages: List<String>, results: StringBuffer) {
        if (messages.isEmpty()) return
        results.append("\n")
        results.append(label)
        results.append(": ")
        results.append(messages.size)
        for (message in messages) {
            results.append("\n\t")
            results.append(message)
        }
    }

    class MessageException(val problems: MessageProblems) : RuntimeException(problems.toString())
}
