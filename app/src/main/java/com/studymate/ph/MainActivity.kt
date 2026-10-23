
package com.studymate.ph

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlin.random.Random

private enum class Screen {
    HOME, SUBJECTS, SEARCH, QUIZ, LEVEL_UP, LEVEL_COMPLETE, GAME_OVER, NOTES, FLASHCARDS, TIMER, PROGRESS, REVIEW, PROFILE, SETTINGS
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
    val correct: Boolean,
    val level: Int = 1
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
    val subjectLevels = remember {
        mutableStateMapOf<String, Int>().apply {
            (listOf("Mixed") + studySubjects).forEach { subject ->
                put(
                    subject,
                    prefs.getInt("level_$subject", prefs.getInt("level", 1)).coerceIn(1, 100)
                )
            }
        }
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
    var tickingSound by remember { mutableStateOf(prefs.getBoolean("tickingSound", true)) }
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 55) }
    DisposableEffect(Unit) { onDispose { toneGenerator.release() } }

    var loading by rememberSaveable { mutableStateOf(true) }
    var loggedIn by remember {
        mutableStateOf(prefs.getBoolean("loggedIn", false))
    }
    var studentName by remember {
        mutableStateOf(prefs.getString("studentName", "") ?: "")
    }
    var studentEmail by remember {
        mutableStateOf(prefs.getString("studentEmail", "") ?: "")
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var importMessage by rememberSaveable { mutableStateOf("") }

    val importedQuestions = remember {
        mutableStateListOf<Question>().apply {
            addAll(decodeQuestions(prefs.getString("importedQuestions", "[]") ?: "[]"))
        }
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
    var levelUpFrom by rememberSaveable { mutableIntStateOf(1) }
    var selectedAnswer by rememberSaveable { mutableIntStateOf(-1) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    var secondsLeft by rememberSaveable { mutableIntStateOf(30) }
    var lives by rememberSaveable { mutableIntStateOf(3) }
    var streak by rememberSaveable { mutableIntStateOf(0) }
    var quizCorrect by rememberSaveable { mutableIntStateOf(0) }
    var quizScore by rememberSaveable { mutableIntStateOf(0) }
    var used5050 by rememberSaveable { mutableStateOf(false) }
    var usedTimeFreeze by rememberSaveable { mutableStateOf(false) }
    var usedSkip by rememberSaveable { mutableStateOf(false) }
    var hiddenChoices by remember { mutableStateOf(setOf<Int>()) }
    var quizResultSaved by rememberSaveable { mutableStateOf(false) }
    var quizStartedAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    val localLeaderboard = remember {
        mutableStateListOf<String>().apply {
            addAll((prefs.getString("localLeaderboard", "") ?: "").split("\n").filter { it.isNotBlank() })
        }
    }
    var showSubjectDialog by remember { mutableStateOf(false) }
    var subjectInput by rememberSaveable { mutableStateOf("") }

    fun saveProgress() {
        subjectLevels[selectedSubject] = level
        prefs.edit()
            .putInt("level", level)
            .putInt("level_$selectedSubject", level)
            .putInt("answered", answered)
            .putInt("correct", correct)
            .putString("answeredIds", encodeStrings(answeredIds))
            .putString("history", encodeHistory(history))
            .putString("customSubjects", encodeStrings(customSubjects))
            .putString("importedQuestions", encodeQuestions(importedQuestions))
            .putString("studentName", studentName)
            .putString("studentEmail", studentEmail)
            .putBoolean("loggedIn", loggedIn)
            .apply()
    }

    fun startQuiz(subject: String) {
        selectedSubject = subject
        level = subjectLevels.getOrPut(subject) {
            prefs.getInt("level_$subject", prefs.getInt("level", 1)).coerceIn(1, 100)
        }
        questionIndex = 0
        selectedAnswer = -1
        revealed = false
        secondsLeft = 30
        lives = 3
        streak = 0
        quizCorrect = 0
        quizScore = 0
        used5050 = false
        usedTimeFreeze = false
        usedSkip = false
        hiddenChoices = emptySet()
        quizResultSaved = false
        quizStartedAt = System.currentTimeMillis()

        val builtIn = makeQuestions(subject, level)
        val imported = importedQuestions.filter {
            subject == "Mixed" || it.subject.equals(subject, ignoreCase = true)
        }
        val pool = (builtIn + imported).distinctBy { it.id }
        questions = pool.filter { it.id !in answeredIds }.shuffled().take(20)

        screen = Screen.QUIZ.name
    }

    fun retryCurrentQuiz() {
        questionIndex = 0
        selectedAnswer = -1
        revealed = false
        secondsLeft = 30
        lives = 3
        streak = 0
        quizCorrect = 0
        quizScore = 0
        used5050 = false
        usedTimeFreeze = false
        usedSkip = false
        hiddenChoices = emptySet()
        quizResultSaved = false
        quizStartedAt = System.currentTimeMillis()
        questions = questions.shuffled()
        screen = Screen.QUIZ.name
    }

    fun saveQuizResult() {
        if (quizResultSaved) return
        quizResultSaved = true
        val elapsedSeconds = ((System.currentTimeMillis() - quizStartedAt) / 1000L).toInt().coerceAtLeast(1)
        val entry = "${quizScore.coerceAtLeast(0)}|$quizCorrect|$elapsedSeconds|${studentName.ifBlank { "Student" }}"
        localLeaderboard.add(entry)
        val sorted = localLeaderboard.sortedByDescending { it.substringBefore('|').toIntOrNull() ?: 0 }.take(10)
        localLeaderboard.clear()
        localLeaderboard.addAll(sorted)
        prefs.edit().putString("localLeaderboard", localLeaderboard.joinToString("\n")).apply()
    }

    fun recordAnswer(index: Int, timedOut: Boolean = false) {
        val q = questions.getOrNull(questionIndex) ?: return
        if (revealed) return
        selectedAnswer = if (timedOut) -1 else index
        revealed = true
        val isCorrect = !timedOut && index == q.answer
        answered++
        if (isCorrect) {
            correct++
            quizCorrect++
            streak++
            val speedBonus = (secondsLeft * 2).coerceAtLeast(0)
            val comboBonus = if (streak >= 5) 100 else if (streak >= 3) 50 else 0
            quizScore += 100 + speedBonus + comboBonus
        } else {
            lives = (lives - 1).coerceAtLeast(0)
            streak = 0
        }
        answeredIds.add(q.id)
        history.add(0, ReviewItem(q, selectedAnswer, isCorrect, level))
        while (history.size > 500) history.removeAt(history.lastIndex)
        saveProgress()
        if (lives <= 0) {
            saveQuizResult()
            screen = Screen.GAME_OVER.name
        }
    }

    fun finishQuiz() {
        saveQuizResult()
        val total = questions.size.coerceAtLeast(1)
        val passNeeded = kotlin.math.ceil(total * 0.75).toInt()
        if (quizCorrect >= passNeeded) {
            if (level < 100) {
                levelUpFrom = level
                level = (level + 1).coerceAtMost(100)
                saveProgress()
                screen = Screen.LEVEL_UP.name
            } else {
                saveProgress()
                screen = Screen.LEVEL_COMPLETE.name
            }
        } else {
            screen = Screen.GAME_OVER.name
        }
    }

    val currentScreen = runCatching {
        Screen.valueOf(screen)
    }.getOrDefault(Screen.HOME)

    LaunchedEffect(currentScreen, questionIndex, revealed, secondsLeft) {
        if (currentScreen == Screen.QUIZ && questions.getOrNull(questionIndex) != null && !revealed) {
            if (secondsLeft > 0) {
                delay(1000)
                secondsLeft = (secondsLeft - 1).coerceAtLeast(0)
            } else {
                recordAnswer(-1, timedOut = true)
            }
        }
    }

    LaunchedEffect(currentScreen, questionIndex, revealed, secondsLeft, tickingSound) {
        if (currentScreen == Screen.QUIZ && !revealed && tickingSound && secondsLeft in 1..10) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
        }
    }

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

    if (loading) {
        LaunchedEffect(Unit) {
            delay(1400)
            loading = false
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("📚", style = MaterialTheme.typography.displayLarge)
                Text(
                    "StudyMate PH",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.primary,
                    fontWeight = FontWeight.ExtraBold
                )
                Text("Loading your learning space…", color = muted)
                CircularProgressIndicator(color = colors.primary)
            }
        }
        return
    }

    if (!loggedIn) {
        LoginPage(
            name = studentName,
            email = studentEmail,
            onLogin = { name, email ->
                studentName = name
                studentEmail = email
                loggedIn = true
                prefs.edit()
                    .putBoolean("loggedIn", true)
                    .putString("studentName", name)
                    .putString("studentEmail", email)
                    .apply()
            }
        )
        return
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            importMessage = "Import cancelled."
        } else {
            val raw = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()

            if (raw.isNullOrBlank()) {
                importMessage = "Could not read the selected file."
            } else {
                val imported = parseImportFile(raw)
                if (imported.first.isEmpty() && imported.second.isEmpty()) {
                    importMessage = "No valid questions or lessons were found."
                } else {
                    importedQuestions.addAll(imported.first)
                    notes.addAll(imported.second)
                    prefs.edit()
                        .putString("importedQuestions", encodeQuestions(importedQuestions))
                        .putString("notes", encodeNotes(notes))
                        .apply()
                    importMessage =
                        "Imported ${imported.first.size} question(s) and ${imported.second.size} lesson(s)."
                }
            }
        }
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
                        selected = currentScreen == Screen.SEARCH,
                        onClick = { screen = Screen.SEARCH.name },
                        icon = { Text("🔍") },
                        label = { Text("Search") }
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.PROGRESS,
                        onClick = { screen = Screen.PROGRESS.name },
                        icon = { Text("📊") },
                        label = { Text("Progress") }
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.PROFILE,
                        onClick = { screen = Screen.PROFILE.name },
                        icon = { Text("👤") },
                        label = { Text("Profile") }
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
                            "Welcome, ${studentName.ifBlank { "Learner" }}! ✨",
                            "Level $level • ${achievementsFor(answered, correct).size} achievements unlocked"
                        ) { screen = Screen.PROFILE.name }

                        CardBox(
                            "🧠 Quick Quiz",
                            "Mixed questions from all 7 subjects"
                        ) { startQuiz("Mixed") }

                        CardBox(
                            "📚 My Subjects",
                            "Math, Science, English, Filipino, AP, ICT, PE & Health"
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
                            "📈 Progress Dashboard",
                            "Scores, accuracy, level, achievements"
                        ) { screen = Screen.PROGRESS.name }

                        CardBox(
                            "🔍 Search Lessons & Subjects",
                            "Find a subject, lesson, or imported question"
                        ) { screen = Screen.SEARCH.name }

                        CardBox(
                            "👤 Student Profile",
                            "Manage your local StudyMate account"
                        ) { screen = Screen.PROFILE.name }

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

                        studySubjects.forEach { name ->
                            CardBox(
                                "${emoji(name)} $name",
                                "Practice questions • Level ${subjectLevels[name] ?: prefs.getInt("level_$name", level)}"
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

                        Text(
                            "🎯 Current Level: ${subjectLevels[selectedSubject] ?: level} / 100",
                            color = muted,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Complete the available questions to unlock the next level automatically.",
                            color = muted
                        )
                    }

                    Screen.SEARCH -> {
                        Heading(
                            "🔍 Search",
                            "Search subjects, lessons, and imported questions."
                        )

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Search") },
                            placeholder = { Text("Example: science, fractions, history") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        val query = searchQuery.trim()
                        val subjectResults = studySubjects.filter {
                            query.isBlank() || it.contains(query, ignoreCase = true)
                        }

                        subjectResults.forEach { name ->
                            CardBox(
                                "${emoji(name)} $name",
                                "Open ${name.lowercase()} practice"
                            ) { startQuiz(name) }
                        }

                        importedQuestions
                            .filter {
                                query.isBlank() ||
                                    it.text.contains(query, ignoreCase = true) ||
                                    it.subject.contains(query, ignoreCase = true)
                            }
                            .take(30)
                            .forEach { q ->
                                CardBox(
                                    "📝 ${q.subject}",
                                    q.text
                                ) { startQuiz(q.subject) }
                            }

                        if (query.isNotBlank() && subjectResults.isEmpty() &&
                            importedQuestions.none {
                                it.text.contains(query, ignoreCase = true) ||
                                    it.subject.contains(query, ignoreCase = true)
                            }
                        ) {
                            Text("No matching subjects or imported questions.", color = muted)
                        }
                    }

                    Screen.PROFILE -> {
                        ProfilePage(
                            name = studentName,
                            email = studentEmail,
                            level = level,
                            answered = answered,
                            correct = correct,
                            onSave = { name, email ->
                                studentName = name
                                studentEmail = email
                                prefs.edit()
                                    .putString("studentName", name)
                                    .putString("studentEmail", email)
                                    .apply()
                            },
                            onLogout = {
                                loggedIn = false
                                prefs.edit().putBoolean("loggedIn", false).apply()
                                screen = Screen.HOME.name
                            }
                        )
                    }

                    Screen.QUIZ -> {
                        val q = questions.getOrNull(questionIndex)

                        Heading(
                            "🔥 $selectedSubject Thrill Quiz",
                            "Level $level • Question ${if (q == null) 0 else questionIndex + 1}/${questions.size} • Pass: 75%"
                        )

                        if (q != null) {
                            CardBox(
                                "${if (secondsLeft <= 10) "🚨 RED ZONE" else "⏱️ Time left"} • ${secondsLeft}s",
                                "❤️ Lives: ${"♥ ".repeat(lives).trim()}${if (lives == 0) "—" else ""}   🔥 Streak: $streak   ⭐ Score: $quizScore"
                            ) {}
                            if (secondsLeft <= 10) {
                                Text("Hurry! Red Zone!", color = Color(0xFFFF5252), fontWeight = FontWeight.ExtraBold)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = {
                                    if (!used5050 && !revealed) {
                                        used5050 = true
                                        val wrong = q.choices.indices.filter { it != q.answer }.shuffled().take(2)
                                        hiddenChoices = wrong.toSet()
                                    }
                                }, enabled = !used5050 && !revealed, modifier = Modifier.weight(1f)) { Text("50:50") }
                                OutlinedButton(onClick = {
                                    if (!usedTimeFreeze && !revealed) { usedTimeFreeze = true; secondsLeft += 15 }
                                }, enabled = !usedTimeFreeze && !revealed, modifier = Modifier.weight(1f)) { Text("+15 sec") }
                                OutlinedButton(onClick = {
                                    if (!usedSkip && !revealed) {
                                        usedSkip = true
                                        if (questionIndex >= questions.lastIndex) finishQuiz() else {
                                            questionIndex++; selectedAnswer = -1; revealed = false; secondsLeft = 30; hiddenChoices = emptySet()
                                        }
                                    }
                                }, enabled = !usedSkip && !revealed, modifier = Modifier.weight(1f)) { Text("Skip") }
                            }
                        }

                        if (q == null) {
                            CardBox(
                                "No new questions in this set",
                                "There are no unanswered questions available for this subject at this level. Choose another subject or return to Subjects."
                            ) {}

                            Button(
                                onClick = { screen = Screen.SUBJECTS.name },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Back to Subjects")
                            }
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (secondsLeft <= 10 && !revealed) Color(0xFF5B1018) else colors.surface
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
                                        if (i !in hiddenChoices || revealed) {
                                            OutlinedButton(
                                                onClick = { recordAnswer(i) },
                                                enabled = !revealed,
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
                                    }

                                    if (revealed) {
                                        Text(
                                            if (selectedAnswer == q.answer) {
                                                if (streak >= 5) "🔥 COMBO FIRE! 5+ streak! + bonus points!" else if (streak >= 3) "🔥 COMBO! 3+ streak! Bonus points!" else "Correct! 🎉 + speed bonus"
                                            } else if (selectedAnswer == -1) {
                                                "⏰ Time's up! Correct answer shown below."
                                            } else {
                                                "Incorrect — one life lost. Review the correct answer."
                                            },
                                            color = colors.secondary,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Text(q.explanation)

                                        Button(
                                            onClick = {
                                                if (lives <= 0) {
                                                    saveQuizResult()
                                                    screen = Screen.GAME_OVER.name
                                                } else if (questionIndex >= questions.lastIndex) {
                                                    finishQuiz()
                                                } else {
                                                    questionIndex++
                                                    selectedAnswer = -1
                                                    revealed = false
                                                    secondsLeft = 30
                                                    hiddenChoices = emptySet()
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                if (questionIndex >= questions.lastIndex) {
                                                    if (level >= 100) "Finish Quiz • Level 100 🏆" else "Finish Quiz →"
                                                } else {
                                                    "Next Question →"
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Screen.LEVEL_UP -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text("🎉", style = MaterialTheme.typography.displayLarge)
                                Text(
                                    "LEVEL UP!",
                                    style = MaterialTheme.typography.displaySmall,
                                    color = colors.primary,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    "Level $levelUpFrom completed!",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Welcome to Level $level!",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = colors.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Your new level has been saved automatically. Keep learning and reach Level 100!",
                                    color = muted
                                )
                                CardBox("💾 Progress saved", "You can close the app and return to Level $level.") {}
                                Button(
                                    onClick = { startQuiz(selectedSubject) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Continue to Level $level →")
                                }
                                OutlinedButton(
                                    onClick = { screen = Screen.SUBJECTS.name },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Choose Another Subject")
                                }
                            }
                        }
                    }

                    Screen.LEVEL_COMPLETE -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text("🏆", style = MaterialTheme.typography.displayLarge)
                                Text(
                                    "LEVEL 100 COMPLETE!",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = colors.primary,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    "Congratulations! You have reached the highest level.",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Your progress is saved. You can keep practicing or review your answers.",
                                    color = muted
                                )
                                Button(
                                    onClick = { screen = Screen.SUBJECTS.name },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Back to Subjects")
                                }
                                OutlinedButton(
                                    onClick = { screen = Screen.PROGRESS.name },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("View Progress")
                                }
                            }
                        }
                    }

                    Screen.GAME_OVER -> {
                        Heading("💥 Quiz Over", "Thrill Quiz results")
                        CardBox(
                            "${if (lives <= 0) "Out of lives!" else "Passing score not reached."}",
                            "Correct answers: $quizCorrect / ${questions.size}\nScore: $quizScore\nPassing target: ${kotlin.math.ceil(questions.size.coerceAtLeast(1) * 0.75).toInt()} correct answers (75%).\nYour progress and review history are saved."
                        ) {}
                        Button(onClick = { retryCurrentQuiz() }, modifier = Modifier.fillMaxWidth()) { Text("Try Again") }
                        OutlinedButton(onClick = { screen = Screen.PROGRESS.name }, modifier = Modifier.fillMaxWidth()) { Text("View Progress & Leaderboard") }
                        OutlinedButton(onClick = { screen = Screen.SUBJECTS.name }, modifier = Modifier.fillMaxWidth()) { Text("Choose Another Subject") }
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
                            "📊 Progress Dashboard",
                            "Your learning activity and achievements."
                        )

                        CardBox("Questions answered", "$answered") {}
                        CardBox("Correct answers", "$correct") {}
                        CardBox("Accuracy", "$accuracy%") {}
                        CardBox("Current level", "$level / 100") {}
                        CardBox("🏆 Achievements", achievementsFor(answered, correct).joinToString(" • "))
                        CardBox("🏁 Local Top 10 Leaderboard", if (localLeaderboard.isEmpty()) "Complete a Thrill Quiz to appear here." else localLeaderboard.mapIndexed { index, entry ->
                            val parts = entry.split('|')
                            "${index + 1}. ${parts.getOrElse(3) { "Student" }} — ${parts.getOrElse(0) { "0" }} pts (${parts.getOrElse(1) { "0" }} correct, ${parts.getOrElse(2) { "0" }} sec)"
                        }.joinToString("\n")) {}

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
                            "Piliin ang subject at uri ng sagot."
                        )

                        if (history.isEmpty()) {
                            Text("Wala pang nasagot na tanong.", color = muted, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            val availableSubjects = history.map { it.question.subject }.distinct().sorted()
                            var reviewSubject by rememberSaveable { mutableStateOf("All") }
                            var reviewResult by rememberSaveable { mutableStateOf("All") }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("All" to "Lahat").forEach { (key, label) ->
                                    FilterChip(
                                        selected = reviewSubject == key,
                                        onClick = { reviewSubject = key },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                                availableSubjects.forEach { subjectName ->
                                    FilterChip(
                                        selected = reviewSubject == subjectName,
                                        onClick = { reviewSubject = subjectName },
                                        label = { Text(subjectName, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = reviewResult == "All",
                                    onClick = { reviewResult = "All" },
                                    label = { Text("Lahat (${history.size})", style = MaterialTheme.typography.labelSmall) }
                                )
                                FilterChip(
                                    selected = reviewResult == "Correct",
                                    onClick = { reviewResult = "Correct" },
                                    label = { Text("✓ Correct (${history.count { it.correct }})", style = MaterialTheme.typography.labelSmall) }
                                )
                                FilterChip(
                                    selected = reviewResult == "Wrong",
                                    onClick = { reviewResult = "Wrong" },
                                    label = { Text("✕ Wrong (${history.count { !it.correct }})", style = MaterialTheme.typography.labelSmall) }
                                )
                            }

                            val filteredHistory = history.filter { item ->
                                (reviewSubject == "All" || item.question.subject == reviewSubject) &&
                                    (reviewResult == "All" ||
                                        (reviewResult == "Correct" && item.correct) ||
                                        (reviewResult == "Wrong" && !item.correct))
                            }

                            if (filteredHistory.isEmpty()) {
                                Text("Walang sagot sa filter na ito.", color = muted, style = MaterialTheme.typography.bodySmall)
                            } else {
                                filteredHistory.forEach { item ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = colors.surface)
                                    ) {
                                        Column(
                                            Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                "${item.question.subject} • Level ${item.level} • ${if (item.correct) "✓ Correct" else "✕ Wrong"}",
                                                color = if (item.correct) Color(0xFF2E7D32) else Color(0xFFC62828),
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                            Text(item.question.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                "Sagot mo: ${item.question.choices.getOrElse(item.selected) { "—" }}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                "Tamang sagot: ${item.question.choices.getOrElse(item.question.answer) { "—" }}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(item.question.explanation, color = muted, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
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

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Red Zone ticking sound")
                                Text("Beeps during the final 10 seconds of a question.", color = muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = tickingSound,
                                onCheckedChange = {
                                    tickingSound = it
                                    prefs.edit().putBoolean("tickingSound", it).apply()
                                }
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

                        Button(
                            onClick = {
                                launcher.launch(arrayOf("application/json", "text/plain"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🗂️ Import Lessons / Questions")
                        }

                        if (importMessage.isNotBlank()) {
                            Text(importMessage, color = colors.secondary)
                        }

                        CardBox(
                            "☁️ Online Database / Sync",
                            "Firebase connection is not configured yet. This version keeps data locally and is ready for Firebase integration."
                        ) {}

                        Text(
                            "Notes, quiz scores, profile, imported content, and review history are saved on this device.",
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
private fun LoginPage(
    name: String,
    email: String,
    onLogin: (String, String) -> Unit
) {
    var localName by rememberSaveable { mutableStateOf(name) }
    var localEmail by rememberSaveable { mutableStateOf(email) }

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("🔐", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Welcome to StudyMate PH",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text("Create or sign in to your local student account.", color = muted)

                OutlinedTextField(
                    value = localName,
                    onValueChange = { localName = it },
                    label = { Text("Student name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = localEmail,
                    onValueChange = { localEmail = it },
                    label = { Text("Email (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = {
                        val cleanName = localName.trim().ifBlank { "Student" }
                        onLogin(cleanName, localEmail.trim())
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue")
                }

                Text(
                    "Note: this is a local account. True online login requires Firebase Authentication.",
                    color = muted
                )
            }
        }
    }
}

@Composable
private fun ProfilePage(
    name: String,
    email: String,
    level: Int,
    answered: Int,
    correct: Int,
    onSave: (String, String) -> Unit,
    onLogout: () -> Unit
) {
    var localName by rememberSaveable(name) { mutableStateOf(name) }
    var localEmail by rememberSaveable(email) { mutableStateOf(email) }

    Heading("👤 Student Profile", "Your StudyMate account and learning summary.")

    OutlinedTextField(
        value = localName,
        onValueChange = { localName = it },
        label = { Text("Student name") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    OutlinedTextField(
        value = localEmail,
        onValueChange = { localEmail = it },
        label = { Text("Email") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Button(
        onClick = { onSave(localName.trim().ifBlank { "Student" }, localEmail.trim()) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Save Profile")
    }

    CardBox("📚 Level", "$level / 100") {}
    CardBox("📝 Questions answered", "$answered") {}
    CardBox("🎯 Correct answers", "$correct") {}
    CardBox("🏆 Achievements", achievementsFor(answered, correct).joinToString(" • "))

    OutlinedButton(
        onClick = onLogout,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Log out")
    }
}

private fun achievementsFor(answered: Int, correct: Int): List<String> {
    val result = mutableListOf<String>()
    if (answered >= 1) result += "🌱 First Step"
    if (answered >= 10) result += "📚 10 Questions"
    if (answered >= 50) result += "🔥 50 Questions"
    if (correct >= 10) result += "🎯 Sharp Mind"
    if (correct >= 50) result += "🏆 Quiz Master"
    if (answered >= 100) result += "🚀 Study Champion"
    return result.ifEmpty { listOf("🌱 Start your first quiz") }
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
private fun CardBox(title: String, subtitle: String, onClick: () -> Unit = {}) {
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
    "Araling Panlipunan" -> "🌏"
    "ICT" -> "💻"
    "PE & Health" -> "🏃"
    else -> "🧠"
}

private val studySubjects = listOf(
    "Math", "Science", "English", "Filipino",
    "Araling Panlipunan", "ICT", "PE & Health"
)

private fun makeQuestions(subject: String, level: Int): List<Question> {
    val safeLevel = level.coerceIn(1, 100)
    val subjects = if (subject == "Mixed") studySubjects else listOf(subject)
    val result = mutableListOf<Question>()

    subjects.forEach { name ->
        questionBank(name, safeLevel).forEachIndexed { i, item ->
            result += Question(
                "$name-L$safeLevel-Q${i + 1}",
                name,
                "Level $safeLevel: ${item.text}",
                item.choices,
                item.answer,
                item.explanation
            )
        }
    }

    return result.distinctBy { it.id }
}

/*
 * Generates a fresh question set for every level. The formulas and topics
 * deliberately scale with the level so Level 1 is approachable while
 * Levels 50–100 require more reasoning. IDs include the level, so old
 * saved progress/history remains compatible with the existing app.
 */
private fun questionBank(subject: String, level: Int): List<BankItem> {
    val base = when (subject) {
        "Math" -> mathQuestions(level)
        "Science" -> scienceQuestions(level)
        "English" -> englishQuestions(level)
        "Filipino" -> filipinoQuestions(level)
        "Araling Panlipunan" -> aralingPanlipunanQuestions(level)
        "ICT" -> ictQuestions(level)
        "PE & Health" -> peHealthQuestions(level)
        else -> emptyList()
    }
    return (base + extraQuestions(subject, level)).distinctBy { it.text }
}

private fun intChoices(answer: Int, seed: Int): List<String> {
    val candidates = linkedSetOf(
        answer,
        answer + 1 + (seed % 3),
        answer - 1 - (seed % 2),
        answer + 3 + (seed % 4),
        answer - 3 - (seed % 3)
    ).filter { it >= 0 }

    return candidates.take(4).shuffled(Random(seed))
        .let { values ->
            if (answer in values) values else listOf(answer) + values
        }
        .take(4)
        .map(Int::toString)
}

private fun textChoices(correct: String, alternatives: List<String>, seed: Int): List<String> =
    (listOf(correct) + alternatives).distinct().take(4).shuffled(Random(seed))

private fun mathQuestions(level: Int): List<BankItem> {
    val difficulty = when {
        level <= 20 -> 1
        level <= 40 -> 2
        level <= 60 -> 3
        level <= 80 -> 4
        else -> 5
    }

    val a = level + 3
    val b = (level % 9) + 2
    val c = (level % 7) + 3

    val q1Answer = a + b * difficulty
    val q2Answer = a * b + c
    val q3Answer = (a + b) * difficulty
    val q4Answer = a * difficulty - b
    val q5Answer = (level + 10) * (difficulty + 1)
    val q6Answer = level * 2 + 5
    val q7Answer = (level % 10) + difficulty * 5
    val q8Answer = level + difficulty * 7

    return listOf(
        BankItem("$a + ($b × $difficulty) = ?", intChoices(q1Answer, level), intChoices(q1Answer, level).indexOf(q1Answer.toString()), "Multiply before adding."),
        BankItem("$a × $b + $c = ?", intChoices(q2Answer, level + 1), intChoices(q2Answer, level + 1).indexOf(q2Answer.toString()), "Do the multiplication first, then add."),
        BankItem("($a + $b) × $difficulty = ?", intChoices(q3Answer, level + 2), intChoices(q3Answer, level + 2).indexOf(q3Answer.toString()), "Add inside the parentheses before multiplying."),
        BankItem("$a × $difficulty − $b = ?", intChoices(q4Answer, level + 3), intChoices(q4Answer, level + 3).indexOf(q4Answer.toString()), "Multiply first, then subtract."),
        BankItem("A pattern starts at ${level + 10} and is multiplied by ${difficulty + 1}. What is the next value?", intChoices(q5Answer, level + 4), intChoices(q5Answer, level + 4).indexOf(q5Answer.toString()), "Multiply the starting value by the stated factor."),
        BankItem("If x + $level = ${q6Answer + level}, what is x?", intChoices(q6Answer, level + 5), intChoices(q6Answer, level + 5).indexOf(q6Answer.toString()), "Subtract $level from both sides."),
        BankItem("What is ${q7Answer} × 2 + ${difficulty}?", intChoices(q7Answer * 2 + difficulty, level + 6), intChoices(q7Answer * 2 + difficulty, level + 6).indexOf((q7Answer * 2 + difficulty).toString()), "Follow multiplication before addition."),
        BankItem("If a number is $difficulty more than $q8Answer, what is the number?", intChoices(q8Answer + difficulty, level + 7), intChoices(q8Answer + difficulty, level + 7).indexOf((q8Answer + difficulty).toString()), "Add the difference to the original value.")
    ).map { item ->
        val answerText = item.choices[item.answer]
        item.copy(
            choices = item.choices,
            answer = item.choices.indexOf(answerText)
        )
    }
}

private fun scienceQuestions(level: Int): List<BankItem> {
    val temp = 20 + level
    val band = when {
        level <= 20 -> "life"
        level <= 40 -> "matter"
        level <= 60 -> "physics"
        level <= 80 -> "earth"
        else -> "space"
    }

    val q = when (band) {
        "life" -> listOf(
            "Which body system transports oxygen and nutrients around the body?" to "Circulatory system",
            "What do green plants use to capture light energy?" to "Chlorophyll",
            "Which cell structure contains most genetic material?" to "Nucleus",
            "What is the main job of roots?" to "Absorb water and minerals"
        )
        "matter" -> listOf(
            "What state of matter has a fixed volume but takes the shape of its container?" to "Liquid",
            "What happens to most substances when they are heated?" to "They expand",
            "What is the pH of a neutral solution?" to "7",
            "What is the smallest unit of an element that keeps its chemical identity?" to "Atom"
        )
        "physics" -> listOf(
            "What is the SI unit of force?" to "Newton",
            "If distance stays constant and time decreases, what happens to average speed?" to "It increases",
            "What type of energy is stored in a stretched rubber band?" to "Elastic potential energy",
            "What force opposes motion between surfaces?" to "Friction"
        )
        "earth" -> listOf(
            "Which layer of Earth is the thin outer solid layer?" to "Crust",
            "What process turns liquid water into water vapor?" to "Evaporation",
            "What instrument measures air pressure?" to "Barometer",
            "What causes day and night?" to "Earth's rotation"
        )
        else -> listOf(
            "Which planet is famous for its prominent ring system?" to "Saturn",
            "What force keeps planets in orbit around the Sun?" to "Gravity",
            "What is a light-year a measure of?" to "Distance",
            "What galaxy contains our Solar System?" to "Milky Way"
        )
    }

    val extra = listOf(
        "At level $level, a sample temperature is ${temp}°C. Which unit is used for this temperature?",
        "Which statement best describes scientific evidence?",
        "Which tool is most useful for measuring a length accurately?",
        "Why do scientists repeat measurements?"
    )

    return q.mapIndexed { i, (question, correct) ->
        val alternatives = when (correct) {
            "Circulatory system" -> listOf("Digestive system", "Skeletal system", "Respiratory system")
            "Chlorophyll" -> listOf("Hemoglobin", "Keratin", "Calcium")
            "Nucleus" -> listOf("Cell wall", "Ribosome", "Vacuole")
            "Absorb water and minerals" -> listOf("Make seeds", "Produce sound", "Store sunlight")
            "Liquid" -> listOf("Solid", "Gas", "Plasma")
            "They expand" -> listOf("They disappear", "They freeze", "They lose all mass")
            "7" -> listOf("0", "5", "14")
            "Atom" -> listOf("Organ", "Cell", "Tissue")
            "Newton" -> listOf("Joule", "Watt", "Pascal")
            "It increases" -> listOf("It decreases", "It becomes zero", "It stays exactly the same")
            "Elastic potential energy" -> listOf("Sound energy", "Nuclear energy", "Light energy")
            "Friction" -> listOf("Gravity", "Magnetism", "Buoyancy")
            "Crust" -> listOf("Inner core", "Outer core", "Mantle")
            "Evaporation" -> listOf("Condensation", "Freezing", "Melting")
            "Barometer" -> listOf("Thermometer", "Ammeter", "Compass")
            "Earth's rotation" -> listOf("Earth's orbit", "The Moon's phases", "Ocean tides only")
            "Saturn" -> listOf("Mars", "Mercury", "Venus")
            "Gravity" -> listOf("Friction", "Sound", "Electricity")
            "Distance" -> listOf("Time", "Mass", "Temperature")
            "Milky Way" -> listOf("Andromeda", "Sombrero", "Whirlpool")
            else -> listOf("Only opinion", "A random guess", "An unrelated claim")
        }
        BankItem(
            question,
            textChoices(correct, alternatives, level + i),
            textChoices(correct, alternatives, level + i).indexOf(correct),
            "The correct answer is $correct."
        )
    } + extra.mapIndexed { i, question ->
        val correct = when (i) {
            0 -> "°C"
            1 -> "A conclusion supported by observations or measurements"
            2 -> "Ruler"
            else -> "To improve reliability and reduce random error"
        }
        val alternatives = when (i) {
            0 -> listOf("kg", "m", "L")
            1 -> listOf("A personal preference", "A rumor", "A guess with no evidence")
            2 -> listOf("Spoon", "Clock", "Pillow")
            else -> listOf("To make results less useful", "To avoid recording data", "To guarantee every result is identical")
        }
        val choices = textChoices(correct, alternatives, level + 20 + i)
        BankItem(question, choices, choices.indexOf(correct), "The best answer is $correct.")
    }
}

private fun englishQuestions(level: Int): List<BankItem> {
    val n = level + 2
    val items = if (level <= 25) listOf(
        "Which word is a noun?" to "student",
        "Which word is a verb?" to "write",
        "Which word is an adjective?" to "careful",
        "What is the plural of 'mouse'?" to "mice",
        "What is the opposite of 'expand'?" to "contract",
        "Which punctuation mark ends a direct question?" to "?",
        "What is a synonym for 'happy'?" to "glad",
        "Choose the correct sentence." to "She is ready."
    ) else if (level <= 50) listOf(
        "Which sentence uses the past tense correctly?" to "They visited the museum.",
        "Which word is an adverb?" to "quickly",
        "Which sentence has correct subject-verb agreement?" to "The students are studying.",
        "What is the comparative form of 'good'?" to "better",
        "Which word is a conjunction?" to "because",
        "What does a pronoun replace?" to "A noun or noun phrase",
        "Which sentence uses an apostrophe correctly?" to "Maria's book is new.",
        "What is the main purpose of a topic sentence?" to "State the paragraph's main idea"
    ) else if (level <= 75) listOf(
        "Which sentence is in the passive voice?" to "The project was completed by the team.",
        "What is a metaphor?" to "A direct comparison without like or as",
        "Which transition shows contrast?" to "however",
        "What is an inference?" to "A conclusion based on evidence",
        "Which sentence avoids a misplaced modifier?" to "Walking home, Ana saw the sunset.",
        "What is the purpose of a thesis statement?" to "Present the main claim",
        "Which word is closest in meaning to 'concise'?" to "brief",
        "What is an independent clause?" to "A clause that can stand as a sentence"
    ) else listOf(
        "Which revision is most concise?" to "The plan failed because it lacked funding.",
        "What is an author's tone?" to "The attitude conveyed by the writing",
        "Which appeal relies on evidence and reasoning?" to "logos",
        "What is a counterclaim?" to "An opposing argument",
        "Which sentence best demonstrates parallel structure?" to "She likes reading, writing, and drawing.",
        "What is the purpose of citing a source?" to "Give credit and support a claim",
        "Which word best describes language with multiple meanings depending on context?" to "ambiguous",
        "What is the strongest thesis?" to "A specific, arguable claim supported by reasons"
    )

    return items.mapIndexed { i, (question, correct) ->
        val alternatives = listOf(
            "table", "run", "bright", "dogs",
            "increase", "!", "sad", "They is ready.",
            "They will visit the museum.", "slowly", "The student are studying.",
            "gooder", "and", "An adjective", "Maria book is new.",
            "Give a random detail", "The team completed the project.", "A comparison using like or as",
            "therefore", "A prediction with no basis", "Add a decorative sentence",
            "A phrase only", "long", "A clause that cannot stand alone",
            "The plan failed because it was not successful due to a lack of funding.",
            "The topic of the writing", "pathos", "A supporting detail", "She likes to read, writing, and drawing.",
            "Make the text longer", "confusing"
        ).filter { it != correct }
        val choices = textChoices(correct, alternatives, level + i)
        val suffix = if (level > 1) " (Level $level practice)" else ""
        BankItem("$question$suffix", choices, choices.indexOf(correct), "The best answer is $correct.")
    }
}

private fun filipinoQuestions(level: Int): List<BankItem> {
    val items = if (level <= 25) listOf(
        "Alin ang pangngalan?" to "guro",
        "Alin ang pandiwa?" to "sumulat",
        "Alin ang pang-uri?" to "maganda",
        "Ano ang kasingkahulugan ng 'masaya'?" to "maligaya",
        "Ano ang kasalungat ng 'mataas'?" to "mababa",
        "Alin ang panghalip panao?" to "siya",
        "Anong bantas ang ginagamit sa tanong?" to "tandang pananong",
        "Ano ang Filipino ng 'book'?" to "aklat"
    ) else if (level <= 50) listOf(
        "Alin ang pang-abay?" to "mabilis",
        "Alin ang pang-ukol?" to "sa",
        "Ano ang simuno sa 'Si Ana ay nagbabasa.'?" to "Si Ana",
        "Ano ang panaguri sa 'Si Ana ay nagbabasa.'?" to "nagbabasa",
        "Ano ang kasingkahulugan ng 'matapang'?" to "magiting",
        "Ano ang kasalungat ng 'malawak'?" to "makitid",
        "Alin ang wastong pangungusap?" to "Nag-aaral si Ben araw-araw.",
        "Ano ang tawag sa salitang nagsasaad ng kilos?" to "pandiwa"
    ) else if (level <= 75) listOf(
        "Ano ang tayutay na naghahambing gamit ang 'parang' o 'tulad'?" to "Pagtutulad",
        "Ano ang pangunahing diwa ng isang teksto?" to "Pinakamahalagang kaisipan",
        "Ano ang layunin ng isang talata?" to "Maglahad ng magkakaugnay na ideya",
        "Alin ang halimbawa ng pangatnig?" to "dahil",
        "Ano ang denotatibong kahulugan?" to "Literal na kahulugan",
        "Ano ang konotatibong kahulugan?" to "Dagdag o pahiwatig na kahulugan",
        "Ano ang paksa ng pangungusap?" to "Pinag-uusapan",
        "Ano ang pandiwang naganap na?" to "Perpektibo"
    ) else listOf(
        "Ano ang tesis sa isang argumentatibong sanaysay?" to "Pangunahing paninindigan o pahayag",
        "Ano ang kontra-argumento?" to "Punto ng kabilang panig",
        "Ano ang kredibilidad ng sanggunian?" to "Pagiging mapagkakatiwalaan ng source",
        "Ano ang lohikal na pangangatwiran?" to "Paggamit ng wastong batayan at ugnayan ng ideya",
        "Ano ang bias?" to "Pagkiling na maaaring makaapekto sa paghatol",
        "Ano ang sintesis?" to "Pag-uugnay ng mahahalagang ideya mula sa mga sanggunian",
        "Ano ang retorikal na tanong?" to "Tanong na hindi nangangailangan ng direktang sagot",
        "Ano ang kohesyon sa teksto?" to "Pagkakaugnay ng mga salita at ideya"
    )

    return items.mapIndexed { i, (question, correct) ->
        val alternatives = listOf(
            "mesa", "tumakbo", "mabilis", "malungkot", "mababa",
            "ako", "tuldok", "lapis", "at", "kay Ben", "araw-araw",
            "pang-uri", "Pagtutulad", "Maliit na detalye", "Magbigay ng hiwa-hiwalay na ideya",
            "ngunit", "Kahulugang teknikal lamang", "Kahulugang walang pahiwatig",
            "Kumilos", "Impektibo", "Paksa", "Pahayag na walang batayan"
        ).filter { it != correct }
        val choices = textChoices(correct, alternatives, level + i)
        BankItem("$question (Antas $level)", choices, choices.indexOf(correct), "Ang tamang sagot ay $correct.")
    }
}

private fun aralingPanlipunanQuestions(level: Int): List<BankItem> {
    val items = when {
        level <= 25 -> listOf(
            "Ano ang kabisera ng Pilipinas?" to "Maynila",
            "Ano ang pinakamalaking pulo sa Pilipinas?" to "Luzon",
            "Ano ang tawag sa pag-aaral ng nakaraan?" to "Kasaysayan",
            "Alin ang likas na yaman?" to "Ginto",
            "Ano ang pangunahing yunit ng pamahalaan sa Pilipinas?" to "Barangay",
            "Ano ang tawag sa anyong tubig na napaliligiran ng lupa?" to "Lawa",
            "Ano ang tawag sa mga taong naninirahan sa isang bansa?" to "Mamamayan",
            "Ano ang salapi ng Pilipinas?" to "Piso"
        )
        level <= 50 -> listOf(
            "Ano ang tawag sa sangay ng pamahalaan na gumagawa ng batas?" to "Lehislatibo",
            "Ano ang pangunahing layunin ng buwis?" to "Pondohan ang serbisyong pampubliko",
            "Ano ang ekonomiks?" to "Pag-aaral ng paggamit ng limitadong yaman",
            "Ano ang demokrasya?" to "Pamamahalang nakabatay sa pakikilahok ng mamamayan",
            "Ano ang heograpiya?" to "Pag-aaral ng lugar, tao, at kapaligiran",
            "Ano ang migrasyon?" to "Paglipat ng tirahan ng tao",
            "Ano ang suplay?" to "Dami ng produktong handang ipagbili",
            "Ano ang demand?" to "Dami ng produktong nais at kayang bilhin"
        )
        level <= 75 -> listOf(
            "Ano ang tawag sa kasunduang nagtatapos sa isang digmaan?" to "Kasunduang pangkapayapaan",
            "Bakit mahalaga ang Saligang Batas?" to "Itinatakda nito ang pangunahing tuntunin ng pamahalaan",
            "Ano ang soberanya?" to "Pinakamataas na kapangyarihan ng isang estado",
            "Ano ang kolonyalismo?" to "Paghahari ng isang makapangyarihang bansa sa ibang lupain",
            "Ano ang nasyonalismo?" to "Pagpapahalaga at pagkakaisa para sa sariling bansa",
            "Ano ang urbanisasyon?" to "Pagdami at paglawak ng populasyon sa mga lungsod",
            "Ano ang implasyon?" to "Pangkalahatang pagtaas ng presyo ng bilihin",
            "Ano ang opportunity cost?" to "Halaga ng pinakamagandang alternatibong isinuko"
        )
        else -> listOf(
            "Ano ang checks and balances?" to "Paglilimita at pagbabantay ng mga sangay sa isa't isa",
            "Ano ang human rights?" to "Mga batayang karapatang taglay ng bawat tao",
            "Ano ang sustainable development?" to "Pag-unlad na isinasaalang-alang ang pangmatagalang pangangailangan",
            "Ano ang fiscal policy?" to "Pamamahala sa buwis at paggasta ng pamahalaan",
            "Ano ang monetary policy?" to "Pamamahala sa salapi at interest rates",
            "Ano ang globalization?" to "Lumalim na ugnayan ng mga bansa sa kalakalan at impormasyon",
            "Ano ang civic participation?" to "Pakikilahok ng mamamayan sa buhay panlipunan at pampamahalaan",
            "Ano ang primary source sa kasaysayan?" to "Orihinal na batis mula sa panahong pinag-aaralan"
        )
    }

    return items.mapIndexed { i, (question, correct) ->
        val alternatives = listOf(
            "Batas ng kalikasan", "Lungsod", "Ekonomiks", "Plastik",
            "Paaralan", "Dagat", "Turista", "Dolyar",
            "Ehekutibo", "Para sa personal na luho", "Pag-aaral ng panahon lamang",
            "Pamamahalang walang mamamayan", "Pag-aaral ng halaman", "Paglalakbay para sa bakasyon",
            "Dami ng produktong itinapon", "Dami ng produktong walang presyo",
            "Kasunduang pangkalakalan", "Piliing tuntunin lamang", "Kapangyarihan ng isang indibidwal",
            "Pagbibigay ng lupa", "Pagkakawatak-watak", "Pagbaba ng populasyon sa lungsod",
            "Pagbaba ng presyo", "Presyo ng lahat ng bagay",
            "Isang panuntunan sa laro", "Pribilehiyo lamang ng iilan", "Pag-unlad nang walang limitasyon",
            "Pagtatakda ng school schedule", "Pagpi-print ng libro", "Paglalakbay sa ibang bansa",
            "Pag-iwas sa halalan", "Modernong aklat-aralin"
        ).filter { it != correct }
        val choices = textChoices(correct, alternatives, level + i)
        BankItem("$question • Antas $level", choices, choices.indexOf(correct), "Ang tamang sagot ay $correct.")
    }
}

private fun ictQuestions(level: Int): List<BankItem> {
    val items = when {
        level <= 25 -> listOf(
            "Ano ang ibig sabihin ng CPU?" to "Central Processing Unit",
            "Alin ang input device?" to "Keyboard",
            "Ano ang gamit ng web browser?" to "Mag-access ng websites",
            "Alin ang operating system?" to "Android",
            "Ano ang tawag sa programang tumutulong laban sa malware?" to "Antivirus",
            "Alin ang output device?" to "Monitor",
            "Ano ang ginagamit upang mag-type ng text?" to "Keyboard",
            "Ano ang file extension ng karaniwang larawan?" to ".jpg"
        )
        level <= 50 -> listOf(
            "Ano ang RAM?" to "Temporary working memory",
            "Ano ang LAN?" to "Local Area Network",
            "Ano ang URL?" to "Web address",
            "Ano ang cloud storage?" to "Online storage accessed through a network",
            "Ano ang phishing?" to "Panlilinlang para makuha ang sensitibong impormasyon",
            "Ano ang backup?" to "Kopya ng data para sa recovery",
            "Ano ang algorithm?" to "Sunod-sunod na hakbang para malutas ang problema",
            "Ano ang database?" to "Organisadong koleksiyon ng data"
        )
        level <= 75 -> listOf(
            "Ano ang IP address?" to "Address ng device sa network",
            "Ano ang encryption?" to "Pagbabago ng data sa anyong mas mahirap basahin nang walang key",
            "Ano ang two-factor authentication?" to "Pagpapatunay gamit ang dalawang uri ng factor",
            "Ano ang loop sa programming?" to "Umuulit na bahagi ng code",
            "Ano ang variable?" to "Pangalan para sa value na maaaring magbago",
            "Ano ang HTTP?" to "Protocol na ginagamit para sa web communication",
            "Ano ang firewall?" to "System na kumokontrol sa network traffic",
            "Ano ang version control?" to "Pamamahala sa mga pagbabago sa files o code"
        )
        else -> listOf(
            "Ano ang hashing?" to "Pagbuo ng fixed-length digest mula sa data",
            "Ano ang API?" to "Interface na nagpapahintulot sa software systems na mag-usap",
            "Ano ang database index?" to "Structure na nagpapabilis ng ilang queries",
            "Ano ang authentication?" to "Pagpapatunay ng identity",
            "Ano ang authorization?" to "Pagpapatukoy kung ano ang pinapayagang gawin ng user",
            "Ano ang least privilege principle?" to "Ibigay lamang ang kinakailangang access",
            "Ano ang recursion?" to "Function na tumatawag sa sarili nito sa problemang may base case",
            "Ano ang debugging?" to "Paghahanap at pag-aayos ng errors sa program"
        )
    }

    return items.mapIndexed { i, (question, correct) ->
        val alternatives = listOf(
            "Central Audio Processor", "Mouse", "Mag-edit ng larawan offline", "Windows keyboard",
            "Game", "Printer", "Microphone", ".txt",
            "Permanent disk storage", "Wide Area Network", "Computer password", "Offline notebook",
            "Pag-compress ng video", "Pag-delete ng files", "Random na instruction", "Larawan lamang",
            "Username", "Pagpapaikli ng text", "Isang password lamang", "Isang comment",
            "Constant na hindi nagbabago", "Email-only protocol", "App icon", "Pag-save ng screenshot",
            "Password list", "Random data", "Pagbibigay ng lahat ng access", "Pag-uulit nang walang condition",
            "Function na hindi kailanman tumatakbo", "Pag-format ng computer"
        ).filter { it != correct }
        val choices = textChoices(correct, alternatives, level + i)
        BankItem("$question • Level $level", choices, choices.indexOf(correct), "The correct answer is $correct.")
    }
}

private fun peHealthQuestions(level: Int): List<BankItem> {
    val items = when {
        level <= 25 -> listOf(
            "Ano ang magandang gawin bago mag-ehersisyo?" to "Warm-up",
            "Bakit mahalaga ang hydration?" to "Para mapalitan ang fluid na nawawala",
            "Alin ang cardiovascular exercise?" to "Jumping jacks",
            "Ano ang mahalagang bahagi ng balanced lifestyle?" to "Regular activity at sapat na pahinga",
            "Ano ang dapat gawin kapag nakakaramdam ng matinding sakit habang nag-eehersisyo?" to "Huminto at magsabi sa trusted adult/coach",
            "Bakit mahalaga ang stretching?" to "Nakatutulong sa flexibility at paghahanda ng katawan",
            "Alin ang halimbawa ng muscular-strength activity?" to "Bodyweight squats",
            "Ano ang pangunahing layunin ng cool-down?" to "Unti-unting ibalik sa normal ang katawan"
        )
        level <= 50 -> listOf(
            "Ano ang fitness component na tumutukoy sa kakayahan ng puso at baga na magtrabaho nang matagal?" to "Cardiorespiratory endurance",
            "Ano ang flexibility?" to "Kakayahang igalaw ang joints sa angkop na range",
            "Ano ang muscular endurance?" to "Kakayahang paulit-ulit na gamitin ang muscles",
            "Ano ang balanced meal?" to "Pagkain na may iba't ibang nutrient-rich food groups",
            "Bakit mahalaga ang sapat na tulog?" to "Nakatutulong ito sa recovery at normal na paggana ng katawan",
            "Ano ang halimbawa ng moderate physical activity?" to "Brisk walking",
            "Ano ang tamang prinsipyo sa pagtaas ng training load?" to "Unti-unti at naaangkop sa kakayahan",
            "Ano ang dapat gawin bago gumamit ng sports equipment?" to "Suriin kung ligtas at maayos itong gamitin"
        )
        level <= 75 -> listOf(
            "Ano ang overload principle?" to "Unti-unting pagdagdag ng hamon sa katawan",
            "Ano ang specificity principle?" to "Ang training ay dapat tumugma sa layunin",
            "Ano ang rest and recovery?" to "Panahon para makabawi ang katawan pagkatapos ng activity",
            "Ano ang healthy stress-management habit?" to "Regular activity, pahinga, at pakikipag-usap sa taong pinagkakatiwalaan",
            "Ano ang ibig sabihin ng good posture?" to "Maayos na alignment ng katawan habang gumagalaw o nakaupo",
            "Ano ang injury-prevention strategy?" to "Tamang technique at unti-unting progression",
            "Ano ang kahalagahan ng warm-up?" to "Ihanda ang katawan para sa physical activity",
            "Ano ang role ng hydration sa activity?" to "Tumulong mapanatili ang normal na fluid balance"
        )
        else -> listOf(
            "Ano ang FITT principle?" to "Frequency, Intensity, Time, at Type",
            "Ano ang periodization?" to "Pagpaplano ng training sa magkakaibang phases",
            "Ano ang training specificity?" to "Pagpili ng exercises na angkop sa target adaptation",
            "Ano ang RPE?" to "Rating of Perceived Exertion",
            "Ano ang recovery indicator?" to "Pagtingin sa performance, pagod, tulog, at pakiramdam",
            "Ano ang pinakamainam na approach sa long-term fitness?" to "Consistent, progressive, at sustainable na routine",
            "Ano ang injury-risk reduction approach?" to "Progressive load, tamang technique, at sapat na recovery",
            "Ano ang health-related fitness?" to "Fitness qualities na may kaugnayan sa pangkalahatang kalusugan"
        )
    }

    return items.mapIndexed { i, (question, correct) ->
        val alternatives = listOf(
            "Matulog agad", "Iwasan ang tubig", "Reading", "Laging puyat",
            "Ipagpatuloy ang activity kahit masakit", "Pagpigil sa lahat ng movement",
            "Bodyweight squats", "Biglang huminto pagkatapos ng intense activity",
            "Maximum strength only", "Kakayahang magbasa", "Kakayahang humawak ng libro",
            "Isang uri ng pagkain lamang", "Mas kaunting tulog", "Brisk walking",
            "Biglang pinakamabigat na load", "Huwag suriin ang equipment",
            "Pagbabago nang walang plano", "Training para sa lahat ng sport nang sabay",
            "Walang pahinga", "Pag-iwas sa lahat ng activity", "Pag-arko ng katawan",
            "Pagmamadali sa exercises", "Paghahanda pagkatapos ng activity",
            "Pagpapanatili ng fluid balance", "Fast, Ice, Train, Test",
            "Random na daily exercises", "Exercise na walang layunin", "Rate of Physical Training",
            "Performance lamang", "Extreme routine", "Training nang walang recovery",
            "Academic fitness"
        ).filter { it != correct }
        val choices = textChoices(correct, alternatives, level + i)
        BankItem("$question • Level $level", choices, choices.indexOf(correct), "Ang tamang sagot ay $correct.")
    }
}

private fun extraQuestions(subject: String, level: Int): List<BankItem> {
    val pairs = when (subject) {
        "Math" -> (1..12).map { i ->
            val x = level + i
            val y = (level % 5) + i
            val ans = x + y
            "Solve: $x + $y = ?" to ans.toString()
        }
        "Science" -> listOf(
            "Which state of matter has particles packed closely together?" to "Solid",
            "What do animals need for cellular respiration?" to "Oxygen",
            "What is the process of liquid water becoming gas?" to "Evaporation",
            "What instrument measures temperature?" to "Thermometer",
            "What is Earth's natural satellite?" to "Moon",
            "What organ is mainly used for breathing?" to "Lungs",
            "What source provides most energy for Earth's climate system?" to "Sun",
            "What is a change from gas to liquid called?" to "Condensation",
            "What simple machine uses a wheel and axle?" to "Wheel and axle",
            "What protects Earth from much harmful solar radiation?" to "Atmosphere",
            "What is the center of an atom called?" to "Nucleus",
            "What happens to shadows when a light source is blocked?" to "They form"
        )
        "English" -> listOf(
            "Which word is a pronoun?" to "they",
            "Which word is a preposition?" to "under",
            "Which word is a conjunction?" to "and",
            "What is the past tense of 'teach'?" to "taught",
            "What is the plural of 'leaf'?" to "leaves",
            "Which word is an adverb?" to "carefully",
            "What is a synonym for 'rapid'?" to "fast",
            "What is an antonym of 'generous'?" to "selfish",
            "What is the purpose of a conclusion?" to "Wrap up the main ideas",
            "What does context help a reader determine?" to "Meaning",
            "What is a paragraph's central idea?" to "Its main point",
            "What is a reliable source?" to "A source supported by trustworthy evidence"
        )
        "Filipino" -> listOf(
            "Alin ang panghalip?" to "sila",
            "Alin ang pang-abay?" to "mabilis",
            "Alin ang pangatnig?" to "at",
            "Ano ang nakaraan ng 'kumain'?" to "Kumain",
            "Ano ang kasingkahulugan ng 'mabilis'?" to "matulin",
            "Ano ang kasalungat ng 'malaki'?" to "maliit",
            "Ano ang gamit ng pang-uri?" to "Maglarawan",
            "Ano ang gamit ng pandiwa?" to "Magsaad ng kilos o pangyayari",
            "Ano ang pangunahing diwa?" to "Pinakamahalagang kaisipan",
            "Ano ang paksa?" to "Pinag-uusapan",
            "Ano ang talata?" to "Pangkat ng magkakaugnay na pangungusap",
            "Ano ang salawikain?" to "Kasabihang may aral"
        )
        "Araling Panlipunan" -> listOf(
            "Ano ang tawag sa pag-aaral ng mapa at lokasyon?" to "Heograpiya",
            "Ano ang tawag sa kabuhayan ng mga tao?" to "Ekonomiya",
            "Ano ang ibig sabihin ng demograpiya?" to "Pag-aaral ng populasyon",
            "Ano ang pangunahing sangay ng pamahalaang nagpapatupad ng batas?" to "Ehekutibo",
            "Ano ang tawag sa sangay na nagbibigay-kahulugan sa batas?" to "Hudikatura",
            "Ano ang teritoryo?" to "Sakop na lupain at katubigan ng isang estado",
            "Ano ang kultura?" to "Paraan ng pamumuhay ng isang pangkat",
            "Ano ang kalakalan?" to "Palitan o pagbili at pagbebenta ng produkto",
            "Ano ang badyet?" to "Plano para sa kita at paggasta",
            "Ano ang census?" to "Opisyal na pagbibilang at paglalarawan ng populasyon",
            "Ano ang pamahalaan?" to "Institusyong namamahala sa isang pamayanan o bansa",
            "Ano ang responsableng mamamayan?" to "Taong tumutupad sa tungkulin at gumagalang sa karapatan ng iba"
        )
        "ICT" -> listOf(
            "Ano ang ibig sabihin ng URL?" to "Uniform Resource Locator",
            "Ano ang ibig sabihin ng RAM?" to "Random Access Memory",
            "Ano ang tawag sa pisikal na bahagi ng computer?" to "Hardware",
            "Ano ang tawag sa mga program at apps?" to "Software",
            "Ano ang gamit ng search engine?" to "Maghanap ng impormasyon online",
            "Ano ang secure password?" to "Mahaba at unique na password",
            "Ano ang malware?" to "Malicious software",
            "Ano ang backup?" to "Kopya ng data",
            "Ano ang network?" to "Mga device na magkakaugnay para magbahagi ng data",
            "Ano ang spreadsheet?" to "Tool para sa tabular data at calculations",
            "Ano ang presentation software?" to "Software para gumawa ng slides",
            "Ano ang programming language?" to "Wika para magbigay ng instructions sa computer"
        )
        "PE & Health" -> listOf(
            "Ano ang flexibility?" to "Kakayahang igalaw ang joints sa angkop na range",
            "Ano ang strength?" to "Kakayahang lumikha ng puwersa ang muscles",
            "Ano ang endurance?" to "Kakayahang magpatuloy sa activity",
            "Ano ang warm-up?" to "Paghahanda ng katawan bago ang activity",
            "Ano ang cool-down?" to "Unti-unting pagpapababa ng intensity pagkatapos ng activity",
            "Ano ang hydration?" to "Pagpapanatili ng sapat na fluid sa katawan",
            "Ano ang balanced diet?" to "Iba't ibang nutrient-rich foods sa tamang dami",
            "Ano ang rest day?" to "Panahong nakalaan para sa recovery",
            "Ano ang proper technique?" to "Ligtas at tamang paraan ng pagsasagawa ng exercise",
            "Ano ang physical activity?" to "Galaw ng katawan na gumagamit ng energy",
            "Ano ang healthy habit?" to "Regular na activity, sapat na pahinga, at masustansiyang pagkain",
            "Ano ang safety check?" to "Pagsuri sa lugar at equipment bago activity"
        )
        else -> emptyList()
    }

    return pairs.mapIndexed { i, (question, correct) ->
        val alternatives = when (subject) {
            "Math" -> {
                val answer = correct.toInt()
                intChoices(answer, level + 100 + i).filter { it != correct }.take(3)
            }
            "Science" -> listOf("Energy", "Gravity", "Cell", "Water", "Air", "Rock", "Heat", "Light").filter { it != correct }
            "English" -> listOf("quickly", "table", "because", "walk", "beautiful", "them", "slow", "finish").filter { it != correct }
            "Filipino" -> listOf("mesa", "tumakbo", "maganda", "ngunit", "ako", "mabagal", "bahay", "masaya").filter { it != correct }
            "Araling Panlipunan" -> listOf("Ekonomiks", "Kasaysayan", "Pamahalaan", "Barangay", "Buwis", "Kultura", "Kalakalan", "Populasyon").filter { it != correct }
            "ICT" -> listOf("Mouse", "Monitor", "Browser", "File", "Internet", "Printer", "Keyboard", "Database").filter { it != correct }
            else -> listOf("Reading", "Sleeping", "Running", "Typing", "Skipping", "Eating", "Drawing", "Resting").filter { it != correct }
        }
        val choices = textChoices(correct, alternatives, level + 100 + i)
        BankItem("$question • Level $level", choices, choices.indexOf(correct), "The correct answer is $correct.")
    }
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

private fun encodeQuestions(questions: List<Question>): String {
    val array = JSONArray()
    questions.forEach { array.put(encodeQuestion(it)) }
    return array.toString()
}

private fun decodeQuestions(raw: String): List<Question> = runCatching {
    val array = JSONArray(raw)
    (0 until array.length()).mapNotNull { index ->
        runCatching { decodeQuestion(array.getJSONObject(index)) }.getOrNull()
    }
}.getOrDefault(emptyList())

private fun parseImportFile(raw: String): Pair<List<Question>, List<Note>> {
    return runCatching {
        val root = JSONObject(raw)
        val questions = mutableListOf<Question>()
        val lessons = mutableListOf<Note>()

        val questionArray = root.optJSONArray("questions") ?: JSONArray()
        for (i in 0 until questionArray.length()) {
            val obj = questionArray.optJSONObject(i) ?: continue
            val choicesArray = obj.optJSONArray("choices") ?: continue
            val choices = (0 until choicesArray.length()).map { choicesArray.optString(it) }
                .filter { it.isNotBlank() }

            val answer = obj.optInt("answer", -1)
            if (obj.optString("text").isNotBlank() &&
                choices.size >= 2 &&
                answer in choices.indices
            ) {
                questions += Question(
                    id = obj.optString("id").ifBlank { "imported-${System.currentTimeMillis()}-$i" },
                    subject = obj.optString("subject").ifBlank { "Imported" },
                    text = obj.optString("text"),
                    choices = choices,
                    answer = answer,
                    explanation = obj.optString("explanation").ifBlank { "Review the lesson and try again." }
                )
            }
        }

        val lessonArray = root.optJSONArray("lessons") ?: JSONArray()
        for (i in 0 until lessonArray.length()) {
            val obj = lessonArray.optJSONObject(i) ?: continue
            val title = obj.optString("title").ifBlank { "Imported Lesson ${i + 1}" }
            val body = obj.optString("body")
            if (body.isNotBlank()) {
                lessons += Note(
                    id = System.currentTimeMillis() + i,
                    title = title,
                    body = body
                )
            }
        }

        Pair(questions, lessons)
    }.getOrElse { emptyList<Question>() to emptyList() }
}

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
                .put("level", it.level)
        )
    }
    return array.toString()
}

private fun decodeHistory(raw: String): List<ReviewItem> = runCatching {
    val array = JSONArray(raw)
    (0 until array.length()).map {
        val obj = array.getJSONObject(it)
        val question = decodeQuestion(obj.getJSONObject("question"))
        val levelFromQuestion = Regex("(?:-L|Level\\s+)(\\d+)", RegexOption.IGNORE_CASE)
            .find(question.id)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("Level\\s+(\\d+)", RegexOption.IGNORE_CASE)
                .find(question.text)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: 1
        ReviewItem(
            question,
            obj.optInt("selected"),
            obj.optBoolean("correct"),
            obj.optInt("level", levelFromQuestion).coerceIn(1, 100)
        )
    }
}.getOrDefault(emptyList())
