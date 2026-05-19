package com.example.test

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth

// ─── Theme Colors ─────────────────────────────────────────────────────────────
private val BgDark        = Color(0xFF000000)
private val BgCard        = Color(0xFFFFFFFF).copy(alpha = 0.05f)
private val BgCardAlt     = Color(0xFFFFFFFF).copy(alpha = 0.1f)
private val AccentPurple  = Color(0xFF8B5CF6)
private val AccentPink    = Color(0xFFEC4899)
private val AccentGreen   = Color(0xFF10B981)
private val TextPrimary   = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFE2E8F0)
private val TextMuted     = Color(0xFF94A3B8)


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
        "🏋️ Gym motivation",
        "😴 Peaceful sleep",
        "🚗 Road trip vibes",
        "💔 Heartbreak healing",
        "🎉 Party energy",
        "☕ Morning coffee",
        "🌙 Late night chills",
        "🎭 Arabic classics"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main content area - Scrollable only if needed to prevent overflow on small screens
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                // Glowing Icon Header
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(AccentPurple, AccentPink)))
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(BgDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✨", fontSize = 28.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "AI Curator",
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                )
                Text(
                    "Your mood, our playlist",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Prompt Input Card - Glassmorphism
                Surface(
                    color = BgCard,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        placeholder = {
                            Text(
                                "What are you feeling right now?",
                                color = TextMuted,
                                fontSize = 15.sp
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        colors = TextFieldDefaults.textFieldColors(
                            textColor = TextPrimary,
                            cursorColor = AccentPurple,
                            backgroundColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = TextStyle(fontSize = 16.sp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "Popular Vibes",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )

                // Grid of suggestions - Compact
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { suggestion ->
                                SuggestionChip(
                                    label = suggestion,
                                    onClick = { promptText = suggestion.substringAfter(" ") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Fixed bottom button
            Button(
                onClick = { if (promptText.isNotBlank()) onGenerateClicked(promptText.trim()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = promptText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = AccentPurple,
                    disabledBackgroundColor = BgCardAlt
                ),
                shape = RoundedCornerShape(26.dp),
                elevation = ButtonDefaults.elevation(0.dp)
            ) {
                Text(
                    "Create Playlist",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SuggestionChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = BgCard,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        elevation = 0.dp
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
                // ── Enhanced Header ─────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF3B0764), BgDark)
                            )
                        )
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                            }
                            
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = AccentPurple,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            "AI Generation",
                            color = AccentPurple,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        
                        Text(
                            state.playlistName,
                            color = TextPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 36.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        Text(
                            "${state.songs.size} hand-picked tracks for your mood",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // ── Premium Action Buttons ───────────────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Play All - Primary Action
                            Button(
                                onClick = { onPlaySong(state.songs, 0) },
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(52.dp),
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
                                shape = RoundedCornerShape(26.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = BgDark)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Play All", color = BgDark, fontWeight = FontWeight.Bold)
                            }

                            // Save - Secondary Action
                            val isSaved = saveState is SaveState.Saved
                            val isSaving = saveState is SaveState.Saving
                            Button(
                                onClick = {
                                    if (!isSaved && !isSaving) {
                                        onSavePlaylist(state.playlistName, state.songs)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = if (isSaved) AccentGreen else BgCardAlt
                                ),
                                shape = RoundedCornerShape(26.dp),
                                enabled = !isSaving
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(
                                        if (isSaved) Icons.Default.Check else Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (isSaved) "Saved" else "Save",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Song List ────────────────────────────────────────────────
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(state.songs) { index, song ->
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { visible = true }
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(tween(400, delayMillis = index * 50)) +
                                    slideInVertically(tween(400, delayMillis = index * 50)) { it / 3 }
                        ) {
                            AISongRow(
                                song = song,
                                index = index,
                                onClick = { onPlaySong(state.songs, index) }
                            )
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
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        color = BgCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Song Image
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(song.imageUrl.ifBlank { null })
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                
                // Fallback icon if image fails or is empty
                if (song.imageUrl.isBlank()) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = AccentPurple.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                Icons.Default.PlayCircleFilled,
                contentDescription = null,
                tint = AccentPurple,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
