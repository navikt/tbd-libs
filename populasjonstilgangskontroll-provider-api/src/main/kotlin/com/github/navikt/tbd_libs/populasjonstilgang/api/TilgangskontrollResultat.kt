package com.github.navikt.tbd_libs.populasjonstilgang.api

sealed interface TilgangskontrollResultat {
    object Ok : TilgangskontrollResultat
    data class ManglerTilgang(val tilgangSomMangler: TilgangSomMangler): TilgangskontrollResultat
    object IdentIkkeFunnet : TilgangskontrollResultat
    data class UventetFeil(val menneskeligLesbarForklaring: String): TilgangskontrollResultat
}


enum class TilgangSomMangler {
    StrengtFortroligAdresse,
    StrengtFortroligAdresseUtland,
    FortroligAdresse,
    EgenAnsatt,
    Habilitet,
    Verge,
    GeografiskTilhørighet,
    PersonDød
}
