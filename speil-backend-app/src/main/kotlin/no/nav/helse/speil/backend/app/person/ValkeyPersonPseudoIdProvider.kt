package no.nav.helse.speil.backend.app.person

import com.github.navikt.tbd_libs.personpseudoid.PersonPseudoIdClient
import com.github.navikt.tbd_libs.personpseudoid.ValkeyConfig

class ValkeyPersonPseudoIdProvider(
    valkeyConfig: ValkeyConfig,
) : PersonPseudoIdProvider {
    private val client = PersonPseudoIdClient(valkeyConfig)

    override fun nyPersonPseudoId(identitetsnummer: Identitetsnummer): PersonPseudoId = PersonPseudoId(client.nyPersonPseudoId(identitetsnummer.value))

    override fun finnIdentitetsnummer(personPseudoId: PersonPseudoId): Identitetsnummer? = client.finnIdentitetsnummer(personPseudoId.value)?.let { Identitetsnummer(it) }

    companion object {

        fun fraEnv(
            instansNavn: String = "personpseudoid",
            env: Map<String, String> = System.getenv(),
        ): ValkeyPersonPseudoIdProvider {
            val prefix = "VALKEY_${instansNavn.uppercase()}"
            val config =
                ValkeyConfig(
                    username = env.getValue("${prefix}_USERNAME"),
                    password = env.getValue("${prefix}_PASSWORD"),
                    connectionString = env.getValue("${prefix}_URI"),
                )
            return ValkeyPersonPseudoIdProvider(config)
        }
    }
}
