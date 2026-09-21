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
