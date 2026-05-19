package com.example.test

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

// ─── Theme Colors ─────────────────────────────────────────────────────────────

private val BgDark        = Color(0xFF0D0D0F)
private val BgCard        = Color(0xFF1A1A1E)
private val BgCardAlt     = Color(0xFF222228)
private val AccentPurple  = Color(0xFF8B5CF6)
private val AccentPink    = Color(0xFFEC4899)
private val AccentGreen   = Color(0xFF22C55E)
private val TextPrimary   = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF9CA3AF)
private val TextMuted     = Color(0xFF6B7280)

// ─── Root Container ───────────────────────────────────────────────────────────

@Composable
fun AIPlaylistFeatureContainer(
    viewModel: PlaylistViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize(), color = BgDark) {
        if (currentScreen == "prompt") {
            PromptScreen(onGenerateClicked = { prompt ->
                viewModel.generatePlaylist(prompt)
            })
        } else {
            val uiState by viewModel.uiState.collectAsState()
            val saveState by viewModel.saveState.collectAsState()

            PlaylistResultScreen(
                state      = uiState,
                saveState  = saveState,
                onBack     = { viewModel.resetState(); viewModel.navigateTo("prompt") },
                onPlaySong = { songs, index ->
                    if (com.example.test.player.MusicPlayerManager.isInitialized()) {
                        com.example.test.player.MusicPlayerManager.playPlaylist(songs, index)
                    } else {
                        com.example.test.player.MusicPlayerManager.initialize(context) {
                            com.example.test.player.MusicPlayerManager.playPlaylist(songs, index)
                        }
                    }
                },
                onSavePlaylist = { name, songs ->
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@PlaylistResultScreen
                    viewModel.saveGeneratedPlaylist(
                        context      = context,
                        userId       = uid,
                        playlistName = name,
                        songs        = songs
                    )
                }
            )
        }
    }
}

// ─── Prompt Screen ────────────────────────────────────────────────────────────

