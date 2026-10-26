package com.notifyvoice.app.domain

enum class RingerMode { SOUND, VIBRATE, SILENT }

/** Vibrate permits private speech; calls, DND and muted media still block it. */
fun playbackConditionsReason(
    ringerMode: RingerMode?,
    communicationActive: Boolean,
    dndAllowsSound: Boolean,
    mediaVolume: Int,
): Reason? {
    if (communicationActive) return Reason.CALL
    if (ringerMode != RingerMode.SOUND && ringerMode != RingerMode.VIBRATE) return Reason.SILENT
    if (!dndAllowsSound) return Reason.DND
    if (mediaVolume <= 0) return Reason.MEDIA_MUTED
    return null
}
