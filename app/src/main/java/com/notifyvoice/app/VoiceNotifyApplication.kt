package com.notifyvoice.app

import android.app.Application
import com.notifyvoice.app.audio.AnnouncementController

class VoiceNotifyApplication : Application() {
    val controller: AnnouncementController by lazy { AnnouncementController(this) }
}
