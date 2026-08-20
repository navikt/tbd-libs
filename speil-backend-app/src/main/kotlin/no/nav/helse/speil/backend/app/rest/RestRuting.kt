package no.nav.helse.speil.backend.app.rest

import io.ktor.server.routing.Route
import no.nav.helse.speil.backend.app.auth.Brukerrolle

class RestRuting<ROLLE : Brukerrolle, TRANSAKSJON> internal constructor(
    @PublishedApi internal val route: Route,
    @PublishedApi internal val restAdapter: RestAdapter<ROLLE, TRANSAKSJON>,
) {
    inline fun <reified RESOURCE : Any, reified RESPONSE, ERROR : ApiErrorCode> get(
        behandler: GetBehandler<RESOURCE, RESPONSE, ERROR, ROLLE, TRANSAKSJON>,
    ) = with(route) { get(behandler, restAdapter) }

    inline fun <reified RESOURCE : Any, reified REQUEST : Any, reified RESPONSE, ERROR : ApiErrorCode> post(
        behandler: PostBehandler<RESOURCE, REQUEST, RESPONSE, ERROR, ROLLE, TRANSAKSJON>,
    ) = with(route) { post(behandler, restAdapter) }

    inline fun <reified RESOURCE : Any, reified REQUEST : Any, reified RESPONSE, ERROR : ApiErrorCode> put(
        behandler: PutBehandler<RESOURCE, REQUEST, RESPONSE, ERROR, ROLLE, TRANSAKSJON>,
    ) = with(route) { put(behandler, restAdapter) }

    inline fun <reified RESOURCE : Any, reified REQUEST : Any, reified RESPONSE, ERROR : ApiErrorCode> patch(
        behandler: PatchBehandler<RESOURCE, REQUEST, RESPONSE, ERROR, ROLLE, TRANSAKSJON>,
    ) = with(route) { patch(behandler, restAdapter) }

    inline fun <reified RESOURCE : Any, reified RESPONSE, ERROR : ApiErrorCode> delete(
        behandler: DeleteBehandler<RESOURCE, RESPONSE, ERROR, ROLLE, TRANSAKSJON>,
    ) = with(route) { delete(behandler, restAdapter) }
}
