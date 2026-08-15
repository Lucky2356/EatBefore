package com.eatbefore.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Save
import androidx.compose.ui.graphics.vector.ImageVector
import com.eatbefore.R
import com.eatbefore.navigation.Routes

/**
 * The sections settings are split into, in the order they are shown.
 *
 * Ordered by how often they are opened rather than by importance: the theme and the
 * "how soon is soon" threshold are touched far more than the catalogue account.
 */
enum class SettingsSection(val titleRes: Int, val descRes: Int, val route: String, val icon: ImageVector) {
    APPEARANCE(
        titleRes = R.string.settings_section_appearance,
        descRes = R.string.settings_section_appearance_desc,
        route = Routes.SETTINGS_APPEARANCE,
        icon = Icons.Outlined.Palette,
    ),
    INVENTORY(
        titleRes = R.string.settings_section_inventory,
        descRes = R.string.settings_section_inventory_desc,
        route = Routes.SETTINGS_INVENTORY,
        icon = Icons.Outlined.Inventory2,
    ),
    NOTIFICATIONS(
        titleRes = R.string.settings_section_notifications,
        descRes = R.string.settings_section_notifications_desc,
        route = Routes.SETTINGS_NOTIFICATIONS,
        icon = Icons.Outlined.Notifications,
    ),
    DATA(
        titleRes = R.string.settings_section_data,
        descRes = R.string.settings_section_data_desc,
        route = Routes.SETTINGS_DATA,
        icon = Icons.Outlined.Save,
    ),
    SHARING(
        titleRes = R.string.settings_section_sharing,
        descRes = R.string.settings_section_sharing_desc,
        route = Routes.SETTINGS_SHARING,
        icon = Icons.Outlined.People,
    ),
    CATALOG(
        titleRes = R.string.settings_section_catalog,
        descRes = R.string.settings_section_catalog_desc,
        route = Routes.SETTINGS_CATALOG,
        icon = Icons.Outlined.CloudUpload,
    ),
    ABOUT(
        titleRes = R.string.settings_section_about,
        descRes = R.string.settings_section_about_desc,
        route = Routes.SETTINGS_ABOUT,
        icon = Icons.Outlined.Info,
    ),
}

/**
 * One searchable setting: what it is called, where it lives, and any other words a person
 * might look for it by ("тёмная" for the theme, "пароль" for the catalogue account).
 */
data class SettingEntry(val titleRes: Int, val section: SettingsSection, val keywordsRes: Int? = null)

/**
 * Every setting the search can find, kept by hand.
 *
 * Kept by hand on purpose: reading it out of the composables would mean a registry that
 * has to be fed at composition time, and search would then only find what had already
 * been drawn. The cost is that this list has to be extended along with a new setting —
 * [SettingsIndexTest] checks that no section is left without entries, which is what a
 * forgotten addition looks like.
 */
val SETTINGS_INDEX: List<SettingEntry> = listOf(
    SettingEntry(R.string.settings_theme, SettingsSection.APPEARANCE, R.string.settings_kw_theme),
    SettingEntry(R.string.settings_dynamic_colors, SettingsSection.APPEARANCE),

    SettingEntry(R.string.settings_soon_days, SettingsSection.INVENTORY, R.string.settings_kw_soon),
    SettingEntry(R.string.settings_default_location, SettingsSection.INVENTORY),
    SettingEntry(R.string.locations_title, SettingsSection.INVENTORY, R.string.settings_kw_places),
    SettingEntry(R.string.settings_detailed_quantity, SettingsSection.INVENTORY),

    SettingEntry(
        R.string.settings_notifications_enabled,
        SettingsSection.NOTIFICATIONS,
        R.string.settings_kw_notifications,
    ),
    SettingEntry(R.string.settings_notification_time, SettingsSection.NOTIFICATIONS),
    SettingEntry(R.string.settings_quiet_hours, SettingsSection.NOTIFICATIONS, R.string.settings_kw_quiet),

    SettingEntry(R.string.settings_auto_backup, SettingsSection.DATA, R.string.settings_kw_backup),
    SettingEntry(R.string.settings_export, SettingsSection.DATA),
    SettingEntry(R.string.settings_import, SettingsSection.DATA),
    SettingEntry(R.string.settings_restore_auto, SettingsSection.DATA, R.string.settings_kw_restore),

    SettingEntry(R.string.settings_sharing_folder, SettingsSection.SHARING, R.string.settings_kw_sharing),
    SettingEntry(R.string.settings_device_name, SettingsSection.SHARING),
    SettingEntry(R.string.settings_sharing_now, SettingsSection.SHARING),

    SettingEntry(R.string.settings_off_account, SettingsSection.CATALOG, R.string.settings_kw_catalog),
    SettingEntry(R.string.settings_off_check, SettingsSection.CATALOG),

    SettingEntry(R.string.settings_version, SettingsSection.ABOUT, R.string.settings_kw_version),
    SettingEntry(R.string.settings_diagnostics, SettingsSection.ABOUT, R.string.settings_kw_diagnostics),
)
