
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
            listOf("Mixed", "Math", "Science", "English", "Filipino",
                "Araling Panlipunan", "ICT", "PE & Health").forEach { subject ->
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

                        listOf(
                            "Math", "Science", "English", "Filipino",
                            "Araling Panlipunan", "ICT", "PE & Health"
                        ).forEach { name ->
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
                        val subjectResults = listOf(
                            "Math", "Science", "English", "Filipino",
                            "Araling Panlipunan", "ICT", "PE & Health"
                        ).filter {
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
                            "Answers are grouped by subject, level, and result."
                        )

                        if (history.isEmpty()) {
                            Text("No answered questions yet.", color = muted)
                        } else {
                            history
                                .groupBy { it.question.subject }
                                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                                .forEach { (subjectName, subjectHistory) ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = colors.surface
                                        )
                                    ) {
                                        Column(
                                            Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                "📘 $subjectName",
                                                fontWeight = FontWeight.ExtraBold,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = colors.primary
                                            )

                                            subjectHistory
                                                .groupBy { it.level }
                                                .toSortedMap()
                                                .forEach { (reviewLevel, levelHistory) ->
                                                    Divider()
                                                    Text(
                                                        "Level $reviewLevel",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.titleSmall
                                                    )

                                                    listOf(
                                                        true to "✓ Tama ang Sagot",
                                                        false to "✕ Mali ang Sagot"
                                                    ).forEach { (isCorrectGroup, heading) ->
                                                        val resultItems = levelHistory.filter {
                                                            it.correct == isCorrectGroup
                                                        }
                                                        Text(
                                                            "$heading (${resultItems.size})",
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isCorrectGroup) Color(0xFF2E7D32) else Color(0xFFC62828)
                                                        )

                                                        if (resultItems.isEmpty()) {
                                                            Text(
                                                                "Wala pang mga tanong sa grupong ito.",
                                                                color = muted
                                                            )
                                                        } else {
                                                            resultItems.forEach { item ->
                                                                Card(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    colors = CardDefaults.cardColors(
                                                                        containerColor = colors.background
                                                                    )
                                                                ) {
                                                                    Column(
                                                                        Modifier.padding(12.dp),
                                                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                                                    ) {
                                                                        Text(
                                                                            item.question.text,
                                                                            fontWeight = FontWeight.SemiBold
                                                                        )
                                                                        Text(
                                                                            "Sagot mo: ${
                                                                                item.question.choices.getOrElse(item.selected) { "—" }
                                                                            }"
                                                                        )
                                                                        Text(
                                                                            "Tamang sagot: ${
                                                                                item.question.choices.getOrElse(item.question.answer) { "—" }
                                                                            }"
                                                                        )
                                                                        Text(
                                                                            item.question.explanation,
                                                                            color = muted
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
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

    "Araling Panlipunan" -> listOf(
        BankItem("Ano ang kabisera ng Pilipinas?", listOf("Cebu", "Manila", "Davao", "Baguio"), 1, "Ang Manila ang kabisera ng Pilipinas."),
        BankItem("Ano ang tawag sa pamahalaang pinamumunuan ng pangulo?", listOf("Presidential", "Monarchical", "Tribal", "Feudal"), 0, "Ang Pilipinas ay may presidential form of government."),
        BankItem("Alin ang halimbawa ng likas na yaman?", listOf("Ginto", "Plastik", "Salamin", "Semento"), 0, "Ang ginto ay likas na yamang mineral."),
        BankItem("Ano ang tawag sa pag-aaral ng nakaraan?", listOf("Heograpiya", "Kasaysayan", "Ekonomiks", "Agham"), 1, "Ang kasaysayan ay pag-aaral ng nakaraan."),
        BankItem("Alin ang pangunahing anyong tubig?", listOf("Bundok", "Lambak", "Karagatan", "Burol"), 2, "Ang karagatan ay malaking anyong tubig.")
    )

    "ICT" -> listOf(
        BankItem("Ano ang ibig sabihin ng CPU?", listOf("Central Processing Unit", "Computer Power Utility", "Central Program User", "Control Processing User"), 0, "CPU means Central Processing Unit."),
        BankItem("Alin ang input device?", listOf("Monitor", "Printer", "Keyboard", "Speaker"), 2, "Ang keyboard ay ginagamit sa pag-input ng data."),
        BankItem("Ano ang gamit ng web browser?", listOf("Mag-browse ng websites", "Magluto", "Mag-print ng pera", "Mag-charge ng phone"), 0, "Ang browser ay ginagamit para mag-access ng web pages."),
        BankItem("Alin ang halimbawa ng operating system?", listOf("Android", "Keyboard", "HDMI", "Mouse"), 0, "Android ay isang operating system."),
        BankItem("Ano ang tawag sa programang tumutulong protektahan ang computer laban sa malware?", listOf("Antivirus", "Calculator", "Player", "Editor"), 0, "Ang antivirus software ay tumutulong laban sa malware.")
    )

    "PE & Health" -> listOf(
        BankItem("Ano ang magandang gawin bago mag-ehersisyo?", listOf("Warm-up", "Matulog", "Mag-fast food", "Umupo buong oras"), 0, "Ang warm-up ay naghahanda sa katawan para sa activity."),
        BankItem("Alin ang halimbawa ng cardiovascular exercise?", listOf("Jumping jack", "Reading", "Drawing", "Typing"), 0, "Ang jumping jack ay nagpapataas ng heart rate."),
        BankItem("Bakit mahalaga ang hydration?", listOf("Para mapalitan ang fluid na nawawala", "Para hindi na kumain", "Para hindi matulog", "Para maging invisible"), 0, "Mahalaga ang tubig para mapanatili ang normal na hydration."),
        BankItem("Ano ang mahalagang bahagi ng balanced lifestyle?", listOf("Regular activity at sapat na pahinga", "Laging puyat", "Walang galaw", "Laging skip meals"), 0, "Kasama sa healthy routine ang activity at sapat na pahinga."),
        BankItem("Ano ang dapat gawin kapag nakakaramdam ng matinding sakit habang nag-eehersisyo?", listOf("Huminto at magsabi sa trusted adult/coach", "Ipagpatuloy agad", "Dagdagan ang intensity", "Huwag pansinin"), 0, "Ang matinding sakit ay senyales para huminto at humingi ng angkop na tulong.")
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
