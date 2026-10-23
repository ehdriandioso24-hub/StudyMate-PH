package com.studymate.ph

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private enum class Screen {
    HOME, SUBJECTS, QUIZ, NOTES, FLASHCARDS, TIMER, PROGRESS, REVIEW, SETTINGS
}

private data class Note(val id: Long, val title: String, val body: String)

private data class Question(
    val id: String,
    val subject: String,
    val text: String,
    val choices: List<String>,
    val answer: Int,
    val explanation: String
)

private data class ReviewItem(
    val question: Question,
    val selected: Int,
    val correct: Boolean
)

private data class Flashcard(
    val subject: String,
    val front: String,
    val back: String
)

private data class BankItem(
    val text: String,
    val choices: List<String>,
    val answer: Int,
    val explanation: String
)

private val bg = Color(0xFF080D1D)
private val panel = Color(0xFF111A30)
private val purple = Color(0xFFC084FC)
private val pink = Color(0xFFF472D0)
private val muted = Color(0xFFA9B5D0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = purple,
                    secondary = pink,
                    background = bg,
                    surface = panel,
                    onPrimary = Color(0xFF160D28),
                    onBackground = Color(0xFFF5F3FF),
                    onSurface = Color(0xFFF5F3FF)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = bg
                ) {
                    StudyMateApp()
                }
            }
        }
    }
}

