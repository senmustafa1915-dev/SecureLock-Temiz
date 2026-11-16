package com.securelock.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import com.securelock.app.model.AppRule
import com.securelock.app.model.SecureState
import com.securelock.app.security.DeviceProtection
import com.securelock.app.security.LockSession
import com.securelock.app.security.SecureStore
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var store: SecureStore

    private val dayNames = listOf(
        "Pazar",
        "Pazartesi",
        "Salı",
        "Çarşamba",
        "Perşembe",
        "Cuma",
        "Cumartesi"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        store = SecureStore.get(this)

        if (store.state().initialized) {
            showApps()
        } else {
            showSetup()
        }
    }

    private fun page(title: String): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 15, 18, 15)
            setBackgroundColor(Color.BLACK)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val heading = TextView(this).apply {
            text = title
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }

        header.addView(
            heading,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val menu = Button(this).apply {
            text = "☰"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(30, 30, 30))

            setOnClickListener {
                showMenu(this)
            }
        }

        header.addView(menu)
        root.addView(header)

        setContentView(root)

        return root
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)

        popup.menu.add("Uygulamalar").setOnMenuItemClickListener {
            showApps()
            true
        }

        popup.menu.add("Aktif Kilitler").setOnMenuItemClickListener {
            showActiveLocks()
            true
        }

        popup.menu.add("Ayarlar").setOnMenuItemClickListener {
            requireMaster {
                showSettings()
            }
            true
        }

        popup.show()
    }

    private fun text(
        value: String,
        size: Float = 15f,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
            setPadding(8, 8, 8, 8)

            if (bold) {
                typeface = Typeface.DEFAULT_BOLD
            }
        }
    }

    private fun edit(
        hint: String,
        numeric: Boolean = false,
        password: Boolean = false
    ): EditText {
        return EditText(this).apply {
            this.hint = hint
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)

            if (numeric) {
                inputType =
                    android.text.InputType.TYPE_CLASS_NUMBER
            }

            if (password) {
                inputType =
                    android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD

                filters = arrayOf(
                    android.text.InputFilter.LengthFilter(12)
                )
            }
        }
    }

    private fun button(
        value: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = value
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(36, 36, 36))
            setOnClickListener { action() }
        }
    }

    private fun addScroll(
        parent: LinearLayout
    ): LinearLayout {
        val scroll = ScrollView(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(content)

        parent.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        return content
    }

    private fun showSetup() {
        val root = page("SECURE LOCK")
        val content = addScroll(root)

        content.addView(text("İlk kurulum", 18f, true))

        content.addView(text("Normal kilit türü"))

        val type = Spinner(this)

        type.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("PIN ŞİFRESİ", "DESEN")
        )

        content.addView(type)

        val normalPin = edit(
            "Normal uygulama şifresi",
            numeric = true,
            password = true
        )

        val firstPattern = PatternGridView(this)
        val secondPattern = PatternGridView(this)

        firstPattern.layoutParams = LinearLayout.LayoutParams(
            250,
            250
        )

        secondPattern.layoutParams = LinearLayout.LayoutParams(
            250,
            250
        )

        var patternOne: String? = null
        var patternTwo: String? = null

        firstPattern.onComplete = {
            patternOne = it

            Toast.makeText(
                this,
                "İlk desen alındı.",
                Toast.LENGTH_SHORT
            ).show()
        }

        secondPattern.onComplete = {
            patternTwo = it

            Toast.makeText(
                this,
                "İkinci desen alındı.",
                Toast.LENGTH_SHORT
            ).show()
        }

        content.addView(normalPin)
        content.addView(text("İlk desen"))
        content.addView(firstPattern)
        content.addView(text("Deseni doğrula"))
        content.addView(secondPattern)

        fun updateMode() {
            val pattern = type.selectedItemPosition == 1

            normalPin.visibility =
                if (pattern) View.GONE else View.VISIBLE

            firstPattern.visibility =
                if (pattern) View.VISIBLE else View.GONE

            secondPattern.visibility =
                if (pattern) View.VISIBLE else View.GONE
        }

        type.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    updateMode()
                }

                override fun onNothingSelected(
                    parent: android.widget.AdapterView<*>?
                ) = Unit
            }

        updateMode()

        val strict = edit(
            "Katı Mod şifresi",
            numeric = true,
            password = true
        )

        val master = edit(
            "Yönetici şifresi",
            numeric = true,
            password = true
        )

        content.addView(strict)
        content.addView(master)

        root.addView(
            button("Kurulumu tamamla") {
                val isPattern = type.selectedItemPosition == 1

                val normal = if (isPattern) {
                    if (patternOne.isNullOrBlank() ||
                        patternOne != patternTwo
                    ) {
                        Toast.makeText(
                            this,
                            "Desenler eşleşmiyor.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@button
                    }

                    patternOne!!
                } else {
                    normalPin.text.toString()
                }

                val strictValue = strict.text.toString()
                val masterValue = master.text.toString()

                if (normal.length !in 4..64 ||
                    strictValue.length !in 4..12 ||
                    masterValue.length !in 4..12 ||
                    !strictValue.all(Char::isDigit) ||
                    !masterValue.all(Char::isDigit) ||
                    (!isPattern && !normal.all(Char::isDigit))
                ) {
                    Toast.makeText(
                        this,
                        "Şifreleri kontrol et.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@button
                }

                try {
                    store.initialize(
                        normal,
                        if (isPattern) "PATTERN" else "PIN",
                        strictValue,
                        masterValue
                    )

                    showApps()
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        e.message ?: "Kurulum başarısız.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    private fun showApps() {
        val root = page("UYGULAMALAR")

        val search = edit("Uygulama ara")

        root.addView(search)

        val list = addScroll(root)

        val installed = try {
            packageManager.getInstalledApplications(
                PackageManager.MATCH_ALL
            )
                .filter {
                    it.packageName != packageName
                }
                .sortedBy {
                    packageManager.getApplicationLabel(it)
                        .toString()
                        .lowercase(Locale.getDefault())
                }
        } catch (_: Exception) {
            emptyList()
        }

        fun render(query: String) {
            list.removeAllViews()

            installed.forEach { info ->
                val label = try {
                    packageManager.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                    info.packageName
                }

                if (!label.contains(query, true) &&
                    !info.packageName.contains(query, true)
                ) {
                    return@forEach
                }

                val rule = store.getRule(info.packageName)

                val modes = mutableListOf<String>()

                if (rule.normalEnabled) modes.add("NORMAL")
                if (rule.vipEnabled) modes.add("VIP")

                if (rule.strictUntil > System.currentTimeMillis()) {
                    modes.add("KATI")
                }

                if (rule.planEnabled) modes.add("PLANLI")

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(8, 12, 8, 12)
                }

                row.addView(text(label, 16f, true))

                row.addView(text(info.packageName, 10f))

                if (modes.isNotEmpty()) {
                    row.addView(
                        text(modes.joinToString(" + "), 11f, true)
                    )
                }

                row.setOnClickListener {
                    val launch = packageManager
                        .getLaunchIntentForPackage(info.packageName)

                    if (launch != null) {
                        try {
                            startActivity(launch)
                        } catch (_: Exception) {
                            Toast.makeText(
                                this,
                                "Uygulama açılamadı.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                row.setOnLongClickListener {
                    requireMaster {
                        editRule(info.packageName, label)
                    }
                    true
                }

                list.addView(row)

                val divider = View(this).apply {
                    setBackgroundColor(Color.DKGRAY)
                }

                list.addView(
                    divider,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                    )
                )
            }
        }

        search.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    render(s?.toString().orEmpty())
                }

                override fun afterTextChanged(s: Editable?) = Unit
            }
        )

        render("")
    }

    private fun requireMaster(action: () -> Unit) {
        val input = edit(
            "Yönetici şifresi",
            numeric = true,
            password = true
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle("Yönetici doğrulaması")
            .setView(input)
            .setNegativeButton("İptal", null)
            .setPositiveButton("Doğrula", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val value = input.text.toString()

                    if (store.verifyMaster(value)) {
                        store.recordSuccess("__MASTER__")
                        dialog.dismiss()
                        action()
                    } else {
                        val delay = store.recordFailure("__MASTER__")

                        Toast.makeText(
                            this,
                            if (delay > 0L) {
                                "Yönetici bekleme cezası başladı."
                            } else {
                                "Şifre yanlış."
                            },
                            Toast.LENGTH_LONG
                        ).show()

                        if (delay > 0L) {
                            DeviceProtection.lockNow(this)
                        }
                    }
                }
        }

        dialog.show()
    }

    private fun editRule(
        packageName: String,
        label: String
    ) {
        val old = store.getRule(packageName)

        if (old.strictUntil > System.currentTimeMillis()) {
            Toast.makeText(
                this,
                "Katı Mod süresi dolmadan kural değiştirilemez.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        showRuleEditor(packageName, label, old)
    }

    private fun parseTime(
        value: String,
        fallback: Int
    ): Int {
        return try {
            val parts = value.split(":")

            require(parts.size == 2)

            val hour = parts[0].toInt()
            val minute = parts[1].toInt()

            require(hour in 0..23 && minute in 0..59)

            hour * 60 + minute
        } catch (_: Exception) {
            fallback
        }
    }

    private fun showRuleEditor(
        packageName: String,
        label: String,
        old: AppRule
    ) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 8)
        }

        val scroll = ScrollView(this)
        scroll.addView(content)

        fun checkBox(
            label: String,
            checked: Boolean
        ): CheckBox {
            return CheckBox(this).apply {
                text = label
                isChecked = checked
                setTextColor(Color.WHITE)
            }
        }

        val normal = checkBox(
            "Normal Kilit",
            old.normalEnabled
        )

        val vip = checkBox(
            "Sıkı Mod (VIP)",
            old.vipEnabled
        )

        val strict = checkBox(
            "Katı Mod",
            old.strictUntil > 0L
        )

        val plan = checkBox(
            "Planlı Kilit",
            old.planEnabled
        )

        val vipPin = edit(
            "Yeni VIP şifresi (boşsa mevcut şifre korunur)",
            numeric = true,
            password = true
        )

        val strictPin = edit(
            "Katı Mod şifresiyle doğrula",
            numeric = true,
            password = true
        )

        val hours = edit(
            "Katı Mod süresi (saat)",
            numeric = true
        ).apply {
            setText("24")
        }

        val start = edit("Başlangıç saati HH:mm").apply {
            setText(
                "%02d:%02d".format(
                    old.startMinute / 60,
                    old.startMinute % 60
                )
            )
        }

        val end = edit("Bitiş saati HH:mm").apply {
            setText(
                "%02d:%02d".format(
                    old.endMinute / 60,
                    old.endMinute % 60
                )
            )
        }

        content.addView(normal)
        content.addView(vip)
        content.addView(vipPin)
        content.addView(strict)
        content.addView(strictPin)
        content.addView(hours)
        content.addView(plan)
        content.addView(start)
        content.addView(end)

        content.addView(text("Planlı Kilit günleri", 16f, true))

        val dayChecks = Array(7) { index ->
            checkBox(
                dayNames[index],
                old.daysMask and (1 shl index) != 0
            )
        }

        dayChecks.forEach(content::addView)

        val dialog = AlertDialog.Builder(this)
            .setTitle(label)
            .setView(scroll)
            .setNegativeButton("İptal", null)
            .setPositiveButton("Kaydet", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    try {
                        var vipHash = old.vipHash

                        if (vip.isChecked) {
                            val value = vipPin.text.toString()

                            if (value.isNotBlank()) {
                                require(
                                    value.length in 4..12 &&
                                        value.all(Char::isDigit)
                                ) {
                                    "VIP şifresi 4-12 rakam olmalı."
                                }

                                vipHash = store.hashCredential(value)
                            }

                            require(!vipHash.isNullOrBlank()) {
                                "VIP şifresi belirlemelisin."
                            }
                        } else {
                            vipHash = null
                        }

                        val strictUntil = if (strict.isChecked) {
                            require(
                                store.verifyStrict(
                                    strictPin.text.toString()
                                )
                            ) {
                                "Katı Mod şifresi yanlış."
                            }

                            val duration = hours.text.toString()
                                .toInt()
                                .coerceIn(1, 720)

                            System.currentTimeMillis() +
                                duration * 60L * 60L * 1000L
                        } else {
                            0L
                        }

                        val startMinute = parseTime(
                            start.text.toString(),
                            1380
                        )

                        val endMinute = parseTime(
                            end.text.toString(),
                            420
                        )

                        var mask = 0

                        dayChecks.forEachIndexed { index, item ->
                            if (item.isChecked) {
                                mask = mask or (1 shl index)
                            }
                        }

                        require(
                            !plan.isChecked ||
                                (startMinute != endMinute && mask != 0)
                        ) {
                            "Planlı Kilit saatlerini ve günlerini kontrol et."
                        }

                        store.saveRule(
                            packageName,
                            AppRule(
                                normalEnabled = normal.isChecked,
                                vipHash = vipHash,
                                strictUntil = strictUntil,
                                planEnabled = plan.isChecked,
                                startMinute = startMinute,
                                endMinute = endMinute,
                                daysMask = mask
                            )
                        )

                        LockSession.clear(packageName)

                        dialog.dismiss()

                        showApps()

                    } catch (e: Exception) {
                        Toast.makeText(
                            this,
                            e.message ?: "Kaydetme başarısız.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }

        dialog.show()
    }

    private fun showActiveLocks() {
        val root = page("AKTİF KİLİTLER")
        val list = addScroll(root)

        store.allRules().forEach { (pkg, rule) ->
            val label = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(pkg, 0)
                ).toString()
            } catch (_: Exception) {
                pkg
            }

            val active = mutableListOf<String>()

            if (rule.normalEnabled) active.add("Normal")

            if (rule.vipEnabled) active.add("VIP")

            if (rule.strictUntil > System.currentTimeMillis()) {
                active.add("Katı")
            }

            if (rule.planEnabled) active.add("Planlı")

            if (active.isNotEmpty()) {
                list.addView(
                    text(
                        "$label\n${active.joinToString(" + ")}",
                        15f
                    )
                )
            }
        }
    }

    private fun showSettings() {
        val root = page("AYARLAR")
        val content = addScroll(root)

        val status = DeviceProtection.apply(this)

        content.addView(
            text(
                "Cihaz yöneticisi: ${
                    if (status.adminActive) "Aktif" else "Kapalı"
                }"
            )
        )

        content.addView(
            text(
                "Device Owner: ${
                    if (status.deviceOwner) "Aktif" else "Aktif değil"
                }"
            )
        )

        content.addView(
            text(
                "Secure Lock kaldırma engeli: ${
                    if (status.uninstallBlocked) "Aktif" else "Pasif"
                }"
            )
        )

        content.addView(
            button("Cihaz yöneticisini etkinleştir") {
                DeviceProtection.requestAdmin(this)
            }
        )

        content.addView(
            button("Erişilebilirlik ayarlarını aç") {
                startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                )
            }
        )

        content.addView(
            button("Device Owner ADB komutunu göster") {
                AlertDialog.Builder(this)
                    .setTitle("Bugjaeger komutu")
                    .setMessage(
                        "dpm set-device-owner " +
                            "com.securelock.app/.admin.SecureLockAdminReceiver\n\n" +
                            "Komutu Bugjaeger içindeki ADB Shell bölümünde çalıştır."
                    )
                    .setPositiveButton("Tamam", null)
                    .show()
            }
        )

        content.addView(
            button("Cihaz korumasını yenile") {
                val result = DeviceProtection.apply(this)

                Toast.makeText(
                    this,
                    if (result.deviceOwner &&
                        result.uninstallBlocked
                    ) {
                        "Secure Lock kaldırma engeli aktif."
                    } else {
                        "Device Owner yetkisi gerekli."
                    },
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    override fun onResume() {
        super.onResume()

        DeviceProtection.apply(this)
    }
}
