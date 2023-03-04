package com.example.myapplication

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import com.example.myapplication.databinding.ActivityMainBinding
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.shazam.shazamkit.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var catalog: Catalog
    private lateinit var currentSession: StreamingSession
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isRecording = false
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)

    private lateinit var binding: ActivityMainBinding
    private val REQUEST_AUDIO_PERMISSION_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        configureShazamKitSession("eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsImtpZCI6IjQyODQ0MzIzTUsifQ.eyJleHAiOjE2OTE1MjAwMjQsImlzcyI6IjVCU05ZWVlINEsiLCJ0eXAiOiJKV1QifQ.GJrFb0Y0jfdosQz31EO31yuzQlh-tcIlZfsVX0Dm03c8-U60p7xzbNQtAoB81ep6SCZYscVdYUNamKCjdWPMOg")
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)


    }

    // Configure Shazam session to be use in the app
    private fun configureShazamKitSession(
        developerToken: String?,
    ) {
        try {
            if (developerToken == null) {
                return
            }
            val tokenProvider = DeveloperTokenProvider {
                DeveloperToken(developerToken)
            }
            catalog = ShazamKit.createShazamCatalog(tokenProvider)
            coroutineScope.launch {
                when (val result = ShazamKit.createStreamingSession(
                    catalog, AudioSampleRateInHz.SAMPLE_RATE_44100, 8192
                )) {
                    is ShazamKitResult.Success -> {
                        currentSession = result.data

                    }
                    is ShazamKitResult.Failure -> {
                        result.reason.message?.let { onError(it) }
                    }
                }
                currentSession.let {
                    currentSession.recognitionResults().collect { result: MatchResult ->
                        try {
                            when (result) {
                                is MatchResult.Match -> {
                                    val songInfo = findViewById<TextView>(R.id.textView)
                                    songInfo.text = result.toJsonString()
                                    stopListening();

                                }
                                is MatchResult.NoMatch -> onError("Not Found")
                                is MatchResult.Error ->   onError(result.exception.message)
                            }
                        } catch (e: Exception) {
                            e.message?.let { onError(it) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.message?.let { onError(it) }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startListening() {
        try {

            val audioSource = MediaRecorder.AudioSource.DEFAULT
            val audioFormat = AudioFormat.Builder().setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(41_000).build()

            audioRecord =
                AudioRecord.Builder().setAudioSource(audioSource).setAudioFormat(audioFormat)
                    .build()
            val bufferSize = AudioRecord.getMinBufferSize(
                41_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            audioRecord?.startRecording()
            isRecording = true
            recordingThread = Thread({
                val readBuffer = ByteArray(bufferSize)
                while (isRecording) {
                    val actualRead = audioRecord!!.read(readBuffer, 0, bufferSize)
                    currentSession.matchStream(readBuffer, actualRead, System.currentTimeMillis())
                }
            }, "AudioRecorder Thread")
            recordingThread!!.start()
        } catch (e: Exception) {
            e.message?.let { onError(it) }
            stopListening()
        }
    }

    /// Allow us to stop recording our song
    private fun stopListening() {
        val button = findViewById<Button>(R.id.button)
        if (audioRecord != null) {
            isRecording = false
            audioRecord!!.stop()
            audioRecord!!.release()
            audioRecord = null
            recordingThread = null
            button.isEnabled = true
            button.text = "Search Song"
            button.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        }

    }

    private fun onError(message: String?) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        stopListening()
        println(message)
    }

    fun performRecording(){
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_AUDIO_PERMISSION_CODE,
            )
        } else {
            startListening()
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}


fun MatchResult.Match.toJsonString(): String {
    val itemJsonArray = JSONArray()
    this.matchedMediaItems.forEach { item ->
        val itemJsonObject = JSONObject()
        itemJsonObject.put("title", item.title)
        itemJsonObject.put("subtitle", item.subtitle)
        itemJsonObject.put("shazamId", item.shazamID)
        itemJsonObject.put("appleMusicId", item.appleMusicID)
        item.appleMusicURL?.let {
            itemJsonObject.put("appleMusicUrl", it.toURI().toString())
        }
        item.artworkURL?.let {
            itemJsonObject.put("artworkUrl", it.toURI().toString())
        }
        itemJsonObject.put("artist", item.artist)
        itemJsonObject.put("matchOffset", item.matchOffsetInMs)
        item.videoURL?.let {
            itemJsonObject.put("videoUrl", it.toURI().toString())
        }
        item.webURL?.let {
            itemJsonObject.put("webUrl", it.toURI().toString())
        }
        itemJsonObject.put("genres", JSONArray(item.genres))
        itemJsonObject.put("isrc", item.isrc)
        itemJsonArray.put(itemJsonObject)
    }
    return itemJsonArray.toString()

}