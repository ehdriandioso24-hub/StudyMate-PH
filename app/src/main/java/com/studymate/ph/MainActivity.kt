package com.studymate.ph

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StudyMateApp()
        }
    }
}

@Composable
fun StudyMateApp() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "👋 Hi, Student!",
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("Ready to learn today?")

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "📚 My Subjects",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("📐 Math")
                Text("🔬 Science")
                Text("📖 English")
                Text("🇵🇭 Filipino")

                Spacer(modifier = Modifier.height(24.dp))

                Text("🧠 Quick Quiz")
                Text("📝 My Notes")
                Text("🃏 Flashcards")
            }
        }
    }
}
