package com.securelock.app.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.securelock.app.model.CredentialFactor
import com.securelock.app.model.LockRequirement
import com.securelock.app.security.DeviceProtection
import com.securelock.app.security.LockPolicy
import com.securelock.app.security.LockSession
import com.securelock.app.security.SecureStore
import com.securelock.app.ui.PatternGridView
import java.util.concurrent.Executors

class AppLockAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var store: SecureStore

    private val handler = Handler(Looper.getMainLooper())

    private val executor = Executors.newSingleThreadExecutor()

    private var foregroundPackage: String? = null

    private var gatePackage: String? = null

    private var gateView: LinearLayout? = null

    private var gateStatus: TextView? = null

    private var requirement: LockRequirement? = null

    private var factors: List<CredentialFactor> = emptyList()

    private var factorIndex = 0

    private var selectedBreakMs = 0L

    private var busy = false

    private var ticker: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()

        windowManager = getSystemService(
            Context.WINDOW_SERVICE
        ) as WindowManager

        store = SecureStore.get(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType !=
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType !=
            AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }

        val packageName = event.packageName?.toString() ?: return

        if (packageName == this.packageName) return

        inspectSecureLockSettings(packageName)

        val previous = foregroundPackage

        if (previous != null && previous != packageName) {
            LockSession.onLeft(previous)

            if (gatePackage == previous) {
                removeGate()
            }
        }

        foregroundPackage = packageName

        evaluate(packageName)
    }

    private fun evaluate(packageName: String) {
        val current = LockPolicy.requirement(
            this,
            packageName
        )

        if (current == null) {
            if (gatePackage == packageName) {
                removeGate()
            }
            return
        }

        val split = isInMultiWindow(packageName)

        if (split) {
            LockSession.clear(packageName)
            tryExitSplitScreen()
        }

        if (!split && LockSession.isAllowed(packageName)) {
            if (gatePackage == packageName) {
                removeGate()
            }
            return
        }

        if (gatePackage == packageName && gateView != null) {
            requirement = current
            return
        }

        showGate(packageName, current)
    }

    private fun isInMultiWindow(packageName: String): Boolean {
        return try {
            val bounds = Rect()

            val window = windows.firstOrNull { item ->
                if (item.type != AccessibilityWindowInfo.TYPE_APPLICATION) {
                    return@firstOrNull false
                }

                val root = item.root ?: return@firstOrNull false

                val match =
                    root.packageName?.toString() == packageName

                root.recycle()

                match
            } ?: return false

            window.getBoundsInScreen(bounds)

            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels

            bounds.width() < screenWidth * 0.85f ||
                bounds.height() < screenHeight * 0.85f
        } catch (_: Exception) {
            false
        }
    }

    private fun tryExitSplitScreen() {
        try {
            val actionAvailable = systemActions.any {
                it.id == GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
            }

            if (actionAvailable) {
                performGlobalAction(
                    GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
                )
            }
        } catch (_: Exception) {
            // Perde ekranda kalır; sistem aksiyonu yoksa
            // Android çoklu pencereyi zorla kapatamaz.
        }
    }

    private fun showGate(
        packageName: String,
        newRequirement: LockRequirement
    ) {
        removeGate()

        gatePackage = packageName
        requirement = newRequirement
        factors = newRequirement.factors
        factorIndex = 0
        selectedBreakMs = 0L
        busy = false

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36, 28, 36, 28)
            setBackgroundColor(Color.rgb(5, 5, 5))
        }

        gateView = root

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.OPAQUE
        )

        params.gravity = Gravity.TOP or Gravity.START

        try {
            windowManager.addView(root, params)
        } catch (_: Exception) {
            gateView = null
            gatePackage = null
            return
        }

        renderGate()
    }

    private fun addText(
        parent: LinearLayout,
        value: String,
        size: Float,
        bold: Boolean = false
    ): TextView {
        val view = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(8, 10, 8, 10)

            if (bold) {
                typeface = Typeface.DEFAULT_BOLD
            }
        }

        parent.addView(
            view,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        return view
    }

    private fun addButton(
        parent: LinearLayout,
        label: String,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(35, 35, 35))
            setOnClickListener { action() }
        }

        parent.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun renderGate() {
        val root = gateView ?: return
        val pkg = gatePackage ?: return
        val current = requirement ?: return

        ticker?.let(handler::removeCallbacks)
        ticker = null

        root.removeAllViews()

        addText(root, "SECURE LOCK", 28f, true)

        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (_: Exception) {
            pkg
        }

        addText(root, appName, 20f, true)

        if (current.strictBlocked) {
            gateStatus = addText(
                root,
                "",
                30f,
                true
            )

            addText(
                root,
                "KATI MOD\nSüre tamamlanmadan erişim verilemez.",
                16f
            )

            val timer = object : Runnable {
                override fun run() {
                    val remaining =
                        current.strictUntil - System.currentTimeMillis()

                    if (remaining <= 0L) {
                        removeGate()
                        evaluate(pkg)
                        return
                    }

                    val seconds = remaining / 1000L

                    val hours = seconds / 3600L
                    val minutes = seconds % 3600L / 60L
                    val rest = seconds % 60L

                    gateStatus?.text = String.format(
                        "%02d:%02d:%02d",
                        hours,
                        minutes,
                        rest
                    )

                    handler.postDelayed(this, 1000L)
                }
            }

            ticker = timer
            handler.post(timer)

            return
        }

        val factor = factors.getOrNull(factorIndex)

        if (factor == null) {
            finishAuthentication()
            return
        }

        val status = when (factor) {
            CredentialFactor.NORMAL -> "NORMAL MOD ŞİFRESİ"
            CredentialFactor.VIP -> "VIP ÖZEL ŞİFRE"
        }

        gateStatus = addText(
            root,
            status,
            17f,
            true
        )

        val cooldown = store.failureRemaining(pkg)

        if (cooldown > 0L) {
            showCooldown(root, pkg, cooldown)
            return
        }

        if (current.breakAllowed &&
            factor == CredentialFactor.NORMAL
        ) {
            addButton(root, "5 Dakika Mola") {
                selectedBreakMs = 5L * 60L * 1000L
                gateStatus?.text = "5 dakika mola seçildi."
            }

            addButton(root, "10 Dakika Mola") {
                selectedBreakMs = 10L * 60L * 1000L
                gateStatus?.text = "10 dakika mola seçildi."
            }
        }

        if (factor == CredentialFactor.NORMAL &&
            store.normalType() == "PATTERN"
        ) {
            val pattern = PatternGridView(this)

            pattern.layoutParams = LinearLayout.LayoutParams(
                260,
                260
            )

            pattern.onComplete = { value ->
                verify(value)
            }

            root.addView(pattern)
        } else {
            val input = EditText(this).apply {
                hint = "Şifreni gir"
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                textSize = 22f

                inputType =
                    InputType.TYPE_CLASS_NUMBER or
                        InputType.TYPE_NUMBER_VARIATION_PASSWORD

                filters = arrayOf(
                    android.text.InputFilter.LengthFilter(12)
                )
            }

            root.addView(input)

            input.requestFocus()

            input.post {
                val keyboard = getSystemService(
                    INPUT_METHOD_SERVICE
                ) as InputMethodManager

                keyboard.showSoftInput(
                    input,
                    InputMethodManager.SHOW_IMPLICIT
                )
            }

            addButton(root, "Giriş") {
                verify(input.text.toString())
            }
        }
    }

    private fun showCooldown(
        root: LinearLayout,
        packageName: String,
        initial: Long
    ) {
        val timerText = addText(
            root,
            "",
            27f,
            true
        )

        addText(
            root,
            "Yanlış deneme nedeniyle geçici bekleme uygulanıyor.",
            15f
        )

        val timer = object : Runnable {
            override fun run() {
                val remaining =
                    store.failureRemaining(packageName)

                if (remaining <= 0L) {
                    renderGate()
                    return
                }

                timerText.text =
                    "KALAN SÜRE\n${remaining / 1000L + 1} saniye"

                handler.postDelayed(this, 1000L)
            }
        }

        ticker = timer
        handler.post(timer)
    }

    private fun verify(value: String) {
        val pkg = gatePackage ?: return

        val current = requirement ?: return

        if (busy || value.isBlank()) return

        if (store.failureRemaining(pkg) > 0L) {
            renderGate()
            return
        }

        val factor = factors.getOrNull(factorIndex) ?: return

        busy = true

        executor.execute {
            val correct = when (factor) {
                CredentialFactor.NORMAL ->
                    store.verifyNormal(value)

                CredentialFactor.VIP ->
                    store.verifyVip(pkg, value)
            }

            handler.post {
                busy = false

                if (gatePackage != pkg || gateView == null) {
                    return@post
                }

                if (correct) {
                    store.recordSuccess(pkg)

                    factorIndex++

                    if (factorIndex >= factors.size) {
                        finishAuthentication()
                    } else {
                        renderGate()
                    }
                } else {
                    val delay = store.recordFailure(pkg)

                    if (delay > 0L) {
                        DeviceProtection.lockNow(this)
                    }

                    Toast.makeText(
                        this,
                        if (delay > 0L) {
                            "Yanlış deneme. Bekleme cezası başladı."
                        } else {
                            "Şifre yanlış."
                        },
                        Toast.LENGTH_SHORT
                    ).show()

                    renderGate()
                }
            }
        }
    }

    private fun finishAuthentication() {
        val pkg = gatePackage ?: return

        LockSession.grant(
            pkg,
            selectedBreakMs
        )

        removeGate()
    }

    private fun removeGate() {
        ticker?.let(handler::removeCallbacks)
        ticker = null

        gateView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }

        gateView = null
        gatePackage = null
        requirement = null
    }

    private fun inspectSecureLockSettings(packageName: String) {
        val settingsPackages = setOf(
            "com.android.settings",
            "com.samsung.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller"
        )

        if (packageName !in settingsPackages) return

        val root = rootInActiveWindow ?: return

        val values = mutableListOf<String>()

        fun collect(
            node: android.view.accessibility.AccessibilityNodeInfo,
            depth: Int
        ) {
            if (depth > 20 || values.size > 400) return

            node.text?.toString()?.let(values::add)
            node.contentDescription?.toString()?.let(values::add)

            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                collect(child, depth + 1)
            }
        }

        collect(root, 0)

        val allText = values.joinToString(" ")
            .lowercase()

        val ownName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(
                    this.packageName,
                    0
                )
            ).toString().lowercase()
        } catch (_: Exception) {
            "secure lock"
        }

        val ownPage =
            allText.contains(ownName) ||
                allText.contains(this.packageName.lowercase())

        val uninstallText =
            allText.contains("kaldır") ||
                allText.contains("uninstall")

        if (ownPage && uninstallText) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    override fun onInterrupt() {
        removeGate()
    }

    override fun onDestroy() {
        removeGate()
        executor.shutdownNow()
        super.onDestroy()
    }
}
