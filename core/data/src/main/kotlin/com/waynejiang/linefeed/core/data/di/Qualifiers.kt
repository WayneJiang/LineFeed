package com.waynejiang.linefeed.core.data.di

import javax.inject.Qualifier

// One Retrofit instance per base URL (PLAN.md §2.5), disambiguated with these qualifiers since all
// three share the same OkHttpClient/converter setup.
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SpaceflightRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenMeteoRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DummyJsonRetrofit

/** The IO-bound dispatcher network/DB work runs on; lets tests substitute a test dispatcher. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * A process-lifetime [kotlinx.coroutines.CoroutineScope] for work that must outlive any single
 * screen (e.g. a [com.waynejiang.linefeed.core.domain.util.SingleFlight]-shared refresh started
 * from a pull-to-refresh the user has since navigated away from).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
