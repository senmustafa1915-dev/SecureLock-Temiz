package com.securelock.app.model

enum class CredentialType {
    PIN,
    PATTERN
}

enum class CredentialFactor {
    NORMAL,
    VIP
}

data class AppRule(
    val normalEnabled: Boolean = false,

    val vipHash: String? = null,

    val strictUntil: Long = 0L,

    val planEnabled: Boolean = false,

    val startMinute: Int = 1380,

    val endMinute: Int = 420,

    val daysMask: Int = 127
) {
    val vipEnabled: Boolean
        get() = !vipHash.isNullOrBlank()

    val hasAnyRule: Boolean
        get() = normalEnabled ||
            vipEnabled ||
            strictUntil > System.currentTimeMillis() ||
            planEnabled
}

data class FailureRecord(
    val count: Int = 0,
    val strikes: Int = 0,
    val blockedUntil: Long = 0L
)

data class SecureState(
    val normalHash: String? = null,

    val normalType: String = "PIN",

    val strictHash: String? = null,

    val masterHash: String? = null,

    val rules: Map<String, AppRule> = emptyMap(),

    val failures: Map<String, FailureRecord> = emptyMap()
) {
    val initialized: Boolean
        get() = normalHash != null &&
            strictHash != null &&
            masterHash != null
}

data class LockRequirement(
    val packageName: String,
    val strictUntil: Long = 0L,
    val factors: List<CredentialFactor> = emptyList(),
    val breakAllowed: Boolean = false
) {
    val strictBlocked: Boolean
        get() = strictUntil > System.currentTimeMillis()
}
