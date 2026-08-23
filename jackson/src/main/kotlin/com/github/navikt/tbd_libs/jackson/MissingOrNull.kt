package com.github.navikt.tbd_libs.jackson

import tools.jackson.databind.JsonNode

fun JsonNode?.isMissingOrNull() = this == null || this.isNull || this.isMissingNode
