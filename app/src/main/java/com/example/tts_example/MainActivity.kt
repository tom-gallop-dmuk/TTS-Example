package com.example.tts_example

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.LANG_MISSING_DATA
import android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale


class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var srcTextView: TextInputEditText
    private var tts: TextToSpeech? = null
    private val REQUEST_CODE_SPEECH_INPUT = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContentView(R.layout.activity_main)
        setupScreen()
    }

    override fun onDestroy() {
        // shutdown tts
        if (tts != null) {
            tts?.stop()
            tts?.shutdown()
        }
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        // Initialise tts
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.UK)
            if (result == LANG_MISSING_DATA || result == LANG_NOT_SUPPORTED) {
                // Failed to set language
                Log.e("TTS", "Failed to initialise language to UK")
            }
        } else {
            // Failed to initialise
            Log.e("TTS", "Failed to initialise TextToSpeech")
        }
    }

    private fun setupScreen() {
        val switchButton = findViewById<Button>(R.id.buttonSwitchLang)
        val sourceSyncButton = findViewById<ToggleButton>(R.id.buttonSyncSource)
        val targetSyncButton = findViewById<ToggleButton>(R.id.buttonSyncTarget)
        srcTextView = findViewById(R.id.sourceText)
        val targetTextView = findViewById<TextView>(R.id.targetText)
        val downloadedModelsTextView = findViewById<TextView>(R.id.downloadedModels)
        val sourceLangSelector = findViewById<Spinner>(R.id.sourceLangSelector)
        val targetLangSelector = findViewById<Spinner>(R.id.targetLangSelector)
        val speakButton = findViewById<Button>(R.id.speakButton)
        val micButton = findViewById<FloatingActionButton>(R.id.micBtn)
        val viewModel = ViewModelProviders.of(this)[MainViewModel::class.java]

        // Get available language list and set up source and target language spinners
        // with default selections.
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item, viewModel.availableLanguages
        )
        sourceLangSelector.adapter = adapter
        targetLangSelector.adapter = adapter
        sourceLangSelector.setSelection(adapter.getPosition(MainViewModel.Language("en")))
        targetLangSelector.setSelection(adapter.getPosition(MainViewModel.Language("es")))
        sourceLangSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                setProgressText(targetTextView)
                viewModel.sourceLang.value = adapter.getItem(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                targetTextView.text = ""
            }
        }
        targetLangSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                setProgressText(targetTextView)
                viewModel.targetLang.value = adapter.getItem(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                targetTextView.text = ""
            }
        }
        switchButton.setOnClickListener {
            val targetText = targetTextView.text.toString()
            setProgressText(targetTextView)
            val sourceLangPosition = sourceLangSelector.selectedItemPosition
            sourceLangSelector.setSelection(targetLangSelector.selectedItemPosition)
            targetLangSelector.setSelection(sourceLangPosition)

            // Also update srcTextView with targetText
            srcTextView.setText(targetText)
            viewModel.sourceText.value = targetText
        }

        // Set up toggle buttons to delete or download remote models locally.
        sourceSyncButton.setOnCheckedChangeListener { _, isChecked ->
            val language = adapter.getItem(sourceLangSelector.selectedItemPosition)
            if (isChecked) {
                viewModel.downloadLanguage(language!!)
            } else {
                viewModel.deleteLanguage(language!!)
            }
        }
        targetSyncButton.setOnCheckedChangeListener { _, isChecked ->
            val language = adapter.getItem(targetLangSelector.selectedItemPosition)
            if (isChecked) {
                viewModel.downloadLanguage(language!!)
            } else {
                viewModel.deleteLanguage(language!!)
            }
        }

        // Translate input text as it is typed
        srcTextView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                setProgressText(targetTextView)
                viewModel.sourceText.postValue(s.toString())
            }
        })
        viewModel.translatedText.observe(
            this
        ) { resultOrError ->
            if (resultOrError.error != null) {
                srcTextView.error = resultOrError.error!!.localizedMessage
            } else {
                targetTextView.text = resultOrError.result
            }
        }

        // Update sync toggle button states based on downloaded models list.
        viewModel.availableModels.observe(
            this
        ) { translateRemoteModels ->
            val output = this.getString(
                R.string.downloaded_models_label,
                translateRemoteModels
            )
            downloadedModelsTextView.text = output

            sourceSyncButton.isChecked = !viewModel.requiresModelDownload(
                adapter.getItem(sourceLangSelector.selectedItemPosition)!!,
                translateRemoteModels
            )
            targetSyncButton.isChecked = !viewModel.requiresModelDownload(
                adapter.getItem(targetLangSelector.selectedItemPosition)!!,
                translateRemoteModels
            )
        }

        speakButton.setOnClickListener {
            speak(
                targetTextView.text.toString(),
                adapter.getItem(targetLangSelector.selectedItemPosition)?.code ?: "en"
            )
        }

        micButton.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale(adapter.getItem(sourceLangSelector.selectedItemPosition)?.code ?: "en")
            )
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speech to text")

            try {
                startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT)
            } catch (e: Exception) {
                // on below line we are displaying error message in toast
                Toast
                    .makeText(
                        this@MainActivity, " " + e.message,
                        Toast.LENGTH_SHORT
                    )
                    .show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // in this method we are checking request
        // code with our result code.
        if (requestCode == REQUEST_CODE_SPEECH_INPUT) {
            // on below line we are checking if result code is ok
            if (resultCode == RESULT_OK && data != null) {

                // in that case we are extracting the
                // data from our array list
                val res: ArrayList<String> =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) as ArrayList<String>

                // on below line we are setting data
                // to our output text view.
                srcTextView.setText(
                    res[0]
                )
            }
        }
    }

    private fun speak(text: String, languageCode: String) {
        val result = tts!!.setLanguage(Locale(languageCode))
        if (result == LANG_MISSING_DATA || result == LANG_NOT_SUPPORTED) {
            // Failed to set language, default to en
            Log.e("TTS", "Failed to set language ($languageCode)")
            Toast.makeText(applicationContext, "Language not supported in TextToSpeech", Toast.LENGTH_LONG).show()
        } else {
            Log.d("TTS", "Language set: $languageCode")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun setProgressText(tv: TextView) {
        tv.text = applicationContext.getString(R.string.translate_progress)
    }
}