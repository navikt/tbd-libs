package com.github.navikt.tbd_libs.jackson

import com.fasterxml.jackson.databind.JsonNode

fun JsonNode?.isMissingOrNull() = this == null || this.isNull || this.isMissingNode
