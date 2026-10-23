
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

                    Screen.PROGRESS -> {
                        val accuracy = if (answered == 0) {
                            0
                        } else {
                            correct * 100 / answered
                        }

                        Heading(
                            "📈 Your Progress",
                            "Your results are saved on this device."
                        )

                        CardBox("Questions answered", "$answered") {}
                        CardBox("Correct answers", "$correct") {}
                        CardBox("Accuracy", "$accuracy%") {}
                        CardBox("Current level", "$level / 100") {}

                        Button(
                            onClick = { screen = Screen.REVIEW.name },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📚 Review Answered Questions")
                        }

                        if (history.isEmpty()) {
                            Text("No review history yet.", color = muted)
                        }
                    }

                    Screen.REVIEW -> {
                        Heading(
                            "📚 Answer Review",
                            "Review questions you have already answered."
                        )

                        if (history.isEmpty()) {
                            Text("No answered questions yet.", color = muted)
                        }

                        history.forEach { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = colors.surface
                                )
                            ) {
                                Column(
                                    Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        "${if (item.correct) "✓ Correct" else "✕ Incorrect"} • ${item.question.subject}",
                                        color = colors.secondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        item.question.text,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Your answer: ${
                                            item.question.choices.getOrElse(item.selected) { "—" }
                                        }"
                                    )
                                    Text(
                                        "Correct answer: ${
                                            item.question.choices.getOrElse(item.question.answer) { "—" }
                                        }"
                                    )
                                    Text(item.question.explanation, color = muted)
                                }
                            }
                        }
                    }

                    Screen.SETTINGS -> {
                        Heading(
                            "⚙️ Settings",
                            "Customize your app."
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { lightTheme = !lightTheme }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Light Theme", modifier = Modifier.weight(1f))
                            Switch(
                                checked = lightTheme,
                                onCheckedChange = { lightTheme = it }
                            )
                        }

                        Button(
                            onClick = {
                                prefs.edit()
                                    .putBoolean("lightTheme", lightTheme)
                                    .apply()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Settings")
                        }

                        Text(
                            "Notes, quiz scores, and review history are saved locally.",
                            color = muted
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showSubjectDialog) {
        AlertDialog(
            onDismissRequest = { showSubjectDialog = false },
            title = { Text("Add a subject") },
            text = {
                OutlinedTextField(
                    value = subjectInput,
                    onValueChange = { subjectInput = it },
                    label = { Text("Subject name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = subjectInput.trim()
                        if (name.isNotEmpty() && name !in customSubjects) {
                            customSubjects.add(name)
                            prefs.edit()
                                .putString("customSubjects", encodeStrings(customSubjects))
                                .apply()
                        }
                        subjectInput = ""
                        showSubjectDialog = false
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubjectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun Heading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(subtitle, color = muted)
    }
}

@Composable
private fun CardBox(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(subtitle, color = muted)
        }
    }
}

@Composable
private fun NotesPage(
    notes: List<Note>,
    onSave: (String, String) -> Unit,
    onEdit: (Note, String, String) -> Unit,
    onDelete: (Note) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }

    Heading("📝 My Notes", "Notes are saved on this device.")
    Text(
        "Small steps every day lead to big results. 🌟",
        color = MaterialTheme.colorScheme.secondary
    )

    OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("Note title") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    OutlinedTextField(
        value = body,
        onValueChange = { body = it },
        label = { Text("Write your notes here") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3
    )

    Button(
        onClick = {
            if (title.isNotBlank() || body.isNotBlank()) {
                val id = editingId
                if (id == null) {
                    onSave(title.trim().ifBlank { "Untitled note" }, body.trim())
                } else {
                    notes.firstOrNull { it.id == id }?.let {
                        onEdit(it, title.trim().ifBlank { "Untitled note" }, body.trim())
                    }
                }
                title = ""
                body = ""
                editingId = null
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (editingId == null) "Save Note" else "Update Note")
    }

    if (editingId != null) {
        TextButton(
            onClick = {
                editingId = null
                title = ""
                body = ""
            }
        ) {
            Text("Cancel Edit")
        }
    }

    if (notes.isEmpty()) {
        Text("No notes yet. Add your first note above.", color = muted)
    }

    notes.toList().asReversed().forEach { note ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    note.title,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(note.body)

                Row {
                    TextButton(
                        onClick = {
                            editingId = note.id
                            title = note.title
                            body = note.body
                        }
                    ) {
                        Text("Edit")
                    }

                    TextButton(
                        onClick = {
                            onDelete(note)
                            if (editingId == note.id) {
                                editingId = null
                                title = ""
                                body = ""
                            }
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun FlashcardsPage() {
    val cards = remember { makeFlashcards() }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var showAnswer by rememberSaveable { mutableStateOf(false) }
    val card = cards[index % cards.size]

    Heading(
        "🎴 Flashcards",
        "Try answering before revealing the answer."
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "${index + 1} / ${cards.size} • ${emoji(card.subject)} ${card.subject}",
                color = MaterialTheme.colorScheme.secondary
            )

            Text(
                if (showAnswer) "ANSWER" else "QUESTION",
                color = muted
            )

            Text(
                if (showAnswer) card.back else card.front,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = { showAnswer = !showAnswer },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (showAnswer) "Hide Answer" else "Show Answer")
            }

            OutlinedButton(
                onClick = {
                    index = (index + 1) % cards.size
                    showAnswer = false
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Next Flashcard →")
            }
        }
    }
}

@Composable
private fun TimerPage(prefs: android.content.SharedPreferences) {
    var duration by remember {
        mutableIntStateOf(prefs.getInt("timerDuration", 25).coerceIn(1, 180))
    }
    var seconds by remember {
        mutableIntStateOf(prefs.getInt("timerRemaining", 1500).coerceAtLeast(0))
    }
    var running by rememberSaveable { mutableStateOf(false) }
    var customMinutes by rememberSaveable { mutableStateOf("25") }
    var note by rememberSaveable {
        mutableStateOf(prefs.getString("timerNote", "") ?: "")
    }

    val presets = remember {
        mutableStateListOf<Int>().apply {
            addAll(
                decodeStrings(prefs.getString("timerPresets", "[]") ?: "[]")
                    .mapNotNull { it.toIntOrNull() }
                    .filter { it in 1..180 }
            )
        }
    }

    LaunchedEffect(running) {
        while (running && seconds > 0) {
            delay(1000)
            seconds--
            prefs.edit().putInt("timerRemaining", seconds).apply()
        }
        if (seconds == 0) running = false
    }

    fun choose(minutes: Int) {
        duration = minutes.coerceIn(1, 180)
        seconds = duration * 60
        running = false
        prefs.edit()
            .putInt("timerDuration", duration)
            .putInt("timerRemaining", seconds)
            .apply()
    }

    Heading("⏱️ Study Timer", "Choose a duration and focus.")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                String.format("%02d:%02d", seconds / 60, seconds % 60),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(5, 15, 25, 45, 60).forEach { minutes ->
                    OutlinedButton(onClick = { choose(minutes) }) {
                        Text("${minutes}m")
                    }
                }
            }

            OutlinedTextField(
                value = customMinutes,
                onValueChange = { customMinutes = it.filter(Char::isDigit).take(3) },
                label = { Text("Custom minutes (1–180)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    customMinutes.toIntOrNull()
                        ?.takeIf { it in 1..180 }
                        ?.let { choose(it) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set Custom Time")
            }

            Button(
                onClick = {
                    if (seconds == 0) seconds = duration * 60
                    running = !running
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (running) "Pause" else "Start")
            }

            OutlinedButton(
                onClick = {
                    running = false
                    choose(duration)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset")
            }

            if (seconds == 0) {
                Text("Session complete! Great work 🎉", color = MaterialTheme.colorScheme.secondary)
            }

            OutlinedTextField(
                value = note,
                onValueChange = {
                    note = it
                    prefs.edit().putString("timerNote", it).apply()
                },
                label = { Text("Study session note") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Button(
                onClick = {
                    if (duration !in presets) {
                        presets.add(duration)
                        prefs.edit()
                            .putString("timerPresets", encodeStrings(presets.map { it.toString() }))
                            .apply()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Current Preset")
            }

            presets.toList().forEach { minutes ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { choose(minutes) }) {
                        Text("Use ${minutes}m preset")
                    }
                    TextButton(
                        onClick = {
                            presets.remove(minutes)
                            prefs.edit()
                                .putString("timerPresets", encodeStrings(presets.map { it.toString() }))
                                .apply()
                        }
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

private fun emoji(subject: String): String = when (subject) {
    "Math" -> "📐"
    "Science" -> "🧪"
    "English" -> "📖"
    "Filipino" -> "🇵🇭"
    else -> "🧠"
}

private fun makeQuestions(subject: String, level: Int): List<Question> {
    val subjects = if (subject == "Mixed") {
        listOf("Math", "Science", "English", "Filipino")
    } else {
        listOf(subject)
    }

    val result = mutableListOf<Question>()

    subjects.forEach { name ->
        if (name == "Math") {
            for (n in 1..40) {
                val a = level + n + 2
                val b = (n % 9) + 2
                val operation = when ((n + level) % 4) {
                    0 -> "+"
                    1 -> "-"
                    2 -> "×"
                    else -> "÷"
                }

                val x: Int
                val y: Int
                val answer: Int
                val text: String

                when (operation) {
                    "+" -> {
                        x = a
                        y = b + level / 5
                        answer = x + y
                        text = "$x + $y = ?"
                    }
                    "-" -> {
                        x = a + b
                        y = b
                        answer = x - y
                        text = "$x - $y = ?"
                    }
                    "×" -> {
                        x = (level % 12) + 2
                        y = b
                        answer = x * y
                        text = "$x × $y = ?"
                    }
                    else -> {
                        x = b * ((n % 10) + 2)
                        y = b
                        answer = x / y
                        text = "$x ÷ $y = ?"
                    }
                }

                val options = linkedSetOf(
                    answer + 1,
                    answer - 1,
                    answer + 2,
                    answer
                ).toList().shuffled()

                result.add(
                    Question(
                        "Math-L$level-Q$n",
                        "Math",
                        text,
                        options.map { it.toString() },
                        options.indexOf(answer),
                        "The correct answer is $answer."
                    )
                )
            }
        } else {
            questionBank(name).forEachIndexed { i, item ->
                result.add(
                    Question(
                        "$name-L$level-Q${i + 1}",
                        name,
                        "Level $level: ${item.text}",
                        item.choices,
                        item.answer,
                        item.explanation
                    )
                )
            }
        }
    }

    return result.distinctBy { it.id }
}

private fun questionBank(subject: String): List<BankItem> = when (subject) {
    "Science" -> listOf(
        BankItem("Which planet is known as the Red Planet?", listOf("Venus", "Jupiter", "Mars", "Mercury"), 2, "Mars looks reddish because of iron-rich dust."),
        BankItem("Which organ pumps blood around the body?", listOf("Lungs", "Heart", "Brain", "Stomach"), 1, "The heart pumps blood."),
        BankItem("What do plants use to make food?", listOf("Moonlight", "Sunlight", "Plastic", "Sound"), 1, "Plants use light energy during photosynthesis."),
        BankItem("What is H₂O commonly called?", listOf("Salt", "Hydrogen", "Water", "Oxygen"), 2, "H₂O is water."),
        BankItem("Which gas do humans need to breathe?", listOf("Oxygen", "Helium", "Neon", "Methane"), 0, "Humans need oxygen for cellular respiration."),
        BankItem("What force pulls objects toward Earth?", listOf("Friction", "Gravity", "Sound", "Light"), 1, "Gravity attracts objects with mass."),
        BankItem("Which plant part usually absorbs water?", listOf("Flower", "Leaf", "Root", "Fruit"), 2, "Roots absorb water and minerals."),
        BankItem("At what temperature does water freeze in Celsius?", listOf("0°C", "10°C", "50°C", "100°C"), 0, "Water freezes at 0°C under standard pressure.")
    )

    "English" -> listOf(
        BankItem("Which word is a noun?", listOf("Quickly", "Beautiful", "Teacher", "Run"), 2, "A noun names a person, place, thing, or idea."),
        BankItem("Which word is a verb?", listOf("Happiness", "Jump", "Blue", "Soft"), 1, "A verb expresses an action or state."),
        BankItem("What is the plural of 'child'?", listOf("Childs", "Childes", "Children", "Childrens"), 2, "The plural is children."),
        BankItem("Which punctuation ends a direct question?", listOf("Period", "Comma", "Question mark", "Colon"), 2, "A direct question ends with a question mark."),
        BankItem("What is the opposite of 'ancient'?", listOf("Old", "Modern", "Historic", "Past"), 1, "Modern means current or recent."),
        BankItem("Which word is an adjective?", listOf("Happily", "Bright", "Speak", "Yesterday"), 1, "An adjective describes a noun or pronoun."),
        BankItem("Choose the correct sentence.", listOf("She are ready.", "She is ready.", "She am ready.", "She be ready."), 1, "Use 'is' with she."),
        BankItem("What is a synonym for 'begin'?", listOf("Finish", "Start", "Close", "Stop"), 1, "Start and begin have similar meanings.")
    )

    "Filipino" -> listOf(
        BankItem("Ano ang Filipino ng 'book'?", listOf("Lapis", "Aklat", "Mesa", "Paaralan"), 1, "Ang aklat ay book sa Filipino."),
        BankItem("Alin ang pangngalan?", listOf("Mabilis", "Tumakbo", "Guro", "Maganda"), 2, "Ang guro ay ngalan ng tao."),
        BankItem("Anong salita ang naglalarawan sa pangngalan?", listOf("Pandiwa", "Pang-uri", "Panghalip", "Pang-ukol"), 1, "Ang pang-uri ay naglalarawan."),
        BankItem("Alin ang pandiwa?", listOf("Bahay", "Masaya", "Sumulat", "Pula"), 2, "Ang sumulat ay nagsasaad ng kilos."),
        BankItem("Ano ang kasalungat ng 'malawak'?", listOf("Malaki", "Makitid", "Mahaba", "Mataas"), 1, "Ang makitid ay kasalungat ng malawak."),
        BankItem("Alin ang panghalip?", listOf("Siya", "Mesa", "Takbo", "Asul"), 0, "Ang siya ay panghalip panao."),
        BankItem("Ano ang kasingkahulugan ng 'masaya'?", listOf("Malungkot", "Maligaya", "Galit", "Pagod"), 1, "Ang maligaya ay kasingkahulugan ng masaya."),
        BankItem("Anong bantas ang ginagamit sa tanong?", listOf("Tuldok", "Kuwit", "Tandang pananong", "Tutuldok"), 2, "Gumagamit ng tandang pananong sa tanong.")
    )

    else -> emptyList()
}

private fun makeFlashcards(): List<Flashcard> = listOf(
    Flashcard("Math", "What is the sum of the interior angles of a triangle?", "180 degrees."),
    Flashcard("Math", "What is 9 × 8?", "72."),
    Flashcard("Math", "How do you find the perimeter of a square?", "Multiply one side by four."),
    Flashcard("Math", "What is half of 100?", "50."),
    Flashcard("Science", "What process do plants use to make food?", "Photosynthesis."),
    Flashcard("Science", "Which planet is called the Red Planet?", "Mars."),
    Flashcard("Science", "What is the chemical formula for water?", "H₂O."),
    Flashcard("Science", "Which organ pumps blood?", "The heart."),
    Flashcard("English", "What does a noun name?", "A person, place, thing, or idea."),
    Flashcard("English", "What is the past tense of 'go'?", "Went."),
    Flashcard("English", "What is a synonym?", "A word with a similar meaning."),
    Flashcard("English", "What punctuation ends a question?", "A question mark."),
    Flashcard("Filipino", "Ano ang pang-uri?", "Salitang naglalarawan sa pangngalan o panghalip."),
    Flashcard("Filipino", "Ano ang pangngalan?", "Salitang tumutukoy sa tao, bagay, hayop, lugar, o ideya."),
    Flashcard("Filipino", "Ano ang pandiwa?", "Salitang nagsasaad ng kilos o galaw."),
    Flashcard("Filipino", "Ano ang kasingkahulugan ng 'mabilis'?", "Matulin.")
)

private fun encodeStrings(values: List<String>): String {
    val array = JSONArray()
    values.forEach { array.put(it) }
    return array.toString()
}

private fun decodeStrings(raw: String): List<String> = runCatching {
    val array = JSONArray(raw)
    (0 until array.length()).map { array.getString(it) }
}.getOrDefault(emptyList())

private fun encodeNotes(notes: List<Note>): String {
    val array = JSONArray()
    notes.forEach {
        array.put(
            JSONObject()
                .put("id", it.id)
                .put("title", it.title)
                .put("body", it.body)
        )
    }
    return array.toString()
}

private fun decodeNotes(raw: String): List<Note> = runCatching {
    val array = JSONArray(raw)
    (0 until array.length()).map {
        val obj = array.getJSONObject(it)
        Note(
            obj.optLong("id"),
            obj.optString("title"),
            obj.optString("body")
        )
    }
}.getOrDefault(emptyList())

private fun encodeQuestion(question: Question): JSONObject = JSONObject()
    .put("id", question.id)
    .put("subject", question.subject)
    .put("text", question.text)
    .put("choices", encodeStrings(question.choices))
    .put("answer", question.answer)
    .put("explanation", question.explanation)

private fun decodeQuestion(obj: JSONObject): Question = Question(
    obj.optString("id"),
    obj.optString("subject"),
    obj.optString("text"),
    decodeStrings(obj.optString("choices", "[]")),
    obj.optInt("answer"),
    obj.optString("explanation")
)

private fun encodeHistory(history: List<ReviewItem>): String {
    val array = JSONArray()
    history.forEach {
        array.put(
            JSONObject()
                .put("question", encodeQuestion(it.question))
                .put("selected", it.selected)
                .put("correct", it.correct)
        )
    }
    return array.toString()
}

private fun decodeHistory(raw: String): List<ReviewItem> = runCatching {
    val array = JSONArray(raw)
    (0 until array.length()).map {
        val obj = array.getJSONObject(it)
        ReviewItem(
            decodeQuestion(obj.getJSONObject("question")),
            obj.optInt("selected"),
            obj.optBoolean("correct")
        )
    }
}.getOrDefault(emptyList())
