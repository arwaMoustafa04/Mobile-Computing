package com.example.test // Ensure this matches your package name!

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val AnghamiDarkBackground = Color(0xFF121214)
val AnghamiPurple = Color(0xFF6200EE)
val AnghamiCardBackground = Color(0xFF1E1E22)

@Composable
fun AIPlaylistFeatureContainer(viewModel: PlaylistViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    var currentScreen by remember { mutableStateOf("prompt") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AnghamiDarkBackground
    ) {
        if (currentScreen == "prompt") {
            PromptScreen(onGenerateClicked = { prompt ->
                viewModel.generatePlaylist(prompt)
                currentScreen = "result"
            })
        } else {
            PlaylistResultScreen(
                state = viewModel.uiState.value,
                onBack = {
                    viewModel.resetState()
                    currentScreen = "prompt"
                }
            )
        }
    }
}

@Composable
fun PromptScreen(onGenerateClicked: (String) -> Unit) {
    var promptText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("AI Playlist Generator ✨", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("What vibe are you matching from your repository?", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))

        OutlinedTextField(
            value = promptText,
            onValueChange = { promptText = it },
            placeholder = { Text("e.g., Chill acoustic tracks or rap workout mix", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = Color.White,
                focusedBorderColor = AnghamiPurple,
                unfocusedBorderColor = Color.Gray
            )
        )

        Button(
            onClick = { if (promptText.isNotBlank()) onGenerateClicked(promptText) },
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(50.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = AnghamiPurple),
            shape = RoundedCornerShape(25.dp)
        ) {
            Text("Generate Playlist", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PlaylistResultScreen(state: UiState, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        when (state) {
            is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = AnghamiPurple)
            is UiState.Error -> {
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error: ${state.message}", color = Color.Red)
                    Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) { Text("Go Back") }
                }
            }
            is UiState.Success -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text("Your AI Vibe Mix 🎧", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(vertical = 16.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(state.songs) { song -> SongItem(song) }
                    }
                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = AnghamiPurple)
                    ) {
                        Text("Back to Prompts", color = Color.White)
                    }
                }
            }
            is UiState.Idle -> {}
        }
    }
}

@Composable
fun SongItem(song: Song) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        backgroundColor = AnghamiCardBackground,
        shape = RoundedCornerShape(8.dp),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = song.title, color = Color.White, fontWeight = FontWeight.Medium)
                Text(text = song.artist, color = Color.LightGray, fontSize = 14.sp)
                Text(text = "Source: GitHub Repository", color = AnghamiPurple, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Text(text = song.duration, color = Color.Gray, fontSize = 12.sp)
        }
    }
}