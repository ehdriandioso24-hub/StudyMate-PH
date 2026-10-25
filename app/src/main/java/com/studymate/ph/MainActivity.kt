package com.studymate.ph

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.media.AudioAttributes
import android.media.ToneGenerator
import android.os.Bundle
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
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

private val bg = Color(0xFF07111F)
private val panel = Color(0xFF111C30)
private val purple = Color(0xFF9B7BFF)
private val pink = Color(0xFFFF72C6)
private val cyan = Color(0xFF35D7F2)
private val muted = Color(0xFFB4C2D8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appPrefs = getSharedPreferences("studymate_data", Context.MODE_PRIVATE)
        setContent {
            val appColors = darkColorScheme(
                primary = Color(0xFF9B7BFF),
                secondary = Color(0xFF35D7F2),
                tertiary = Color(0xFFFF72C6),
                background = Color(0xFF07111F),
                surface = Color(0xFF111C30),
                onPrimary = Color(0xFF100A25),
                onBackground = Color(0xFFE6F7FF),
                onSurface = Color(0xFFE6F7FF)
            )
            MaterialTheme(
                colorScheme = appColors,
                shapes = Shapes(
                    extraSmall = RoundedCornerShape(8.dp),
                    small = RoundedCornerShape(12.dp),
                    medium = RoundedCornerShape(16.dp),
                    large = RoundedCornerShape(22.dp),
                    extraLarge = RoundedCornerShape(28.dp)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = appColors.background
                ) {
                    var showSplash by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        delay(2400)
                        showSplash = false
                    }
                    Box(Modifier.fillMaxSize()) {
                        StudyMateApp()
                        if (showSplash) {
                            StudyMateSplash()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyMateSplash() {
    var pulse by remember { mutableFloatStateOf(0.72f) }
    LaunchedEffect(Unit) {
        while (true) {
            pulse = if (pulse < 0.9f) 1f else 0.72f
            delay(420)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF25205A), Color(0xFF10172E), Color(0xFF050B16)),
                    radius = 1000f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .background(
                        Brush.linearGradient(listOf(cyan.copy(alpha = pulse), purple, pink)),
                        RoundedCornerShape(34.dp)
                    )
                    .padding(3.dp)
                    .background(Color(0xFF091225), RoundedCornerShape(31.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "SM",
                    color = Color.White,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black
                )
            }
            Text(
                "StudyMate PH",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "LEARN • LEVEL UP • SHINE",
                color = cyan,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier.width(170.dp).height(4.dp),
                color = pink,
                trackColor = Color.White.copy(alpha = 0.12f)
            )
            Text("Preparing your study space…", color = muted, style = MaterialTheme.typography.bodySmall)
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
                "Araling Panlipunan", "ICT", "PE & Health", "Study Skills").forEach { subject ->
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
    var experienceXp by remember { mutableIntStateOf(prefs.getInt("experienceXp", 0).coerceAtLeast(0)) }
    val currentWeekKey = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
    var weeklyChallengeKey by remember { mutableStateOf(prefs.getString("weeklyChallengeKey", currentWeekKey) ?: currentWeekKey) }
    var weeklyAnswers by remember { mutableIntStateOf(prefs.getInt("weeklyAnswers", 0).coerceAtLeast(0)) }
    var weeklyRewardClaimed by remember { mutableStateOf(prefs.getBoolean("weeklyRewardClaimed", false)) }
    val friendNames = remember { mutableStateListOf<String>().apply { addAll(decodeStrings(prefs.getString("friendNames", "[]") ?: "[]")) } }
    var friendInput by rememberSaveable { mutableStateOf("") }
    var examMode by rememberSaveable { mutableStateOf(prefs.getBoolean("activeExamMode", false)) }
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
    var selectedSubject by rememberSaveable { mutableStateOf(prefs.getString("activeSelectedSubject", "Mixed") ?: "Mixed") }
    var tickingSound by remember { mutableStateOf(prefs.getBoolean("tickingSound", true)) }
    var musicVolume by remember { mutableStateOf(prefs.getFloat("musicVolume", 0.35f).coerceIn(0f, 1f)) }
    var touchVolume by remember { mutableStateOf(prefs.getFloat("touchVolume", 0.65f).coerceIn(0f, 1f)) }
    var coinVolume by remember { mutableStateOf(prefs.getFloat("coinVolume", 0.80f).coerceIn(0f, 1f)) }
    var correctVolume by remember { mutableStateOf(prefs.getFloat("correctVolume", 0.80f).coerceIn(0f, 1f)) }
    var wrongVolume by remember { mutableStateOf(prefs.getFloat("wrongVolume", 0.80f).coerceIn(0f, 1f)) }
    var gameOverVolume by remember { mutableStateOf(prefs.getFloat("gameOverVolume", 0.85f).coerceIn(0f, 1f)) }
    var levelUpVolume by remember { mutableStateOf(prefs.getFloat("levelUpVolume", 0.85f).coerceIn(0f, 1f)) }
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 55) }
    val soundPool = remember {
        SoundPool.Builder().setMaxStreams(6).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
    }
    val sfxIds = remember(soundPool) {
        mapOf(
            "touch" to soundPool.load(context, R.raw.touch_button, 1),
            "correct" to soundPool.load(context, R.raw.correct_answer, 1),
            "wrong" to soundPool.load(context, R.raw.wrong_answer, 1),
            "level" to soundPool.load(context, R.raw.level_up_bravo, 1),
            "coin" to soundPool.load(context, R.raw.coin_collect, 1),
            "gameover" to soundPool.load(context, R.raw.game_over, 1)
        )
    }
    fun playSfx(name: String, volume: Float) {
        val id = sfxIds[name] ?: return
        if (volume > 0f) runCatching { soundPool.play(id, volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f), 1, 0, 1f) }
    }
    DisposableEffect(Unit) { onDispose { toneGenerator.release(); soundPool.release() } }


    var loggedIn by remember {
        mutableStateOf(prefs.getBoolean("loggedIn", false))
    }
    var studentName by remember {
        mutableStateOf(prefs.getString("studentName", "") ?: "")
    }
    var studentEmail by remember {
        mutableStateOf(prefs.getString("studentEmail", "") ?: "")
    }
    var profileAvatar by remember {
        mutableStateOf(prefs.getString("profileAvatar", "🎓") ?: "🎓")
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

    var questions by remember {
        mutableStateOf(decodeQuestions(prefs.getString("activeQuizQuestions", "[]") ?: "[]"))
    }
    var questionIndex by rememberSaveable { mutableIntStateOf(prefs.getInt("activeQuestionIndex", 0).coerceAtLeast(0)) }
    var levelUpFrom by rememberSaveable { mutableIntStateOf(1) }
    var selectedAnswer by rememberSaveable { mutableIntStateOf(prefs.getInt("activeSelectedAnswer", -1)) }
    var revealed by rememberSaveable { mutableStateOf(prefs.getBoolean("activeRevealed", false)) }
    var secondsLeft by rememberSaveable { mutableIntStateOf(prefs.getInt("activeSecondsLeft", 30).coerceIn(0, 45)) }
    var lives by rememberSaveable { mutableIntStateOf(prefs.getInt("activeLives", 3).coerceIn(0, 3)) }
    var streak by rememberSaveable { mutableIntStateOf(prefs.getInt("activeStreak", 0).coerceAtLeast(0)) }
    var quizCorrect by rememberSaveable { mutableIntStateOf(prefs.getInt("activeQuizCorrect", 0).coerceAtLeast(0)) }
    var quizScore by rememberSaveable { mutableIntStateOf(prefs.getInt("activeQuizScore", 0).coerceAtLeast(0)) }
    var used5050 by rememberSaveable { mutableStateOf(prefs.getBoolean("activeUsed5050", false)) }
    var usedTimeFreeze by rememberSaveable { mutableStateOf(prefs.getBoolean("activeUsedTime", false)) }
    var usedSkip by rememberSaveable { mutableStateOf(prefs.getBoolean("activeUsedSkip", false)) }
    var free5050 by rememberSaveable { mutableStateOf(prefs.getBoolean("activeFree5050", false)) }
    var freeTime by rememberSaveable { mutableStateOf(prefs.getBoolean("activeFreeTime", false)) }
    var freeSkip by rememberSaveable { mutableStateOf(prefs.getBoolean("activeFreeSkip", false)) }
    var hiddenChoices by remember { mutableStateOf((prefs.getString("activeHiddenChoices", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.toSet()) }
    var quizResultSaved by rememberSaveable { mutableStateOf(false) }
    var quizStartedAt by rememberSaveable { mutableLongStateOf(prefs.getLong("activeQuizStartedAt", System.currentTimeMillis())) }
    var dailyChallengeActive by rememberSaveable { mutableStateOf(prefs.getBoolean("activeDailyChallenge", false)) }
    var missionDate by remember { mutableStateOf(prefs.getString("missionDate", "") ?: "") }
    var missionAnswers by remember { mutableIntStateOf(prefs.getInt("missionAnswers", 0).coerceAtLeast(0)) }
    var missionRewardClaimed by remember { mutableStateOf(prefs.getBoolean("missionRewardClaimed", false)) }
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
    var quizPaused by rememberSaveable { mutableStateOf(prefs.getBoolean("activeQuiz", false) && questions.isNotEmpty()) }
    LaunchedEffect(showCoinBurst) {
        if (showCoinBurst) {
            delay(1800)
            showCoinBurst = false
        }
    }
    LaunchedEffect(currentWeekKey) {
        if (weeklyChallengeKey != currentWeekKey) {
            weeklyChallengeKey = currentWeekKey
            weeklyAnswers = 0
            weeklyRewardClaimed = false
            prefs.edit()
                .putString("weeklyChallengeKey", currentWeekKey)
                .putInt("weeklyAnswers", 0)
                .putBoolean("weeklyRewardClaimed", false)
                .apply()
        }
    }
    var quickQuestion by remember { mutableStateOf<Question?>(null) }
    var quickAnswered by remember { mutableStateOf(false) }
    var quickSelected by rememberSaveable { mutableIntStateOf(-1) }

    val levelXp = experienceXp % 1000
    val levelXpProgress = levelXp / 1000f

    fun saveProgress() {
        subjectLevels[selectedSubject] = level
        prefs.edit()
            .putInt("level", level)
            .putInt("level_$selectedSubject", level)
            .putInt("answered", answered)
            .putInt("correct", correct)
            .putInt("coins", coins)
            .putInt("xp", coins)
            .putInt("experienceXp", experienceXp)
            .putString("weeklyChallengeKey", weeklyChallengeKey)
            .putInt("weeklyAnswers", weeklyAnswers)
            .putBoolean("weeklyRewardClaimed", weeklyRewardClaimed)
            .putString("friendNames", encodeStrings(friendNames))
            .putBoolean("activeExamMode", examMode)
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
            .putString("profileAvatar", profileAvatar)
            .putBoolean("loggedIn", loggedIn)
            .putString("missionDate", missionDate)
            .putInt("missionAnswers", missionAnswers)
            .putBoolean("missionRewardClaimed", missionRewardClaimed)
            .putString("activeQuizQuestions", encodeQuestions(questions))
            .putInt("activeQuestionIndex", questionIndex)
            .putInt("activeSelectedAnswer", selectedAnswer)
            .putBoolean("activeRevealed", revealed)
            .putInt("activeSecondsLeft", secondsLeft)
            .putInt("activeLives", lives)
            .putInt("activeStreak", streak)
            .putInt("activeQuizCorrect", quizCorrect)
            .putInt("activeQuizScore", quizScore)
            .putLong("activeQuizStartedAt", quizStartedAt)
            .putString("activeSelectedSubject", selectedSubject)
            .putBoolean("activeUsed5050", used5050)
            .putBoolean("activeUsedTime", usedTimeFreeze)
            .putBoolean("activeUsedSkip", usedSkip)
            .putBoolean("activeFree5050", free5050)
            .putBoolean("activeFreeTime", freeTime)
            .putBoolean("activeFreeSkip", freeSkip)
            .putString("activeHiddenChoices", hiddenChoices.joinToString(","))
            .putBoolean("activeDailyChallenge", dailyChallengeActive)
            .putBoolean("activeQuiz", questions.isNotEmpty() && !quizResultSaved)
            .apply()
    }

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
        val todayWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
        if (weeklyChallengeKey != todayWeek) {
            weeklyChallengeKey = todayWeek
            weeklyAnswers = 0
            weeklyRewardClaimed = false
        }
        weeklyAnswers++
        if (weeklyAnswers >= 25 && !weeklyRewardClaimed) {
            coins += 100
            weeklyRewardClaimed = true
            android.widget.Toast.makeText(context, "Weekly challenge complete! +100 coins 🎉", android.widget.Toast.LENGTH_LONG).show()
        }
        val isCorrect = index == q.answer
        if (isCorrect) {
            playSfx("correct", correctVolume)
            if (tickingSound) runCatching { toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 120) }
            experienceXp += 10
            correct++
            answered++
            coins += 10
            answeredIds.add(q.id)
            history.add(0, ReviewItem(q, index, true, level))
            while (history.size > 500) history.removeAt(history.lastIndex)
            saveProgress()
            android.widget.Toast.makeText(context, "Correct! +10 coins 🪙", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            playSfx("wrong", wrongVolume)
            answered++
            history.add(0, ReviewItem(q, index, false, level))
            while (history.size > 500) history.removeAt(history.lastIndex)
            saveProgress()
            android.widget.Toast.makeText(context, "Not quite. Saved to Review Wrong Answers.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun claimDailyLoginCoins() {
        val today = LocalDate.now().toString()
        if (dailyLoginClaimDate == today) return
        coins += 25
        playSfx("coin", coinVolume)
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
        playSfx("coin", coinVolume)
        when (type) {
            "5050" -> fiftyFiftyItems++
            "time" -> extraTimeItems++
            "skip" -> skipItems++
        }
        saveProgress()
        android.widget.Toast.makeText(context, "Purchased! Your coins and inventory were saved.", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun startQuiz(subject: String, daily: Boolean = false) {
        examMode = false
        quizPaused = false
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
        saveProgress()
        quizPaused = false
        screen = Screen.QUIZ.name
    }

    fun startExamMode() {
        examMode = true
        quizPaused = false
        selectedSubject = "Mixed"
        questionIndex = 0
        selectedAnswer = -1
        revealed = false
        secondsLeft = 60
        lives = 3
        streak = 0
        quizCorrect = 0
        quizScore = 0
        used5050 = false
        usedTimeFreeze = false
        usedSkip = false
        free5050 = false
        freeTime = false
        freeSkip = false
        hiddenChoices = emptySet()
        quizResultSaved = false
        quizStartedAt = System.currentTimeMillis()
        dailyChallengeActive = false
        val pool = (makeQuestions("Mixed", level) + importedQuestions).distinctBy { it.id }
        questions = pool.shuffled().take(20)
        saveProgress()
        screen = Screen.QUIZ.name
    }

    fun retryWrongAnswers() {
        val wrongQuestions = history.filter { !it.correct }.distinctBy { it.question.id }.map { it.question }
        if (wrongQuestions.isEmpty()) {
            android.widget.Toast.makeText(context, "Wala pang maling sagot na ire-review.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        examMode = false
        questions = wrongQuestions.shuffled()
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
        quizPaused = false
        saveProgress()
        screen = Screen.QUIZ.name
    }

    fun retryCurrentQuiz() {
        examMode = false
        quizPaused = false
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
        questions = questions.shuffled()
        saveProgress()
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
        prefs.edit()
            .putString("localLeaderboard", localLeaderboard.joinToString("\n"))
            .putBoolean("activeQuiz", false)
            .apply()
    }

    fun recordAnswer(index: Int, timedOut: Boolean = false) {
        val q = questions.getOrNull(questionIndex) ?: return
        if (revealed) return
        selectedAnswer = if (timedOut) -1 else index
        revealed = true
        val isCorrect = !timedOut && index == q.answer
        answered++
        if (isCorrect) {
            playSfx("correct", correctVolume)
            correct++
            experienceXp += 10
            quizCorrect++
            streak++
            val speedBonus = (secondsLeft * 2).coerceAtLeast(0)
            val comboBonus = if (streak >= 5) 100 else if (streak >= 3) 50 else 0
            quizScore += 100 + speedBonus + comboBonus
            coins += 10
        } else {
            playSfx("wrong", wrongVolume)
            if (tickingSound) runCatching { toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 160) }
            lives = (lives - 1).coerceAtLeast(0)
            streak = 0
        }
        answeredIds.add(q.id)
        history.add(0, ReviewItem(q, selectedAnswer, isCorrect, level))
        while (history.size > 500) history.removeAt(history.lastIndex)
        val todayForMission = LocalDate.now().toString()
        val thisWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
        if (weeklyChallengeKey != thisWeek) {
            weeklyChallengeKey = thisWeek
            weeklyAnswers = 0
            weeklyRewardClaimed = false
        }
        weeklyAnswers++
        if (weeklyAnswers >= 25 && !weeklyRewardClaimed) {
            coins += 100
            weeklyRewardClaimed = true
            android.widget.Toast.makeText(context, "Weekly challenge complete! +100 coins 🎉", android.widget.Toast.LENGTH_LONG).show()
        }
        if (missionDate != todayForMission) {
            missionDate = todayForMission
            missionAnswers = 0
            missionRewardClaimed = false
        }
        missionAnswers++
        if (missionAnswers >= 5 && !missionRewardClaimed) {
            coins += 30
            missionRewardClaimed = true
            android.widget.Toast.makeText(context, "Daily mission complete! +30 bonus coins 🎁", android.widget.Toast.LENGTH_LONG).show()
        }
        saveProgress()
        if (lives <= 0) {
            quizPaused = false
            saveQuizResult()
            screen = Screen.GAME_OVER.name
        }
    }

    fun finishQuiz() {
        quizPaused = false
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

    // Keep the same Home player alive across ordinary screens so the track does not restart.
    val musicTrack = when (currentScreen) {
        Screen.SUBJECTS -> "subjects"
        Screen.QUIZ -> "quiz"
        else -> "home"
    }
    var activeMusicPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(musicTrack, lifecycleOwner) {
        // Home music continues uninterrupted on Search, Notes, Timer, Progress, Flashcards,
        // Profile, Settings, Shop, and other non-quiz/non-subject screens.
        val musicResId = when (musicTrack) {
            "subjects" -> R.raw.all_subject_music
            "quiz" -> R.raw.quiz_music
            else -> R.raw.home_background_music
        }
        val player = musicResId?.let { resourceId ->
            runCatching { MediaPlayer.create(context.applicationContext, resourceId) }.getOrNull()
        }
        if (player != null) {
            player.isLooping = true
            player.setVolume(musicVolume, musicVolume)
            activeMusicPlayer = player
            runCatching { player.start() }
        }

        // Pause background music when the app goes to the background or is no longer visible.
        // Resume the same track when the app returns, without creating an extra player.
        val musicLifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> runCatching {
                    if (player?.isPlaying == true) player.pause()
                }
                Lifecycle.Event.ON_START -> runCatching {
                    if (player != null && !player.isPlaying) player.start()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(musicLifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(musicLifecycleObserver)
            activeMusicPlayer = null
            if (player != null) {
                runCatching { if (player.isPlaying) player.stop() }
                runCatching { player.release() }
            }
        }
    }
    SideEffect { activeMusicPlayer?.setVolume(musicVolume, musicVolume) }

    LaunchedEffect(currentScreen) {
        if (currentScreen == Screen.GAME_OVER) playSfx("gameover", gameOverVolume)
        if (currentScreen == Screen.LEVEL_UP || currentScreen == Screen.LEVEL_COMPLETE) playSfx("level", levelUpVolume)
    }

    BackHandler(enabled = currentScreen != Screen.HOME) {
        if (currentScreen == Screen.QUIZ && !quizResultSaved) {
            quizPaused = true
            saveProgress()
        }
        screen = Screen.HOME.name
    }

    // Thrill Mode countdown: auto-submit as incorrect at zero, with escalating red-zone ticks.
    LaunchedEffect(currentScreen, questionIndex, revealed, secondsLeft) {
        if (currentScreen == Screen.QUIZ && !revealed && questions.isNotEmpty()) {
            if (secondsLeft <= 0) {
                recordAnswer(-1, timedOut = true)
            } else {
                delay(1000)
                secondsLeft = (secondsLeft - 1).coerceAtLeast(0)
                prefs.edit().putInt("activeSecondsLeft", secondsLeft).apply()
                if (secondsLeft <= 10 && tickingSound) {
                    runCatching { toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, if (secondsLeft <= 5) 110 else 65) }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            GlassTopBar(
                screen = currentScreen,
                coins = coins,
                level = level,
                onBack = {
                    if (currentScreen == Screen.QUIZ && !quizResultSaved) {
                        quizPaused = true
                        saveProgress()
                    }
                    screen = Screen.HOME.name
                },
                onProfile = { screen = Screen.PROFILE.name }
            )
        },
        bottomBar = {
            GlassBottomNav(
                currentScreen = currentScreen,
                onNavigate = { screen = it.name }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                when (currentScreen) {
                    Screen.HOME -> {
                        NeonHeroCard(
                            name = if (studentName.isNotBlank()) studentName else "Student",
                            level = level,
                            coins = coins,
                            xp = levelXp,
                            xpProgress = levelXpProgress,
                            achievements = achievementsFor(answered, correct).size,
                            onProfile = { screen = Screen.PROFILE.name }
                        )

                        // Quick-access tiles use the existing destinations and handlers.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HomeShortcutTile(
                                modifier = Modifier.weight(1f),
                                icon = "🎮",
                                title = "Quizzes",
                                subtitle = "Play & earn XP",
                                accent = Color(0xFF9B7BFF),
                                onClick = { openQuickQuiz() }
                            )
                            HomeShortcutTile(
                                modifier = Modifier.weight(1f),
                                icon = "📝",
                                title = "Study Notes",
                                subtitle = "Your reviewers",
                                accent = Color(0xFF35D7F2),
                                onClick = { screen = Screen.NOTES.name }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HomeShortcutTile(
                                modifier = Modifier.weight(1f),
                                icon = "🗂️",
                                title = "Flashcards",
                                subtitle = "Quick review",
                                accent = Color(0xFFFF72C6),
                                onClick = { screen = Screen.FLASHCARDS.name }
                            )
                            HomeShortcutTile(
                                modifier = Modifier.weight(1f),
                                icon = "📚",
                                title = "Subjects",
                                subtitle = "Choose a topic",
                                accent = Color(0xFF35D7F2),
                                onClick = { screen = Screen.SUBJECTS.name }
                            )
                        }

                        if (quizPaused && questions.isNotEmpty() && !quizResultSaved) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF111C30)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, purple.copy(alpha = 0.42f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("⏸️ Quiz Paused", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = purple)
                                    Text("Question ${questionIndex + 1} of ${questions.size} • Score: $quizScore • Lives: $lives", color = muted)
                                    Button(
                                        onClick = { quizPaused = false; screen = Screen.QUIZ.name },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp)
                                    ) { Text("▶ Continue Quiz", fontWeight = FontWeight.Bold) }
                                    OutlinedButton(
                                        onClick = { retryCurrentQuiz() },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp)
                                    ) { Text("🔄 Restart Quiz") }
                                }
                            }
                        }

                        val missionToday = LocalDate.now().toString()
                        val missionProgress = if (missionDate == missionToday) missionAnswers.coerceAtMost(5) else 0
                        CardBox(
                            "🎯 Daily Mission • ${if (missionRewardClaimed && missionDate == missionToday) "Completed" else "$missionProgress/5 answers"}",
                            if (missionRewardClaimed && missionDate == missionToday) "Great work! You earned today's 30 bonus coins. Come back tomorrow for a new mission." else "Answer 5 quiz questions today to earn +30 bonus coins. Progress: $missionProgress/5."
                        ) {}

                        CardBox("📴 Offline Ready", "Core quizzes, notes, flashcards, progress, coins, and shop inventory are stored on this device. Online sync is not configured yet.") {}
                        CardBox(
                            "🗓️ Weekly Challenge • ${weeklyAnswers.coerceAtMost(25)}/25 answers",
                            if (weeklyRewardClaimed) "Completed! You earned +100 bonus coins this week." else "Answer 25 questions this week to earn +100 bonus coins."
                        ) {}
                        CardBox("⭐ Experience Points", "$experienceXp XP • ${1000 - levelXp} XP to next XP milestone") {}

                        NeonActionCard(
                            title = "📝 Exam Mode",
                            subtitle = "20 mixed questions • 60 seconds each • 3 lives • no free power-ups",
                            badge = "EXAM",
                            accent = Color(0xFF35D7F2),
                            onClick = { startExamMode() }
                        )
                        NeonActionCard(
                            title = "Review Wrong Answers",
                            subtitle = "Retry ${history.count { !it.correct }} saved incorrect answers",
                            badge = "PRACTICE",
                            accent = Color(0xFF9B7BFF),
                            onClick = { screen = Screen.REVIEW.name }
                        )

                        NeonActionCard(
                            title = "Quick Quiz",
                            subtitle = "Mixed questions from all 7 subjects",
                            badge = "Fast XP",
                            accent = Color(0xFFFF72C6),
                            onClick = { openQuickQuiz() }
                        )

                        NeonActionCard(
                            title = "🔥 Thrill Mode",
                            subtitle = "20 questions • 30 sec each • 3 lives • 75% to pass",
                            badge = "RED ZONE",
                            accent = Color(0xFFF43F5E),
                            onClick = { startQuiz("Mixed", daily = false) }
                        )

                        NeonActionCard(
                            title = "Daily Challenge",
                            subtitle = "10 mixed questions • +100 coins bonus • Streak: $dailyStreak",
                            badge = if (dailyCompletedDate == LocalDate.now().toString()) "Done Today" else "Ready",
                            accent = Color(0xFFF59E0B),
                            onClick = { startQuiz("Mixed", daily = true) }
                        )

                        val today = LocalDate.now().toString()
                        val claimed = dailyLoginClaimDate == today
                        NeonActionCard(
                            title = if (claimed) "Daily Login Coins Collected" else "Claim Daily Login Coins",
                            subtitle = if (claimed) "Come back tomorrow for another +25 coins" else "Tap here to claim your free +25 coins!",
                            badge = if (claimed) "Claimed" else "+25 Coins",
                            accent = Color(0xFF10B981),
                            onClick = { playSfx("touch", touchVolume); claimDailyLoginCoins() }
                        )

                        NeonActionCard(
                            title = "Coin Shop",
                            subtitle = "Spend coins on 50:50, +15 sec, and Skip • $coins coins available",
                            badge = "Power-Ups",
                            accent = Color(0xFF06B6D4),
                            onClick = { showCoinShopModal = true }
                        )

                        NeonActionCard(
                            title = "My Subjects",
                            subtitle = "7 subjects • Level 1–100",
                            badge = "Categories",
                            accent = Color(0xFF3B82F6),
                            onClick = { screen = Screen.SUBJECTS.name }
                        )

                        NeonActionCard(
                            title = "Flashcards",
                            subtitle = "Quick memory review cards",
                            badge = "Review",
                            accent = Color(0xFFEC4899),
                            onClick = { screen = Screen.FLASHCARDS.name }
                        )

                        NeonActionCard(
                            title = "Study Notes",
                            subtitle = "Write and save your reviewer notes",
                            badge = "Notes",
                            accent = Color(0xFF8B5CF6),
                            onClick = { screen = Screen.NOTES.name }
                        )

                        NeonActionCard(
                            title = "Study Timer",
                            subtitle = "Focus timer with customizable session lengths",
                            badge = "Timer",
                            accent = Color(0xFF6366F1),
                            onClick = { screen = Screen.TIMER.name }
                        )
                    }

                    Screen.SUBJECTS -> {
                        Heading("📚 Subjects", "Select a subject to test your level.")
                        val allSubjects = (listOf("Mixed", "Math", "Science", "English", "Filipino", "Araling Panlipunan", "ICT", "PE & Health", "Study Skills") + customSubjects).distinct()
                        allSubjects.forEach { subj ->
                            CardBox(
                                title = "${emoji(subj)} $subj",
                                subtitle = "Level ${subjectLevels[subj] ?: 1} • Tap to start quiz"
                            ) {
                                startQuiz(subj)
                            }
                        }
                        OutlinedButton(
                            onClick = { showSubjectDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("+ Add Custom Subject")
                        }
                    }

                    Screen.NOTES -> {
                        NotesPage(
                            notes = notes,
                            onSave = { title, body ->
                                notes.add(0, Note(System.currentTimeMillis(), title, body))
                                prefs.edit().putString("notes", encodeNotes(notes)).apply()
                            },
                            onEdit = { oldNote, newTitle, newBody ->
                                val index = notes.indexOfFirst { it.id == oldNote.id }
                                if (index != -1) {
                                    notes[index] = Note(oldNote.id, newTitle, newBody)
                                    prefs.edit().putString("notes", encodeNotes(notes)).apply()
                                }
                            },
                            onDelete = { note ->
                                notes.remove(note)
                                prefs.edit().putString("notes", encodeNotes(notes)).apply()
                            }
                        )
                    }

                    Screen.FLASHCARDS -> {
                        FlashcardsPage()
                    }

                    Screen.TIMER -> {
                        TimerPage(prefs = prefs)
                    }

                    Screen.PROGRESS -> {
                        Heading("📊 Learning Progress", "Overview of your overall stats.")
                        CardBox("🎯 Accuracy Rate", if (answered > 0) "${(correct * 100) / answered}%" else "0%") {}
                        CardBox("📝 Total Answered", "$answered questions") {}
                        CardBox("✅ Correct Answers", "$correct correct") {}
                        CardBox("🔥 Current Streak", "$dailyStreak days") {}
                        CardBox("⭐ Total XP", "$experienceXp XP • Level $level") {}
                        CardBox("🧠 Wrong Answers to Review", "${history.count { !it.correct }} saved incorrect answers") { screen = Screen.REVIEW.name }
                        CardBox("🗓️ Weekly Challenge", "${weeklyAnswers.coerceAtMost(25)}/25 answers • ${if (weeklyRewardClaimed) "Reward claimed" else "+100 coins at 25"}") {}
                        CardBox("🏆 Local Leaderboard", if (localLeaderboard.isEmpty()) "No records yet." else localLeaderboard.take(10).mapIndexed { index, row ->
                            val parts = row.split("|")
                            val player = parts.getOrNull(3).orEmpty().ifBlank { "Student" }
                            val score = parts.getOrNull(0).orEmpty()
                            val right = parts.getOrNull(1).orEmpty()
                            "${index + 1}. $player • $score pts • $right correct"
                        }.joinToString("\n")) {}
                        Heading("👥 Friends Leaderboard", "Local friend list on this device. Online score sync is not configured.")
                        OutlinedTextField(
                            value = friendInput,
                            onValueChange = { friendInput = it },
                            label = { Text("Friend name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val friend = friendInput.trim()
                                if (friend.isNotBlank() && friendNames.none { it.equals(friend, ignoreCase = true) }) {
                                    friendNames.add(friend)
                                    prefs.edit().putString("friendNames", encodeStrings(friendNames)).apply()
                                }
                                friendInput = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Add Friend") }
                        val friendRows = (listOf("${studentName.ifBlank { "You" }} • $quizScore points") + friendNames.map { "$it • score not synced" })
                        friendRows.forEachIndexed { index, row -> CardBox("${index + 1}. $row", if (index == 0) "This device's latest quiz score" else "Add online sync later to compare real scores") {} }
                    }

                    Screen.REVIEW -> {
                        Heading("🧠 Review Wrong Answers", "Revisit missed questions, explanations, and retry them in a practice quiz.")
                        val wrongItems = history.filter { !it.correct }.distinctBy { it.question.id }
                        if (wrongItems.isEmpty()) {
                            CardBox("All caught up!", "Wala pang maling sagot. Maglaro muna ng quiz para makapagdagdag ng review items.") {}
                        } else {
                            wrongItems.forEachIndexed { index, item ->
                                CardBox(
                                    "${index + 1}. ${item.question.subject} • Missed",
                                    "${item.question.text}\n\nYour answer: ${item.question.choices.getOrNull(item.selected) ?: "No answer / timeout"}\nCorrect answer: ${item.question.choices.getOrNull(item.question.answer) ?: "Unavailable"}\nExplanation: ${item.question.explanation}"
                                ) {}
                            }
                            Button(onClick = { retryWrongAnswers() }, modifier = Modifier.fillMaxWidth()) { Text("🔁 Retry Wrong Answers") }
                        }
                        OutlinedButton(onClick = { screen = Screen.PROGRESS.name }, modifier = Modifier.fillMaxWidth()) { Text("Back to Statistics") }
                    }

                    Screen.SEARCH -> {
                        Heading("🔍 Search Questions", "Search questions across all subjects.")
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Search keyword...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        if (searchQuery.isNotBlank()) {
                            val results = makeQuestions("Mixed", level).filter {
                                it.text.contains(searchQuery, ignoreCase = true) || it.subject.contains(searchQuery, ignoreCase = true)
                            }
                            if (results.isEmpty()) {
                                Text("No questions found matching '$searchQuery'.", color = muted)
                            } else {
                                results.take(15).forEach { q ->
                                    CardBox("${emoji(q.subject)} ${q.subject}", q.text) {}
                                }
                            }
                        } else {
                            Text("Type a word above to search questions.", color = muted)
                        }
                    }

                    Screen.PROFILE -> {
                        ProfilePage(
                            name = studentName,
                            avatar = profileAvatar,
                            onAvatarChange = { profileAvatar = it; prefs.edit().putString("profileAvatar", it).apply() },
                            email = studentEmail,
                            level = level,
                            answered = answered,
                            correct = correct,
                            coins = coins,
                            dailyStreak = dailyStreak,
                            fiftyFifty = fiftyFiftyItems,
                            extraTime = extraTimeItems,
                            skip = skipItems,
                            experienceXp = experienceXp,
                            tickingSound = tickingSound,
                            musicVolume = musicVolume,
                            touchVolume = touchVolume,
                            coinVolume = coinVolume,
                            correctVolume = correctVolume,
                            wrongVolume = wrongVolume,
                            gameOverVolume = gameOverVolume,
                            levelUpVolume = levelUpVolume,
                            onMusicVolumeChange = { musicVolume = it; prefs.edit().putFloat("musicVolume", it).apply() },
                            onTouchVolumeChange = { touchVolume = it; prefs.edit().putFloat("touchVolume", it).apply() },
                            onCoinVolumeChange = { coinVolume = it; prefs.edit().putFloat("coinVolume", it).apply() },
                            onCorrectVolumeChange = { correctVolume = it; prefs.edit().putFloat("correctVolume", it).apply() },
                            onWrongVolumeChange = { wrongVolume = it; prefs.edit().putFloat("wrongVolume", it).apply() },
                            onGameOverVolumeChange = { gameOverVolume = it; prefs.edit().putFloat("gameOverVolume", it).apply() },
                            onLevelUpVolumeChange = { levelUpVolume = it; prefs.edit().putFloat("levelUpVolume", it).apply() },
                            importMessage = importMessage,
                            onToggleTicking = {
                                tickingSound = !tickingSound
                                prefs.edit().putBoolean("tickingSound", tickingSound).apply()
                            },
                            onImport = { importMessage = "Lessons/Questions import feature ready." },
                            onSave = { name, email ->
                                studentName = name
                                studentEmail = email
                                saveProgress()
                                android.widget.Toast.makeText(context, "Profile saved!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onLogout = {
                                loggedIn = false
                                saveProgress()
                            }
                        )
                    }

                    Screen.QUIZ -> {
                        val q = questions.getOrNull(questionIndex)
                        if (q == null) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No questions available for this level.")
                                Button(onClick = { screen = Screen.HOME.name }) { Text("Back to Home") }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Question ${questionIndex + 1}/${questions.size}", fontWeight = FontWeight.Bold, color = purple)
                                    Spacer(Modifier.weight(1f))
                                    Text(if (examMode) "📝 EXAM • ❤️ $lives" else "❤️ $lives", color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold)
                                }
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = if (secondsLeft <= 10) Color(0xFF7F1D1D) else panel),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(
                                            if (examMode) "📝 EXAM TIMER" else if (secondsLeft <= 10) "🚨 RED ZONE!  HURRY!" else "⏱ Time remaining",
                                            color = if (secondsLeft <= 10) Color(0xFFFF6B6B) else muted,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text("$secondsLeft seconds", color = if (secondsLeft <= 10) Color.White else purple, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                                        LinearProgressIndicator(
                                            progress = { secondsLeft / (if (examMode) 60f else 30f) },
                                            modifier = Modifier.fillMaxWidth(),
                                            color = if (secondsLeft <= 10) Color(0xFFF43F5E) else purple
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(onClick = {
                                        if (!examMode && !revealed && (free5050 || fiftyFiftyItems > 0)) {
                                            val wrong = q.choices.indices.filter { it != q.answer && it !in hiddenChoices }.shuffled().take(2).toSet()
                                            hiddenChoices = hiddenChoices + wrong
                                            used5050 = true
                                            if (!free5050 && fiftyFiftyItems > 0) fiftyFiftyItems-- else free5050 = false
                                            saveProgress()
                                        }
                                    }, enabled = !examMode && !revealed && (free5050 || fiftyFiftyItems > 0), modifier = Modifier.weight(1f)) { Text(if (examMode) "🎯 50:50 OFF" else if (free5050) "🎯 50:50 FREE" else "🎯 50:50 ($fiftyFiftyItems)") }
                                    OutlinedButton(onClick = {
                                        if (!examMode && !revealed && (freeTime || extraTimeItems > 0)) {
                                            secondsLeft += 15
                                            usedTimeFreeze = true
                                            if (!freeTime && extraTimeItems > 0) extraTimeItems-- else freeTime = false
                                            saveProgress()
                                        }
                                    }, enabled = !examMode && !revealed && (freeTime || extraTimeItems > 0), modifier = Modifier.weight(1f)) { Text(if (examMode) "⏱ TIME FIXED" else if (freeTime) "⏱ +15 sec FREE" else "⏱ +15 sec ($extraTimeItems)") }
                                    OutlinedButton(onClick = {
                                        if (!examMode && !revealed && (freeSkip || skipItems > 0)) {
                                            usedSkip = true
                                            if (!freeSkip && skipItems > 0) skipItems-- else freeSkip = false
                                            saveProgress()
                                            hiddenChoices = emptySet()
                                            selectedAnswer = -1
                                            revealed = false
                                            if (questionIndex + 1 < questions.size) {
                                                questionIndex++
                                                secondsLeft = if (examMode) 60 else 30
                                                saveProgress()
                                            } else finishQuiz()
                                        }
                                    }, enabled = !examMode && !revealed && (freeSkip || skipItems > 0), modifier = Modifier.weight(1f)) { Text(if (examMode) "⏭ SKIP OFF" else if (freeSkip) "⏭ Skip FREE" else "⏭ Skip ($skipItems)") }
                                }
                                Text(q.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                                q.choices.forEachIndexed { idx, choice ->
                                    if (idx !in hiddenChoices) {
                                        val btnColor = when {
                                            revealed && idx == q.answer -> Color(0xFF10B981)
                                            revealed && idx == selectedAnswer -> Color(0xFFF43F5E)
                                            else -> panel
                                        }
                                        Button(
                                            onClick = { recordAnswer(idx) },
                                            enabled = !revealed,
                                            colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(choice, color = Color.White)
                                        }
                                    }
                                }

                                if (revealed) {
                                    Text(
                                        if (selectedAnswer == q.answer) "Correct! 🎉" else "Incorrect! ${q.explanation}",
                                        color = if (selectedAnswer == q.answer) Color(0xFF34D399) else Color(0xFFF87171),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Button(
                                        onClick = {
                                            revealed = false
                                            selectedAnswer = -1
                                            hiddenChoices = emptySet()
                                            if (questionIndex + 1 < questions.size && lives > 0) {
                                                questionIndex++
                                                secondsLeft = if (examMode) 60 else 30
                                                saveProgress()
                                            } else {
                                                finishQuiz()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(if (questionIndex + 1 < questions.size && lives > 0) "Next Question →" else "Finish Quiz")
                                    }
                                }
                            }
                        }
                    }

                    Screen.GAME_OVER -> {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("💔 Game Over", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFFF43F5E))
                            Text("Score: $quizScore • Correct: $quizCorrect/${questions.size}", color = muted)
                            Button(onClick = { retryCurrentQuiz() }, modifier = Modifier.fillMaxWidth()) { Text("Try Again") }
                            OutlinedButton(onClick = { screen = Screen.HOME.name }, modifier = Modifier.fillMaxWidth()) { Text("Back to Home") }
                        }
                    }

                    Screen.LEVEL_UP -> {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("🎉 Level Up!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                            Text("You reached Level $level in $selectedSubject!", color = muted)
                            Button(onClick = { screen = Screen.HOME.name }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
                        }
                    }

                    Screen.LEVEL_COMPLETE -> {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("🏆 Max Level Reached!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFFFDE68A))
                            Text("You completed Level 100!", color = muted)
                            Button(onClick = { screen = Screen.HOME.name }, modifier = Modifier.fillMaxWidth()) { Text("Back to Home") }
                        }
                    }

                    else -> {
                        Text("Page under construction.", color = muted)
                    }
                }
            }
        }
    }

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
                                if (quickSelected == question.answer) "Correct! +10 coins 🪙" else "Incorrect. ${question.explanation}",
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
private fun VolumeControl(label: String, volume: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text("${(volume * 100).toInt()}%", color = MaterialTheme.colorScheme.secondary)
        }
        Slider(value = volume, onValueChange = onChange, valueRange = 0f..1f)
    }
}

@Composable
private fun ProfilePage(
    name: String,
    avatar: String,
    onAvatarChange: (String) -> Unit,
    email: String,
    level: Int,
    answered: Int,
    correct: Int,
    coins: Int,
    dailyStreak: Int,
    fiftyFifty: Int,
    extraTime: Int,
    skip: Int,
    experienceXp: Int,
    tickingSound: Boolean,
    musicVolume: Float,
    touchVolume: Float,
    coinVolume: Float,
    correctVolume: Float,
    wrongVolume: Float,
    gameOverVolume: Float,
    levelUpVolume: Float,
    onMusicVolumeChange: (Float) -> Unit,
    onTouchVolumeChange: (Float) -> Unit,
    onCoinVolumeChange: (Float) -> Unit,
    onCorrectVolumeChange: (Float) -> Unit,
    onWrongVolumeChange: (Float) -> Unit,
    onGameOverVolumeChange: (Float) -> Unit,
    onLevelUpVolumeChange: (Float) -> Unit,
    importMessage: String,
    onToggleTicking: () -> Unit,
    onImport: () -> Unit,
    onSave: (String, String) -> Unit,
    onLogout: () -> Unit
) {
    var localName by rememberSaveable(name) { mutableStateOf(name) }
    var localEmail by rememberSaveable(email) { mutableStateOf(email) }

    Heading("$avatar Student Profile", "Customize your profile and learning summary.")
    Text("Choose your avatar", fontWeight = FontWeight.SemiBold)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("🎓", "🦊", "🐼", "🚀", "🌟", "📚", "🐯", "🐸", "🦄", "⚡", "🎧", "🧠")
            .chunked(6)
            .forEach { avatarRow ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    avatarRow.forEach { icon ->
                        Surface(
                            modifier = Modifier.weight(1f).clickable { onAvatarChange(icon) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (avatar == icon) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
                            border = if (avatar == icon) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Text(icon, modifier = Modifier.padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
    }

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
    CardBox("⭐ Experience", "$experienceXp XP") {}
    CardBox("🪙 Coins", "$coins coins") {}
    CardBox("🛒 Coin Shop Inventory", "50:50: $fiftyFifty • +15 sec: $extraTime • Skip: $skip") {}
    CardBox("🔥 Daily streak", "$dailyStreak day(s)") {}
    CardBox("📝 Questions answered", "$answered") {}
    CardBox("🎯 Correct answers", "$correct") {}
    CardBox("🏆 Achievements", achievementsFor(answered, correct).joinToString(" • "))

    Heading("ℹ️ About StudyMate PH", "Your Smart Learning Companion")
    CardBox(
        "LEARN SMARTER. DREAM BIGGER.",
        "Ang StudyMate PH ay isang educational application na idinisenyo upang maging maaasahang katuwang ng mga estudyanteng Pilipino sa kanilang paglalakbay sa pag-aaral. Layunin nitong gawing mas organisado, mas makabuluhan, at mas nakahihikayat ang bawat hakbang tungo sa kaalaman."
    ) {}
    CardBox(
        "Ang Layunin Namin",
        "Naniniwala kami na ang pag-aaral ay hindi lamang tungkol sa pagkuha ng mataas na marka—ito rin ay tungkol sa pagtuklas ng potensyal, paghubog ng disiplina, at pagbuo ng mas magandang kinabukasan. Nais ng StudyMate PH na maging bahagi ng prosesong iyon sa pamamagitan ng paghikayat sa mga estudyanteng matuto nang may layunin at magpatuloy kahit may mga pagsubok."
    ) {}
    CardBox(
        "Bakit StudyMate PH?",
        "📚 ORGANISADONG PAG-AARAL — Tulungan ang sarili na magkaroon ng mas maayos na routine at study habits.\n\n✨ MAS AKTIBONG PAGKATUTO — Gawing mas kawili-wili ang pagre-review at pag-unawa sa mga aralin.\n\n🚀 TUNGO SA MGA PANGARAP — Bawat maliit na hakbang sa pag-aaral ay ambag sa mas malaking tagumpay."
    ) {}
    CardBox(
        "💜 Mensahe",
        "“Hindi kailangang maging perpekto sa simula. Ang mahalaga ay ang patuloy na pagkatuto, pagsisikap, at paniniwala sa sariling kakayahan.”"
    ) {}
    CardBox("🎚️ Level Difficulty", "Level 1–20: Easy\nLevel 21–40: Medium\nLevel 41–60: Hard\nLevel 61–80: Very Hard\nLevel 81–100: Expert") {}
    CardBox("Developed by EHDZ", "Made with passion for Filipino learners.\nStudyMate PH • Learn Smarter. Dream Bigger.") {}
    CardBox("Tested by", "Prince Jay Orcullo") {}

    Heading("⚙️ Settings", "App preferences are now inside your Profile.")

    CardBox("🧊 Cool Theme", "StudyMate PH now uses one consistent cool blue, cyan, and indigo theme. Theme switching has been removed.") {}

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

    Heading("🔊 Sound & Music Volume", "I-adjust nang magkakahiwalay ang bawat tunog. 0% = mute, 100% = pinakamalakas.")
    VolumeControl("🎵 Background music", musicVolume, onMusicVolumeChange)
    VolumeControl("👆 Touch / button effects", touchVolume, onTouchVolumeChange)
    VolumeControl("🪙 Coins claim / purchase", coinVolume, onCoinVolumeChange)
    VolumeControl("✅ Correct answer", correctVolume, onCorrectVolumeChange)
    VolumeControl("❌ Wrong answer", wrongVolume, onWrongVolumeChange)
    VolumeControl("💔 Game over", gameOverVolume, onGameOverVolumeChange)
    VolumeControl("🏆 Level up / win", levelUpVolume, onLevelUpVolumeChange)

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
                            Color(0xFF172238).copy(alpha = 0.98f),
                            Color(0xFF131D35).copy(alpha = 0.96f),
                            Color(0xFF0E1728).copy(alpha = 0.99f)
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
private fun HomeShortcutTile(
    modifier: Modifier = Modifier,
    icon: String,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF111C30),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.58f)),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.16f), Color(0xFF111C30), Color(0xFF0D1729))
                    )
                )
                .padding(horizontal = 12.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.18f),
                border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.45f))
            ) {
                Text(icon, modifier = Modifier.padding(9.dp), style = MaterialTheme.typography.titleMedium)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.ExtraBold, color = Color(0xFFEAF7FF), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = muted, style = MaterialTheme.typography.labelSmall)
            }
            Text("›", color = accent, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
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
                        listOf(Color(0xFF30266F), Color(0xFF151E35), Color(0xFF101827))
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("✦  StudyMate PH", color = Color(0xFFB69AFF), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                        Text("Welcome, $name! ✨", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        Text("Level $level • $achievements achievements unlocked", color = muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = Color(0xFF101A2B).copy(alpha = 0.96f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFC857).copy(alpha = 0.48f))
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
                        listOf(accent.copy(alpha = 0.20f), Color(0xFF172238).copy(alpha = 0.94f), Color(0xFF0E1728).copy(alpha = 0.99f))
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
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (screen != Screen.HOME) {
            OutlinedButton(onClick = onBack) { Text("←") }
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("StudyMate PH", color = Color(0xFFB69AFF), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
            Text("Level $level • Learn smarter, one day at a time", color = muted, style = MaterialTheme.typography.bodySmall)
        }
        Surface(
            modifier = Modifier.clickable(onClick = onProfile),
            shape = MaterialTheme.shapes.large,
            color = Color(0xFF172238).copy(alpha = 0.96f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFC857).copy(alpha = 0.42f))
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
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).navigationBarsPadding(),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color(0xFF0D1729).copy(alpha = 0.98f),
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
                    color = if (selected) Color(0xFF8B5CF6).copy(alpha = 0.24f) else Color.Transparent,
                    border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB69AFF).copy(alpha = 0.48f)) else null
                ) {
                    Column(
                        Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(icon)
                        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) Color(0xFFE7DDFF) else muted)
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
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            autoCorrect = true
        )
    )

    OutlinedTextField(
        value = body,
        onValueChange = { body = it },
        label = { Text("Write your notes here") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            autoCorrect = true
        )
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
        listOf("Math", "Science", "English", "Filipino", "Araling Panlipunan", "ICT", "PE & Health", "Study Skills")
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

    "Study Skills" -> listOf(
        BankItem("Ano ang magandang unang hakbang kapag maraming gawain?", listOf("Unahin ang pinakamadali lang palagi", "Gumawa ng listahan at magtakda ng prayoridad", "Huwag pansinin ang deadline", "Gawin lahat nang sabay-sabay"), 1, "Ang listahan at malinaw na prayoridad ay tumutulong sa pagplano."),
        BankItem("Aling paraan ang makatutulong para maalala ang aralin?", listOf("Active recall at pagsagot nang walang kopya", "Basahin lang nang paulit-ulit habang distracted", "Iwasang mag-review", "Mag-aral nang walang pahinga"), 0, "Ang active recall ay nagpapraktis ng pagkuha ng impormasyon mula sa memorya."),
        BankItem("Bakit nakatutulong ang spaced practice?", listOf("Pinagsisiksik nito lahat sa isang gabi", "Ibinabalik ang review sa magkakahiwalay na oras", "Inaalis nito ang pangangailangang matulog", "Pinapalitan nito ang pagsasanay"), 1, "Ang pag-uulit sa pagitan ng mga araw ay nakatutulong sa pangmatagalang pag-alala."),
        BankItem("Ano ang mainam gawin kapag hindi mo maintindihan ang tanong?", listOf("Hulaan agad nang hindi binabasa", "Hatiin ito sa mas maliliit na bahagi at tukuyin ang hinihingi", "Isara ang libro", "Kopyahin ang unang sagot"), 1, "Ang paghahati sa problema ay nakatutulong makita ang mga kinakailangang hakbang."),
        BankItem("Alin ang magandang paraan para mabawasan ang distractions habang nag-aaral?", listOf("Ihanda ang gamit at ilayo ang hindi kailangang notifications", "Buksan lahat ng social media", "Magpalit ng gawain kada ilang segundo", "Mag-aral sa maraming sabay-sabay na video"), 0, "Ang paghahanda ng lugar at paglimita ng notifications ay nakatutulong sa focus."),
        BankItem("Tama o Mali: Ang maikling pahinga sa pagitan ng study sessions ay maaaring makatulong sa pagpapanatili ng focus.", listOf("Tama", "Mali"), 0, "Ang planadong maiikling pahinga ay maaaring makatulong sa pagbalik ng atensyon."),
        BankItem("Ano ang dapat gawin pagkatapos magkamali sa practice quiz?", listOf("Tingnan ang paliwanag at unawain ang pagkakamali", "Burahin ang tanong sa isip at huwag nang balikan", "Itigil ang pag-aaral", "Memorize ang letra lang"), 0, "Ang pag-review sa paliwanag ay tumutulong matukoy ang dapat pag-aralan muli."),
        BankItem("Alin ang mas kapaki-pakinabang na study goal?", listOf("Mag-aral nang kaunti kapag may oras lang", "Tapusin ang 10 practice questions bago mag-7 PM", "Maging perpekto agad", "Huwag magtakda ng oras"), 1, "Ang tiyak at nasusukat na layunin na may takdang oras ay mas madaling sundan.")
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
