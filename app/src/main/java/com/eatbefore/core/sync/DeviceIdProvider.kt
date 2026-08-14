package com.eatbefore.core.sync

import com.eatbefore.core.datastore.UserPreferencesRepository
import kotlinx.coroutines.flow.first
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

    /**
     * What the other household member sees against actions done here.
     *
     * The phone's model until the user picks something better: "Кто выбросил сметану?" is
     * answered well enough by «Redmi Note 12», and asking for a name during setup would be
     * one more form between the user and their first product. Blank if even the model is
     * unavailable, in which case the peer falls back to "another device".
     */
    suspend fun deviceName(): String =
        preferences.preferences.first().deviceName ?: android.os.Build.MODEL.orEmpty()
}
