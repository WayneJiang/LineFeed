package com.waynejiang.linefeed.core.domain.freshness

/** Why a refresh evaluation is happening; drives whether TTL is respected (see [FreshnessPolicy]). */
enum class RefreshTrigger { COLD_START, FOREGROUND, NETWORK_RESTORED, USER_PULL }

enum class SkipReason { FRESH, OFFLINE }

sealed interface RefreshDecision {
    data object Fetch : RefreshDecision
    data class Skip(val reason: SkipReason) : RefreshDecision
}
