package com.securelock.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.securelock.app.security.DeviceProtection
import com.securelock.app.security.SecureStore
import java.util.concurrent.Executors

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pending = goAsync()

        Executors.newSingleThreadExecutor().execute {
            try {
                SecureStore.get(context).state()
                DeviceProtection.apply(context)
            } finally {
                pending.finish()
            }
        }
    }
}
