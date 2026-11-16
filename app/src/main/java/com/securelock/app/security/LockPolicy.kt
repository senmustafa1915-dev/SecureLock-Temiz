package com.securelock.app.security

import android.content.Context
import com.securelock.app.model.AppRule
import com.securelock.app.model.CredentialFactor
import com.securelock.app.model.LockRequirement
import java.util.Calendar

object LockPolicy {

    fun isScheduled(
        rule: AppRule,
        time: Long
    ): Boolean {
        if (!rule.planEnabled) return false

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = time

        val minute =
            calendar.get(Calendar.HOUR_OF_DAY) * 60 +
                calendar.get(Calendar.MINUTE)

        val today = calendar.get(Calendar.DAY_OF_WEEK) - 1
        val yesterday = (today + 6) % 7

        val start = rule.startMinute.coerceIn(0, 1439)
        val end = rule.endMinute.coerceIn(0, 1439)

        if (start == end) return false

        fun enabled(day: Int): Boolean {
            return rule.daysMask and (1 shl day) != 0
        }

        return if (start < end) {
            enabled(today) &&
                minute >= start &&
                minute < end
        } else {
            val currentDay = enabled(today) && minute >= start
            val previousDay = enabled(yesterday) && minute < end

            currentDay || previousDay
        }
    }

    fun requirement(
        context: Context,
        packageName: String
    ): LockRequirement? {
        val store = SecureStore.get(context)
        val rule = store.getRule(packageName)
        val now = System.currentTimeMillis()

        if (rule.strictUntil > now) {
            return LockRequirement(
                packageName = packageName,
                strictUntil = rule.strictUntil
            )
        }

        val scheduled = isScheduled(rule, now)

        val factors = mutableListOf<CredentialFactor>()

        if (rule.normalEnabled || scheduled) {
            factors.add(CredentialFactor.NORMAL)
        }

        if (rule.vipEnabled) {
            factors.add(CredentialFactor.VIP)
        }

        if (factors.isEmpty()) return null

        return LockRequirement(
            packageName = packageName,
            factors = factors,
            breakAllowed =
                factors.size == 1 &&
                    factors.first() == CredentialFactor.NORMAL &&
                    rule.normalEnabled &&
                    !scheduled &&
                    !rule.vipEnabled
        )
    }

    fun protectedPackages(context: Context): Set<String> {
        return SecureStore.get(context)
            .allRules()
            .filterValues { it.hasAnyRule }
            .keys
    }
}
