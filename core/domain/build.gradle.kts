plugins {
    id("linefeed.jvm.library")
}

// `paging-common` is a KMP artifact with a JVM target, so it can live in this pure-JVM module.
// `api` (not `implementation`): `ArticleRepository.feedPagingData()` returns `Flow<PagingData<FeedArticle>>`,
// so PagingData is part of this module's own public API surface and must leak to consumers.
dependencies {
    api(libs.paging.common)
}
