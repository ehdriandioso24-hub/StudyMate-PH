package com.studymate.ph

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    HOME, SUBJECTS, LESSONS, QUIZ, NOTES, FLASHCARDS, TIMER, PROGRESS, PROFILE, ACHIEVEMENTS, SETTINGS, IMPORT
}

private data class Note(val id: Long, val title: String, val body: String)
private data class Question(
    val id: String, val subject: String, val text: String,
    val choices: List<String>, val answer: Int, val explanation: String
)
private data class ReviewItem(val question: Question, val selected: Int, val correct: Boolean)
private data class Flashcard(val subject: String, val front: String, val back: String)
private data class BankItem(val text: String, val choices: List<String>, val answer: Int, val explanation: String)
private data class Lesson(val subject: String, val title: String, val body: String)
private data class Achievement(val title: String, val description: String, val unlocked: Boolean)

private val bg = Color(0xFF070B18)
private val panel = Color(0xFF111A30)
private val purple = Color(0xFFC084FC)
private val pink = Color(0xFFF472D0)
private val muted = Color(0xFFA9B5D0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var loading by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(1400)
                loading = false
            }
            if (loading) {
                LoadingScreen()
            } else {
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
                    Surface(Modifier.fillMaxSize(), color = bg) { StudyMateApp() }
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("📚", style = MaterialTheme.typography.displayLarge)
            Text("StudyMate PH", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = purple)
            Text("Learn smarter. Achieve more.", color = muted)
            CircularProgressIndicator(color = pink)
        }
    }
}

