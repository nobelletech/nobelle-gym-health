package com.nobelle.phone.exercise

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Whether the device can narrate instruction steps from an offline voice.
 * [VOICE_MISSING] switches the UI to captions-only mode (with install
 * guidance) — narration is never blocked on, and never falls back to
 * streaming voices.
 */
enum class OfflineVoiceState { READY, VOICE_MISSING }

/**
 * Narration of exercise instruction steps via the system [TextToSpeech]
 * engine. One utterance per step (QUEUE_ADD), so captions and the
 * stick-figure animation cue advance exactly when each step starts.
 *
 * Semantics (the "no overlap, no auto-repeat" rules):
 *  - [play] stops any running narration first (STOP — never a QUEUE_FLUSH
 *    race) and speaks from the current step; [replay] restarts at step 0;
 *    [pause] stops speech but keeps the current step so play resumes there.
 *  - Instructions are narrated once per play; when the last utterance
 *    finishes the animation keeps looping silently and the captions un-highlight.
 *  - [loadScript] is called by the detail dialog per exercise.
 *
 * Initialisation is asynchronous (the TTS engine callback) and the UI never
 * blocks on it: [voiceState] is null until the engine reports readiness.
 */
class ExerciseNarrator(context: Context) {
    private val appContext: Context = context.applicationContext

    private val _voiceState = MutableStateFlow<OfflineVoiceState?>(null)
    val voiceState: StateFlow<OfflineVoiceState?> = _voiceState.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    /** Index of the step whose utterance is currently playing (captions cue). */
    private val _currentStepIndex = MutableStateFlow<Int?>(null)
    val currentStepIndex: StateFlow<Int?> = _currentStepIndex.asStateFlow()

    private var steps: List<String> = emptyList()
    private var speakSteps = true

    private var tts: TextToSpeech? = null
    private var disposed = false

    init {
        // Probe engines in order (default first, then every installed one)
        // and keep the first that offers an offline voice for the device
        // language. Each engine initialises asynchronously; the callback
        // chain shuts down failures and moves to the next candidate.
        val candidates: List<String?> = listOf(null) + ttsEnginePackages()
        probeNext(candidates, 0)
    }

    /** Installed TTS engine packages, discovered via their TTS_SERVICE intent. */
    private fun ttsEnginePackages(): List<String> =
        appContext.packageManager.queryIntentServices(
            android.content.Intent("android.intent.action.TTS_SERVICE"), 0,
        ).mapNotNull { it.serviceInfo?.packageName }.distinct()

    private fun probeNext(candidates: List<String?>, index: Int) {
        if (disposed || index >= candidates.size) {
            if (tts == null) _voiceState.value = OfflineVoiceState.VOICE_MISSING
            return
        }
        // A (pathological) synchronous callback would arrive before the holder
        // is set; the holder protects against that race.
        val holder = java.util.concurrent.atomic.AtomicReference<TextToSpeech?>(null)
        val engine = candidates[index]?.let {
            TextToSpeech(appContext, { status ->
                onEngineInit(status, holder.get(), candidates, index)
            }, it)
        } ?: TextToSpeech(appContext) { status ->
            onEngineInit(status, holder.get(), candidates, index)
        }
        holder.set(engine)
    }

    private fun onEngineInit(
        status: Int,
        engine: TextToSpeech?,
        candidates: List<String?>,
        index: Int,
    ) {
        if (disposed) {
            engine?.shutdown()
            return
        }
        if (status == TextToSpeech.SUCCESS && engine != null) {
            val voice = offlineVoiceFor(engine)
            if (voice != null) {
                engine.voice = voice
                engine.language = voice.locale
                engine.setOnUtteranceProgressListener(listener())
                tts = engine
                _voiceState.value = OfflineVoiceState.READY
                return
            }
        }
        engine?.shutdown()
        probeNext(candidates, index + 1)
    }

    private fun listener() = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val index = utteranceId?.substringAfter(UTTERANCE_PREFIX)?.toIntOrNull()
            if (index != null && index in steps.indices) {
                _currentStepIndex.value = index
                _speaking.value = true
            }
        }

        override fun onDone(utteranceId: String?) {
            // QUEUE_ADD advances to the next step automatically; when the
            // final step is done the run is over — never auto-repeat.
            val index = utteranceId?.substringAfter(UTTERANCE_PREFIX)?.toIntOrNull()
            if (index != null && index == steps.lastIndex) finishRun()
        }

        override fun onError(utteranceId: String?) {
            finishRun()
        }
    }

    /** Installs the narration script for one exercise. Stops anything running. */
    fun loadScript(instructionSteps: List<String>, speak: Boolean) {
        stop()
        steps = instructionSteps
        speakSteps = speak
        _currentStepIndex.value = null
    }

    /** Stops any running narration (STOP) and speaks from the current step.
     * When no offline voice is installed, or the manifest disabled speech,
     * this is a no-op — the UI stays in captions-only mode. */
    fun play() {
        val engine = tts ?: return
        engine.stop()
        if (steps.isEmpty()) return
        if (_voiceState.value != OfflineVoiceState.READY || !speakSteps) {
            _speaking.value = false
            return
        }
        val start = (_currentStepIndex.value ?: 0).coerceIn(0, steps.lastIndex)
        _speaking.value = true
        for (i in start until steps.size) {
            engine.speak(steps[i], TextToSpeech.QUEUE_ADD, null, utteranceId(i))
        }
    }

    /** Stops speech but keeps the current step, so [play] resumes there. */
    fun pause() {
        tts?.stop()
        _speaking.value = false
    }

    /** Restarts narration from step 0. */
    fun replay() {
        _currentStepIndex.value = 0
        play()
    }

    /** Stops speech and clears the current step (dialog dismissed). */
    fun stop() {
        tts?.stop()
        _speaking.value = false
        _currentStepIndex.value = null
    }

    fun shutdown() {
        disposed = true
        stop()
        tts?.shutdown()
        tts = null
        _voiceState.value = null
    }

    private fun finishRun() {
        _speaking.value = false
        _currentStepIndex.value = null
    }

    private fun utteranceId(index: Int): String = "$UTTERANCE_PREFIX$index"

    /**
     * Offline-voice selection for the device language: prefer the exact
     * locale (e.g. en-CA), else any voice of the same language (en-US works
     * for Canadian English — same language, offline data on device). The
     * voice must have its data on-device: LANG_MISSING_DATA means the voice
     * would need to be streamed — not acceptable in airplane mode.
     */
    private fun offlineVoiceFor(engine: TextToSpeech): android.speech.tts.Voice? {
        val device = Locale.getDefault()
        // minSdk is 30, so the LOLLIPOP+ voices API is always present.
        val voices = engine.voices?.filter { it.locale != null } ?: emptyList()
        val exact = voices.firstOrNull {
            it.locale.language.equals(device.language, ignoreCase = true) &&
                it.locale.country.equals(device.country, ignoreCase = true)
        }
        val sameLanguage = voices.firstOrNull {
            it.locale.language.equals(device.language, ignoreCase = true)
        }
        val chosen = exact ?: sameLanguage ?: return null
        return if (engine.isLanguageAvailable(chosen.locale) != TextToSpeech.LANG_MISSING_DATA) chosen else null
    }

    private companion object {
        const val UTTERANCE_PREFIX = "nobelle-step-"
    }
}