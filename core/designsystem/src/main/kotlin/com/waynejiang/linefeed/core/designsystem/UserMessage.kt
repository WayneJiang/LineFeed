package com.waynejiang.linefeed.core.designsystem

import androidx.annotation.StringRes

/**
 * A one-shot, snackbar-shaped message. Carries a resource id (not a resolved `String`) so
 * ViewModels stay Android-resource-free... except they still need *a* string id source, and
 * [messageRes] living here keeps that one small compromise in one well-known place instead of each
 * feature module inventing its own. [id] lets the UI dedupe "already shown" via
 * `LaunchedEffect(message.id)`/consumed-callback patterns without comparing message content.
 */
data class UserMessage(
    val id: Long,
    @StringRes val messageRes: Int,
    val formatArgs: List<Any> = emptyList(),
)
