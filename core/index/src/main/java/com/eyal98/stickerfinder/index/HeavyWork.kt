package com.eyal98.stickerfinder.index

/**
 * Coordinates the CPU-heavy model jobs in this process. Captioning and picture tagging both keep
 * several cores busy; run together, each goes at about half speed and the phone gets warm, so
 * picture tagging steps aside while captioning runs and resumes when it ends.
 */
internal object HeavyWork {
    @Volatile
    var captioning: Boolean = false
}
