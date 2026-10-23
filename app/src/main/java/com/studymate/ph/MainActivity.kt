
package com.studymate.ph

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private enum class Screen {
    HOME, SUBJECTS, SEARCH, QUIZ, LEVEL_UP, LEVEL_COMPLETE, GAME_OVER, NOTES, FLASHCARDS, TIMER, PROGRESS, REVIEW, PROFILE, SHOP, SETTINGS
}

private data class Note(val id: Long, val title: String, val body: String)

private data class Question(
    val id: String,
    val subject: String,
    val text: String,
    val choices: List<String>,
    val answer: Int,
    val explanation: String,
    val type: String = "Multiple Choice"
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
    val explanation: String,
    val type: String = "Multiple Choice"
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
    var coins by remember {
        mutableIntStateOf(prefs.getInt("coins", prefs.getInt("xp", 0)).coerceAtLeast(0))
    }
    var dailyStreak by remember {
        mutableIntStateOf(prefs.getInt("dailyStreak", 0).coerceAtLeast(0))
    }
    var lastStudyDate by remember {
        mutableStateOf(prefs.getString("lastStudyDate", "") ?: "")
    }
    var dailyCompletedDate by remember {
        mutableStateOf(prefs.getString("dailyCompletedDate", "") ?: "")
    }
    var dailyLoginClaimDate by remember {
        mutableStateOf(prefs.getString("dailyLoginClaimDate", "") ?: "")
    }
    var fiftyFiftyItems by remember { mutableIntStateOf(prefs.getInt("item_5050", 0).coerceAtLeast(0)) }
    var extraTimeItems by remember { mutableIntStateOf(prefs.getInt("item_time", 0).coerceAtLeast(0)) }
    var skipItems by remember { mutableIntStateOf(prefs.getInt("item_skip", 0).coerceAtLeast(0)) }
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
    // Every new quiz grants one free use of each power-up; purchased inventory is additional.
    var free5050 by rememberSaveable { mutableStateOf(false) }
    var freeTime by rememberSaveable { mutableStateOf(false) }
    var freeSkip by rememberSaveable { mutableStateOf(false) }
    var hiddenChoices by remember { mutableStateOf(setOf<Int>()) }
    var quizResultSaved by rememberSaveable { mutableStateOf(false) }
    var quizStartedAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var dailyChallengeActive by rememberSaveable { mutableStateOf(false) }
    val localLeaderboard = remember {
        mutableStateListOf<String>().apply {
            addAll((prefs.getString("localLeaderboard", "") ?: "").split("\n").filter { it.isNotBlank() })
        }
    }
    var showSubjectDialog by remember { mutableStateOf(false) }
    var subjectInput by rememberSaveable { mutableStateOf("") }
    var showQuickQuizModal by remember { mutableStateOf(false) }
    var showCoinShopModal by remember { mutableStateOf(false) }
    var showCoinBurst by remember { mutableStateOf(false) }
    var quickQuestion by remember { mutableStateOf<Question?>(null) }
    var quickAnswered by remember { mutableStateOf(false) }
    var quickSelected by rememberSaveable { mutableIntStateOf(-1) }

    val levelXp = (correct * 100 + quizScore).coerceAtLeast(0) % 1000
    val levelXpProgress = levelXp / 1000f


    fun openQuickQuiz() {
        val pool = makeQuestions("Mixed", level)
            .filter { it.id !in answeredIds }
            .shuffled()
        quickQuestion = pool.firstOrNull()
        quickSelected = -1
        quickAnswered = false
        showQuickQuizModal = true
    }

    fun answerQuickQuiz(index: Int) {
        val q = quickQuestion ?: return
        if (quickAnswered) return
        quickSelected = index
        quickAnswered = true
        val isCorrect = index == q.answer
        if (isCorrect) {
            correct++
            answered++
            coins += 20
            answeredIds.add(q.id)
            history.add(0, ReviewItem(q, index, true, level))
            while (history.size > 500) history.removeAt(history.lastIndex)
            saveProgress()
            android.widget.Toast.makeText(context, "Correct! +20 coins 🪙", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(context, "Not quite. Try the full quiz for more practice.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

        subjectLevels[selectedSubject] = level
        prefs.edit()
            .putInt("level", level)
            .putInt("level_$selectedSubject", level)
            .putInt("answered", answered)
            .putInt("correct", correct)
            .putInt("coins", coins)
            .putInt("xp", coins)
            .putInt("dailyStreak", dailyStreak)
            .putInt("item_5050", fiftyFiftyItems)
            .putInt("item_time", extraTimeItems)
            .putInt("item_skip", skipItems)
            .putString("lastStudyDate", lastStudyDate)
            .putString("dailyCompletedDate", dailyCompletedDate)
            .putString("dailyLoginClaimDate", dailyLoginClaimDate)
            .putString("answeredIds", encodeStrings(answeredIds))
            .putString("history", encodeHistory(history))
            .putString("customSubjects", encodeStrings(customSubjects))
            .putString("importedQuestions", encodeQuestions(importedQuestions))
            .putString("studentName", studentName)
            .putString("studentEmail", studentEmail)
            .putBoolean("loggedIn", loggedIn)
            .apply()
    }

    fun claimDailyLoginCoins() {
        val today = LocalDate.now().toString()
        if (dailyLoginClaimDate == today) return
        coins += 25
        dailyLoginClaimDate = today
        showCoinBurst = true
        saveProgress()
    }

    fun buyItem(type: String) {
        val cost = when (type) {
            "5050" -> 50
            "time" -> 30
            "skip" -> 40
            else -> 0
        }
        if (cost == 0) {
            android.widget.Toast.makeText(context, "Unknown shop item", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (coins < cost) {
            android.widget.Toast.makeText(context, "Not enough coins. You have $coins, need $cost.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        coins -= cost
        when (type) {
            "5050" -> fiftyFiftyItems++
            "time" -> extraTimeItems++
            "skip" -> skipItems++
        }
        saveProgress()
        android.widget.Toast.makeText(context, "Purchased! Your coins and inventory were saved.", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun startQuiz(subject: String, daily: Boolean = false) {
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
        free5050 = true
        freeTime = true
        freeSkip = true
        hiddenChoices = emptySet()
        quizResultSaved = false
        quizStartedAt = System.currentTimeMillis()
        dailyChallengeActive = daily

        val builtIn = makeQuestions(subject, level)
        val imported = importedQuestions.filter {
            subject == "Mixed" || it.subject.equals(subject, ignoreCase = true)
        }
        val pool = (builtIn + imported).distinctBy { it.id }
        questions = pool.filter { it.id !in answeredIds }.shuffled().take(if (daily) 10 else 20)

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
        free5050 = true
        freeTime = true
        freeSkip = true
        hiddenChoices = emptySet()
        quizResultSaved = false
        quizStartedAt = System.currentTimeMillis()
        dailyChallengeActive = false
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
            coins += 10 + (speedBonus / 2) + if (streak >= 5) 10 else 0
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
            if (dailyChallengeActive) {
                val today = LocalDate.now().toString()
                if (dailyCompletedDate != today) {
                    dailyStreak = if (lastStudyDate.isNotBlank()) {
                        val gap = runCatching { ChronoUnit.DAYS.between(LocalDate.parse(lastStudyDate), LocalDate.parse(today)) }.getOrDefault(99L)
                        if (gap == 1L) dailyStreak + 1 else 1
                    } else 1
                    lastStudyDate = today
                    dailyCompletedDate = today
                    coins += 100
                    saveProgress()
                }
                dailyChallengeActive = false
            }
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

    StudyMateModals(
        showCoinBurst = showCoinBurst,
        showQuickQuizModal = showQuickQuizModal,
        showCoinShopModal = showCoinShopModal,
        showSubjectDialog = showSubjectDialog,
        coins = coins,
        quickQuestion = quickQuestion,
        quickAnswered = quickAnswered,
        quickSelected = quickSelected,
        fiftyFiftyItems = fiftyFiftyItems,
        extraTimeItems = extraTimeItems,
        skipItems = skipItems,
        subjectInput = subjectInput,
        onQuickDismiss = { showQuickQuizModal = false },
        onQuickAnswer = { answerQuickQuiz(it) },
        onStartFullQuiz = { showQuickQuizModal = false; startQuiz("Mixed") },
        onShopDismiss = { showCoinShopModal = false },
        onBuy5050 = { buyItem("5050") },
        onBuyTime = { buyItem("time") },
        onBuySkip = { buyItem("skip") },
        onSubjectDismiss = { showSubjectDialog = false },
        onSubjectInputChange = { subjectInput = it },
        onAddSubject = { name ->
            val trimmed = name.trim()
            if (trimmed.isNotEmpty() && trimmed !in customSubjects) {
                customSubjects.add(trimmed)
                prefs.edit().putString("customSubjects", encodeStrings(customSubjects)).apply()
            }
            subjectInput = ""
            showSubjectDialog = false
        }
    )
}

@Composable
private fun StudyMateModals(
    showCoinBurst: Boolean,
    showQuickQuizModal: Boolean,
    showCoinShopModal: Boolean,
    showSubjectDialog: Boolean,
    coins: Int,
    quickQuestion: Question?,
    quickAnswered: Boolean,
    quickSelected: Int,
    fiftyFiftyItems: Int,
    extraTimeItems: Int,
    skipItems: Int,
    subjectInput: String,
    onQuickDismiss: () -> Unit,
    onQuickAnswer: (Int) -> Unit,
    onStartFullQuiz: () -> Unit,
    onShopDismiss: () -> Unit,
    onBuy5050: () -> Unit,
    onBuyTime: () -> Unit,
    onBuySkip: () -> Unit,
    onSubjectDismiss: () -> Unit,
    onSubjectInputChange: (String) -> Unit,
    onAddSubject: (String) -> Unit
) {
    if (showCoinBurst) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                modifier = Modifier.padding(top = 82.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = Color(0xFF064E3B).copy(alpha = 0.94f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.65f))
            ) {
                Text(
                    "✨ +25 COINS 🪙",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    color = Color(0xFFA7F3D0),
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }

    if (showQuickQuizModal) {
        AlertDialog(
            onDismissRequest = onQuickDismiss,
            containerColor = Color(0xFF0F172A),
            titleContentColor = Color.White,
            textContentColor = muted,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🧠 Quick Quiz", fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.weight(1f))
                    Text("🪙 $coins", color = Color(0xFFFDE68A), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                val question = quickQuestion
                if (question == null) {
                    Text("No unanswered Quick Quiz question is available right now.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(question.text, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Type: ${question.type}", color = Color(0xFFC4B5FD), style = MaterialTheme.typography.bodySmall)
                        question.choices.forEachIndexed { index, choice ->
                            val selectedColor = when {
                                quickAnswered && index == question.answer -> Color(0xFF10B981)
                                quickAnswered && index == quickSelected -> Color(0xFFF43F5E)
                                else -> Color(0xFF1E293B)
                            }
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !quickAnswered) { onQuickAnswer(index) },
                                shape = MaterialTheme.shapes.medium,
                                color = selectedColor.copy(alpha = if (quickAnswered && (index == question.answer || index == quickSelected)) 0.28f else 0.75f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (index == question.answer && quickAnswered) Color(0xFF10B981) else Color.White.copy(alpha = 0.10f)
                                )
                            ) {
                                Text(choice, Modifier.padding(12.dp), color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (quickAnswered) {
                            Text(
                                if (quickSelected == question.answer) "Correct! +20 coins 🪙" else "Incorrect. ${question.explanation}",
                                color = if (quickSelected == question.answer) Color(0xFF6EE7B7) else Color(0xFFFDA4AF),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onStartFullQuiz) {
                    Text(if (quickQuestion != null && quickAnswered) "Start Full Quiz" else "Full Quiz")
                }
            },
            dismissButton = {
                TextButton(onClick = onQuickDismiss) { Text("Close") }
            }
        )
    }

    if (showCoinShopModal) {
        AlertDialog(
            onDismissRequest = onShopDismiss,
            containerColor = Color(0xFF0F172A),
            titleContentColor = Color.White,
            textContentColor = muted,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🛒 Coin Shop", fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.weight(1f))
                    Text("🪙 $coins", color = Color(0xFFFDE68A), fontWeight = FontWeight.ExtraBold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Spend coins for extra power-ups. Every new quiz still gives one FREE use of each.", color = muted)
                    ShopModalItem("50:50", "Remove two wrong choices", 50, fiftyFiftyItems, coins >= 50, onBuy5050)
                    ShopModalItem("+15 sec", "Add 15 seconds", 30, extraTimeItems, coins >= 30, onBuyTime)
                    ShopModalItem("Skip", "Skip without losing a life", 40, skipItems, coins >= 40, onBuySkip)
                }
            },
            confirmButton = {
                TextButton(onClick = onShopDismiss) { Text("Done") }
            }
        )
    }

    if (showSubjectDialog) {
        AlertDialog(
            onDismissRequest = onSubjectDismiss,
            title = { Text("Add a subject") },
            text = {
                OutlinedTextField(
                    value = subjectInput,
                    onValueChange = onSubjectInputChange,
                    label = { Text("Subject name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { onAddSubject(subjectInput) }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = onSubjectDismiss) { Text("Cancel") }
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
private fun ShopPage(
    coins: Int,
    fiftyFifty: Int,
    extraTime: Int,
    skip: Int,
    onBuy5050: () -> Unit,
    onBuyTime: () -> Unit,
    onBuySkip: () -> Unit
) {
    Heading("🛒 Coin Shop", "Spend coins to buy extra uses. Each new quiz also gives one FREE 50:50, +15 sec, and Skip.")
    CardBox("🪙 Your Coins", "$coins coins available") {}

    ShopItem(
        title = "50:50",
        description = "Removes two wrong choices during a quiz.",
        cost = 50,
        owned = fiftyFifty,
        canBuy = coins >= 50,
        onBuy = onBuy5050
    )
    ShopItem(
        title = "+15 sec",
        description = "Adds 15 seconds to the current question.",
        cost = 30,
        owned = extraTime,
        canBuy = coins >= 30,
        onBuy = onBuyTime
    )
    ShopItem(
        title = "Skip",
        description = "Skip the current question without losing a life.",
        cost = 40,
        owned = skip,
        canBuy = coins >= 40,
        onBuy = onBuySkip
    )

    Text("Items are saved on this device and can be used in future quizzes.", color = muted)
}

@Composable
private fun ShopItem(
    title: String,
    description: String,
    cost: Int,
    owned: Int,
    canBuy: Boolean,
    onBuy: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(description, color = muted)
            Text("⭐ $cost coins • Owned: $owned", color = MaterialTheme.colorScheme.secondary)
            Button(onClick = onBuy, enabled = canBuy, modifier = Modifier.fillMaxWidth()) {
                Text(if (canBuy) "Buy for $cost coins" else "Need $cost coins")
            }
        }
    }
}

@Composable
private fun ShopModalItem(
    title: String,
    description: String,
    cost: Int,
    owned: Int,
    canBuy: Boolean,
    onBuy: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = Color(0xFF111827).copy(alpha = 0.9f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.25f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold)
                Text(description, color = muted, style = MaterialTheme.typography.bodySmall)
                Text("Owned: $owned • $cost coins", color = Color(0xFFC4B5FD), style = MaterialTheme.typography.labelSmall)
            }
            Button(
                onClick = onBuy,
                enabled = canBuy,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("Buy")
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
    coins: Int,
    dailyStreak: Int,
    fiftyFifty: Int,
    extraTime: Int,
    skip: Int,
    lightTheme: Boolean,
    tickingSound: Boolean,
    importMessage: String,
    onToggleLightTheme: () -> Unit,
    onToggleTicking: () -> Unit,
    onImport: () -> Unit,
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
    CardBox("🪙 Coins", "$coins coins") {}
    CardBox("🛒 Coin Shop Inventory", "50:50: $fiftyFifty • +15 sec: $extraTime • Skip: $skip") {}
    CardBox("🔥 Daily streak", "$dailyStreak day(s)") {}
    CardBox("📝 Questions answered", "$answered") {}
    CardBox("🎯 Correct answers", "$correct") {}
    CardBox("🏆 Achievements", achievementsFor(answered, correct).joinToString(" • "))

    Heading("⚙️ Settings", "App preferences are now inside your Profile.")

    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggleLightTheme() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Light Theme", fontWeight = FontWeight.SemiBold)
            Text("Switch between light and neon dark appearance.", color = muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = lightTheme, onCheckedChange = { onToggleLightTheme() })
    }

    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggleTicking() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Red Zone ticking sound", fontWeight = FontWeight.SemiBold)
            Text("Beeps during the final 10 seconds of a question.", color = muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = tickingSound, onCheckedChange = { onToggleTicking() })
    }

    OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
        Text("🗂️ Import Lessons / Questions")
    }
    if (importMessage.isNotBlank()) {
        Text(importMessage, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
    }

    CardBox(
        "☁️ Online Database / Sync",
        "Firebase connection is not configured yet. Your data stays local and is ready for future sync."
    ) {}

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
    if (correct >= 100) result += "🧠 Master Learner"
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
    val accent = if (title.contains("🔥") || title.contains("Daily")) Color(0xFFF59E0B)
        else if (title.contains("🛒") || title.contains("Coins")) Color(0xFF06B6D4)
        else Color(0xFF8B5CF6)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF111827).copy(alpha = 0.94f),
                            Color(0xFF172554).copy(alpha = 0.78f),
                            Color(0xFF0B1020).copy(alpha = 0.96f)
                        )
                    )
                )
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, color = muted)
            }
        }
    }
}

@Composable
private fun NeonHeroCard(
    name: String,
    level: Int,
    coins: Int,
    xp: Int,
    xpProgress: Float,
    achievements: Int,
    onProfile: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onProfile),
        shape = MaterialTheme.shapes.extraLarge,
        border = androidx.compose.foundation.BorderStroke(1.dp, purple.copy(alpha = 0.45f)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF312E81), Color(0xFF111827), Color(0xFF0F172A))
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("StudyMate PH", color = Color(0xFFA78BFA), fontWeight = FontWeight.ExtraBold)
                        Text("Welcome, $name! ✨", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        Text("Level $level • $achievements achievements unlocked", color = muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = Color(0xFF0F172A).copy(alpha = 0.82f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f))
                    ) {
                        Text("🪙 $coins", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color(0xFFFDE68A), fontWeight = FontWeight.ExtraBold)
                    }
                }
                Text("XP Progress  •  $xp / 1000 XP", color = Color(0xFFC4B5FD), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(
                    progress = { xpProgress },
                    modifier = Modifier.fillMaxWidth().height(9.dp),
                    color = Color(0xFF22D3EE),
                    trackColor = Color(0xFF1E293B),
                )
            }
        }
    }
}

@Composable
private fun NeonActionCard(
    title: String,
    subtitle: String,
    badge: String,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.20f), Color(0xFF111827).copy(alpha = 0.88f), Color(0xFF0B1020).copy(alpha = 0.96f))
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = accent.copy(alpha = 0.14f),
                border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.45f))
            ) {
                Text(title.take(2), modifier = Modifier.padding(12.dp), fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(7.dp))
                    Surface(color = accent.copy(alpha = 0.18f), shape = MaterialTheme.shapes.small) {
                        Text(badge, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Text(subtitle, color = muted, style = MaterialTheme.typography.bodySmall)
            }
            Text("›", color = accent, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun GlassTopBar(
    screen: Screen,
    coins: Int,
    level: Int,
    onBack: () -> Unit,
    onProfile: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (screen != Screen.HOME) {
            OutlinedButton(onClick = onBack) { Text("←") }
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("StudyMate PH", color = Color(0xFFC084FC), fontWeight = FontWeight.ExtraBold)
            Text("Level $level • Learn smarter, one day at a time", color = muted, style = MaterialTheme.typography.bodySmall)
        }
        Surface(
            modifier = Modifier.clickable(onClick = onProfile),
            shape = MaterialTheme.shapes.large,
            color = Color(0xFF0F172A).copy(alpha = 0.82f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.38f))
        ) {
            Text("🪙 $coins  👤", modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), color = Color(0xFFFDE68A), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GlassBottomNav(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit
) {
    val items = listOf(
        Triple(Screen.HOME, "🏠", "Home"),
        Triple(Screen.SUBJECTS, "📚", "Subjects"),
        Triple(Screen.SEARCH, "🔍", "Search"),
        Triple(Screen.PROGRESS, "📊", "Progress"),
        Triple(Screen.PROFILE, "👤", "Profile")
    )
    Surface(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color(0xFF0F172A).copy(alpha = 0.92f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        shadowElevation = 10.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { (item, icon, label) ->
                val selected = currentScreen == item
                Surface(
                    modifier = Modifier.weight(1f).clickable { onNavigate(item) },
                    shape = MaterialTheme.shapes.large,
                    color = if (selected) Color(0xFF7C3AED).copy(alpha = 0.28f) else Color.Transparent,
                    border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA78BFA).copy(alpha = 0.35f)) else null
                ) {
                    Column(
                        Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(icon)
                        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) Color(0xFFE9D5FF) else muted)
                    }
                }
            }
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
    // Keep all previous quiz features, but make the new levels progressively harder.
    // Mixed now covers every subject while retaining the same saved-progress system.
    val subjects = if (subject == "Mixed") {
        listOf("Math", "Science", "English", "Filipino", "Araling Panlipunan", "ICT", "PE & Health")
    } else {
        listOf(subject)
    }

    val result = mutableListOf<Question>()
    val tier = when {
        level <= 20 -> 1
        level <= 40 -> 2
        level <= 60 -> 3
        level <= 80 -> 4
        else -> 5
    }

    subjects.forEach { name ->
        if (name == "Math") {
            for (n in 1..40) {
                val seed = level * 97 + n * 13
                val a = level + n + 2
                val b = (n % 9) + 2

                val answer: Int
                val text: String

                when (tier) {
                    1 -> {
                        when ((n + level) % 3) {
                            0 -> {
                                val x = a
                                val y = b + level / 10
                                answer = x + y
                                text = "$x + $y = ?"
                            }
                            1 -> {
                                val x = a + b
                                answer = x - b
                                text = "$x − $b = ?"
                            }
                            else -> {
                                val x = (level % 8) + 2
                                answer = x * b
                                text = "$x × $b = ?"
                            }
                        }
                    }
                    2 -> {
                        when ((n + level) % 4) {
                            0 -> {
                                val x = a + b
                                val y = b + (n % 4)
                                answer = x + y
                                text = "$x + $y = ?"
                            }
                            1 -> {
                                val x = a + b + 5
                                val y = b + 2
                                answer = x - y
                                text = "$x − $y = ?"
                            }
                            2 -> {
                                val x = (level % 10) + 3
                                val y = b + 2
                                answer = x * y
                                text = "$x × $y = ?"
                            }
                            else -> {
                                val y = b + 1
                                val x = y * ((n % 8) + 3)
                                answer = x / y
                                text = "$x ÷ $y = ?"
                            }
                        }
                    }
                    3 -> {
                        when ((n + level) % 4) {
                            0 -> {
                                val x = a + 10
                                val y = b + 3
                                val z = (n % 5) + 2
                                answer = x + y - z
                                text = "($x + $y) − $z = ?"
                            }
                            1 -> {
                                val x = (level % 12) + 4
                                val y = b + 2
                                val z = n % 4 + 1
                                answer = x * y + z
                                text = "($x × $y) + $z = ?"
                            }
                            2 -> {
                                val x = a + b + 12
                                val y = b + 4
                                val z = (n % 6) + 2
                                answer = x - y + z
                                text = "$x − $y + $z = ?"
                            }
                            else -> {
                                val y = b + 2
                                val quotient = (n % 8) + 4
                                val x = y * quotient
                                answer = quotient + (n % 3)
                                text = "$x ÷ $y + ${n % 3} = ?"
                            }
                        }
                    }
                    4 -> {
                        val x = (level % 15) + n + 10
                        val y = (n % 7) + 3
                        val z = (level % 5) + 2
                        when ((seed % 4)) {
                            0 -> {
                                answer = x * y - z
                                text = "$x × $y − $z = ?"
                            }
                            1 -> {
                                answer = x + y * z
                                text = "$x + ($y × $z) = ?"
                            }
                            2 -> {
                                answer = (x - y) * z
                                text = "($x − $y) × $z = ?"
                            }
                            else -> {
                                answer = x * y + z
                                text = "($x × $y) + $z = ?"
                            }
                        }
                    }
                    else -> {
                        val x = (level % 20) + n + 15
                        val y = (n % 8) + 4
                        val z = (level % 9) + 3
                        when (seed % 5) {
                            0 -> {
                                answer = x * y - z * 2
                                text = "($x × $y) − ($z × 2) = ?"
                            }
                            1 -> {
                                answer = (x + z) * y - z
                                text = "($x + $z) × $y − $z = ?"
                            }
                            2 -> {
                                answer = x * (y + z) - y
                                text = "$x × ($y + $z) − $y = ?"
                            }
                            3 -> {
                                answer = (x - z) * y + z
                                text = "($x − $z) × $y + $z = ?"
                            }
                            else -> {
                                answer = x + y * z - 2
                                text = "$x + ($y × $z) − 2 = ?"
                            }
                        }
                    }
                }

                // Plausible, close distractors. Their positions are randomized.
                val distractors = linkedSetOf(
                    answer + 1,
                    answer - 1,
                    answer + 2,
                    answer - 2,
                    answer + 3,
                    answer - 3,
                    answer + 5,
                    answer - 5
                ).filter { it >= 0 && it != answer }.shuffled().take(3)
                val options = (distractors + answer).shuffled()

                result.add(
                    Question(
                        "Math-L$level-Q$n",
                        "Math",
                        "Level $level • ${difficultyName(level)}: $text",
                        options.map { it.toString() },
                        options.indexOf(answer),
                        "Difficulty: ${difficultyName(level)}. Solve using the correct order of operations where needed. The correct answer is $answer.",
                        "Calculation"
                    )
                )
            }
        } else {
            questionBank(name).forEachIndexed { i, item ->
                // The same saved answeredIds mechanism remains in place.
                // Level-specific wording creates a fresh question instance while
                // gradually moving from recall to application/challenge.
                val variant = ((level - 1) % 5)
                val challengeText = when (tier) {
                    1 -> item.text
                    2 -> "Choose the best answer: ${item.text}"
                    3 -> "Think carefully and apply what you know: ${item.text}"
                    4 -> "Challenge: Which answer is most accurate? ${item.text}"
                    else -> "Expert challenge: After analyzing the choices, ${item.text.lowercase().replaceFirstChar { it.uppercase() }}"
                }
                val finalText = "Level $level • ${difficultyName(level)} • V${variant + 1}: $challengeText"

                val shuffledChoices = item.choices.shuffled()
                val shuffledAnswer = shuffledChoices.indexOf(item.choices[item.answer])
                result.add(
                    Question(
                        "$name-L$level-Q${i + 1}-V${variant + 1}",
                        name,
                        finalText,
                        shuffledChoices,
                        shuffledAnswer,
                        "Difficulty: ${difficultyName(level)}. ${item.explanation}",
                        item.type
                    )
                )
            }
        }
    }

    return result.distinctBy { it.id }
}

private fun difficultyName(level: Int): String = when {
    level <= 20 -> "Easy"
    level <= 40 -> "Medium"
    level <= 60 -> "Hard"
    level <= 80 -> "Very Hard"
    else -> "Expert"
}

private fun questionBank(subject: String): List<BankItem> = (when (subject) {
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
}) + extraQuestionBank(subject)

private fun extraQuestionBank(subject: String): List<BankItem> = when (subject) {
    "Science" -> listOf(
        BankItem("True or False: The Earth revolves around the Sun.", listOf("True", "False"), 0, "Earth orbits the Sun.", "True / False"),
        BankItem("Which state of matter has a fixed volume but no fixed shape?", listOf("Solid", "Liquid", "Gas", "Plasma"), 1, "A liquid keeps its volume but takes the shape of its container.", "Concept"),
        BankItem("Which part of a cell contains genetic material?", listOf("Nucleus", "Cell wall", "Vacuole", "Cytoplasm"), 0, "In typical eukaryotic cells, DNA is mainly stored in the nucleus.", "Concept"),
        BankItem("A shadow is formed when light is blocked by an object. True or False?", listOf("True", "False"), 0, "An opaque object can block light and form a shadow.", "True / False"),
        BankItem("Which energy source is renewable?", listOf("Coal", "Solar", "Diesel", "Natural gas"), 1, "Solar energy is naturally replenished.", "Application"),
        BankItem("Why does ice float on liquid water?", listOf("It is less dense", "It is hotter", "It is heavier", "It has no mass"), 0, "Solid water is less dense than liquid water.", "Reasoning"),
        BankItem("Which organ is primarily responsible for gas exchange?", listOf("Lungs", "Kidneys", "Liver", "Skin"), 0, "The lungs exchange oxygen and carbon dioxide.", "Concept")
    )
    "English" -> listOf(
        BankItem("True or False: 'Quickly' is usually an adverb.", listOf("True", "False"), 0, "Quickly commonly modifies a verb and is an adverb.", "True / False"),
        BankItem("Choose the sentence with correct subject-verb agreement.", listOf("The students studies.", "The students study.", "The students studying.", "The students is study."), 1, "The plural subject 'students' takes 'study'.", "Grammar"),
        BankItem("Which word is closest in meaning to 'accurate'?", listOf("Correct", "Noisy", "Ancient", "Careless"), 0, "Accurate means correct or exact.", "Vocabulary"),
        BankItem("What is the main purpose of a conclusion in a paragraph?", listOf("Introduce a new topic", "Wrap up the main idea", "List random words", "Remove the title"), 1, "A conclusion closes the discussion and reinforces the main idea.", "Reading"),
        BankItem("Which is a complete sentence?", listOf("Because it rained.", "Running in the park.", "The class started early.", "After lunch."), 2, "The sentence has a subject and a complete predicate.", "Grammar"),
        BankItem("True or False: A synonym has a similar meaning to another word.", listOf("True", "False"), 0, "Synonyms are words with similar meanings.", "True / False"),
        BankItem("In a story, what does 'setting' describe?", listOf("Where and when", "Only the villain", "The final answer", "The title font"), 0, "Setting is the time and place of a story.", "Reading")
    )
    "Filipino" -> listOf(
        BankItem("Tama o Mali: Ang pangngalan ay ngalan ng tao, bagay, lugar, o ideya.", listOf("Tama", "Mali"), 0, "Ang pangngalan ay tumutukoy sa tao, bagay, lugar, hayop, o ideya.", "Tama / Mali"),
        BankItem("Alin ang wastong gamit ng 'ng' sa pangungusap?", listOf("Kumain ng mangga si Ana.", "Kumain nang mangga si Ana.", "Kumain ng si Ana mangga.", "Ng kumain mangga Ana."), 0, "Ginagamit ang 'ng' bilang pananda sa layon sa pangungusap.", "Grammar"),
        BankItem("Ano ang pangunahing layunin ng isang talata?", listOf("Maglahad ng malinaw na ideya", "Maglagay ng random na salita", "Tanggalin ang paksa", "Ulitin ang pamagat lamang"), 0, "Ang talata ay naglalahad at nag-uugnay ng mga ideya.", "Pag-unawa"),
        BankItem("Alin ang kasingkahulugan ng 'mabilis'?", listOf("Matulin", "Mabagal", "Tahimik", "Mabigat"), 0, "Ang matulin ay kasingkahulugan ng mabilis.", "Talasalitaan"),
        BankItem("Tama o Mali: Ang pandiwa ay nagsasaad ng kilos o gawain.", listOf("Tama", "Mali"), 0, "Ang pandiwa ay nagsasaad ng kilos o kalagayan.", "Tama / Mali"),
        BankItem("Alin ang pang-uri sa 'Mabait na bata ang tumulong.'?", listOf("Bata", "Tumulong", "Mabait", "Ang"), 2, "Ang 'mabait' ay naglalarawan sa bata.", "Grammar"),
        BankItem("Ano ang kasalungat ng 'malinis'?", listOf("Marumi", "Mabango", "Maayos", "Maliwanag"), 0, "Ang marumi ay kasalungat ng malinis.", "Talasalitaan")
    )
    "Araling Panlipunan" -> listOf(
        BankItem("Tama o Mali: Ang Pilipinas ay isang kapuluan.", listOf("Tama", "Mali"), 0, "Ang Pilipinas ay binubuo ng maraming pulo.", "Tama / Mali"),
        BankItem("Alin ang halimbawa ng yamang tubig?", listOf("Ilog", "Ginto", "Kagubatan", "Lupaing sakahan"), 0, "Ang ilog ay anyong tubig at likas na yaman.", "Concept"),
        BankItem("Bakit mahalaga ang mga batas sa isang lipunan?", listOf("Upang magkaroon ng kaayusan", "Upang walang tuntunin", "Upang alisin ang pamahalaan", "Upang bawasan ang edukasyon"), 0, "Tumutulong ang batas sa pagpapanatili ng kaayusan at karapatan.", "Reasoning"),
        BankItem("Alin ang halimbawa ng yamang mineral?", listOf("Ginto", "Palay", "Isda", "Kahoy"), 0, "Ang ginto ay yamang mineral.", "Concept"),
        BankItem("Ano ang tawag sa pag-aaral ng lokasyon at anyong lupa?", listOf("Heograpiya", "Panitikan", "Pisika", "Musika"), 0, "Ang heograpiya ay nag-aaral ng lugar, lokasyon, at pisikal na katangian ng daigdig.", "Concept"),
        BankItem("Tama o Mali: Ang mamamayan ay bahagi ng isang pamayanan.", listOf("Tama", "Mali"), 0, "Ang mga mamamayan ay bumubuo ng mga pamayanan.", "Tama / Mali")
    )
    "ICT" -> listOf(
        BankItem("True or False: A strong password should be easy for everyone to guess.", listOf("True", "False"), 1, "Strong passwords should be difficult to guess.", "True / False"),
        BankItem("Which file extension is commonly used for a plain text file?", listOf(".txt", ".mp3", ".png", ".exe"), 0, "The .txt extension is commonly used for plain text.", "Digital Literacy"),
        BankItem("What should you do before opening an unknown email attachment?", listOf("Verify the sender and attachment", "Open immediately", "Forward to everyone", "Disable security"), 0, "Verify unexpected attachments before opening them.", "Safety"),
        BankItem("Which is an example of cloud storage?", listOf("Online file storage", "A keyboard", "A monitor", "A USB cable"), 0, "Cloud storage stores files on remote servers accessed over the internet.", "Concept"),
        BankItem("What does URL identify?", listOf("A web address", "A battery level", "A keyboard key", "A screen size"), 0, "URL means Uniform Resource Locator and identifies a web resource address.", "Concept"),
        BankItem("True or False: Software updates can include security fixes.", listOf("True", "False"), 0, "Updates often include bug fixes and security patches.", "True / False")
    )
    "PE & Health" -> listOf(
        BankItem("Tama o Mali: Mahalaga ang warm-up bago ang masiglang ehersisyo.", listOf("Tama", "Mali"), 0, "Ang warm-up ay naghahanda ng katawan para sa activity.", "Tama / Mali"),
        BankItem("Ano ang dapat unahin kapag sobrang pagod na habang nag-eehersisyo?", listOf("Magpahinga at uminom ng tubig", "Dagdagan ang bilis", "Huwag pansinin", "Magpatuloy nang walang pahinga"), 0, "Mahalagang makinig sa katawan at magpahinga kapag kinakailangan.", "Scenario"),
        BankItem("Alin ang mas mainam na source ng hydration sa regular na activity?", listOf("Tubig", "Soft drink lamang", "Candy", "Chips"), 0, "Tubig ay pangunahing paraan upang mapanatili ang hydration.", "Health"),
        BankItem("Alin ang halimbawa ng flexibility exercise?", listOf("Stretching", "Typing", "Reading", "Watching TV"), 0, "Ang stretching ay tumutulong sa flexibility.", "Fitness"),
        BankItem("Tama o Mali: Ang sapat na tulog ay bahagi ng healthy lifestyle.", listOf("Tama", "Mali"), 0, "Mahalaga ang sapat na pahinga para sa kalusugan at performance.", "Tama / Mali"),
        BankItem("Ano ang magandang gawin kapag nahihilo habang nag-eehersisyo?", listOf("Huminto, magpahinga, at magsabi sa coach/trusted adult", "Dagdagan ang intensity", "Tumakbo nang mas mabilis", "Huwag pansinin"), 0, "Ang pagkahilo ay dahilan para huminto at humingi ng angkop na tulong.", "Scenario")
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
    .put("type", question.type)

private fun decodeQuestion(obj: JSONObject): Question = Question(
    obj.optString("id"),
    obj.optString("subject"),
    obj.optString("text"),
    decodeStrings(obj.optString("choices", "[]")),
    obj.optInt("answer"),
    obj.optString("explanation"),
    obj.optString("type", "Multiple Choice")
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
