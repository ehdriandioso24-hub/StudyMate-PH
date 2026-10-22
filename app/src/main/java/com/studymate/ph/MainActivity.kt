package com.studymate.ph

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

private enum class Screen { HOME, SUBJECTS, QUIZ_MENU, QUIZ, NOTES, FLASHCARDS, TIMER, PROGRESS }

private data class Question(val question: String, val options: List<String>, val answer: Int, val explanation: String)
private data class Flashcard(val front: String, val back: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StudyMateApp(this) }
    }
}

@Composable
fun StudyMateApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("studymate_data", Context.MODE_PRIVATE) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var selectedSubject by remember { mutableStateOf("Math") }
    var notes by remember { mutableStateOf(prefs.getString("notes", "") ?: "") }
    var completedQuizzes by remember { mutableStateOf(prefs.getInt("completed_quizzes", 0)) }
    var bestScore by remember { mutableStateOf(prefs.getInt("best_score", 0)) }
    var customSubjects by remember {
        mutableStateOf(prefs.getString("custom_subjects", "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList())
    }
    val subjects = listOf("Math", "Science", "English", "Filipino") + customSubjects

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFC084FC), secondary = Color(0xFFF472D0),
            background = Color(0xFF080D1D), surface = Color(0xFF111A30)
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (screen) {
                Screen.HOME -> HomeScreen(completedQuizzes, bestScore) { screen = it }
                Screen.SUBJECTS -> SubjectsScreen(subjects, selectedSubject,
                    onSelect = { selectedSubject = it; screen = Screen.QUIZ_MENU },
                    onAddSubject = { name ->
                        val clean = name.trim()
                        if (clean.isNotBlank() && !subjects.contains(clean)) {
                            val updated = customSubjects + clean
                            customSubjects = updated
                            prefs.edit().putString("custom_subjects", updated.joinToString("|")).apply()
                        }
                    }, onBack = { screen = Screen.HOME })
                Screen.QUIZ_MENU -> QuizMenuScreen(selectedSubject, { screen = Screen.QUIZ }) { screen = Screen.SUBJECTS }
                Screen.QUIZ -> QuizScreen(selectedSubject,
                    onFinished = { score, total ->
                        completedQuizzes++
                        bestScore = maxOf(bestScore, score)
                        prefs.edit().putInt("completed_quizzes", completedQuizzes).putInt("best_score", bestScore).apply()
                        screen = Screen.PROGRESS
                    }, onBack = { screen = Screen.QUIZ_MENU })
                Screen.NOTES -> NotesScreen(notes, { value ->
                    notes = value
                    prefs.edit().putString("notes", value).apply()
                }) { screen = Screen.HOME }
                Screen.FLASHCARDS -> FlashcardsScreen(selectedSubject) { screen = Screen.HOME }
                Screen.TIMER -> StudyTimerScreen { screen = Screen.HOME }
                Screen.PROGRESS -> ProgressScreen(completedQuizzes, bestScore) { screen = Screen.HOME }
            }
        }
    }
}

@Composable
fun Header(title: String, onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Text("‹", fontSize = 36.sp, modifier = Modifier.clickable { onBack() }.padding(end = 12.dp))
        }
        Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun HomeScreen(completed: Int, bestScore: Int, navigate: (Screen) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            Spacer(Modifier.height(25.dp))
            Text("👋 Hi, Student!", fontSize = 29.sp, fontWeight = FontWeight.Bold)
            Text("Ready to learn today?", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("📊 Your Progress", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Quizzes completed: $completed")
                    Text("Best score: $bestScore")
                }
            }
            Spacer(Modifier.height(20.dp))
            HomeButton("📚 Subjects", "Math, Science, English, Filipino") { navigate(Screen.SUBJECTS) }
            HomeButton("🧠 Quick Quiz", "Test your knowledge") { navigate(Screen.QUIZ_MENU) }
            HomeButton("📝 My Notes", "Save your notes offline") { navigate(Screen.NOTES) }
            HomeButton("🃏 Flashcards", "Review important concepts") { navigate(Screen.FLASHCARDS) }
            HomeButton("⏱️ Study Timer", "25-minute focus timer") { navigate(Screen.TIMER) }
            HomeButton("🏆 Progress", "See your learning progress") { navigate(Screen.PROGRESS) }
        }
    }
}

