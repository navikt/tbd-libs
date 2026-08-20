package no.nav.helse.speil.backend.app.auth

/**
 * App-definert brukerrolle. Implementeres av appens egen enum (f.eks. `Brukerrolle.Beslutter`), slik
 * at rollemodellen ikke lekker inn i libben som en generisk type-parameter (jf. ,
 * 1: "typeparameter-støy" — brukerroller ble endret fra typeparameter til
 * markørinterface for lesbarhet).
 */
interface Brukerrolle {
  val navn: String
}
