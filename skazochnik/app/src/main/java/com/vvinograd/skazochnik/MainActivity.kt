package com.vvinograd.skazochnik

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {

    private val generator = StoryGenerator()
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var girl = false
    private var selectedHero = HEROES.first()

    private lateinit var nameInput: EditText
    private lateinit var boyButton: Button
    private lateinit var girlButton: Button
    private val heroButtons = mutableListOf<Pair<Hero, Button>>()
    private lateinit var storyCard: LinearLayout
    private lateinit var storyText: TextView
    private lateinit var speakButton: Button
    private lateinit var scroll: ScrollView

    private val purple = Color.parseColor("#5B3FA8")
    private val lilac = Color.parseColor("#EDE6FF")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = purple
        setContentView(buildUi())
        initTts()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    // ---------- Интерфейс ----------

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }

        root.addView(TextView(this).apply {
            text = "✨ Сказочник"
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(purple)
        })
        root.addView(label("Сказка, где твой ребёнок — главный герой"))

        root.addView(section("Как зовут ребёнка?"))
        nameInput = EditText(this).apply {
            hint = "Например, Миша"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            textSize = 18f
            setSingleLine()
        }
        root.addView(nameInput)

        val genderRow = row()
        boyButton = chip("👦 Мальчик") { girl = false; refreshSelection() }
        girlButton = chip("👧 Девочка") { girl = true; refreshSelection() }
        genderRow.addView(boyButton, weighted())
        genderRow.addView(girlButton, weighted())
        root.addView(genderRow)

        root.addView(section("Кто будет другом?"))
        HEROES.chunked(3).forEach { chunk ->
            val r = row()
            chunk.forEach { hero ->
                val b = chip("${hero.emoji}\n${hero.title}") { selectedHero = hero; refreshSelection() }
                heroButtons += hero to b
                r.addView(b, weighted())
            }
            root.addView(r)
        }

        root.addView(bigButton("📖 Сочинить сказку") { makeStory() }, topMargin(dp(20)))

        storyCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(Color.WHITE, dp(16), stroke = lilac)
            visibility = View.GONE
        }
        storyText = TextView(this).apply {
            textSize = 18f
            setLineSpacing(0f, 1.25f)
            setTextColor(Color.parseColor("#2B2340"))
            setTextIsSelectable(true)
        }
        storyCard.addView(storyText)

        val actions = row()
        speakButton = chip("🔊 Читать вслух") { toggleSpeak() }
        actions.addView(speakButton, weighted())
        actions.addView(chip("🎲 Другая сказка") { makeStory() }, weighted())
        storyCard.addView(actions, topMargin(dp(12)))

        root.addView(storyCard, topMargin(dp(20)))

        refreshSelection()
        scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#FAF7FF"))
            addView(root)
        }
        applySystemBarInsets(scroll)
        return scroll
    }

    /** На Android 15 приложение рисуется под системными панелями — отодвигаем содержимое. */
    private fun applySystemBarInsets(v: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        // светлый фон — значит, тёмные значки в строке состояния
        window.insetsController?.setSystemBarsAppearance(
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
        )
        window.statusBarColor = Color.parseColor("#FAF7FF")
        window.navigationBarColor = Color.parseColor("#FAF7FF")
        v.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun refreshSelection() {
        styleChip(boyButton, !girl)
        styleChip(girlButton, girl)
        heroButtons.forEach { (hero, b) -> styleChip(b, hero == selectedHero) }
    }

    private fun makeStory() {
        hideKeyboard()
        stopSpeaking()
        storyText.text = generator.generate(nameInput.text.toString(), girl, selectedHero)
        storyCard.visibility = View.VISIBLE
        scroll.post { scroll.smoothScrollTo(0, storyCard.top) }
    }

    // ---------- Озвучка ----------

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val result = tts?.setLanguage(Locale("ru", "RU"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            tts?.setSpeechRate(0.9f)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    runOnUiThread { speakButton.text = "🔊 Читать вслух" }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    runOnUiThread { speakButton.text = "🔊 Читать вслух" }
                }
            })
        }
    }

    private fun toggleSpeak() {
        if (tts?.isSpeaking == true) {
            stopSpeaking()
            return
        }
        if (!ttsReady) {
            Toast.makeText(
                this,
                "Нет русского голоса. Установите его: Настройки → Синтез речи",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        tts?.speak(storyText.text, TextToSpeech.QUEUE_FLUSH, null, "story")
        speakButton.text = "⏹ Стоп"
    }

    private fun stopSpeaking() {
        tts?.stop()
        if (::speakButton.isInitialized) speakButton.text = "🔊 Читать вслух"
    }

    // ---------- Мелочи для вёрстки ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
        if (stroke != null) setStroke(dp(2), stroke)
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.parseColor("#6E6787"))
    }

    private fun section(text: String) = TextView(this).apply {
        this.text = text
        textSize = 17f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#2B2340"))
        setPadding(0, dp(20), 0, dp(6))
    }

    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

    private fun weighted() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
        setMargins(dp(4), dp(4), dp(4), dp(4))
    }

    private fun topMargin(px: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = px }

    private fun chip(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 15f
        gravity = Gravity.CENTER
        stateListAnimator = null
        setPadding(dp(8), dp(10), dp(8), dp(10))
        setOnClickListener { onClick() }
        styleChip(this, false)
    }

    private fun styleChip(b: Button, selected: Boolean) {
        b.background = rounded(if (selected) purple else lilac, dp(14))
        b.setTextColor(if (selected) Color.WHITE else purple)
    }

    private fun bigButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 19f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.WHITE)
        stateListAnimator = null
        background = rounded(purple, dp(18))
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setOnClickListener { onClick() }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(nameInput.windowToken, 0)
        nameInput.clearFocus()
    }
}
