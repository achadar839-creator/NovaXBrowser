package com.novax.browser

/**
 * NovaX Browser staged feature registry.
 * PREPARE ONLY: these flags are not wired into MainActivity yet.
 * Do not treat a listed flag as an active feature.
 */
object FeatureRegistry {
    const val PREPARE_ONLY = true
    const val ADULT_FILTER_V2 = false
    const val SAFE_SEARCH_V2 = false
    const val TRACKER_FILTER_V2 = false
    const val ISOLATED_INCOGNITO_PROFILE = false
    const val HTTP_BYPASS_FIX = false
    const val DESKTOP_MODE_PERSISTENCE = false
    const val MINI_PLAYER_LIFECYCLE_FIX = false
    const val VIP_URL_VALIDATION = false
    const val SECURE_ADMIN_AUTH = false
}
