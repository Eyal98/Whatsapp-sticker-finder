package com.eyal98.stickerfinder.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * A sticker the user sent after searching for [queryKey] (normalized, see PickRanking.key): what
 * search learns from. Stays on the phone like everything else, is never in problem reports, and
 * "Clear search history" in About deletes it. [tagged] is set once the search has been turned into
 * a tag on the sticker (see SearchTags), or was found not to fit one, so it's done only once and a
 * tag the user removes doesn't come back.
 */
@Entity(tableName = "search_picks", primaryKeys = ["queryKey", "stickerId"], indices = [Index("stickerId")])
data class SearchPick(
    val queryKey: String,
    val stickerId: Long,
    val count: Int,
    val lastAt: Long,
    @ColumnInfo(defaultValue = "0")
    val tagged: Boolean = false,
)
