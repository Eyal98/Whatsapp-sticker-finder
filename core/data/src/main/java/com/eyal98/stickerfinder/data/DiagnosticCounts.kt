package com.eyal98.stickerfinder.data

/** Aggregate numbers about the index, for the diagnostics report. Holds no sticker content. */
data class DiagnosticCounts(
    val total: Int,
    val indexedCount: Int,
    /** Not yet indexed and already failed at least once. */
    val failingNow: Int,
    /** Needed more than one attempt (whether or not they finished since). */
    val retried: Int,
    val maxAttempts: Int,
    /** Indexed, but the image couldn't be decoded. */
    val undecodable: Int,
    val withText: Int,
    val captioned: Int,
    val withCaption: Int,
    val captionRetried: Int,
    val animated: Int,
    val starred: Int,
    val imageTagged: Int,
    val withImageTags: Int,
    val imageTagRetried: Int,
    val withPackName: Int,
    val withEmojis: Int,
    val vectors: Int,
)
