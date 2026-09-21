package com.waynejiang.linefeed.feature.feed

/**
 * Pure placement rule for where a service card gets spliced into the article list (PLAN.md §7.5):
 * one after the [firstAfter]-th article (0-based `sortIndex`), then every [every] articles after
 * that. Kept as its own tiny type (not inlined into [FeedPagingTransforms]) so the spacing rule has
 * one obvious place to change and to unit test independent of `insertSeparators`/Paging machinery.
 */
class ServiceCardSlots(private val firstAfter: Int = 3, private val every: Int = 6) {
    init {
        require(firstAfter >= 0) { "firstAfter must be >= 0" }
        require(every > 0) { "every must be > 0" }
    }

    /** Returns the slot index a service card should occupy immediately before [sortIndex], or null. */
    fun slotBefore(sortIndex: Long): Int? {
        if (sortIndex < firstAfter) return null
        val offset = sortIndex - firstAfter
        return if (offset % every == 0L) (offset / every).toInt() else null
    }
}