@Composable
fun PromptScreen(onGenerateClicked: (String) -> Unit) {
    var promptText by remember { mutableStateOf("") }

    val suggestions = listOf(
        "🏋️ Intense gym session",
        "😴 Calm bedtime vibes",
        "🚗 Road trip energy",
        "💔 Heartbreak healing",
        "🎉 Party mode",
        "☕ Morning coffee",
        "🌙 Late night feels",
        "🎭 Arabic nostalgia"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(BgCard),
            contentAlignment = Alignment.Center
        ) { Text("✨", fontSize = 32.sp) }

        Spacer(modifier = Modifier.height(20.dp))

        Text("AI Playlist", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(
            "Describe your vibe — AI picks the songs",
            fontSize = 14.sp, color = TextSecondary, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 28.dp)
        )

        OutlinedTextField(
            value = promptText,
            onValueChange = { promptText = it },
            placeholder = { Text("e.g., Relaxing Sunday morning coffee...", color = TextMuted, fontSize = 14.sp) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor            = TextPrimary,
                focusedBorderColor   = AccentPurple,
                unfocusedBorderColor = BgCardAlt,
                backgroundColor      = BgCard,
                cursorColor          = AccentPurple
            ),
            maxLines = 4
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Quick ideas:", color = TextMuted, fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))

        suggestions.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { suggestion ->
                    SuggestionChip(
                        label   = suggestion,
                        onClick = { promptText = suggestion.substringAfter(" ") },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(2 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick  = { if (promptText.isNotBlank()) onGenerateClicked(promptText.trim()) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled  = promptText.isNotBlank(),
            colors   = ButtonDefaults.buttonColors(
                backgroundColor        = AccentPurple,
                disabledBackgroundColor = BgCardAlt
            ),
            shape     = RoundedCornerShape(28.dp),
            elevation = ButtonDefaults.elevation(defaultElevation = 0.dp)
        ) {
            Text(
                "Generate Playlist",
                color          = if (promptText.isNotBlank()) Color.White else TextMuted,
                fontWeight     = FontWeight.SemiBold,
                fontSize       = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SuggestionChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick  = onClick,
        modifier = modifier,
        shape    = RoundedCornerShape(20.dp),
        color    = BgCardAlt,
        elevation = 0.dp
    ) {
        Text(
            text     = label,
            color    = TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            textAlign = TextAlign.Center
        )
    }
}

// ─── Result Screen ────────────────────────────────────────────────────────────

@Composable
fun PlaylistResultScreen(
    state:          UiState,
    saveState:      SaveState,
    onBack:         () -> Unit,
    onPlaySong:     (List<com.example.musicplayer.Song>, Int) -> Unit,
    onSavePlaylist: (name: String, songs: List<com.example.musicplayer.Song>) -> Unit
) {
    val context = LocalContext.current

    // Show a toast whenever save succeeds or fails
    LaunchedEffect(saveState) {
        when (saveState) {
            is SaveState.Saved -> Toast.makeText(context, "Playlist saved to your library! 🎵", Toast.LENGTH_SHORT).show()
            is SaveState.Error -> Toast.makeText(context, saveState.message, Toast.LENGTH_LONG).show()
            else -> {}
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        when (state) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AccentPurple, strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("AI is curating your playlist…", color = TextSecondary, fontSize = 14.sp)
                        Text("Fetching songs from the repository", color = TextMuted, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("😕", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Couldn't generate playlist", color = TextPrimary,
                            fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(state.message, color = TextSecondary, fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
                        Button(
                            onClick = onBack,
                            colors  = ButtonDefaults.buttonColors(backgroundColor = AccentPurple),
                            shape   = RoundedCornerShape(24.dp)
                        ) { Text("Try Again", color = Color.White) }
                    }
                }
            }

            is UiState.Success -> {
                // ── Header ──────────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(AccentPurple.copy(alpha = 0.25f), BgDark)))
                        .padding(16.dp)
                ) {
                    Column {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("🎧 Your AI Playlist", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "\"${state.playlistName}\"",
                            color = AccentPurple, fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp),
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${state.songs.size} songs · Curated by AI",
                            color = TextMuted, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // ── Action Buttons Row ───────────────────────────────
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {

                            // Play All
                            Button(
                                onClick = { onPlaySong(state.songs, 0) },
                                colors  = ButtonDefaults.buttonColors(backgroundColor = AccentPurple),
                                shape   = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null,
                                    tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play All", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }

                            // Save to Library
                            val isSaved  = saveState is SaveState.Saved
                            val isSaving = saveState is SaveState.Saving
                            Button(
                                onClick = {
                                    if (!isSaved && !isSaving) {
                                        onSavePlaylist(state.playlistName, state.songs)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor        = if (isSaved) AccentGreen else BgCardAlt,
                                    disabledBackgroundColor = BgCardAlt
                                ),
                                shape   = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                enabled = !isSaving
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        color       = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier    = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Saving…", color = Color.White, fontWeight = FontWeight.SemiBold)
                                } else {
                                    Icon(
                                        if (isSaved) Icons.Default.BookmarkAdded else Icons.Default.BookmarkBorder,
                                        contentDescription = null,
                                        tint = Color.White, modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (isSaved) "Saved!" else "Save Playlist",
                                        color = Color.White, fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Song List ────────────────────────────────────────────────
                LazyColumn(
                    modifier        = Modifier.fillMaxSize(),
                    contentPadding  = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(state.songs) { index, song ->
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { visible = true }
                        AnimatedVisibility(
                            visible = visible,
                            enter   = fadeIn(tween(300, delayMillis = index * 60)) +
                                    slideInVertically(tween(300, delayMillis = index * 60)) { it / 2 }
                        ) {
                            AISongRow(song = song, index = index,
                                onClick = { onPlaySong(state.songs, index) })
                        }
                    }
                }
            }

            is UiState.Idle -> { /* nothing */ }
        }
    }
}

// ─── Song Row ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AISongRow(song: com.example.musicplayer.Song, index: Int, onClick: () -> Unit) {
    Card(
        modifier        = Modifier.fillMaxWidth(),
        backgroundColor = BgCard,
        shape           = RoundedCornerShape(12.dp),
        elevation       = 0.dp,
        onClick         = onClick
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(BgCardAlt),
                contentAlignment = Alignment.Center
            ) {
                Text("${index + 1}", color = AccentPurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title,  color = TextPrimary,   fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = TextSecondary, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.MusicNote, contentDescription = "Play",
                tint = AccentPurple.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
    }
}