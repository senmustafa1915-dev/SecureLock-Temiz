package com.securelock.app.security

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import com.securelock.app.admin.SecureLockAdminReceiver

data class DeviceStatus(
    val adminActive: Boolean,
    val deviceOwner: Boolean,
    val uninstallBlocked: Boolean,
    val dateTimeRestricted: Boolean
)

object DeviceProtection {

    fun admin(context: Context): ComponentName {
        return ComponentName(
            context,
            SecureLockAdminReceiver::class.java
        )
    }

    private fun manager(context: Context): DevicePolicyManager {
        return context.getSystemService(
            Context.DEVICE_POLICY_SERVICE
        ) as DevicePolicyManager
    }

    fun isAdminActive(context: Context): Boolean {
        return manager(context).isAdminActive(admin(context))
    }

    fun isDeviceOwner(context: Context): Boolean {
        return manager(context).isDeviceOwnerApp(
            context.packageName
        )
    }

    fun apply(context: Context): DeviceStatus {
        val dpm = manager(context)
        val component = admin(context)

        val adminActive = dpm.isAdminActive(component)
        val owner = dpm.isDeviceOwnerApp(context.packageName)

        var uninstallBlocked = false
        var dateTimeRestricted = false

        if (owner) {
            try {
                dpm.setUninstallBlocked(
                    component,
                    context.packageName,
                    true
                )

                uninstallBlocked = dpm.isUninstallBlocked(
                    component,
                    context.packageName
                )
            } catch (_: SecurityException) {
                uninstallBlocked = false
            }

            try {
                dpm.addUserRestriction(
                    component,
                    UserManager.DISALLOW_CONFIG_DATE_TIME
                )

                if (Build.VERSION.SDK_INT >= 30) {
                    dpm.setAutoTimeEnabled(
                        component,
                        true
                    )
                    dpm.setAutoTimeZoneEnabled(
                        component,
                        true
                    )
                }

                dateTimeRestricted = true
            } catch (_: Exception) {
                dateTimeRestricted = false
            }
        }

        return DeviceStatus(
            adminActive = adminActive,
            deviceOwner = owner,
            uninstallBlocked = uninstallBlocked,
            dateTimeRestricted = dateTimeRestricted
        )
    }

    fun requestAdmin(context: Context) {
        val intent = android.content.Intent(
            DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN
        )

        intent.putExtra(
            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
            admin(context)
        )

        intent.putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Secure Lock cihaz korumasını etkinleştir."
        )

        context.startActivity(intent)
    }

    fun lockNow(context: Context): Boolean {
        return try {
            val dpm = manager(context)

            if (!dpm.isAdminActive(admin(context))) {
                return false
            }

            dpm.lockNow()
            true
        } catch (_: Exception) {
            false
        }
    }
}
