package com.vvinograd.kuda

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Date
import kotlin.math.max

class MainActivity : Activity(), SensorEventListener, LocationListener {

    private lateinit var store: SpotStore
    private var spots = mutableListOf<Spot>()
    private var selected: Spot? = null

    private lateinit var sensors: SensorManager
    private lateinit var locations: LocationManager
    private var location: Location? = null
    private var declination = 0f

    /** Куда смотрит верх телефона, градусы от истинного севера. */
    private var heading: Float? = null
    private var compassUnreliable = false
    private val rotation = FloatArray(9)
    private val orientation = FloatArray(3)
    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null

    private lateinit var targetTitle: TextView
    private lateinit var distanceText: TextView
    private lateinit var hintText: TextView
    private lateinit var compass: CompassView
    private lateinit var statusText: TextView
    private lateinit var targetActions: LinearLayout
    private lateinit var listBox: LinearLayout

    private val bg = Color.parseColor("#0B132B")
    private val card = Color.parseColor("#1C2541")
    private val accent = Color.parseColor("#FCA311")
    private val textMain = Color.parseColor("#F1F5FB")
    private val textDim = Color.parseColor("#8DA2C0")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        store = SpotStore(this)
        spots = store.load()
        selected = spots.find { it.id == store.selectedId } ?: spots.lastOrNull()

        sensors = getSystemService(SENSOR_SERVICE) as SensorManager
        locations = getSystemService(LOCATION_SERVICE) as LocationManager

