package com.hex.demonvoice

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.*
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.Button
import android.widget.SeekBar
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import kotlin.concurrent.thread
import kotlin.math.tanh

class AudioService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var isProcessing = false

    private val sampleRate = 44100
    private var bufferSize = AudioRecord.getMinBufferSize(sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT)

    private lateinit var record: AudioRecord
    private lateinit var track: AudioTrack

    private var pitchValue = 0.7f
    private var distortValue = 4.0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        createFloatingButton()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "demon_voice_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Demon Voice Changer", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Pro VoiceChanger Demonio")
            .setContentText("Efecto demoníaco activo")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1, notification)
    }

    private fun createFloatingButton() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingView = inflater.inflate(R.layout.floating_widget, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 300

        windowManager.addView(floatingView, params)

        val btnToggle = floatingView.findViewById<Button>(R.id.btnToggle)
        val sliderPitch = floatingView.findViewById<SeekBar>(R.id.sliderPitch)
        val sliderDistort = floatingView.findViewById<SeekBar>(R.id.sliderDistort)

        sliderPitch.max = 100
        sliderPitch.progress = ((pitchValue - 0.5f) * 100).toInt()
        sliderPitch.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                pitchValue = 0.5f + (progress / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sliderDistort.max = 100
        sliderDistort.progress = ((distortValue - 1f) * 10).toInt()
        sliderDistort.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                distortValue = 1f + (progress / 10f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnToggle.setOnClickListener {
            isProcessing = !isProcessing
            if (isProcessing) startAudioProcessing() else stopAudioProcessing()
        }
    }

    private fun startAudioProcessing() {
        record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Root puede usar REMOTE_SUBMIX
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        record.startRecording()
        track.play()

        thread {
            val audioBuffer = ShortArray(bufferSize)
            while (isProcessing) {
                val read = record.read(audioBuffer, 0, bufferSize)
                if (read > 0) {
                    val processed = applyDemonEffect(audioBuffer, read)
                    track.write(processed, 0, read)
                }
            }
        }
    }

    private fun stopAudioProcessing() {
        isProcessing = false
        record.stop()
        record.release()
        track.stop()
        track.release()
    }

    private fun applyDemonEffect(input: ShortArray, length: Int): ShortArray {
        val output = ShortArray(length)
        for (i in 0 until length) {
            val idx = (i * pitchValue).toInt().coerceAtMost(length - 1)
            output[i] = (tanh(input[idx] / 32768.0 * distortValue) * 32767.0).toInt().toShort()
        }
        return output
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
        stopAudioProcessing()
    }
}