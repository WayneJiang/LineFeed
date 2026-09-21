package com.waynejiang.linefeed.core.domain.model

/** Coarse network shape, enough to drive freshness TTLs and offline UI without leaking platform APIs. */
enum class NetworkStatus { OFFLINE, METERED, UNMETERED }

/** The three independently-refreshed feed sources (see PLAN.md §3 for their distinct TTLs). */
enum class ContentSource { WEATHER, ARTICLES, SERVICES }

/** A small, UI-facing error taxonomy; the data layer maps every `Throwable` into one of these. */
enum class AppError { OFFLINE, TIMEOUT, SERVER, PARSE, UNKNOWN }
