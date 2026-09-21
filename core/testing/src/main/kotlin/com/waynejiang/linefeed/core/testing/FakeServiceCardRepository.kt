package com.waynejiang.linefeed.core.testing

import com.waynejiang.linefeed.core.domain.model.ServiceCard
import com.waynejiang.linefeed.core.domain.repository.ServiceCardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeServiceCardRepository(initial: List<ServiceCard> = emptyList()) : ServiceCardRepository {
    private val cards = MutableStateFlow(initial)

    fun setCards(value: List<ServiceCard>) {
        cards.value = value
    }

    override fun observeServiceCards(): Flow<List<ServiceCard>> = cards
}
