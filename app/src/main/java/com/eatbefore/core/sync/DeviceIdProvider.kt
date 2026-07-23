package com.eatbefore.core.sync

import com.eatbefore.core.datastore.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stable identifier for this installation, used to tell whose events are whose during
 * household sync.
 *
 * Generated locally and never derived from hardware identifiers (ANDROID_ID, IMEI):
 * those are device-wide, survive uninstall and would let the shared file link the user
 * across apps. A random per-install id is enough to merge journals and reveals nothing.
 */
@Singleton
class DeviceIdProvider @Inject constructor(private val preferences: UserPreferencesRepository) {
    suspend fun deviceId(): String = preferences.deviceId()
}
