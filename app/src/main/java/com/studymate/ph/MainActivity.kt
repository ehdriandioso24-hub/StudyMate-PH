package com.studymate.ph

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current

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

                Text(
                    text = "Ready to learn today?",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "📚 My Subjects",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        Toast.makeText(context, "Math selected", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📐 Math")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        Toast.makeText(context, "Science selected", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔬 Science")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        Toast.makeText(context, "English selected", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📖 English")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        Toast.makeText(context, "Filipino selected", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🇵🇭 Filipino")
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "⚡ Quick Study",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        Toast.makeText(context, "Quiz opened", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🧠 Quick Quiz")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        Toast.makeText(context, "Notes opened", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📝 My Notes")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        Toast.makeText(context, "Flashcards opened", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🃏 Flashcards")
                }
            }
        }
    }
}