@Composable
private fun StudyMateApp() {
    val context = LocalContext.current

    val prefs = remember {
        context.getSharedPreferences("studymate_data", Context.MODE_PRIVATE)
    }

    var screen by rememberSaveable { mutableStateOf(Screen.HOME.name) }
    var level by remember {
        mutableIntStateOf(prefs.getInt("level", 1).coerceIn(1, 100))
    }
    var answered by remember {
        mutableIntStateOf(prefs.getInt("answered", 0))
    }
    var correct by remember {
        mutableIntStateOf(prefs.getInt("correct", 0))
    }
    var selectedSubject by rememberSaveable { mutableStateOf("Mixed") }
    var lightTheme by remember {
        mutableStateOf(prefs.getBoolean("lightTheme", false))
    }

    val notes = remember {
        mutableStateListOf<Note>().apply {
            addAll(decodeNotes(prefs.getString("notes", "[]") ?: "[]"))
        }
    }

    val answeredIds = remember {
        mutableStateListOf<String>().apply {
            addAll(decodeStrings(prefs.getString("answeredIds", "[]") ?: "[]"))
        }
    }

    val history = remember {
        mutableStateListOf<ReviewItem>().apply {
            addAll(decodeHistory(prefs.getString("history", "[]") ?: "[]"))
        }
    }

    val customSubjects = remember {
        mutableStateListOf<String>().apply {
            addAll(decodeStrings(prefs.getString("customSubjects", "[]") ?: "[]"))
        }
    }

    var questions by remember { mutableStateOf(emptyList<Question>()) }
    var questionIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedAnswer by rememberSaveable { mutableIntStateOf(-1) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    var showSubjectDialog by remember { mutableStateOf(false) }
    var subjectInput by rememberSaveable { mutableStateOf("") }

    fun saveProgress() {
        prefs.edit()
            .putInt("level", level)
            .putInt("answered", answered)
            .putInt("correct", correct)
            .putString("answeredIds", encodeStrings(answeredIds))
            .putString("history", encodeHistory(history))
            .putString("customSubjects", encodeStrings(customSubjects))
            .apply()
    }

    fun startQuiz(subject: String) {
        selectedSubject = subject
        questionIndex = 0
        selectedAnswer = -1
        revealed = false

        val pool = makeQuestions(subject, level)
        questions = pool.filter { it.id !in answeredIds }.shuffled().take(20)

        screen = Screen.QUIZ.name
    }

    val currentScreen = runCatching {
        Screen.valueOf(screen)
    }.getOrDefault(Screen.HOME)

    BackHandler(enabled = currentScreen != Screen.HOME) {
        screen = when (currentScreen) {
            Screen.QUIZ -> Screen.SUBJECTS.name
            Screen.REVIEW -> Screen.PROGRESS.name
            else -> Screen.HOME.name
        }
    }

    val colors = if (lightTheme) {
        lightColorScheme(
            primary = Color(0xFF6D28D9),
            secondary = Color(0xFFDB2777),
            background = Color(0xFFF7F4FF),
            surface = Color.White,
            onBackground = Color(0xFF17112A),
            onSurface = Color(0xFF17112A)
        )
    } else {
        MaterialTheme.colorScheme
    }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = colors.background,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentScreen != Screen.HOME) {
                        OutlinedButton(onClick = {
                            screen = if (currentScreen == Screen.QUIZ) {
                                Screen.SUBJECTS.name
                            } else {
                                Screen.HOME.name
                            }
                        }) {
                            Text("← Back")
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    Column {
                        Text(
                            "StudyMate PH",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Learn smarter, one day at a time.",
                            color = muted
                        )
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = panel) {
                    NavigationBarItem(
                        selected = currentScreen == Screen.HOME,
                        onClick = { screen = Screen.HOME.name },
                        icon = { Text("🏠") },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.SUBJECTS,
                        onClick = { screen = Screen.SUBJECTS.name },
                        icon = { Text("📚") },
                        label = { Text("Subjects") }
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.NOTES,
                        onClick = { screen = Screen.NOTES.name },
                        icon = { Text("📝") },
                        label = { Text("Notes") }
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.PROGRESS,
                        onClick = { screen = Screen.PROGRESS.name },
                        icon = { Text("📈") },
                        label = { Text("Progress") }
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (currentScreen) {
                    Screen.HOME -> {
                        CardBox(
                            "Welcome, Learner! ✨",
                            "Current Level: $level / 100"
                        ) {}

                        CardBox(
                            "🧠 Quick Quiz",
                            "Mixed questions from four subjects"
                        ) { startQuiz("Mixed") }

                        CardBox(
                            "📚 My Subjects",
                            "Math, Science, English, and Filipino"
                        ) { screen = Screen.SUBJECTS.name }

                        CardBox(
                            "🎴 Flashcards",
                            "Review important study facts"
                        ) { screen = Screen.FLASHCARDS.name }

                        CardBox(
                            "⏱️ Study Timer",
                            "Focus using study sessions"
                        ) { screen = Screen.TIMER.name }

                        CardBox(
                            "📝 My Notes",
                            "Create, edit, and save notes"
                        ) { screen = Screen.NOTES.name }

                        CardBox(
                            "📈 Progress",
                            "View scores and answer history"
                        ) { screen = Screen.PROGRESS.name }

                        CardBox(
                            "⚙️ Settings",
                            "Customize app appearance"
                        ) { screen = Screen.SETTINGS.name }

                        Text(
                            "Small progress is still progress. Keep going! 🌟",
                            color = colors.secondary
                        )
                    }

                    Screen.SUBJECTS -> {
                        Heading(
                            "📚 My Subjects",
                            "Choose a subject to start practicing."
                        )

                        listOf("Math", "Science", "English", "Filipino").forEach { name ->
                            CardBox(
                                "${emoji(name)} $name",
                                "Practice questions • Level $level"
                            ) {
                                startQuiz(name)
                            }
                        }

                        customSubjects.forEach { name ->
                            CardBox("📘 $name", "Custom subject") {
                                startQuiz("Mixed")
                            }
                        }

                        OutlinedButton(
                            onClick = { showSubjectDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("＋ Add Custom Subject")
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    level = (level - 1).coerceAtLeast(1)
                                    saveProgress()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Previous Level")
                            }

                            Button(
                                onClick = {
                                    level = (level + 1).coerceAtMost(100)
                                    saveProgress()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Next Level ↑")
                            }
                        }

                        Text("Level $level / 100", color = muted)
                    }

                    Screen.QUIZ -> {
                        val q = questions.getOrNull(questionIndex)

                        Heading(
                            "🧠 $selectedSubject Quiz",
                            "Level $level • Question ${if (q == null) 0 else questionIndex + 1}/${questions.size}"
                        )

                        if (q == null) {
                            CardBox(
                                "No new questions in this set",
                                "Try another subject or move to the next level."
                            ) {}

                            Button(
                                onClick = {
                                    level = (level + 1).coerceAtMost(100)
                                    saveProgress()
                                    startQuiz(selectedSubject)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Try Next Level")
                            }
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = colors.surface
                                )
                            ) {
                                Column(
                                    Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        "${emoji(q.subject)} ${q.subject}",
                                        color = colors.secondary
                                    )

                                    Text(
                                        q.text,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )

                                    q.choices.forEachIndexed { i, choice ->
                                        OutlinedButton(
                                            onClick = {
                                                if (!revealed) {
                                                    selectedAnswer = i
                                                    revealed = true

                                                    val isCorrect = i == q.answer
                                                    answered++
                                                    if (isCorrect) correct++

                                                    answeredIds.add(q.id)
                                                    history.add(
                                                        0,
                                                        ReviewItem(q, i, isCorrect)
                                                    )

                                                    while (history.size > 500) {
                                                        history.removeAt(history.lastIndex)
                                                    }

                                                    saveProgress()
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                when {
                                                    revealed && i == q.answer -> "✓ $choice"
                                                    revealed && i == selectedAnswer -> "✕ $choice"
                                                    else -> choice
                                                }
                                            )
                                        }
                                    }

                                    if (revealed) {
                                        Text(
                                            if (selectedAnswer == q.answer) {
                                                "Correct! 🎉"
                                            } else {
                                                "Review the correct answer."
                                            },
                                            color = colors.secondary,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Text(q.explanation)

                                        Button(
                                            onClick = {
                                                questionIndex++
                                                selectedAnswer = -1
                                                revealed = false

                                                if (questionIndex >= questions.size) {
                                                    screen = Screen.PROGRESS.name
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Next Question →")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Screen.NOTES -> NotesPage(
                        notes = notes,
                        onSave = { title, body ->
                            notes.add(
                                Note(System.currentTimeMillis(), title, body)
                            )
                            prefs.edit()
                                .putString("notes", encodeNotes(notes))
                                .apply()
                        },
                        onEdit = { note, title, body ->
                            val i = notes.indexOfFirst { it.id == note.id }
                            if (i >= 0) {
                                notes[i] = note.copy(title = title, body = body)
                            }
                            prefs.edit()
                                .putString("notes", encodeNotes(notes))
                                .apply()
                        },
                        onDelete = { note ->
                            notes.removeAll { it.id == note.id }
                            prefs.edit()
                                .putString("notes", encodeNotes(notes))
                                .apply()
                        }
                    )

                    Screen.FLASHCARDS -> FlashcardsPage()

                    Screen.TIMER -> TimerPage(prefs)

                    Screen.PR