@Composable
fun HomeButton(title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onClick() }, shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SubjectsScreen(subjects: List<String>, selectedSubject: String, onSelect: (String) -> Unit, onAddSubject: (String) -> Unit, onBack: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var newSubject by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Header("📚 My Subjects", onBack)
        LazyColumn(Modifier.padding(horizontal = 20.dp)) {
            items(subjects) { subject ->
                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onSelect(subject) }, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(when (subject) { "Math" -> "📐"; "Science" -> "🔬"; "English" -> "📖"; "Filipino" -> "🇵🇭"; else -> "📘" }, fontSize = 25.sp)
                        Spacer(Modifier.width(15.dp))
                        Text(subject, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                OutlinedButton({ showDialog = true }, Modifier.fillMaxWidth()) { Text("＋ Add Custom Subject") }
            }
        }
    }
    if (showDialog) {
        AlertDialog(onDismissRequest = { showDialog = false }, title = { Text("Add Subject") },
            text = { OutlinedTextField(newSubject, { newSubject = it }, label = { Text("Subject name") }, singleLine = true) },
            confirmButton = { TextButton({ onAddSubject(newSubject); newSubject = ""; showDialog = false }) { Text("Add") } },
            dismissButton = { TextButton({ showDialog = false }) { Text("Cancel") } })
    }
}

@Composable
fun QuizMenuScreen(subject: String, onStart: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Header("🧠 Quiz", onBack)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(22.dp)) {
                Text("Subject")
                Text(subject, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("Answer the questions and get your score.")
                Spacer(Modifier.height(20.dp))
                Button(onStart, Modifier.fillMaxWidth()) { Text("▶ Start Quiz") }
            }
        }
    }
}

private fun getQuestions(subject: String): List<Question> = when (subject) {
    "Math" -> listOf(
        Question("What is 12 × 5?", listOf("50", "60", "70", "80"), 1, "12 × 5 = 60."),
        Question("What is 3²?", listOf("6", "8", "9", "12"), 2, "3 × 3 = 9."),
        Question("What is 100 ÷ 4?", listOf("20", "25", "30", "40"), 1, "100 ÷ 4 = 25.")
    )
    "Science" -> listOf(
        Question("Which organ pumps blood?", listOf("Lungs", "Heart", "Brain", "Stomach"), 1, "The heart pumps blood around the body."),
        Question("Which gas do humans need for respiration?", listOf("Oxygen", "Helium", "Carbon dioxide", "Hydrogen"), 0, "Humans need oxygen for respiration."),
        Question("Which planet is called the Red Planet?", listOf("Venus", "Mars", "Jupiter", "Mercury"), 1, "Mars is known as the Red Planet.")
    )
    "English" -> listOf(
        Question("Which word is a noun?", listOf("Run", "Beautiful", "Teacher", "Quickly"), 2, "Teacher is a noun."),
        Question("Choose the correct sentence.", listOf("She are happy.", "She is happy.", "She am happy.", "She be happy."), 1, "The correct sentence is 'She is happy.'"),
        Question("What is the opposite of ancient?", listOf("Old", "Modern", "Historic", "Past"), 1, "The opposite of ancient is modern.")
    )
    "Filipino" -> listOf(
        Question("Alin ang pangngalan?", listOf("Tumakbo", "Masaya", "Guro", "Mabilis"), 2, "Ang guro ay pangngalan."),
        Question("Ano ang kasingkahulugan ng maganda?", listOf("Marumi", "Marikit", "Maliit", "Mabagal"), 1, "Ang marikit ay kasingkahulugan ng maganda."),
        Question("Ano ang kasalungat ng mataas?", listOf("Mababa", "Mahaba", "Malaki", "Malawak"), 0, "Ang kasalungat ng mataas ay mababa.")
    )
    else -> listOf(
        Question("What is a good study habit?", listOf("Take organized notes", "Never review", "Skip practice", "Ignore examples"), 0, "Organized notes can help with review."),
        Question("What can improve learning?", listOf("Practice", "Never studying", "Ignoring lessons", "Skipping review"), 0, "Regular practice can improve learning.")
    )
}

