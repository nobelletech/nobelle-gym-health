package com.nobelle.phone.health

import com.nobelle.core.health.HealthConnectCategory

/** Maps Health Connect permission strings back to the app's categories. */
object HealthConnectPermissions {
    fun categoriesFor(permissionStrings: Set<String>): Set<HealthConnectCategory> =
        HealthConnectCategory.entries.filter { it.permission in permissionStrings }.toSet()
}
