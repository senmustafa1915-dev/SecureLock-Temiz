package com.securelock.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.securelock.app.model.AppRule
import com.securelock.app.model.FailureRecord
import com.securelock.app.model.SecureState
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

class SecureStore private constructor(context: Context) {

    companion object {
        private const val PREFS = "secure_lock_encrypted"

        private const val DATA_KEY = "encrypted_state"

        private const val KEY_ALIAS = "SecureLock_AES_GCM"

        private const val ITERATIONS = 180000

        private const val KEY_LENGTH = 256

        @Volatile
        private var instance: SecureStore? = null

        fun get(context: Context): SecureStore {
            return instance ?: synchronized(this) {
                instance ?: SecureStore(
                    context.applicationContext
                ).also { instance = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(
        PREFS,
        Context.MODE_PRIVATE
    )

    private val mutex = Any()

    @Volatile
    private var cached: SecureState? = null

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")

        keyStore.load(null)

        val existing = keyStore.getKey(
            KEY_ALIAS,
            null
        )

        if (existing is SecretKey) {
            return existing
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or
                KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(
                KeyProperties.ENCRYPTION_PADDING_NONE
            )
            .setRandomizedEncryptionRequired(true)
            .build()

        generator.init(spec)

        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        cipher.init(
            Cipher.ENCRYPT_MODE,
            key()
        )

        val encrypted = cipher.doFinal(
            value.toByteArray(Charsets.UTF_8)
        )

        return Base64.encodeToString(
            cipher.iv + encrypted,
            Base64.NO_WRAP
        )
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(
            value,
            Base64.NO_WRAP
        )

        require(bytes.size > 12)

        val iv = bytes.copyOfRange(0, 12)

        val encrypted = bytes.copyOfRange(
            12,
            bytes.size
        )

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, iv)
        )

        return String(
            cipher.doFinal(encrypted),
            Charsets.UTF_8
        )
    }

    fun hashCredential(value: String): String {
        require(value.length in 4..64)

        val salt = ByteArray(16)

        SecureRandom().nextBytes(salt)

        val specification = PBEKeySpec(
            value.toCharArray(),
            salt,
            ITERATIONS,
            KEY_LENGTH
        )

        val derived = try {
            SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(specification)
                .encoded
        } finally {
            specification.clearPassword()
        }

        return listOf(
            ITERATIONS.toString(),
            Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(derived, Base64.NO_WRAP)
        ).joinToString(":")
    }

    fun verifyCredential(
        value: String,
        encodedHash: String?
    ): Boolean {
        if (encodedHash.isNullOrBlank()) return false

        return try {
            val parts = encodedHash.split(":")

            if (parts.size != 3) return false

            val iterations = parts[0].toInt()

            val salt = Base64.decode(
                parts[1],
                Base64.NO_WRAP
            )

            val expected = Base64.decode(
                parts[2],
                Base64.NO_WRAP
            )

            val specification = PBEKeySpec(
                value.toCharArray(),
                salt,
                iterations,
                expected.size * 8
            )

            val actual = try {
                SecretKeyFactory
                    .getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(specification)
                    .encoded
            } finally {
                specification.clearPassword()
            }

            MessageDigest.isEqual(
                actual,
                expected
            )
        } catch (_: Exception) {
            false
        }
    }

    private fun readNullable(
        json: JSONObject,
        name: String
    ): String? {
        if (!json.has(name) || json.isNull(name)) {
            return null
        }

        return json.getString(name)
            .takeIf { it.isNotBlank() }
    }

    fun state(): SecureState {
        cached?.let { return it }

        synchronized(mutex) {
            cached?.let { return it }

            val encrypted = prefs.getString(
                DATA_KEY,
                null
            )

            if (encrypted.isNullOrBlank()) {
                return SecureState().also {
                    cached = it
                }
            }

            val json = JSONObject(decrypt(encrypted))

            val rules = mutableMapOf<String, AppRule>()

            val rulesJson =
                json.optJSONObject("rules") ?: JSONObject()

            val ruleKeys = rulesJson.keys()

            while (ruleKeys.hasNext()) {
                val packageName = ruleKeys.next()

                val item = rulesJson.optJSONObject(
                    packageName
                ) ?: continue

                rules[packageName] = AppRule(
                    normalEnabled = item.optBoolean("normal"),
                    vipHash = readNullable(item, "vip"),
                    strictUntil = item.optLong("strictUntil"),
                    planEnabled = item.optBoolean("plan"),
                    startMinute = item.optInt("start", 1380),
                    endMinute = item.optInt("end", 420),
                    daysMask = item.optInt("days", 127)
                )
            }

            val failures = mutableMapOf<String, FailureRecord>()

            val failuresJson =
                json.optJSONObject("failures") ?: JSONObject()

            val failureKeys = failuresJson.keys()

            while (failureKeys.hasNext()) {
                val scope = failureKeys.next()

                val item = failuresJson.optJSONObject(
                    scope
                ) ?: continue

                failures[scope] = FailureRecord(
                    count = item.optInt("count"),
                    strikes = item.optInt("strikes"),
                    blockedUntil = item.optLong("blockedUntil")
                )
            }

            val loaded = SecureState(
                normalHash = readNullable(json, "normal"),
                normalType = json.optString("normalType", "PIN"),
                strictHash = readNullable(json, "strictPin"),
                masterHash = readNullable(json, "master"),
                rules = rules,
                failures = failures
            )

            cached = loaded

            return loaded
        }
    }

    private fun saveState(state: SecureState) {
        synchronized(mutex) {
            val json = JSONObject()

            json.put("normal", state.normalHash)
            json.put("normalType", state.normalType)
            json.put("strictPin", state.strictHash)
            json.put("master", state.masterHash)

            val rulesJson = JSONObject()

            state.rules.forEach { (packageName, rule) ->
                val item = JSONObject()

                item.put("normal", rule.normalEnabled)
                item.put("vip", rule.vipHash)
                item.put("strictUntil", rule.strictUntil)
                item.put("plan", rule.planEnabled)
                item.put("start", rule.startMinute)
                item.put("end", rule.endMinute)
                item.put("days", rule.daysMask)

                rulesJson.put(packageName, item)
            }

            json.put("rules", rulesJson)

            val failuresJson = JSONObject()

            state.failures.forEach { (scope, record) ->
                val item = JSONObject()

                item.put("count", record.count)
                item.put("strikes", record.strikes)
                item.put("blockedUntil", record.blockedUntil)

                failuresJson.put(scope, item)
            }

            json.put("failures", failuresJson)

            val committed = prefs.edit()
                .putString(DATA_KEY, encrypt(json.toString()))
                .commit()

            check(committed) {
                "Secure Lock verileri kaydedilemedi."
            }

            cached = state
        }
    }

    fun initialize(
        normal: String,
        normalType: String,
        strict: String,
        master: String
    ) {
        require(normal.length in 4..64)
        require(normalType == "PIN" || normalType == "PATTERN")
        require(strict.length in 4..12 && strict.all(Char::isDigit))
        require(master.length in 4..12 && master.all(Char::isDigit))

        val current = state()

        saveState(
            current.copy(
                normalHash = hashCredential(normal),
                normalType = normalType,
                strictHash = hashCredential(strict),
                masterHash = hashCredential(master)
            )
        )
    }

    fun getRule(packageName: String): AppRule {
        return state().rules[packageName] ?: AppRule()
    }

    fun allRules(): Map<String, AppRule> {
        return state().rules.toMap()
    }

    fun saveRule(
        packageName: String,
        rule: AppRule
    ) {
        val current = state()
        val updated = current.rules.toMutableMap()

        if (rule.hasAnyRule) {
            updated[packageName] = rule
        } else {
            updated.remove(packageName)
        }

        saveState(
            current.copy(rules = updated)
        )
    }

    fun verifyNormal(value: String): Boolean {
        return verifyCredential(
            value,
            state().normalHash
        )
    }

    fun verifyStrict(value: String): Boolean {
        return verifyCredential(
            value,
            state().strictHash
        )
    }

    fun verifyMaster(value: String): Boolean {
        return verifyCredential(
            value,
            state().masterHash
        )
    }

    fun verifyVip(
        packageName: String,
        value: String
    ): Boolean {
        return verifyCredential(
            value,
            getRule(packageName).vipHash
        )
    }

    fun normalType(): String {
        return state().normalType
    }

    fun normalLength(): Int {
        return state().normalHash?.let {
            0
        } ?: 0
    }

    fun failureRemaining(scope: String): Long {
        val record = state().failures[scope] ?: return 0L

        return (record.blockedUntil - System.currentTimeMillis())
            .coerceAtLeast(0L)
    }

    fun recordFailure(scope: String): Long {
        val current = state()

        val old = current.failures[scope]
            ?: FailureRecord()

        if (old.blockedUntil > System.currentTimeMillis()) {
            return old.blockedUntil - System.currentTimeMillis()
        }

        val count = old.count + 1

        if (count < 3) {
            val changed = current.failures.toMutableMap()

            changed[scope] = old.copy(count = count)

            saveState(current.copy(failures = changed))

            return 0L
        }

        val strikes = old.strikes + 1

        val delays = listOf(
            10_000L,
            30_000L,
            60_000L,
            300_000L,
            900_000L,
            1_800_000L,
            3_600_000L
        )

        val delay = delays[
            (strikes - 1).coerceIn(0, delays.lastIndex)
        ]

        val changed = current.failures.toMutableMap()

        changed[scope] = FailureRecord(
            count = 0,
            strikes = strikes,
            blockedUntil = System.currentTimeMillis() + delay
        )

        saveState(current.copy(failures = changed))

        return delay
    }

    fun recordSuccess(scope: String) {
        val current = state()
        val old = current.failures[scope] ?: return

        val changed = current.failures.toMutableMap()

        changed[scope] = old.copy(
            count = 0,
            strikes = 0,
            blockedUntil = 0L
        )

        saveState(current.copy(failures = changed))
    }
}
