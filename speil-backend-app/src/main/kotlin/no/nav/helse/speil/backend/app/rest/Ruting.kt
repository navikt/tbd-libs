package no.nav.helse.speil.backend.app.rest

import io.github.smiley4.ktoropenapi.resources.delete as documentedDelete
import io.github.smiley4.ktoropenapi.resources.get as documentedGet
import io.github.smiley4.ktoropenapi.resources.patch as documentedPatch
import io.github.smiley4.ktoropenapi.resources.post as documentedPost
import io.github.smiley4.ktoropenapi.resources.put as documentedPut
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import no.nav.helse.speil.backend.app.auth.Brukerrolle

inline fun <reified RESOURCE : Any, reified RESPONSE, ERROR : ApiErrorCode, ROLLE : Brukerrolle, TRANSAKSJON> Route.get(
    behandler: GetBehandler<RESOURCE, RESPONSE, ERROR, ROLLE, TRANSAKSJON>,
    restAdapter: RestAdapter<ROLLE, TRANSAKSJON>,
) {
    documentedGet<RESOURCE>({ behandler.openApiUtenRequestBody<RESPONSE, ERROR, ROLLE>(this) }) { resource ->
        restAdapter.håndter(call, resource, behandler) { r, kallKontekst -> behandler.behandle(r, kallKontekst) }
    }
}

inline fun <reified RESOURCE : Any, reified REQUEST : Any, reified RESPONSE, ERROR : ApiErrorCode, ROLLE : Brukerrolle, TRANSAKSJON> Route.post(
    behandler: PostBehandler<RESOURCE, REQUEST, RESPONSE, ERROR, ROLLE, TRANSAKSJON>,
    restAdapter: RestAdapter<ROLLE, TRANSAKSJON>,
) {
    documentedPost<RESOURCE>({ behandler.openApiMedRequestBody<REQUEST, RESPONSE, ERROR, ROLLE>(this) }) { resource ->
        val request = call.receive<REQUEST>()
        restAdapter.håndter(call, resource, behandler) { r, kallKontekst -> behandler.behandle(r, request, kallKontekst) }
    }
}

inline fun <reified RESOURCE : Any, reified REQUEST : Any, reified RESPONSE, ERROR : ApiErrorCode, ROLLE : Brukerrolle, TRANSAKSJON> Route.put(
    behandler: PutBehandler<RESOURCE, REQUEST, RESPONSE, ERROR, ROLLE, TRANSAKSJON>,
    restAdapter: RestAdapter<ROLLE, TRANSAKSJON>,
) {
    documentedPut<RESOURCE>({ behandler.openApiMedRequestBody<REQUEST, RESPONSE, ERROR, ROLLE>(this) }) { resource ->
        val request = call.receive<REQUEST>()
        restAdapter.håndter(call, resource, behandler) { r, kallKontekst -> behandler.behandle(r, request, kallKontekst) }
    }
}

inline fun <reified RESOURCE : Any, reified REQUEST : Any, reified RESPONSE, ERROR : ApiErrorCode, ROLLE : Brukerrolle, TRANSAKSJON> Route.patch(
    behandler: PatchBehandler<RESOURCE, REQUEST, RESPONSE, ERROR, ROLLE, TRANSAKSJON>,
    restAdapter: RestAdapter<ROLLE, TRANSAKSJON>,
) {
    documentedPatch<RESOURCE>({ behandler.openApiMedRequestBody<REQUEST, RESPONSE, ERROR, ROLLE>(this) }) { resource ->
        val request = call.receive<REQUEST>()
        restAdapter.håndter(call, resource, behandler) { r, kallKontekst -> behandler.behandle(r, request, kallKontekst) }
    }
}

inline fun <reified RESOURCE : Any, reified RESPONSE, ERROR : ApiErrorCode, ROLLE : Brukerrolle, TRANSAKSJON> Route.delete(
    behandler: DeleteBehandler<RESOURCE, RESPONSE, ERROR, ROLLE, TRANSAKSJON>,
    restAdapter: RestAdapter<ROLLE, TRANSAKSJON>,
) {
    documentedDelete<RESOURCE>({ behandler.openApiUtenRequestBody<RESPONSE, ERROR, ROLLE>(this) }) { resource ->
        restAdapter.håndter(call, resource, behandler) { r, kallKontekst -> behandler.behandle(r, kallKontekst) }
    }
}