@Composable
private fun StudyMateApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("studymate_data", Context.MODE_PRIVATE) }

    var screen by rememberSaveable { mutableStateOf(Screen.HOME.name) }
    var loggedIn by rememberSaveable { mutableStateOf(prefs.getBoolean("loggedIn", false)) }
    var name by remember { mutableStateOf(prefs.getString("name", "") ?: "") }
    var email by remember { mutableStateOf(prefs.getString("email", "") ?: "") }
    var level by remember { mutableIntStateOf(prefs.getInt("level", 1).coerceIn(1, 100)) }
    var answered by remember { mutableIntStateOf(prefs.getInt("answered", 0)) }
    var correct by remember { mutableIntStateOf(prefs.getInt("correct", 0)) }
    var xp by remember { mutableIntStateOf(prefs.getInt("xp", 0)) }
    var selectedSubject by rememberSaveable { mutableStateOf("Mixed") }
    var search by rememberSaveable { mutableStateOf("") }
    var showSubjectDialog by remember { mutableStateOf(false) }
    var subjectInput by rememberSaveable { mutableStateOf("") }
    var showLogin by rememberSaveable { mutableStateOf(!loggedIn) }
    var profileName by rememberSaveable { mutableStateOf(name) }

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

    fun saveAll() {
        prefs.edit()
            .putBoolean("loggedIn", loggedIn)
            .putString("name", name)
            .putString("email", email)
            .putInt("level", level)
            .putInt("answered", answered)
            .putInt("correct", correct)
            .putInt("xp", xp)
            .putString("answeredIds", encodeStrings(answeredIds))
            .putString("history", encodeHistory(history))
            .putString("customSubjects", encodeStrings(customSubjects))
            .putString("notes", encodeNotes(notes))
            .apply()
    }

    fun login(loginName: String, loginEmail: String) {
        name = loginName.ifBlank { "Learner" }
        email = loginEmail.trim()
        loggedIn = true
        prefs.edit().putBoolean("loggedIn", true).putString("name", name).putString("email", email).apply()
        showLogin = false
        screen = Screen.HOME.name
    }

    fun startQuiz(subject: String) {
        selectedSubject = subject
        questionIndex = 0
        selectedAnswer = -1
        revealed = false
        questions = makeQuestions(subject, level).filter { it.id !in answeredIds }.shuffled().take(20)
        screen = Screen.QUIZ.name
    }

    if (!loggedIn || showLogin) {
        LoginPage(
            initialName = profileName,
            initialEmail = email,
            onLogin = { n, e -> login(n, e) }
        )
        return
    }

    val currentScreen = runCatching { Screen.valueOf(screen) }.getOrDefault(Screen.HOME)

    BackHandler(enabled = currentScreen != Screen.HOME) {
        screen = when (currentScreen) {
            Screen.QUIZ -> Screen.SUBJECTS.name
            Screen.LESSONS -> Screen.SUBJECTS.name
            else -> Screen.HOME.name
        }
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentScreen != Screen.HOME) {
                    OutlinedButton(onClick = {
                        screen = if (currentScreen == Screen.QUIZ) Screen.SUBJECTS.name else Screen.HOME.name
                    }) { Text("← Back") }
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("StudyMate PH", style = MaterialTheme.typography.headlineSmall, color = purple, fontWeight = FontWeight.ExtraBold)
                    Text("Hi, ${name.ifBlank { "Learner" }} 👋", color = muted)
                }
                TextButton(onClick = { screen = Screen.PROFILE.name }) { Text("Profile") }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = panel) {
                NavigationBarItem(currentScreen == Screen.HOME, { screen = Screen.HOME.name }, icon = { Text("🏠") }, label = { Text("Home") })
                NavigationBarItem(currentScreen == Screen.SUBJECTS, { screen = Screen.SUBJECTS.name }, icon = { Text("📚") }, label = { Text("Learn") })
                NavigationBarItem(currentScreen == Screen.NOTES, { screen = Screen.NOTES.name }, icon = { Text("📝") }, label = { Text("Notes") })
                NavigationBarItem(currentScreen == Screen.PROGRESS, { screen = Screen.PROGRESS.name }, icon = { Text("📊") }, label = { Text("Progress") })
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (currentScreen) {
                Screen.HOME -> {
                    CardBox("Welcome back, ${name.ifBlank { "Learner" }}! ✨", "Level $level • $xp XP") {}
                    CardBox("🧠 Quick Quiz", "Practice mixed questions from Math, Science, English, and Filipino") { startQuiz("Mixed") }
                    CardBox("📚 Subjects & Lessons", "Explore lessons and practice by subject") { screen = Screen.SUBJECTS.name }
                    CardBox("🎴 Flashcards", "Review important study facts") { screen = Screen.FLASHCARDS.name }
                    CardBox("🏆 Achievements", "Track your learning milestones") { screen = Screen.ACHIEVEMENTS.name }
                    CardBox("📝 My Notes", "Create and save your own notes") { screen = Screen.NOTES.name }
                    CardBox("⏱️ Study Timer", "Focus during study sessions") { screen = Screen.TIMER.name }
                    CardBox("📥 Import Content", "Add your own lessons or quiz questions") { screen = Screen.IMPORT.name }
                    CardBox("☁️ Cloud Sync", "Firebase-ready sync configuration") {
                        Toast.makeText(context, "Cloud Sync needs your Firebase project configuration.", Toast.LENGTH_LONG).show()
                    }
                }

                Screen.SUBJECTS -> {
                    Heading("📚 Subjects", "Search lessons or choose a subject.")
                    OutlinedTextField(
                        value = search, onValueChange = { search = it },
                        label = { Text("Search subjects / lessons") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    val subjects = (listOf("Math", "Science", "English", "Filipino") + customSubjects)
                        .filter { it.contains(search, true) }
                    for (subject in subjects) {
                        CardBox(
                            title = "${emoji(subject)} $subject",
                            subtitle = "Level $level • Lessons • Quiz",
                            onClick = {
                                selectedSubject = subject
                                search = ""
                                screen = Screen.LESSONS.name
                            }
                        )
                    }
                    OutlinedButton(onClick = { showSubjectDialog = true }, Modifier.fillMaxWidth()) {
                        Text("＋ Add Custom Subject")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { level = (level - 1).coerceAtLeast(1); saveAll() },
                            Modifier.weight(1f)
                        ) { Text("Previous Level") }
                        Button(
                            onClick = { level = (level + 1).coerceAtMost(100); saveAll() },
                            Modifier.weight(1f)
                        ) { Text("Next Level ↑") }
                    }
                }

                Screen.LESSONS -> {
                    val lessons = lessonsFor(selectedSubject).filter {
                        it.title.contains(search, true) || it.body.contains(search, true)
                    }
                    Heading("${emoji(selectedSubject)} $selectedSubject", "Lessons and practice")
                    OutlinedTextField(
                        value = search, onValueChange = { search = it },
                        label = { Text("Search lessons") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    for (lesson in lessons) {
                        CardBox(
                            title = lesson.title,
                            subtitle = lesson.body,
                            onClick = {}
                        )
                    }
                    Button(onClick = { startQuiz(selectedSubject) }, Modifier.fillMaxWidth()) {
                        Text("🧠 Start $selectedSubject Quiz")
                    }
                }

                Screen.QUIZ -> {
                    val q = questions.getOrNull(questionIndex)
                    Heading("🧠 $selectedSubject Quiz", "Level $level • Question ${if (q == null) 0 else questionIndex + 1}/${questions.size}")
                    if (q == null) {
                        CardBox("No new questions", "Try another subject or move to the next level.") {}
                        Button(onClick = { level = (level + 1).coerceAtMost(100); saveAll(); startQuiz(selectedSubject) }, Modifier.fillMaxWidth()) {
                            Text("Try Next Level")
                        }
                    } else {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = panel)
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("${emoji(q.subject)} ${q.subject}", color = pink)
                                Text(q.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                q.choices.forEachIndexed { i, choice ->
                                    OutlinedButton(
                                        onClick = {
                                            if (!revealed) {
                                                selectedAnswer = i
                                                revealed = true
                                                val isCorrect = i == q.answer
                                                answered++
                                                if (isCorrect) { correct++; xp += 10 } else xp += 2
                                                answeredIds.add(q.id)
                                                history.add(0, ReviewItem(q, i, isCorrect))
                                                while (history.size > 500) history.removeAt(history.lastIndex)
                                                if (xp >= level * 100) level = (level + 1).coerceAtMost(100)
                                                saveAll()
                                            }
                                        },
                                        Modifier.fillMaxWidth()
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
                                    Text(if (selectedAnswer == q.answer) "Correct! +10 XP 🎉" else "Keep learning! +2 XP", color = pink, fontWeight = FontWeight.Bold)
                                    Text(q.explanation)
                                    Button(
                                        onClick = {
                                            questionIndex++
                                            selectedAnswer = -1
                                            revealed = false
                                            if (questionIndex >= questions.size) screen = Screen.PROGRESS.name
                                        }, Modifier.fillMaxWidth()
                                    ) { Text("Next Question →") }
                                }
                            }
                        }
                    }
                }

                Screen.NOTES -> NotesPage(
                    notes = notes,
                    onSave = { title, body ->
                        notes.add(Note(System.currentTimeMillis(), title, body)); saveAll()
                    },
                    onEdit = { note, title, body ->
                        val i = notes.indexOfFirst { it.id == note.id }
                        if (i >= 0) notes[i] = note.copy(title = title, body = body)
                        saveAll()
                    },
                    onDelete = { note -> notes.removeAll { it.id == note.id }; saveAll() }
                )

                Screen.FLASHCARDS -> FlashcardsPage()
                Screen.TIMER -> TimerPage(prefs)

                Screen.PROGRESS -> {
                    val accuracy = if (answered == 0) 0 else correct * 100 / answered
                    Heading("📊 Progress Dashboard", "Your learning activity on this device.")
                    CardBox("XP", "$xp XP") {}
                    CardBox("Level", "$level / 100") {}
                    CardBox("Questions Answered", "$answered") {}
                    CardBox("Correct Answers", "$correct") {}
                    CardBox("Accuracy", "$accuracy%") {}
                    LinearProgressIndicator(
                        progress = { (xp % 100) / 100f },
                        Modifier.fillMaxWidth()
                    )
                    Button(onClick = { screen = Screen.ACHIEVEMENTS.name }, Modifier.fillMaxWidth()) { Text("🏆 View Achievements") }
                    Button(onClick = { screen = Screen.PROFILE.name }, Modifier.fillMaxWidth()) { Text("👤 View Profile") }
                }

                Screen.PROFILE -> {
                    ProfilePage(
                        name = profileName,
                        email = email,
                        level = level,
                        xp = xp,
                        onNameChange = { profileName = it },
                        onSave = {
                            name = profileName.trim().ifBlank { "Learner" }
                            saveAll()
                            Toast.makeText(context, "Profile saved.", Toast.LENGTH_SHORT).show()
                        },
                        onLogout = {
                            loggedIn = false
                            prefs.edit().putBoolean("loggedIn", false).apply()
                            showLogin = true
                        }
                    )
                }

                Screen.ACHIEVEMENTS -> {
                    val accuracy = if (answered > 0) {
                        (c