@Composable
fun QuizScreen(subject: String, onFinished: (Int, Int) -> Unit, onBack: () -> Unit) {
    val questions = remember(subject) { getQuestions(subject) }
    var current by remember { mutableStateOf(0) }
    var score by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val question = questions[current]

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Header("🧠 $subject Quiz", onBack)
        Text("Question ${current + 1} of ${questions.size}")
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(question.question, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                question.options.forEachIndexed { index, option ->
                    Button(onClick = { if (selected == null) { selected = index; if (index == question.answer) score++ } }, Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(option)
                    }
                }
                if (selected != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(if (selected == question.answer) "✅ Correct!" else "❌ Incorrect.", fontWeight = FontWeight.Bold)
                    Text(question.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        if (current < questions.lastIndex) { current++; selected = null } else { onFinished(score, questions.size) }
                    }, Modifier.fillMaxWidth()) { Text(if (current < questions.lastIndex) "Next" else "Finish Quiz") }
                }
            }
        }
    }
}

@Composable
fun NotesScreen(notes: String, onNotesChanged: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Header("📝 My Notes", onBack)
        OutlinedTextField(value = notes, onValueChange = onNotesChanged, modifier = Modifier.fillMaxWidth().weight(1f), label = { Text("Write your notes") })
        Spacer(Modifier.height(12.dp))
        Text("Saved automatically on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun FlashcardsScreen(subject: String, onBack: () -> Unit) {
    val cards = remember(subject) { flashcardsFor(subject) }
    var index by remember { mutableStateOf(0) }
    var showBack by remember { mutableStateOf(false) }
    val card = cards[index]
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Header("🃏 Flashcards", onBack)
        Text(subject, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth().weight(1f).clickable { showBack = !showBack }, shape = RoundedCornerShape(24.dp)) {
            Box(Modifier.fillMaxSize().padding(25.dp), contentAlignment = Alignment.Center) {
                Text(if (showBack) card.back else card.front, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Tap the card to flip it", modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { index = if (index == 0) cards.lastIndex else index - 1; showBack = false }, Modifier.weight(1f)) { Text("Previous") }
            Button(onClick = { index = if (index == cards.lastIndex) 0 else index + 1; showBack = false }, Modifier.weight(1f)) { Text("Next") }
        }
    }
}

private fun flashcardsFor(subject: String): List<Flashcard> = when (subject) {
    "Math" -> listOf(Flashcard("12 × 5", "60"), Flashcard("3²", "9"), Flashcard("100 ÷ 4", "25"))
    "Science" -> listOf(Flashcard("Organ that pumps blood", "Heart"), Flashcard("Gas needed for respiration", "Oxygen"), Flashcard("Red Planet", "Mars"))
    "English" -> listOf(Flashcard("Noun example", "Teacher"), Flashcard("Opposite of ancient", "Modern"), Flashcard("Correct form", "She is happy."))
    "Filipino" -> listOf(Flashcard("Pangngalan example", "Guro"), Flashcard("Kasingkahulugan ng maganda", "Marikit"), Flashcard("Kasalungat ng mataas", "Mababa"))
    else -> listOf(Flashcard("Good study habit", "Take organized notes"), Flashcard("What improves learning?", "Practice"))
}

@Composable
fun StudyTimerScreen(onBack: () -> Unit) {
    var secondsLeft by remember { mutableStateOf(25 * 60) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        while (running && secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
        if (secondsLeft == 0) running = false
    }
    val minutes = secondsLeft / 60
    val seconds = secondsLeft % 60
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Header("⏱️ Study Timer", onBack)
        Spacer(Modifier.height(60.dp))
        Text(String.format(Locale.US, "%02d:%02d", minutes, seconds), fontSize = 60.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(25.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button({ running = !running }) { Text(if (running) "Pause" else "Start") }
            OutlinedButton({ running = false; secondsLeft = 25 * 60 }) { Text("Reset") }
        }
        Spacer(Modifier.height(15.dp))
        Text("25-minute focus session", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ProgressScreen(completed: Int, bestScore: Int, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Header("🏆 Progress", onBack)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(22.dp)) {
                Text("Your Learning", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(15.dp))
                Text("Quizzes completed: $completed", fontSize = 18.sp)
                Text("Best score: $bestScore", fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                Text("Keep practicing and reviewing your lessons!", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
