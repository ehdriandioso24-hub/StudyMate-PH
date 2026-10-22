package com.studymate.ph

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private enum class Screen { HOME, SUBJECTS, QUIZ, NOTES, FLASHCARDS, TIMER, PROGRESS }
private data class Note(val title: String, val body: String)
private data class Question(val text: String, val choices: List<String>, val answer: Int, val explanation: String)

private val bg = Color(0xFF080D1D)
private val panel = Color(0xFF111A30)
private val purple = Color(0xFFC084FC)
private val pink = Color(0xFFF472D0)
private val muted = Color(0xFFA9B5D0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = purple, secondary = pink, background = bg, surface = panel,
                onPrimary = Color(0xFF160D28), onBackground = Color(0xFFF5F3FF), onSurface = Color(0xFFF5F3FF)
            )) {
                Surface(Modifier.fillMaxSize(), color = bg) { StudyMateApp() }
            }
        }
    }
}

@Composable
private fun StudyMateApp() {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var subjectDialog by remember { mutableStateOf(false) }
    var subjectInput by remember { mutableStateOf("") }
    val subjects = remember { mutableStateListOf("Math", "Science", "English", "Filipino") }
    val notes = remember { mutableStateListOf<Note>().apply { addAll(readNotes(context)) } }
    var correct by rememberSaveable { mutableIntStateOf(0) }
    var answered by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = bg,
        topBar = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text("StudyMate PH", style = MaterialTheme.typography.headlineSmall, color = purple, fontWeight = FontWeight.ExtraBold)
                Text("Learn smarter, one day at a time.", color = muted)
            }
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Nav("Home", screen == Screen.HOME) { screen = Screen.HOME }
                Nav("Subjects", screen == Screen.SUBJECTS) { screen = Screen.SUBJECTS }
                Nav("Notes", screen == Screen.NOTES) { screen = Screen.NOTES }
                Nav("Progress", screen == Screen.PROGRESS) { screen = Screen.PROGRESS }
            }
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (screen) {
                Screen.HOME -> {
                    CardBox("Welcome, Learner! ✨", "Ready to learn today? Choose a study tool below.", {})
                    CardBox("📚 My Subjects", "Browse your school subjects") { screen = Screen.SUBJECTS }
                    CardBox("🧠 Quick Quiz", "Answer practice questions") { screen = Screen.QUIZ }
                    CardBox("📝 My Notes", "Write notes saved on this device") { screen = Screen.NOTES }
                    CardBox("🎴 Flashcards", "Review useful study facts") { screen = Screen.FLASHCARDS }
                    CardBox("⏱️ Study Timer", "Focus in short study sessions") { screen = Screen.TIMER }
                    CardBox("📈 Progress", "See your quiz results") { screen = Screen.PROGRESS }
                }
                Screen.SUBJECTS -> {
                    Heading("📚 My Subjects", "Tap a subject to start a quiz.")
                    subjects.forEach { s -> CardBox("📘 $s", "Practice and review $s") { screen = Screen.QUIZ } }
                    Button(onClick = { subjectDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Add Subject") }
                }
                Screen.QUIZ -> QuizPage(
                    onAnswered = { isCorrect -> answered++; if (isCorrect) correct++ }
                )
                Screen.NOTES -> NotesPage(
                    notes,
                    onSave = { title, body -> notes.add(Note(title, body)); writeNotes(context, notes) },
                    onDelete = { note -> notes.remove(note); writeNotes(context, notes) }
                )
                Screen.FLASHCARDS -> FlashcardsPage()
                Screen.TIMER -> TimerPage()
                Screen.PROGRESS -> {
                    val accuracy = if (answered == 0) 0 else correct * 100 / answered
                    Heading("📈 Your Progress", "Totals for this app session.")
                    CardBox("Quiz answers", "$answered answered") {}
                    CardBox("Correct answers", "$correct correct") {}
                    CardBox("Accuracy", "$accuracy%") {}
                    CardBox("Saved notes", "${notes.size} notes on this device") {}
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (subjectDialog) {
        AlertDialog(
            onDismissRequest = { subjectDialog = false },
            title = { Text("Add a subject") },
            text = {
                OutlinedTextField(subjectInput, { subjectInput = it }, label = { Text("Subject name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = subjectInput.trim()
                    if (name.isNotEmpty() && !subjects.contains(name)) subjects.add(name)
                    subjectInput = ""; subjectDialog = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { subjectDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Nav(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick) { Text(if (selected) "● $label" else label, color = if (selected) pink else muted) }
}

@Composable
private fun Heading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = purple, fontWeight = FontWeight.Bold)
        Text(subtitle, color = muted)
    }
}

@Composable
private fun CardBox(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = panel)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = muted)
        }
    }
}

@Composable
private fun QuizPage(onAnswered: (Boolean) -> Unit) {
    val questions = remember {
        listOf(
            Question("What is 8 × 7?", listOf("54", "56", "64", "48"), 1, "8 multiplied by 7 equals 56."),
            Question("Which planet is known as the Red Planet?", listOf("Venus", "Jupiter", "Mars", "Mercury"), 2, "Mars looks reddish because of iron-rich dust."),
            Question("Which word is a noun?", listOf("Quickly", "Beautiful", "Teacher", "Run"), 2, "A teacher is a person, so it is a noun."),
            Question("What is the Filipino word for 'book'?", listOf("Lapis", "Aklat", "Mesa", "Paaralan"), 1, "Aklat means book.")
        )
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    val q = questions[index % questions.size]
    Heading("🧠 Quick Quiz", "Choose the best answer.")
    Text("Question ${index % questions.size + 1} of ${questions.size}", color = muted)
    Card(colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(q.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            q.choices.forEachIndexed { i, choice ->
                OutlinedButton(
                    onClick = { if (!revealed) { selected = i; revealed = true; onAnswered(i == q.answer) } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (revealed && i == q.answer) "✓ $choice" else if (revealed && i == selected) "✕ $choice" else choice) }
            }
            if (revealed) {
                Text(if (selected == q.answer) "Correct! 🎉 ${q.explanation}" else "Not quite. ${q.explanation}", color = purple)
                Button(onClick = { index = (index + 1) % questions.size; selected = -1; revealed = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("Next Question")
                }
            }
        }
    }
}

@Composable
private fun NotesPage(notes: List<Note>, onSave: (String, String) -> Unit, onDelete: (Note) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    Heading("📝 My Notes", "Notes are saved locally on this device.")
    OutlinedTextField(title, { title = it }, label = { Text("Note title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(body, { body = it }, label = { Text("Write your notes here") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
    Button(onClick = {
        if (title.isNotBlank() || body.isNotBlank()) {
            onSave(title.trim().ifBlank { "Untitled note" }, body.trim())
            title = ""; body = ""
        }
    }, modifier = Modifier.fillMaxWidth()) { Text("Save Note") }
    if (notes.isEmpty()) Text("No notes yet. Add your first note above.", color = muted)
    notes.toList().asReversed().forEach { note ->
        Card(colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text(note.title, color = purple, fontWeight = FontWeight.Bold)
                Text(note.body)
                TextButton(onClick = { onDelete(note) }) { Text("Delete note", color = pink) }
            }
        }
    }
}

@Composable
private fun FlashcardsPage() {
    val cards = remember {
        listOf(
            "Math" to "The interior angles of a triangle add up to 180°.",
            "Science" to "Plants use sunlight during photosynthesis to make food.",
            "English" to "A noun names a person, place, thing, or idea.",
            "Filipino" to "Ang pang-uri ay naglalarawan sa pangngalan o panghalip."
        )
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var answerVisible by rememberSaveable { mutableStateOf(false) }
    val item = cards[index % cards.size]
    Heading("🎴 Flashcards", "Review one fact at a time.")
    Card(colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("${index + 1} / ${cards.size} • ${item.first}", color = pink, fontWeight = FontWeight.Bold)
            Text(if (answerVisible) item.second else "Think about a key fact for ${item.first}.", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { answerVisible = !answerVisible }, modifier = Modifier.fillMaxWidth()) {
                Text(if (answerVisible) "Hide Answer" else "Show Answer")
            }
            OutlinedButton(onClick = { index = (index + 1) % cards.size; answerVisible = false }, modifier = Modifier.fillMaxWidth()) {
                Text("Next Flashcard")
            }
        }
    }
}

@Composable
private fun TimerPage() {
    var duration by rememberSaveable { mutableIntStateOf(25) }
    var seconds by rememberSaveable { mutableIntStateOf(25 * 60) }
    var running by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(running, seconds) {
        if (running && seconds > 0) { delay(1000); seconds-- }
        else if (seconds == 0) running = false
    }
    Heading("⏱️ Study Timer", "Choose a duration and focus.")
    Card(colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(String.format("%02d:%02d", seconds / 60, seconds % 60), style = MaterialTheme.typography.displayMedium, color = purple, fontWeight = FontWeight.ExtraBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5, 15, 25).forEach { mins ->
                    OutlinedButton(onClick = { duration = mins; seconds = mins * 60; running = false }) { Text("${mins}m") }
                }
            }
            Button(onClick = { if (seconds == 0) seconds = duration * 60; running = !running }, modifier = Modifier.fillMaxWidth()) {
                Text(if (running) "Pause" else "Start")
            }
            OutlinedButton(onClick = { running = false; seconds = duration * 60 }, modifier = Modifier.fillMaxWidth()) { Text("Reset") }
            if (seconds == 0) Text("Session complete! Great work 🎉", color = pink)
        }
    }
}

private fun readNotes(context: Context): List<Note> {
    val prefs = context.getSharedPreferences("studymate_notes", Context.MODE_PRIVATE)
    val titles = prefs.getString("titles", "") ?: ""
    val bodies = prefs.getString("bodies", "") ?: ""
    if (titles.isEmpty()) return emptyList()
    val titleList = titles.split("\n")
    val bodyList = bodies.split("\n")
    return titleList.mapIndexed { i, title -> Note(title, bodyList.getOrElse(i) { "" }) }
}

private fun writeNotes(context: Context, notes: List<Note>) {
    context.getSharedPreferences("studymate_notes", Context.MODE_PRIVATE).edit()
        .putString("titles", notes.joinToString("\n") { it.title.replace("\n", " ") })
        .putString("bodies", notes.joinToString("\n") { it.body.replace("\n", " ") })
        .apply()
}
