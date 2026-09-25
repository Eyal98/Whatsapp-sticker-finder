package com.eyal98.stickerfinder

import android.app.Application
import com.eyal98.stickerfinder.data.StickerDatabase
import com.eyal98.stickerfinder.data.StickerRepository
import com.eyal98.stickerfinder.index.StickerIndexHost

class StickerFinderApp : Application(), StickerIndexHost {
    override val database: StickerDatabase by lazy { StickerDatabase.create(this) }
    override val repository: StickerRepository by lazy { StickerRepository(database.stickerDao()) }
}
