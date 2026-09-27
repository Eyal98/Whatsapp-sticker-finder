package com.eyal98.stickerfinder.data

import androidx.room.Entity
import androidx.room.Index

/**
 * A sticker the user sent after searching for [queryKey] (normalized, see PickRanking.key): what
 * search learns from. Stays on the phone like everything else, is never in problem reports, and
 * "Clear search history" in About deletes it.
 */
@Entity(tableName = "search_picks", primaryKeys = ["queryKey", "stickerId"], indices = [Index("stickerId")])
data class SearchPick(
    val queryKey: String,
    val stickerId: Long,
    val count: Int,
    val lastAt: Long,
)
