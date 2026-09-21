package com.waynejiang.linefeed.core.data.repository

import com.waynejiang.linefeed.core.data.database.dao.ServiceCardDao
import com.waynejiang.linefeed.core.data.database.dao.SyncMetadataDao
import com.waynejiang.linefeed.core.data.mapper.toAppError
import com.waynejiang.linefeed.core.data.mapper.toDomain
import com.waynejiang.linefeed.core.data.mapper.toEntity
import com.waynejiang.linefeed.core.data.network.ServiceRemoteDataSource
import com.waynejiang.linefeed.core.data.refresh.SourceRefresher
import com.waynejiang.linefeed.core.domain.model.ContentSource
import com.waynejiang.linefeed.core.domain.model.ServiceCard
import com.waynejiang.linefeed.core.domain.refresh.SourceResult
import com.waynejiang.linefeed.core.domain.repository.ServiceCardRepository
import com.waynejiang.linefeed.core.domain.time.AppClock
import com.waynejiang.linefeed.core.domain.util.suspendRunCatching
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Small, fixed-size list (see [PRODUCT_LIMIT]) so replacing the whole set on every refresh (see `ServiceCardDao.replaceAll`) stays cheap. */
private const val PRODUCT_LIMIT = 10

internal class OfflineFirstServiceCardRepository @Inject constructor(
    private val serviceCardDao: ServiceCardDao,
    private val remoteDataSource: ServiceRemoteDataSource,
    private val syncMetadataDao: SyncMetadataDao,
    private val clock: AppClock,
) : ServiceCardRepository, SourceRefresher {

    override val source: ContentSource = ContentSource.SERVICES

    override fun observeServiceCards(): Flow<List<ServiceCard>> =
        serviceCardDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun refresh(): SourceResult {
        val now = clock.now()
        return suspendRunCatching {
            val products = remoteDataSource.fetchProducts(limit = PRODUCT_LIMIT)
            serviceCardDao.replaceAll(products.mapIndexed { position, dto -> dto.toEntity(position = position) })
            syncMetadataDao.markSuccess(source.name, now.toEpochMilli())
        }.fold(
            onSuccess = { SourceResult.Success },
            onFailure = { error ->
                syncMetadataDao.markFailure(source.name, now.toEpochMilli(), error.toAppError().name)
                SourceResult.Failed(error.toAppError())
            },
        )
    }
}
