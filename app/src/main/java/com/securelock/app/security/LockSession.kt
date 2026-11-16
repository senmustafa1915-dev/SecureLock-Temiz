package com.securelock.app.security

import java.util.concurrent.ConcurrentHashMap

object LockSession {

    private data class Session(
        val expiresAt: Long,
        val temporary: Boolean
    )

    private val sessions =
        ConcurrentHashMap<String, Session>()

    fun grant(
        packageName: String,
        durationMs: Long
    ) {
        sessions[packageName] = Session(
            expiresAt = if (durationMs > 0L) {
                System.currentTimeMillis() + durationMs
            } else {
                Long.MAX_VALUE
            },
            temporary = durationMs > 0L
        )
    }

    fun isAllowed(packageName: String): Boolean {
        val session = sessions[packageName] ?: return false

        if (session.expiresAt <= System.currentTimeMillis()) {
            sessions.remove(packageName)
            return false
        }

        return true
    }

    fun onLeft(packageName: String) {
        val session = sessions[packageName] ?: return

        if (!session.temporary) {
            sessions.remove(packageName)
        }
    }

    fun clear(packageName: String) {
        sessions.remove(packageName)
    }

    fun clearAll() {
        sessions.clear()
    }
}
