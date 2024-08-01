package com.the0cp.objection

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import kotlin.properties.Delegates
import kotlinx.coroutines.*

class AccelerationDetector(private val sensorManager: SensorManager, private var threshold: Float, private val onAccelerationDetected: () -> Boolean) : SensorEventListener {

    private var accelerometer: Sensor? = null
    private var gravityValues = FloatArray(3)

    fun startListening() {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopListening() {

        accelerometer?.let {
            sensorManager.unregisterListener(this, it)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        /* val acceleration = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat() */

        val alpha = 0.15f


        gravityValues[0] = alpha * gravityValues[0] + (1 - alpha) * x
        gravityValues[1] = alpha *gravityValues[1] + (1 - alpha) * y
        gravityValues[2] = alpha * gravityValues[2] + (1 - alpha) * z

        val gravityAcceleration = kotlin.math.sqrt(
            (gravityValues[0] * gravityValues[0] +
                    gravityValues[1] * gravityValues[1] +
                    gravityValues[2] * gravityValues[2]).toDouble()
        ).toFloat()



        if (threshold < gravityAcceleration) {
            onAccelerationDetected()
        }
    }
    fun updateThreshold(newThreshold: Float){
        threshold = newThreshold
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
}

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity(){
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var sensorManager: SensorManager
    private lateinit var accelerationDetector: AccelerationDetector

    private lateinit var imageView: ImageView

    private lateinit var wakeLock: PowerManager.WakeLock

    private var threshold: Float = 0F

    private lateinit var voicePath: String

    private var voiceSwitch by Delegates.notNull<Boolean>()

    private var vibSwitch by Delegates.notNull<Boolean>()


    private fun playAudio(context: Context, filePath: String){
        /*
        * setup soundPool
        * */
        var soundPool: SoundPool? = null
        var audioAttributes: AudioAttributes? = null
        var soundId = 0
        audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()

        /*
        * get audio focus
        * */
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                }AudioManager.AUDIOFOCUS_LOSS -> {
            }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                }
            }
        }

        val result = audioManager.requestAudioFocus(
            audioFocusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )

        /*
        * if focus granted, play the audio
        * */
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        {
            try {
                val assetManager = context.assets
                val afd = assetManager.openFd(filePath)
                soundId = soundPool.load(afd, 1)

                soundPool.setOnLoadCompleteListener { _, _, status ->
                    if (status == 0) {
                        soundPool.play(soundId, 1f, 1f, 0, 0, 1f)
                    } else {
                        Log.e("SoundPool", "Failed to load audio:$filePath")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }


    }

    private fun setImg(context: Context, imagePath: String, imageView: ImageView){
        try{
            val assetManager = context.assets
            val inputStream = assetManager.open(imagePath)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            imageView.setImageBitmap(bitmap)
        } catch (e: Exception){
            e.printStackTrace()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)


        /*
        * hide nav bar
        * */
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)


        /*
        * Wake Lock
        **/
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "Objection:KeepScreenOn")
        wakeLock.acquire()



        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)


        imageView = findViewById(R.id.imageView)
        imageView.visibility = View.INVISIBLE

        /*
            *  get preferences
            * */

        val selectedCharacter = preferences.getString("character", "wright")
        val selectedVoice = preferences.getString("voice", "igiari")
        voicePath = "lines/$selectedCharacter/$selectedVoice.mp3"   /* concat strings */

        voiceSwitch = preferences.getBoolean("is_voice", true)  /* get switch state */

        vibSwitch = preferences.getBoolean("is_vib", false) /* get vibrator state */

        /*
        *  set splash
        * */

        val imagePath = "img/$selectedVoice.png"
        setImg(this, imagePath, imageView)

        threshold = (9.81).toFloat() + (preferences.getInt("threshold", 0).toFloat())/20

        accelerationDetector = AccelerationDetector(sensorManager, threshold) {
            accelerationDetector.stopListening()

            if(vibSwitch){
                CoroutineScope(Dispatchers.Default).launch {
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    if (vibrator.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            vibrator.vibrate(200)
                        }
                    }
                }
            }

            if(voiceSwitch){
                playAudio(this, voicePath)
            }
            imageView.visibility = View.VISIBLE
            handler.postDelayed({
                imageView.visibility = View.INVISIBLE
                accelerationDetector.startListening()
            }, 2000)
        }



        val rootLayout: ConstraintLayout = findViewById(R.id.main)
        val settingButton: ImageButton = findViewById(R.id.settingButton)

        settingButton.visibility = View.INVISIBLE

        rootLayout.setOnClickListener {
            if(settingButton.visibility == View.VISIBLE){
                settingButton.visibility = View.INVISIBLE
            }
            else{
                settingButton.visibility = View.VISIBLE
            }
        }

        settingButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    override fun onResume() {
        super.onResume()
        wakeLock.acquire()

        /*
            *  get preferences
            * */

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)

        val selectedCharacter = preferences.getString("character", "wright")
        val selectedVoice = preferences.getString("voice", "igiari")
        voicePath = "lines/$selectedCharacter/$selectedVoice.mp3"   // concat strings

        voiceSwitch = preferences.getBoolean("is_voice", true)  /* get switch state */

        vibSwitch = preferences.getBoolean("is_vib", false) /* get vibrator state */

        /*
        *  set splash
        * */

        val imagePath = "img/$selectedVoice.png"
        setImg(this, imagePath, imageView)

        threshold = (9.81).toFloat() + (preferences.getInt("threshold", 0).toFloat())/20
        accelerationDetector.updateThreshold(threshold)
        accelerationDetector.startListening()

    }

    override fun onPause() {
        super.onPause()
        accelerationDetector.stopListening()
        wakeLock.release()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}