        setContentView(buildUi())
        refreshList()
        update()
    }

    override fun onResume() {
        super.onResume()
        startSensors()
        if (hasLocationPermission()) startLocation() else askPermission()
        update()
    }

    override fun onPause() {
        super.onPause()
        sensors.unregisterListener(this)
        locations.removeUpdates(this)
    }

    // ---------- Геолокация ----------

    private fun hasLocationPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun askPermission() {
        requestPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            REQ_LOCATION,
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION && hasLocationPermission()) startLocation()
        update()
    }

    @Suppress("MissingPermission")
    private fun startLocation() {
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (!locations.allProviders.contains(provider)) continue
            try {
                locations.getLastKnownLocation(provider)?.let { onLocationChanged(it) }
                locations.requestLocationUpdates(provider, 1000L, 0f, this)
            } catch (e: SecurityException) {
                // разрешение отозвали прямо сейчас — покажем это в статусе
            } catch (e: IllegalArgumentException) {
                // провайдера нет на этом устройстве
            }
        }
    }

    override fun onLocationChanged(l: Location) {
        val old = location
        val better = old == null ||
            l.provider == LocationManager.GPS_PROVIDER ||
            l.time - old.time > 10_000L ||
            l.accuracy <= old.accuracy
        if (!better) return
        location = l
        declination = GeomagneticField(
            l.latitude.toFloat(), l.longitude.toFloat(), l.altitude.toFloat(), System.currentTimeMillis(),
        ).declination
        refreshList()
        update()
    }

    private fun locationEnabled() =
        locations.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locations.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    // ---------- Компас ----------

    private fun startSensors() {
        val rv = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rv != null) {
            sensors.registerListener(this, rv, SensorManager.SENSOR_DELAY_UI)
        } else {
            sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
            sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
                sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val ok = when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                true
            }
            Sensor.TYPE_ACCELEROMETER -> {
                gravity = event.values.clone()
                computeFromRaw()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                geomagnetic = event.values.clone()
                computeFromRaw()
            }
            else -> false
        }
        if (!ok) return
        SensorManager.getOrientation(rotation, orientation)
        val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat() + declination
        heading = Geo.smooth(heading, azimuth, 0.2f)
        update()
    }

    private fun computeFromRaw(): Boolean {
        val g = gravity ?: return false
        val m = geomagnetic ?: return false
        return SensorManager.getRotationMatrix(rotation, null, g, m)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD || sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            compassUnreliable = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
                accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
        }
    }

    // ---------- Обновление экрана ----------

    private fun update() {
        val target = selected
        val loc = location

        targetActions.visibility = if (target != null) View.VISIBLE else View.GONE

        if (target == null) {
            targetTitle.text = "Пока нет сохранённых мест"
            distanceText.text = ""
            hintText.text = "Когда будете у машины, отеля или палатки — нажмите «Запомнить место». " +
                "Потом стрелка приведёт вас обратно."
            compass.arrowAngle = null
            compass.arrived = false
        } else {
            targetTitle.text = "${Geo.emojiFor(target.name)} ${target.name}"
            if (loc == null) {
                distanceText.text = "…"
                hintText.text = "Ищу ваше местоположение"
                compass.arrowAngle = null
                compass.arrived = false
            } else {
                val dest = target.toLocation()
                val dist = loc.distanceTo(dest)
                val arrived = dist <= max(15f, loc.accuracy)
                compass.arrived = arrived
                distanceText.text = Geo.formatDistance(dist)

                // Если компаса нет, но человек идёт — берём направление движения из GPS
                val h = heading ?: if (loc.hasBearing() && loc.hasSpeed() && loc.speed > 1f) loc.bearing else null
                if (arrived) {
                    hintText.text = "Вы на месте! 🎉"
                    compass.arrowAngle = null
                } else if (h == null) {
                    hintText.text = "Нет компаса — начните идти, и стрелка определит направление"
                    compass.arrowAngle = null
                } else {
                    val arrow = Geo.arrowAngle(loc.bearingTo(dest), h)
                    compass.arrowAngle = arrow
                    hintText.text = Geo.turnHint(arrow)
                }
            }
        }

        val h = heading
        compass.northAngle = if (h != null) Geo.norm360(-h) else null
        statusText.text = statusLine()
    }

    private fun statusLine(): String {
        if (!hasLocationPermission()) return "⚠ Нет доступа к геолокации — нажмите сюда, чтобы разрешить"
        if (!locationEnabled()) return "⚠ Геолокация выключена — нажмите сюда, чтобы включить"
        val parts = mutableListOf<String>()
        val loc = location
        parts += if (loc == null) "📡 Ищу спутники…" else "📡 Точность ±${loc.accuracy.toInt()} м"
        if (heading != null && compassUnreliable) parts += "🧭 Покрутите телефон восьмёркой для калибровки"
        if (heading != null) parts += "Держите телефон горизонтально"
        return parts.joinToString("\n")
    }

    private fun onStatusClick() {
        when {
            !hasLocationPermission() -> askPermission()
            !locationEnabled() -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    // ---------- Места ----------

    private fun showSaveDialog() {
        val loc = location
        if (loc == null) {
            Toast.makeText(this, "Ещё ищу ваше местоположение, подождите пару секунд", Toast.LENGTH_SHORT).show()
            return
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val input = EditText(this).apply {
            hint = "Название, например «Машина»"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
        }
        box.addView(input)
        val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("🚗 Машина" to "Машина", "🏨 Отель" to "Отель", "⛺ Палатка" to "Палатка").forEach { (label, value) ->
            presets.addView(smallButton(label) { input.setText(value); input.setSelection(value.length) }, weighted())
        }
        box.addView(presets)
        if (loc.accuracy > 30f) {
            box.addView(TextView(this).apply {
                text = "Точность пока ±${loc.accuracy.toInt()} м. Для лучшего результата выйдите на открытое место."
                setTextColor(accent)
                setPadding(0, dp(8), 0, 0)
            })
        }

        AlertDialog.Builder(this)
            .setTitle("Запомнить это место")
            .setView(box)
            .setPositiveButton("Сохранить") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "Место ${spots.size + 1}" }
                val spot = Spot(System.currentTimeMillis(), name, loc.latitude, loc.longitude, System.currentTimeMillis())
                spots.add(spot)
                select(spot)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun select(spot: Spot?) {
        selected = spot
        store.save(spots)
        store.selectedId = spot?.id ?: -1L
        refreshList()
        update()
    }

    private fun confirmDelete(spot: Spot) {
        AlertDialog.Builder(this)
            .setTitle("Удалить «${spot.name}»?")
            .setPositiveButton("Удалить") { _, _ ->
                spots.removeAll { it.id == spot.id }
                select(if (selected?.id == spot.id) spots.lastOrNull() else selected)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openInMaps() {
        val s = selected ?: return
        val uri = Uri.parse("geo:0,0?q=${s.lat},${s.lon}(${Uri.encode(s.name)})")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Нет приложения с картами", Toast.LENGTH_SHORT).show()
        }
    }

    private fun share() {
        val s = selected ?: return
        val text = "${s.name}: https://maps.google.com/?q=${s.lat},${s.lon}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Отправить место"))
    }

    private fun refreshList() {
        if (!::listBox.isInitialized) return
        listBox.removeAllViews()
        if (spots.isEmpty()) return
        listBox.addView(TextView(this).apply {
            text = "Мои места"
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(textMain)
            setPadding(dp(4), dp(8), 0, dp(6))
        })
        val loc = location
        spots.asReversed().forEach { spot ->
            val isSelected = spot.id == selected?.id
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(10), dp(6), dp(10))
                background = rounded(if (isSelected) Color.parseColor("#2B3E63") else card, dp(14))
                setOnClickListener { select(spot) }
                setOnLongClickListener { confirmDelete(spot); true }
            }
            val time = DateFormat.format("d MMM, HH:mm", Date(spot.savedAt))
            val dist = loc?.let { " · " + Geo.formatDistance(it.distanceTo(spot.toLocation())) } ?: ""
            row.addView(TextView(this).apply {
                text = "${Geo.emojiFor(spot.name)}  ${spot.name}\n$time$dist"
                textSize = 15f
                setTextColor(if (isSelected) accent else textMain)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                text = "✕"
                textSize = 16f
                setTextColor(textDim)
                background = null
                setOnClickListener { confirmDelete(spot) }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            listBox.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) })
        }
    }

    private fun Spot.toLocation() = Location("spot").also {
        it.latitude = lat
        it.longitude = lon
    }

    // ---------- Вёрстка ----------

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(28))
        }

        root.addView(TextView(this).apply {
            text = "🧭 Куда идти"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(textMain)
        })

        targetTitle = TextView(this).apply {
            textSize = 20f
            setTextColor(accent)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        }
        root.addView(targetTitle)

        distanceText = TextView(this).apply {
            textSize = 46f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(textMain)
            gravity = Gravity.CENTER
        }
        root.addView(distanceText)

        compass = CompassView(this)
        root.addView(compass)

        hintText = TextView(this).apply {
            textSize = 18f
            setTextColor(textMain)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        root.addView(hintText)

        targetActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        targetActions.addView(smallButton("🗺 На карте") { openInMaps() }, weighted())
        targetActions.addView(smallButton("📤 Отправить") { share() }, weighted())
        root.addView(targetActions)

        root.addView(Button(this).apply {
            text = "📍 Запомнить место"
            isAllCaps = false
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#14213D"))
            stateListAnimator = null
            background = rounded(accent, dp(18))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener { showSaveDialog() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(12); bottomMargin = dp(12) })

        statusText = TextView(this).apply {
            textSize = 13f
            setTextColor(textDim)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
            setOnClickListener { onStatusClick() }
        }
        root.addView(statusText)

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listBox)

        root.addView(TextView(this).apply {
            text = "Долгое нажатие на место — удалить"
            textSize = 12f
            setTextColor(textDim)
            gravity = Gravity.CENTER
        })

        return ScrollView(this).apply {
            setBackgroundColor(bg)
            addView(root)
            applySystemBarInsets(this)
        }
    }

    /** На Android 15 приложение рисуется под системными панелями — отодвигаем содержимое. */
    private fun applySystemBarInsets(v: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        v.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun weighted() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
        setMargins(dp(4), dp(4), dp(4), dp(4))
    }

    private fun smallButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 15f
        setTextColor(textMain)
        stateListAnimator = null
        background = rounded(card, dp(14))
        setOnClickListener { onClick() }
    }

    private companion object {
        const val REQ_LOCATION = 1
    }
}
