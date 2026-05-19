package com.example.test

import com.example.test.BuildConfig
import com.example.musicplayer.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ─── OpenRouter AI Client ──────────────────────────────────────────────────────

object OpenRouterClient {

    private const val API_URL = "https://openrouter.ai/api/v1/chat/completions"
    // You can swap this to any model on OpenRouter, e.g. "anthropic/claude-3-haiku"
    private const val MODEL   = "openai/gpt-4o-mini"

    /** The AI-generated playlist name from the last successful call. */
    var lastPlaylistName: String = ""
        private set

    /**
     * Rich per-song metadata fed to the AI so it can match mood/genre/energy accurately.
     * Keys are the lowercase decoded song titles.
     */
    data class SongMeta(
        val genres: List<String>,
        val moods:  List<String>,
        val energy: String,   // low | low-medium | medium | medium-high | high
        val tempo:  String,   // slow | moderate | moderate-fast | fast
        val lang:   String
    )

    private val SONG_METADATA: Map<String, SongMeta> = mapOf(
        "ana ba3sha2 el bahr"    to SongMeta(listOf("Arabic pop","classic Arabic"),     listOf("nostalgic","romantic","calm","longing"),           "low",         "slow",          "Arabic"),
        "baadak ala bali"        to SongMeta(listOf("Arabic classical","Fairuz"),        listOf("nostalgic","melancholic","romantic","reflective"),  "low",         "slow",          "Arabic"),
        "lamma 3al bab"          to SongMeta(listOf("Arabic classical","Fairuz"),        listOf("nostalgic","joyful","warm","classic"),             "medium",      "moderate",      "Arabic"),
        "shababeek"              to SongMeta(listOf("Egyptian pop","Mohamed Mounir"),    listOf("upbeat","joyful","carefree","sunny"),              "medium-high", "moderate",      "Arabic"),
        "the adults are talking" to SongMeta(listOf("indie rock","alternative"),         listOf("energetic","cool","edgy","driving"),               "high",        "fast",          "English"),
        "ya 3aroset el neil"     to SongMeta(listOf("Egyptian pop","Mohamed Mounir"),    listOf("joyful","celebratory","patriotic","warm"),         "medium",      "moderate",      "Arabic"),
        "ya habibi 3odli tani"   to SongMeta(listOf("Arabic pop","classic Arabic"),     listOf("romantic","longing","emotional","nostalgic"),       "low",         "slow",          "Arabic"),
        "ya sabeya"              to SongMeta(listOf("Egyptian pop","Mohamed Mounir"),    listOf("playful","flirty","upbeat","fun"),                 "medium-high", "moderate-fast", "Arabic"),
        "beat it"                to SongMeta(listOf("pop","rock","Michael Jackson"),     listOf("energetic","confident","powerful","iconic"),       "high",        "fast",          "English"),
        "dirty diana"            to SongMeta(listOf("pop rock","Michael Jackson"),       listOf("intense","dark","seductive","rock"),               "high",        "fast",          "English"),
        "thriller"               to SongMeta(listOf("pop","Michael Jackson","halloween"),listOf("spooky","fun","iconic","dance"),                   "high",        "moderate-fast", "English"),
        "forsa tanya"            to SongMeta(listOf("Egyptian indie rock","Cairokee"),   listOf("hopeful","motivational","emotional","empowering"), "medium-high", "moderate",      "Arabic"),
        "last christmas"         to SongMeta(listOf("pop","christmas","Wham!"),          listOf("nostalgic","festive","bittersweet","holiday"),     "medium",      "moderate",      "English"),
        "mahasbtahash"           to SongMeta(listOf("Arabic pop","Angham"),              listOf("heartbreak","sad","emotional","longing"),          "low",         "slow",          "Arabic"),
        "momken"                 to SongMeta(listOf("Egyptian pop","Mohamed Mounir"),    listOf("hopeful","romantic","gentle","reflective"),        "low-medium",  "slow",          "Arabic"),
        "stay"                   to SongMeta(listOf("pop","hip-hop","The Kid LAROI"),    listOf("energetic","upbeat","heartbreak","dance"),         "high",        "fast",          "English"),
        "broken heart"           to SongMeta(listOf("pop","emotional"),                  listOf("sad","heartbreak","crying","emotional"),           "low",         "slow",          "English"),
        "love drunk"             to SongMeta(listOf("pop rock","Boys Like Girls"),       listOf("energetic","romantic","fun","youthful"),           "high",        "fast",          "English"),
        "3alemooni 3eneiki"      to SongMeta(listOf("Arabic classical","Fairuz"),        listOf("romantic","soft","nostalgic","warm"),              "low",         "slow",          "Arabic"),
        "3awdat el 3askar"       to SongMeta(listOf("Egyptian pop","Mohamed Mounir"),   listOf("political","powerful","emotional","proud"),         "medium-high", "moderate",      "Arabic"),
        "benetweled"             to SongMeta(listOf("Egyptian pop","Mohamed Mounir"),    listOf("philosophical","uplifting","warm","reflective"),   "medium",      "moderate",      "Arabic"),
        "kan lak ma3aya"         to SongMeta(listOf("Egyptian pop","Cairokee"),          listOf("nostalgic","reflective","melancholic","rock"),     "medium",      "moderate",      "Arabic"),
        "marbout b astek"        to SongMeta(listOf("Egyptian indie","Cairokee"),        listOf("upbeat","quirky","energetic","social"),            "high",        "fast",          "Arabic")
    )

    suspend fun generatePlaylist(
        userPrompt: String,
        availableSongs: List<Song>
    ): List<Song> = withContext(Dispatchers.IO) {

        if (availableSongs.isEmpty()) return@withContext emptyList()

        // Build a rich catalogue with genre/mood/energy data so the AI has everything it needs
        val catalogue = buildString {
            availableSongs.forEachIndexed { index, song ->
                val lk   = song.title.lowercase().trim()
                // Improved matching: exact match or partial match if the string is long enough
                val meta = SONG_METADATA.entries
                    .firstOrNull { 
                        val key = it.key.lowercase()
                        lk == key || (lk.length > 5 && (lk.contains(key) || key.contains(lk)))
                    }?.value

                append("[$index] \"${song.title}\" by ${song.artist}")
                
                // Combine Firestore genre with our rich metadata if available
                val genres = mutableListOf<String>()
                if (song.genre.isNotBlank()) genres.add(song.genre)
                meta?.genres?.let { genres.addAll(it) }

                if (genres.isNotEmpty()) {
                    append(" | genres: ${genres.distinct().joinToString(", ")}")
                }

                if (meta != null) {
                    append(" | moods: ${meta.moods.joinToString(", ")}")
                    append(" | energy: ${meta.energy} | tempo: ${meta.tempo} | language: ${meta.lang}")
                }
                appendLine()
            }
        }

        val systemMessage = """
You are an expert music curator AI. Build the perfect playlist from a fixed catalogue of songs.

CATALOGUE FORMAT:
[index] "Title" by Artist | genres: ... | moods: ... | energy: low/medium/high | tempo: slow/moderate/fast | language: ...

YOUR TASK:
1. Deeply understand the user's intent — mood, activity, vibe, emotion, or any context they describe.
2. Select 4–12 songs that best fit. Prioritise mood and energy match above all else.
3. Order songs thoughtfully (e.g. build energy gradually, or keep a consistent mood).
4. If the user implies a language preference (e.g. "Arabic vibes", "English only"), honour it.
5. Never return an empty list — if no perfect match, pick the closest alternatives.
6. Also invent a short creative playlist name (4–6 words) that captures the vibe.

RULES:
- Only use songs from the catalogue. Use their exact index numbers.
- Respond ONLY with a valid compact JSON object — no markdown, no code fences, no explanation.
- Format: {"indices": [3, 7, 1], "playlist_name": "Creative Vibe Name"}
        """.trimIndent()

        val requestBody = JSONObject().apply {
            put("model", MODEL)
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role","system"); put("content", systemMessage) })
                put(JSONObject().apply { put("role","user");   put("content","User request: \"$userPrompt\"\n\nCatalogue:\n$catalogue") })
            })
            put("max_tokens", 256)
            put("temperature", 0.5) // Lower = more consistent, accurate picks
        }

        val connection = URL(API_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer ${BuildConfig.OPENROUTER_API_KEY}")
        connection.setRequestProperty("HTTP-Referer", "https://github.com/TDMMELO/my-music-files")
        connection.setRequestProperty("X-Title", "AI Music Player")
        connection.doOutput    = true
        connection.connectTimeout = 30000
        connection.readTimeout    = 30000

        try {
            connection.outputStream.use { it.write(requestBody.toString().toByteArray()) }

            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                val err = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                when (responseCode) {
                    401  -> throw Exception("Invalid OpenRouter API Key. Please check local.properties.")
                    402  -> throw Exception("OpenRouter account has no credits. Please top up your balance.")
                    404  -> throw Exception("AI model not found. Check the model name in Network.kt.")
                    429  -> throw Exception("Rate limit exceeded. Please wait a moment and try again.")
                    else -> throw Exception("AI Service Error ($responseCode): $err")
                }
            }

            val aiText = JSONObject(responseText)
                .optJSONArray("choices")
                ?.let { it.getJSONObject(0).getJSONObject("message").getString("content") }
                ?: throw Exception("AI returned no results.")

            val cleanJson = "\\{.*\\}".toRegex(RegexOption.DOT_MATCHES_ALL).find(aiText)?.value
                ?: throw Exception("Could not find JSON in AI response.")

            val parsed       = JSONObject(cleanJson)
            val indicesArray = parsed.optJSONArray("indices") ?: return@withContext emptyList()
            lastPlaylistName = parsed.optString("playlist_name", "").ifBlank { userPrompt }

            val selectedSongs = mutableListOf<Song>()
            for (i in 0 until indicesArray.length()) {
                availableSongs.getOrNull(indicesArray.getInt(i))?.let { selectedSongs.add(it) }
            }
            selectedSongs
        } finally {
            connection.disconnect()
        }
    }
}