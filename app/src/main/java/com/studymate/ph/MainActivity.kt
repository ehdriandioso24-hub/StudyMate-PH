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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private enum class Screen {
    HOME, SUBJECTS, SEARCH, QUIZ, LEVEL_UP, LEVEL_COMPLETE, GAME_OVER, NOTES, FLASHCARDS, DICTIONARY, VOCABULARY, TIMER, PROGRESS, REVIEW, PROFILE, SHOP, SETTINGS
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

private val bg = Color(0xFF070D1A)
private val panel = Color(0xFF101A2D)
private val purple = Color(0xFF9D8CFF)
private val pink = Color(0xFFFF78C8)
private val cyan = Color(0xFF48E0F0)
private val muted = Color(0xFFB8C7DC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appPrefs = getSharedPreferences("studymate_data", Context.MODE_PRIVATE)
        setContent {
            val appColors = darkColorScheme(
                primary = Color(0xFF9D8CFF),
                secondary = Color(0xFF48E0F0),
                tertiary = Color(0xFFFF78C8),
                background = Color(0xFF070D1A),
                surface = Color(0xFF101A2D),
                onPrimary = Color(0xFF100A25),
                onBackground = Color(0xFFE6F7FF),
                onSurface = Color(0xFFE6F7FF)
            )
            MaterialTheme(
                colorScheme = appColors,
                shapes = Shapes(
                    extraSmall = RoundedCornerShape(10.dp),
                    small = RoundedCornerShape(14.dp),
                    medium = RoundedCornerShape(18.dp),
                    large = RoundedCornerShape(24.dp),
                    extraLarge = RoundedCornerShape(30.dp)
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HomeShortcutTile(
                                modifier = Modifier.weight(1f),
                                icon = "📖",
                                title = "Dictionary",
                                subtitle = "Tagalog ↔ English",
                                accent = Color(0xFF48E0F0),
                                onClick = { screen = Screen.DICTIONARY.name }
                            )
                            HomeShortcutTile(
                                modifier = Modifier.weight(1f),
                                icon = "🔎",
                                title = "Search",
                                subtitle = "Find quiz questions",
                                accent = Color(0xFF9D8CFF),
                                onClick = { screen = Screen.SEARCH.name }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HomeShortcutTile(
                                modifier = Modifier.weight(1f),
                                icon = "🧠",
                                title = "Vocabulary Builder",
                                subtitle = "Learn & save words",
                                accent = Color(0xFFFF78C8),
                                onClick = { screen = Screen.VOCABULARY.name }
                            )
                            HomeShortcutTile(
                                modifier = Modifier.weight(1f),
                                icon = "📚",
                                title = "Word Practice",
                                subtitle = "Review saved words",
                                accent = Color(0xFF9D8CFF),
                                onClick = { screen = Screen.VOCABULARY.name }
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

                    Screen.DICTIONARY -> {
                        DictionaryPage()
                    }

                    Screen.VOCABULARY -> {
                        VocabularyBuilderPage()
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

private data class DictionaryEntry(
    val english: String,
    val tagalog: String,
    val meaning: String,
    val category: String = "General",
    val example: String = ""
)

private val tagalogEnglishWords = (listOf(
    DictionaryEntry("hello", "kumusta", "A greeting used when meeting someone."),
    DictionaryEntry("good morning", "magandang umaga", "A greeting used in the morning."),
    DictionaryEntry("good afternoon", "magandang hapon", "A greeting used in the afternoon."),
    DictionaryEntry("good evening", "magandang gabi", "A greeting used in the evening."),
    DictionaryEntry("thank you", "salamat", "Used to show gratitude."),
    DictionaryEntry("please", "pakiusap / po", "A polite word or expression."),
    DictionaryEntry("sorry", "pasensya na", "Used to apologize."),
    DictionaryEntry("yes", "oo", "An affirmative answer."),
    DictionaryEntry("no", "hindi", "A negative answer."),
    DictionaryEntry("friend", "kaibigan", "A person you know and like."),
    DictionaryEntry("family", "pamilya", "People related to one another."),
    DictionaryEntry("home", "tahanan / bahay", "The place where someone lives."),
    DictionaryEntry("school", "paaralan", "A place where people learn."),
    DictionaryEntry("teacher", "guro", "A person who teaches."),
    DictionaryEntry("student", "mag-aaral", "A person who studies."),
    DictionaryEntry("book", "aklat / libro", "A set of written or printed pages."),
    DictionaryEntry("word", "salita", "A unit of language with meaning."),
    DictionaryEntry("language", "wika", "A system of communication."),
    DictionaryEntry("learn", "matuto", "To gain knowledge or skill."),
    DictionaryEntry("read", "magbasa", "To look at and understand written words."),
    DictionaryEntry("write", "magsulat", "To form words using letters."),
    DictionaryEntry("speak", "magsalita", "To say words aloud."),
    DictionaryEntry("listen", "makinig", "To pay attention to sounds or speech."),
    DictionaryEntry("understand", "maunawaan", "To know the meaning of something."),
    DictionaryEntry("beautiful", "maganda", "Pleasing to look at."),
    DictionaryEntry("happy", "masaya", "Feeling joy or pleasure."),
    DictionaryEntry("sad", "malungkot", "Feeling unhappy."),
    DictionaryEntry("kind", "mabait", "Friendly, caring, or considerate."),
    DictionaryEntry("brave", "matapang", "Ready to face danger or difficulty."),
    DictionaryEntry("honest", "matapat", "Truthful and fair."),
    DictionaryEntry("help", "tulong / tumulong", "To make something easier for someone."),
    DictionaryEntry("love", "pag-ibig / magmahal", "A strong feeling of care or affection."),
    DictionaryEntry("respect", "paggalang / igalang", "To treat someone with consideration."),
    DictionaryEntry("water", "tubig", "A clear liquid needed by living things."),
    DictionaryEntry("food", "pagkain", "Things people or animals eat."),
    DictionaryEntry("rice", "kanin / bigas", "Cooked rice is kanin; uncooked rice is bigas."),
    DictionaryEntry("house", "bahay", "A building where people live."),
    DictionaryEntry("time", "oras", "A measure of when events happen."),
    DictionaryEntry("today", "ngayon", "The current day."),
    DictionaryEntry("tomorrow", "bukas", "The day after today."),
    DictionaryEntry("yesterday", "kahapon", "The day before today."),
    DictionaryEntry("morning", "umaga", "The early part of the day."),
    DictionaryEntry("night", "gabi", "The dark part of the day."),
    DictionaryEntry("big", "malaki", "Large in size."),
    DictionaryEntry("small", "maliit", "Little in size."),
    DictionaryEntry("fast", "mabilis", "Moving or happening quickly."),
    DictionaryEntry("slow", "mabagal", "Moving or happening without speed."),
    DictionaryEntry("strong", "malakas", "Having great strength or power."),
    DictionaryEntry("weak", "mahina", "Lacking strength or power."),
    DictionaryEntry("clean", "malinis", "Free from dirt."),
    DictionaryEntry("important", "mahalaga", "Having great value or significance."),
    DictionaryEntry("question", "tanong", "A sentence asking for information."),
    DictionaryEntry("answer", "sagot", "A reply to a question."),
    DictionaryEntry("knowledge", "kaalaman", "Facts and skills learned through experience or study."),
    DictionaryEntry("dream", "pangarap / panaginip", "A goal or an experience while sleeping."),
    DictionaryEntry("work", "trabaho / magtrabaho", "An activity done to achieve a result."),
    DictionaryEntry("play", "maglaro", "To take part in an activity for enjoyment."),
    DictionaryEntry("eat", "kumain", "To take food into the body."),
    DictionaryEntry("drink", "uminom", "To take a liquid into the body."),
    DictionaryEntry("walk", "maglakad", "To move on foot."),
    DictionaryEntry("run", "tumakbo", "To move quickly on foot."),
    DictionaryEntry("beautiful day", "magandang araw", "A friendly expression or greeting."),
    DictionaryEntry("where", "saan", "Used to ask about a place."),
    DictionaryEntry("when", "kailan", "Used to ask about time."),
    DictionaryEntry("why", "bakit", "Used to ask for a reason."),
    DictionaryEntry("what", "ano", "Used to ask about a thing or idea."),
    DictionaryEntry("who", "sino", "Used to ask about a person."),
    DictionaryEntry("how", "paano", "Used to ask about a method or way."),
    DictionaryEntry("abandon", "iwanan / talikuran", "To leave someone or something behind.", "Actions", "Do not abandon your goals. — Huwag talikuran ang iyong mga pangarap."),
    DictionaryEntry("ability", "kakayahan", "The skill or power to do something.", "Learning", "Practice improves your ability. — Pinauunlad ng pagsasanay ang iyong kakayahan."),
    DictionaryEntry("accept", "tanggapin", "To receive or agree to something.", "Actions", "Accept helpful advice. — Tanggapin ang kapaki-pakinabang na payo."),
    DictionaryEntry("achieve", "makamit", "To succeed in reaching a goal.", "Learning", "You can achieve your goals. — Makakamit mo ang iyong mga layunin."),
    DictionaryEntry("active", "aktibo / masigla", "Doing things with energy.", "People", "Stay active every day. — Manatiling aktibo araw-araw."),
    DictionaryEntry("advice", "payo", "A suggestion about what someone should do.", "People", "Thank you for your advice. — Salamat sa iyong payo."),
    DictionaryEntry("afraid", "natatakot", "Feeling fear.", "Feelings", "She is afraid of storms. — Natatakot siya sa mga bagyo."),
    DictionaryEntry("allow", "payagan", "To let something happen.", "Actions", "Allow yourself to learn. — Payagan ang sarili na matuto."),
    DictionaryEntry("almost", "halos", "Nearly, but not completely.", "General", "I am almost ready. — Halos handa na ako."),
    DictionaryEntry("ancient", "sinauna", "Very old; from a long time ago.", "Time & Nature", "We studied ancient history. — Pinag-aralan namin ang sinaunang kasaysayan."),
    DictionaryEntry("angry", "galit", "Feeling strong displeasure.", "Feelings", "He is angry about the mistake. — Galit siya sa pagkakamali."),
    DictionaryEntry("animal", "hayop", "A living creature that is not a plant.", "Nature", "The animal needs water. — Kailangan ng hayop ng tubig."),
    DictionaryEntry("arrive", "dumating", "To reach a place.", "Actions", "We arrive at school early. — Maaga kaming dumarating sa paaralan."),
    DictionaryEntry("ask", "magtanong / hingin", "To request information or something.", "Actions", "Ask your teacher. — Magtanong sa iyong guro."),
    DictionaryEntry("begin", "simulan", "To start something.", "Actions", "Begin with one small step. — Magsimula sa isang maliit na hakbang."),
    DictionaryEntry("believe", "maniwala", "To accept something as true.", "Mind", "Believe in your ability. — Maniwala sa iyong kakayahan."),
    DictionaryEntry("borrow", "humiram", "To use something and return it later.", "Actions", "May I borrow your book? — Maaari ba akong humiram ng iyong aklat?"),
    DictionaryEntry("careful", "maingat", "Taking care to avoid danger or mistakes.", "People", "Be careful on the road. — Mag-ingat sa daan."),
    DictionaryEntry("choose", "pumili", "To decide which thing you want.", "Actions", "Choose the correct answer. — Piliin ang tamang sagot."),
    DictionaryEntry("clever", "matalino / madiskarte", "Quick to understand or solve problems.", "People", "That was a clever idea. — Matalinong ideya iyon."),
    DictionaryEntry("compare", "ihambing", "To look at similarities and differences.", "Learning", "Compare the two answers. — Ihambing ang dalawang sagot."),
    DictionaryEntry("complete", "kumpletuhin / buo", "To finish or have all parts.", "Actions", "Complete your homework. — Kumpletuhin ang iyong takdang-aralin."),
    DictionaryEntry("confident", "may tiwala sa sarili", "Believing in your own abilities.", "Feelings", "Be confident when speaking. — Magtiwala sa sarili kapag nagsasalita."),
    DictionaryEntry("create", "lumikha / gumawa", "To make something new.", "Actions", "Create a study plan. — Gumawa ng plano sa pag-aaral."),
    DictionaryEntry("curious", "mausisa", "Eager to learn or know something.", "Feelings", "Curious students ask questions. — Nagtatanong ang mga mausisang mag-aaral."),
    DictionaryEntry("danger", "panganib", "The possibility of harm or injury.", "Safety", "Stay away from danger. — Lumayo sa panganib."),
    DictionaryEntry("decide", "magpasya", "To make a choice.", "Mind", "Decide after thinking carefully. — Magpasya matapos mag-isip nang mabuti."),
    DictionaryEntry("different", "magkaiba / naiiba", "Not the same.", "General", "We have different opinions. — Magkaiba ang aming mga opinyon."),
    DictionaryEntry("discover", "matuklasan", "To find something for the first time.", "Learning", "Discover a new word today. — Tumuklas ng bagong salita ngayon."),
    DictionaryEntry("early", "maaga", "Before the expected time.", "Time & Nature", "I wake up early. — Maaga akong gumigising."),
    DictionaryEntry("effort", "pagsisikap", "An attempt that uses energy.", "Learning", "Your effort matters. — Mahalaga ang iyong pagsisikap."),
    DictionaryEntry("enough", "sapat", "As much as needed.", "General", "We have enough time. — May sapat tayong oras."),
    DictionaryEntry("environment", "kapaligiran", "The surroundings in which people or things live.", "Nature", "Protect the environment. — Protektahan ang kapaligiran."),
    DictionaryEntry("example", "halimbawa", "Something that helps explain a rule or idea.", "Learning", "Give one example. — Magbigay ng isang halimbawa."),
    DictionaryEntry("excited", "nasasabik", "Very happy and enthusiastic.", "Feelings", "I am excited to learn. — Nasasabik akong matuto."),
    DictionaryEntry("explain", "ipaliwanag", "To make an idea clear.", "Learning", "Please explain your answer. — Pakipaliwanag ang iyong sagot."),
    DictionaryEntry("fair", "patas", "Treating people equally and reasonably.", "People", "Everyone deserves a fair chance. — Karapat-dapat ang lahat sa patas na pagkakataon."),
    DictionaryEntry("famous", "sikat", "Known by many people.", "People", "The author is famous. — Sikat ang may-akda."),
    DictionaryEntry("fear", "takot", "An unpleasant feeling caused by danger.", "Feelings", "Face your fear slowly. — Harapin ang iyong takot nang paunti-unti."),
    DictionaryEntry("follow", "sumunod / sundan", "To go after or act according to instructions.", "Actions", "Follow the instructions. — Sundin ang mga tagubilin."),
    DictionaryEntry("forgive", "magpatawad", "To stop feeling anger toward someone.", "Feelings", "It can be hard to forgive. — Maaaring mahirap magpatawad."),
    DictionaryEntry("future", "hinaharap", "The time that has not happened yet.", "Time & Nature", "Prepare for the future. — Maghanda para sa hinaharap."),
    DictionaryEntry("goal", "layunin / mithiin", "Something you want to achieve.", "Learning", "Set a clear goal. — Magtakda ng malinaw na layunin."),
    DictionaryEntry("grateful", "mapagpasalamat", "Feeling thankful.", "Feelings", "I am grateful for your help. — Mapagpasalamat ako sa iyong tulong."),
    DictionaryEntry("healthy", "malusog", "In good physical or mental condition.", "Health", "Eat healthy food. — Kumain ng masustansyang pagkain."),
    DictionaryEntry("improve", "pagbutihin / pagbuti", "To make or become better.", "Learning", "Practice to improve your English. — Magsanay upang pagbutihin ang iyong English."),
    DictionaryEntry("include", "isama", "To make something part of a group.", "Actions", "Include everyone in the game. — Isama ang lahat sa laro."),
    DictionaryEntry("increase", "dagdagan / tumaas", "To become or make greater.", "General", "Reading can increase your vocabulary. — Maaaring palawakin ng pagbabasa ang iyong bokabularyo."),
    DictionaryEntry("journey", "paglalakbay", "Travel from one place to another; a process of change.", "Travel", "Learning is a journey. — Ang pagkatuto ay isang paglalakbay."),
    DictionaryEntry("justice", "katarungan", "Fair treatment according to what is right.", "Society", "People seek justice. — Naghahanap ng katarungan ang mga tao."),
    DictionaryEntry("knowledgeable", "maraming nalalaman", "Having a lot of knowledge.", "Learning", "She is knowledgeable about history. — Marami siyang nalalaman tungkol sa kasaysayan."),
    DictionaryEntry("leader", "pinuno", "A person who guides a group.", "People", "A good leader listens. — Nakikinig ang mabuting pinuno."),
    DictionaryEntry("lesson", "aralin", "Something taught or learned.", "Learning", "Review today's lesson. — Balikan ang aralin ngayong araw."),
    DictionaryEntry("mistake", "pagkakamali", "Something that is incorrect.", "Learning", "Learn from every mistake. — Matuto sa bawat pagkakamali."),
    DictionaryEntry("necessary", "kinakailangan", "Needed or required.", "General", "Sleep is necessary for health. — Kailangan ang tulog para sa kalusugan."),
    DictionaryEntry("opportunity", "pagkakataon", "A suitable time or chance to do something.", "General", "Use every opportunity to learn. — Gamitin ang bawat pagkakataong matuto."),
    DictionaryEntry("patient", "matiyaga / mapagpasensya", "Able to wait calmly; a person receiving medical care.", "People", "Be patient with yourself. — Maging matiyaga sa iyong sarili."),
    DictionaryEntry("peaceful", "mapayapa", "Calm and free from conflict.", "Feelings", "The village is peaceful. — Mapayapa ang nayon."),
    DictionaryEntry("prepare", "maghanda", "To get ready for something.", "Actions", "Prepare for your exam. — Maghanda para sa iyong pagsusulit."),
    DictionaryEntry("protect", "protektahan", "To keep someone or something safe.", "Safety", "Protect your personal information. — Protektahan ang iyong personal na impormasyon."),
    DictionaryEntry("purpose", "layunin", "The reason something is done.", "Mind", "What is the purpose of this task? — Ano ang layunin ng gawaing ito?"),
    DictionaryEntry("quiet", "tahimik", "Making little or no noise.", "General", "Find a quiet place to study. — Humanap ng tahimik na lugar para mag-aral."),
    DictionaryEntry("remember", "alalahanin / tandaan", "To keep something in your mind.", "Mind", "Remember these new words. — Tandaan ang mga bagong salitang ito."),
    DictionaryEntry("responsible", "responsable / may pananagutan", "Able to be trusted to do what is expected.", "People", "Be responsible for your work. — Maging responsable sa iyong gawain."),
    DictionaryEntry("safe", "ligtas", "Protected from harm or danger.", "Safety", "Keep your account safe. — Panatilihing ligtas ang iyong account."),
    DictionaryEntry("share", "ibahagi", "To give part of something to others.", "Actions", "Share your knowledge. — Ibahagi ang iyong kaalaman."),
    DictionaryEntry("solution", "solusyon", "An answer to a problem.", "Learning", "Find a solution together. — Humanap ng solusyon nang magkakasama."),
    DictionaryEntry("success", "tagumpay", "The achievement of a goal.", "Learning", "Success takes practice. — Kailangan ng pagsasanay upang magtagumpay."),
    DictionaryEntry("thoughtful", "maalalahanin", "Showing care by thinking about others.", "People", "That was a thoughtful gift. — Isa iyong maalalahaning regalo."),
    DictionaryEntry("travel", "maglakbay", "To go from one place to another.", "Travel", "We travel by bus. — Naglalakbay kami sakay ng bus."),
    DictionaryEntry("useful", "kapaki-pakinabang", "Helpful for a purpose.", "General", "This dictionary is useful. — Kapaki-pakinabang ang diksyunaryong ito."),
    DictionaryEntry("value", "halaga", "The worth or importance of something.", "General", "Know the value of your time. — Alamin ang halaga ng iyong oras."),
    DictionaryEntry("wise", "marunong / matalino", "Showing good judgment.", "People", "Make a wise decision. — Gumawa ng matalinong pasya."),
    DictionaryEntry("wonder", "magtaka / kamanghaan", "To feel curiosity or amazement.", "Feelings", "I wonder how it works. — Nagtataka ako kung paano ito gumagana."),
    DictionaryEntry("youth", "kabataan", "The period of being young; young people.", "People", "Education helps the youth. — Tinutulungan ng edukasyon ang kabataan."),
    DictionaryEntry("zero", "sero / wala", "The number 0; nothing.", "Numbers", "Zero is less than one. — Mas mababa ang sero kaysa isa."),
    DictionaryEntry("above", "sa itaas", "In a higher place or position.", "Places", "The lamp is above the table. — Nasa itaas ng mesa ang ilaw."),
    DictionaryEntry("behind", "sa likod", "At the back of something.", "Places", "The bag is behind the chair. — Nasa likod ng upuan ang bag."),
    DictionaryEntry("between", "sa pagitan", "In the space separating two things.", "Places", "Sit between your friends. — Umupo sa pagitan ng iyong mga kaibigan."),
    DictionaryEntry("inside", "sa loob", "In the inner part of something.", "Places", "The keys are inside the bag. — Nasa loob ng bag ang mga susi."),
    DictionaryEntry("outside", "sa labas", "Not inside a building or space.", "Places", "Wait outside the room. — Maghintay sa labas ng silid."),
    DictionaryEntry("rain", "ulan", "Water falling from clouds.", "Nature", "The rain is heavy. — Malakas ang ulan."),
    DictionaryEntry("river", "ilog", "A large natural stream of water.", "Nature", "The river flows to the sea. — Dumadaloy ang ilog patungo sa dagat."),
    DictionaryEntry("mountain", "bundok", "A very high natural area of land.", "Nature", "The mountain is tall. — Mataas ang bundok."),
    DictionaryEntry("weather", "panahon", "Conditions such as rain, wind, and temperature.", "Nature", "The weather is warm today. — Mainit ang panahon ngayon."),
    DictionaryEntry("healthy", "malusog", "In good health.", "Health", "Exercise keeps the body healthy. — Pinananatiling malusog ng ehersisyo ang katawan."),
    DictionaryEntry("medicine", "gamot", "A substance used to treat illness.", "Health", "Take medicine as directed. — Inumin ang gamot ayon sa tagubilin."),
    DictionaryEntry("pain", "sakit", "An unpleasant physical feeling.", "Health", "Tell an adult if you feel pain. — Sabihan ang nakatatanda kung nakakaramdam ka ng sakit."),
    DictionaryEntry("breakfast", "almusal", "The first meal of the day.", "Food", "I eat breakfast at seven. — Kumakain ako ng almusal nang alas-siyete."),
    DictionaryEntry("vegetable", "gulay", "An edible plant or part of a plant.", "Food", "Eat vegetables every day. — Kumain ng gulay araw-araw."),
    DictionaryEntry("fruit", "prutas", "The edible part of a plant that often contains seeds.", "Food", "Mango is a sweet fruit. — Matamis na prutas ang mangga."),
    DictionaryEntry("hungry", "gutom", "Needing or wanting food.", "Food", "I am hungry. — Gutom ako."),
    DictionaryEntry("thirsty", "uhaw", "Needing a drink.", "Food", "Drink water when you are thirsty. — Uminom ng tubig kapag nauuhaw."),
    DictionaryEntry("expensive", "mahal", "Costing a lot of money.", "Shopping", "That phone is expensive. — Mahal ang teleponong iyon."),
    DictionaryEntry("cheap", "mura", "Low in price.", "Shopping", "This notebook is cheap. — Mura ang kuwadernong ito."),
    DictionaryEntry("money", "pera", "Something used to pay for goods and services.", "Shopping", "Save your money. — Mag-ipon ng pera."),
    DictionaryEntry("price", "presyo", "The amount of money something costs.", "Shopping", "Check the price first. — Tingnan muna ang presyo."),
    DictionaryEntry("buy", "bumili", "To get something by paying money.", "Shopping", "I want to buy a book. — Gusto kong bumili ng aklat."),
    DictionaryEntry("sell", "magbenta", "To give something in exchange for money.", "Shopping", "They sell fresh fruit. — Nagbebenta sila ng sariwang prutas."),
    DictionaryEntry("care", "pag-aalaga / malasakit", "Attention or concern for someone or something.", "People", "Show care for others. — Magpakita ng malasakit sa iba."),
    DictionaryEntry("community", "pamayanan", "A group of people living in the same area.", "Society", "Our community works together. — Nagtutulungan ang aming pamayanan."),
    DictionaryEntry("culture", "kultura", "The customs and way of life of a group.", "Society", "Learn about Filipino culture. — Matuto tungkol sa kulturang Pilipino."),
    DictionaryEntry("freedom", "kalayaan", "The ability to act or speak without unfair control.", "Society", "Freedom comes with responsibility. — May kaakibat na pananagutan ang kalayaan."),
    DictionaryEntry("law", "batas", "A rule made by an authority.", "Society", "Everyone must follow the law. — Dapat sundin ng lahat ang batas."),
    DictionaryEntry("peace", "kapayapaan", "A state without war or conflict.", "Society", "We hope for peace. — Umaasa kami sa kapayapaan."),
    DictionaryEntry("science", "agham", "The study of the natural world.", "School", "Science explains many natural events. — Ipinaliliwanag ng agham ang maraming pangyayari sa kalikasan."),
    DictionaryEntry("history", "kasaysayan", "The study or record of past events.", "School", "History teaches us about the past. — Itinuturo sa atin ng kasaysayan ang nakaraan."),
    DictionaryEntry("mathematics", "matematika", "The study of numbers, shapes, and patterns.", "School", "Mathematics develops problem-solving skills. — Pinauunlad ng matematika ang kakayahang lumutas ng problema."),
    DictionaryEntry("library", "aklatan", "A place where books and learning materials are kept.", "School", "Study in the library. — Mag-aral sa aklatan."),
    DictionaryEntry("exam", "pagsusulit", "A test of knowledge or skill.", "School", "Review before the exam. — Magbalik-aral bago ang pagsusulit."),
    DictionaryEntry("grade", "marka / baitang", "A score for schoolwork or a level in school.", "School", "Check your grade. — Tingnan ang iyong marka."),
    DictionaryEntry("homework", "takdang-aralin", "Schoolwork to be completed outside class.", "School", "Finish your homework. — Tapusin ang iyong takdang-aralin."),
    DictionaryEntry("computer", "kompyuter", "An electronic device that processes information.", "Technology", "Use the computer carefully. — Gamitin nang maingat ang kompyuter."),
    DictionaryEntry("internet", "internet", "A global network connecting computers and devices.", "Technology", "The internet provides information. — Nagbibigay ng impormasyon ang internet."),
    DictionaryEntry("password", "password / salitang-panseguridad", "A secret string used to access an account.", "Technology", "Never share your password. — Huwag ibahagi ang iyong password."),
    DictionaryEntry("message", "mensahe", "Information sent to someone.", "Technology", "I sent you a message. — Nagpadala ako sa iyo ng mensahe."),
    DictionaryEntry("search", "maghanap / paghahanap", "To look for something.", "Technology", "Search for the word. — Hanapin ang salita."),
    DictionaryEntry("beautiful", "maganda", "Pleasing to the senses or mind.", "Descriptions", "The garden is beautiful. — Maganda ang hardin."),
    DictionaryEntry("bright", "maliwanag / matalino", "Giving much light; intelligent.", "Descriptions", "The room is bright. — Maliwanag ang silid."),
    DictionaryEntry("dark", "madilim", "Having little or no light.", "Descriptions", "The sky is dark. — Madilim ang langit."),
    DictionaryEntry("soft", "malambot", "Easy to press or not hard.", "Descriptions", "The pillow is soft. — Malambot ang unan."),
    DictionaryEntry("hard", "matigas / mahirap", "Firm to touch; difficult to do.", "Descriptions", "This question is hard. — Mahirap ang tanong na ito."),
    DictionaryEntry("warm", "mainit-init", "Slightly hot in a pleasant way.", "Descriptions", "The soup is warm. — Mainit-init ang sabaw."),
    DictionaryEntry("cold", "malamig", "Having a low temperature.", "Descriptions", "The water is cold. — Malamig ang tubig."),
    DictionaryEntry("near", "malapit", "At a short distance.", "Places", "The school is near our house. — Malapit ang paaralan sa aming bahay."),
    DictionaryEntry("far", "malayo", "At a great distance.", "Places", "The market is far away. — Malayo ang palengke."),
    DictionaryEntry("always", "palagi", "At all times.", "Time & Nature", "Always tell the truth. — Palaging magsabi ng totoo."),
    DictionaryEntry("sometimes", "minsan", "On some occasions, but not always.", "Time & Nature", "I sometimes read at night. — Minsan akong nagbabasa sa gabi."),
    DictionaryEntry("never", "hindi kailanman", "Not at any time.", "Time & Nature", "Never give up learning. — Huwag kailanman tumigil sa pagkatuto."),
    DictionaryEntry("soon", "malapit na", "In a short time.", "Time & Nature", "The class will start soon. — Magsisimula na ang klase."),
    DictionaryEntry("already", "na", "Before now or earlier than expected.", "Time & Nature", "I already finished. — Tapos na ako."),
    DictionaryEntry("because", "dahil", "For the reason that.", "Connectors", "I study because I want to learn. — Nag-aaral ako dahil gusto kong matuto."),
    DictionaryEntry("although", "bagaman / kahit na", "Despite the fact that.", "Connectors", "Although it rained, we went. — Kahit umulan, tumuloy kami."),
    DictionaryEntry("however", "gayunman / subalit", "Used to introduce a contrast.", "Connectors", "It was difficult; however, we finished. — Mahirap ito; gayunman, natapos namin."),
    DictionaryEntry("therefore", "samakatuwid / kaya", "For that reason.", "Connectors", "It was late; therefore, we went home. — Gabi na kaya umuwi kami."),
    DictionaryEntry("together", "magkasama", "With one another.", "People", "Let us learn together. — Matuto tayo nang magkasama."),
    DictionaryEntry("alone", "mag-isa", "Without other people.", "People", "She studied alone. — Nag-aral siya nang mag-isa."),
    DictionaryEntry("polite", "magalang", "Showing good manners and respect.", "People", "Be polite to everyone. — Maging magalang sa lahat."),
    DictionaryEntry("rude", "bastos", "Not showing respect or good manners.", "People", "That comment was rude. — Bastos ang komentong iyon."),
    DictionaryEntry("trust", "tiwala / magtiwala", "Belief that someone is reliable.", "Feelings", "Trust takes time to build. — Nangangailangan ng panahon upang mabuo ang tiwala."),
    DictionaryEntry("worry", "mag-alala", "To feel anxious about something.", "Feelings", "Do not worry too much. — Huwag masyadong mag-alala.")) + listOf(
    DictionaryEntry("ability", "kakayahan", "The power or skill to do something.", "Learning"),
    DictionaryEntry("address", "tirahan / adres", "The place where someone lives.", "Places"),
    DictionaryEntry("adult", "nasa hustong gulang", "A fully grown person.", "People"),
    DictionaryEntry("airport", "paliparan", "A place where aircraft take off and land.", "Places"),
    DictionaryEntry("alarm", "alarma", "A signal that warns or wakes someone.", "Technology"),
    DictionaryEntry("answer", "sagot / sumagot", "A reply to a question.", "Actions"),
    DictionaryEntry("apartment", "apartment / paupahang tirahan", "A set of rooms used as a home.", "Places"),
    DictionaryEntry("apple", "mansanas", "A round fruit that can be red, green, or yellow.", "Food"),
    DictionaryEntry("arm", "braso", "The body part between the shoulder and hand.", "Body"),
    DictionaryEntry("arrive", "dumating", "To reach a place.", "Actions"),
    DictionaryEntry("art", "sining", "Creative work such as drawing, music, or painting.", "Learning"),
    DictionaryEntry("ask", "magtanong", "To request information.", "Actions"),
    DictionaryEntry("aunt", "tiya / tita", "The sister of a parent or an uncle's spouse.", "Family"),
    DictionaryEntry("baby", "sanggol", "A very young child.", "Family"),
    DictionaryEntry("backpack", "bag / backpack", "A bag carried on the back.", "School"),
    DictionaryEntry("bake", "maghurno", "To cook food using dry heat in an oven.", "Actions"),
    DictionaryEntry("ball", "bola", "A round object used in games.", "Sports"),
    DictionaryEntry("banana", "saging", "A long, soft, sweet fruit.", "Food"),
    DictionaryEntry("bank", "bangko", "An institution that keeps and lends money.", "Money"),
    DictionaryEntry("bathroom", "banyo", "A room with a toilet or bathing facilities.", "Places"),
    DictionaryEntry("beach", "dalampasigan", "An area beside the sea.", "Places"),
    DictionaryEntry("begin", "simulan", "To start doing something.", "Actions"),
    DictionaryEntry("believe", "maniwala", "To accept something as true.", "Actions"),
    DictionaryEntry("below", "sa ibaba", "In a lower place.", "Places"),
    DictionaryEntry("best", "pinakamahusay", "Better than all others.", "Descriptions"),
    DictionaryEntry("better", "mas mabuti", "Of higher quality or more suitable.", "Descriptions"),
    DictionaryEntry("between", "sa pagitan", "In the space separating two things.", "Places"),
    DictionaryEntry("bicycle", "bisikleta", "A two-wheeled vehicle powered by pedals.", "Transport"),
    DictionaryEntry("bird", "ibon", "An animal with feathers and wings.", "Animals"),
    DictionaryEntry("birthday", "kaarawan", "The yearly anniversary of a person's birth.", "Family"),
    DictionaryEntry("black", "itim", "The darkest color.", "Colors"),
    DictionaryEntry("blue", "asul / bughaw", "The color of a clear daytime sky.", "Colors"),
    DictionaryEntry("body", "katawan", "The physical structure of a person or animal.", "Body"),
    DictionaryEntry("bottle", "bote", "A container for liquids.", "Objects"),
    DictionaryEntry("bowl", "mangkok", "A round, deep dish.", "Objects"),
    DictionaryEntry("bread", "tinapay", "Food made from flour and baked.", "Food"),
    DictionaryEntry("breakfast", "almusal", "The first meal of the day.", "Food"),
    DictionaryEntry("brother", "kapatid na lalaki", "A male sibling.", "Family"),
    DictionaryEntry("build", "magtayo / bumuo", "To make something by putting parts together.", "Actions"),
    DictionaryEntry("busy", "abala", "Having a lot to do.", "Descriptions"),
    DictionaryEntry("buy", "bumili", "To get something by paying money.", "Money"),
    DictionaryEntry("camera", "kamera", "A device used to take photographs or videos.", "Technology"),
    DictionaryEntry("candle", "kandila", "Wax with a wick that produces light.", "Objects"),
    DictionaryEntry("careful", "maingat", "Taking care to avoid danger or mistakes.", "Descriptions"),
    DictionaryEntry("carry", "magdala", "To hold and move something from place to place.", "Actions"),
    DictionaryEntry("center", "gitna", "The middle point or part.", "Places"),
    DictionaryEntry("chair", "upuan / silya", "A seat for one person.", "Objects"),
    DictionaryEntry("change", "magbago / pagbabago", "To make or become different.", "Actions"),
    DictionaryEntry("cheap", "mura", "Low in price.", "Money"),
    DictionaryEntry("cheese", "keso", "A food made from milk.", "Food"),
    DictionaryEntry("child", "bata", "A young person.", "Family"),
    DictionaryEntry("choose", "pumili", "To select from options.", "Actions"),
    DictionaryEntry("city", "lungsod", "A large town where many people live and work.", "Places"),
    DictionaryEntry("classmate", "kaklase", "A person in the same class.", "School"),
    DictionaryEntry("classroom", "silid-aralan", "A room where lessons take place.", "School"),
    DictionaryEntry("clock", "orasan", "A device that shows time.", "Objects"),
    DictionaryEntry("cloud", "ulap", "A visible mass of water droplets in the sky.", "Nature"),
    DictionaryEntry("coffee", "kape", "A drink made from roasted coffee beans.", "Food"),
    DictionaryEntry("coin", "barya", "A flat, round piece of money.", "Money"),
    DictionaryEntry("color", "kulay", "The quality by which objects look red, blue, green, and so on.", "Colors"),
    DictionaryEntry("comfortable", "komportable", "Giving a feeling of ease and comfort.", "Descriptions"),
    DictionaryEntry("cook", "magluto", "To prepare food using heat.", "Actions"),
    DictionaryEntry("corner", "sulok", "The place where two edges or walls meet.", "Places"),
    DictionaryEntry("country", "bansa", "A nation or territory.", "Places"),
    DictionaryEntry("cousin", "pinsan", "A child of one's aunt or uncle.", "Family"),
    DictionaryEntry("customer", "mamimili / kostumer", "A person who buys goods or services.", "Work"),
    DictionaryEntry("dance", "sumayaw / sayaw", "To move rhythmically to music.", "Actions"),
    DictionaryEntry("danger", "panganib", "The possibility of harm or injury.", "Safety"),
    DictionaryEntry("daughter", "anak na babae", "A female child in relation to a parent.", "Family"),
    DictionaryEntry("day", "araw", "A period of 24 hours or the time with daylight.", "Time & Nature"),
    DictionaryEntry("decide", "magpasya", "To make a choice.", "Actions"),
    DictionaryEntry("deep", "malalim", "Extending far down from the surface.", "Descriptions"),
    DictionaryEntry("delicious", "masarap", "Having a pleasant taste.", "Food"),
    DictionaryEntry("dentist", "dentista", "A health professional who cares for teeth.", "Health"),
    DictionaryEntry("different", "magkaiba / naiiba", "Not the same.", "Descriptions"),
    DictionaryEntry("difficult", "mahirap", "Not easy to do or understand.", "Descriptions"),
    DictionaryEntry("dinner", "hapunan", "The main meal eaten in the evening.", "Food"),
    DictionaryEntry("dirty", "marumi", "Not clean.", "Descriptions"),
    DictionaryEntry("doctor", "doktor", "A person trained to treat illness and injury.", "Health"),
    DictionaryEntry("door", "pinto", "A movable barrier used to enter or leave a room.", "Objects"),
    DictionaryEntry("draw", "gumuhit", "To make a picture using lines.", "Actions"),
    DictionaryEntry("dress", "bestida / magbihis", "A one-piece garment or to put on clothes.", "Clothing"),
    DictionaryEntry("driver", "drayber / tsuper", "A person who drives a vehicle.", "Transport"),
    DictionaryEntry("early", "maaga", "Before the expected or usual time.", "Time & Nature"),
    DictionaryEntry("earth", "mundo / lupa", "The planet we live on; also soil.", "Nature"),
    DictionaryEntry("east", "silangan", "The direction where the sun rises.", "Places"),
    DictionaryEntry("easy", "madali", "Not difficult.", "Descriptions"),
    DictionaryEntry("education", "edukasyon", "The process of learning and teaching.", "Learning"),
    DictionaryEntry("egg", "itlog", "An oval food laid by birds such as chickens.", "Food"),
    DictionaryEntry("electricity", "kuryente", "Energy used to power lights and devices.", "Technology"),
    DictionaryEntry("email", "email / elektronikong liham", "A message sent electronically.", "Technology"),
    DictionaryEntry("empty", "walang laman", "Containing nothing.", "Descriptions"),
    DictionaryEntry("end", "wakas / tapusin", "The final part or to finish something.", "General"),
    DictionaryEntry("enemy", "kaaway", "A person who opposes another.", "People"),
    DictionaryEntry("enjoy", "masiyahan", "To get pleasure from something.", "Feelings"),
    DictionaryEntry("enough", "sapat", "As much or as many as needed.", "General"),
    DictionaryEntry("enter", "pumasok", "To go into a place.", "Actions"),
    DictionaryEntry("evening", "gabi / takipsilim", "The later part of the day before night.", "Time & Nature"),
    DictionaryEntry("everyone", "lahat / bawat isa", "All people in a group.", "People"),
    DictionaryEntry("example", "halimbawa", "Something that shows how a rule or idea works.", "Learning"),
    DictionaryEntry("excellent", "napakahusay", "Extremely good.", "Descriptions"),
    DictionaryEntry("excited", "nasasabik", "Very happy and eager about something.", "Feelings"),
    DictionaryEntry("exercise", "ehersisyo / magsanay", "Physical activity or practice to improve a skill.", "Health"),
    DictionaryEntry("expensive", "mahal", "Costing a lot of money.", "Money"),
    DictionaryEntry("experience", "karanasan", "Knowledge gained by doing or seeing something.", "Learning"),
    DictionaryEntry("eye", "mata", "The body part used for seeing.", "Body"),
    DictionaryEntry("face", "mukha", "The front part of the head.", "Body"),
    DictionaryEntry("fact", "katotohanan", "Something known or proved to be true.", "Learning"),
    DictionaryEntry("fail", "mabigo", "To not succeed in doing something.", "Learning"),
    DictionaryEntry("famous", "sikat", "Known by many people.", "Descriptions"),
    DictionaryEntry("farmer", "magsasaka", "A person who grows crops or raises animals.", "Work"),
    DictionaryEntry("father", "ama / tatay", "A male parent.", "Family"),
    DictionaryEntry("fear", "takot", "An unpleasant feeling caused by danger or threat.", "Feelings"),
    DictionaryEntry("feed", "pakainin", "To give food to a person or animal.", "Actions"),
    DictionaryEntry("feel", "makaramdam", "To experience an emotion or physical sensation.", "Feelings"),
    DictionaryEntry("female", "babae", "Of or relating to women or girls.", "People"),
    DictionaryEntry("few", "kaunti", "A small number.", "Numbers"),
    DictionaryEntry("field", "bukid / larangan", "An open area of land or an area of study.", "Nature"),
    DictionaryEntry("fight", "away / makipaglaban", "A struggle or to struggle against someone.", "Actions"),
    DictionaryEntry("fill", "punuin", "To make something full.", "Actions"),
    DictionaryEntry("find", "mahanap", "To discover or locate something.", "Actions"),
    DictionaryEntry("finish", "tapusin", "To bring something to an end.", "Actions"),
    DictionaryEntry("fire", "apoy", "Heat and light produced by burning.", "Nature"),
    DictionaryEntry("fish", "isda", "An animal that lives in water and breathes through gills.", "Animals"),
    DictionaryEntry("floor", "sahig", "The surface of a room that people walk on.", "Objects"),
    DictionaryEntry("flower", "bulaklak", "The colorful part of a plant that can produce seeds.", "Nature"),
    DictionaryEntry("follow", "sumunod", "To go after or act according to instructions.", "Actions"),
    DictionaryEntry("foot", "paa", "The body part used for standing and walking.", "Body"),
    DictionaryEntry("forest", "gubat", "A large area covered with trees.", "Nature"),
    DictionaryEntry("forget", "makalimot", "To fail to remember something.", "Actions"),
    DictionaryEntry("fork", "tinidor", "A utensil with prongs used for eating.", "Objects"),
    DictionaryEntry("free", "libre / malaya", "Not costing money or not controlled.", "General"),
    DictionaryEntry("fresh", "sariwa", "Recently made, gathered, or not spoiled.", "Descriptions"),
    DictionaryEntry("fruit", "prutas", "The edible part of a plant that often contains seeds.", "Food"),
    DictionaryEntry("full", "puno", "Containing as much as possible.", "Descriptions"),
    DictionaryEntry("funny", "nakakatawa", "Causing laughter or amusement.", "Descriptions"),
    DictionaryEntry("future", "hinaharap", "The time that is yet to come.", "Time & Nature"),
    DictionaryEntry("game", "laro", "An activity played for enjoyment or competition.", "Games"),
    DictionaryEntry("garden", "hardin", "An area where plants or flowers are grown.", "Nature"),
    DictionaryEntry("girl", "babae / batang babae", "A female child or young person.", "People"),
    DictionaryEntry("give", "magbigay", "To hand something to someone.", "Actions"),
    DictionaryEntry("glass", "baso / salamin", "A drinking container or a transparent material.", "Objects"),
    DictionaryEntry("go", "pumunta", "To move or travel to a place.", "Actions"),
    DictionaryEntry("goat", "kambing", "A farm animal with horns.", "Animals"),
    DictionaryEntry("gold", "ginto", "A valuable yellow metal.", "Objects"),
    DictionaryEntry("goodbye", "paalam", "An expression used when leaving.", "Greetings"),
    DictionaryEntry("grandfather", "lolo", "The father of a parent.", "Family"),
    DictionaryEntry("grandmother", "lola", "The mother of a parent.", "Family"),
    DictionaryEntry("grass", "damo", "Short green plants covering the ground.", "Nature"),
    DictionaryEntry("green", "berde / luntian", "The color of many plants.", "Colors"),
    DictionaryEntry("group", "grupo", "A number of people or things together.", "People"),
    DictionaryEntry("grow", "lumaki / tumubo", "To become bigger or develop.", "Actions"),
    DictionaryEntry("guess", "manghula", "To give an answer without being certain.", "Learning"),
    DictionaryEntry("guest", "bisita", "A person invited to a place or event.", "People"),
    DictionaryEntry("guide", "gabay / gumabay", "A person or thing that shows the way.", "People"),
    DictionaryEntry("habit", "nakagawian", "Something a person does regularly.", "Learning"),
    DictionaryEntry("hair", "buhok", "The strands growing on the head or body.", "Body"),
    DictionaryEntry("half", "kalahati", "One of two equal parts.", "Numbers"),
    DictionaryEntry("hand", "kamay", "The body part at the end of the arm.", "Body"),
    DictionaryEntry("handsome", "guwapo", "Attractive in appearance, usually describing a man.", "Descriptions"),
    DictionaryEntry("happen", "mangyari", "To take place or occur.", "Actions"),
    DictionaryEntry("hat", "sumbrero", "A covering worn on the head.", "Clothing"),
    DictionaryEntry("hate", "kamuhian / mapoot", "To feel strong dislike.", "Feelings"),
    DictionaryEntry("head", "ulo", "The part of the body containing the brain, eyes, ears, nose, and mouth.", "Body"),
    DictionaryEntry("healthy", "malusog", "In good physical or mental condition.", "Health"),
    DictionaryEntry("heart", "puso", "The organ that pumps blood; also a symbol of love.", "Body"),
    DictionaryEntry("heavy", "mabigat", "Having a lot of weight.", "Descriptions"),
    DictionaryEntry("height", "taas", "How tall something is.", "Descriptions"),
    DictionaryEntry("helpful", "matulungin", "Willing or useful in giving help.", "People"),
    DictionaryEntry("here", "dito", "In this place.", "Places"),
    DictionaryEntry("hill", "burol", "A raised area of land smaller than a mountain.", "Nature"),
    DictionaryEntry("history", "kasaysayan", "The study or record of past events.", "Learning"),
    DictionaryEntry("holiday", "pista opisyal / bakasyon", "A special day or a period away from work or school.", "Time & Nature"),
    DictionaryEntry("hospital", "ospital", "A place where sick or injured people receive treatment.", "Health"),
    DictionaryEntry("hot", "mainit", "Having a high temperature.", "Descriptions"),
    DictionaryEntry("hotel", "hotel / otel", "A place where travelers pay to stay.", "Places"),
    DictionaryEntry("hour", "oras", "A period of 60 minutes.", "Time & Nature"),
    DictionaryEntry("hungry", "gutom", "Needing or wanting food.", "Health"),
    DictionaryEntry("hurt", "masaktan / saktan", "To cause or feel pain.", "Health"),
    DictionaryEntry("ice", "yelo", "Frozen water.", "Nature"),
    DictionaryEntry("idea", "ideya", "A thought or plan.", "Learning"),
    DictionaryEntry("ill", "may sakit", "Not healthy; sick.", "Health"),
    DictionaryEntry("inside", "loob / sa loob", "In the inner part of something.", "Places"),
    DictionaryEntry("interesting", "kawili-wili", "Holding attention or making someone curious.", "Descriptions"),
    DictionaryEntry("internet", "internet", "A worldwide network connecting computers and devices.", "Technology"),
    DictionaryEntry("island", "pulo", "Land surrounded by water.", "Nature"),
    DictionaryEntry("job", "trabaho", "Paid work or a role someone performs.", "Work"),
    DictionaryEntry("join", "sumali", "To become part of a group or activity.", "Actions"),
    DictionaryEntry("juice", "katas / juice", "A drink made from fruit or vegetables.", "Food"),
    DictionaryEntry("keep", "panatilihin / itago", "To continue having or to store something.", "Actions"),
    DictionaryEntry("key", "susi", "A tool for opening a lock; also a keyboard button.", "Objects"),
    DictionaryEntry("kick", "sumipa", "To hit something with the foot.", "Actions"),
    DictionaryEntry("kindness", "kabaitan", "The quality of being kind.", "People"),
    DictionaryEntry("king", "hari", "A male ruler of a kingdom.", "People"),
    DictionaryEntry("kitchen", "kusina", "A room where food is prepared.", "Places"),
    DictionaryEntry("knee", "tuhod", "The joint between the thigh and lower leg.", "Body"),
    DictionaryEntry("knife", "kutsilyo", "A tool with a sharp blade used for cutting.", "Objects"),
    DictionaryEntry("know", "malaman / makaalam", "To have information or understanding.", "Learning"),
    DictionaryEntry("lake", "lawa", "A large body of water surrounded by land.", "Nature"),
    DictionaryEntry("lamp", "lampara", "A device that gives light.", "Objects"),
    DictionaryEntry("land", "lupa / lupain", "The solid surface of the earth.", "Nature"),
    DictionaryEntry("language", "wika / lengguwahe", "A system of words used by a community to communicate.", "Learning"),
    DictionaryEntry("large", "malaki", "Big in size or amount.", "Descriptions"),
    DictionaryEntry("last", "huli / tumagal", "Coming after all others or continuing for a period.", "General"),
    DictionaryEntry("late", "huli / nahuli", "After the expected time.", "Time & Nature"),
    DictionaryEntry("laugh", "tumawa", "To make sounds expressing amusement.", "Feelings"),
    DictionaryEntry("lazy", "tamad", "Unwilling to work or use effort.", "Descriptions"),
    DictionaryEntry("leader", "pinuno", "A person who guides or directs others.", "People"),
    DictionaryEntry("leaf", "dahon", "A flat part of a plant that usually grows from a stem.", "Nature"),
    DictionaryEntry("left", "kaliwa", "The side opposite right.", "Places"),
    DictionaryEntry("leg", "binti", "A body part used for standing and walking.", "Body"),
    DictionaryEntry("lesson", "aralin", "Something taught or learned.", "School"),
    DictionaryEntry("letter", "liham / titik", "A written message or a character in an alphabet.", "Learning"),
    DictionaryEntry("library", "aklatan", "A place where books and other materials can be used.", "School"),
    DictionaryEntry("life", "buhay", "The state of being alive.", "General"),
    DictionaryEntry("light", "liwanag / magaan", "Brightness; also not heavy.", "General"),
    DictionaryEntry("lightning", "kidlat", "A flash of electricity in the sky during a storm.", "Nature"),
    DictionaryEntry("line", "linya / pila", "A long mark or a row of people waiting.", "General"),
    DictionaryEntry("lion", "leon", "A large wild cat.", "Animals"),
    DictionaryEntry("list", "listahan", "A series of names or items.", "General"),
    DictionaryEntry("little", "maliit / kaunti", "Small in size or amount.", "Descriptions"),
    DictionaryEntry("live", "mabuhay / tumira", "To be alive or to reside somewhere.", "Actions"),
    DictionaryEntry("long", "mahaba", "Having great length or duration.", "Descriptions"),
    DictionaryEntry("look", "tumingin", "To direct the eyes toward something.", "Actions"),
    DictionaryEntry("loud", "malakas ang tunog", "Making a lot of sound.", "Descriptions"),
    DictionaryEntry("low", "mababa", "Not high in position or amount.", "Descriptions"),
    DictionaryEntry("lunch", "tanghalian", "A meal eaten around midday.", "Food"),
    DictionaryEntry("machine", "makina", "A device with moving parts that performs work.", "Technology"),
    DictionaryEntry("magazine", "magasin", "A publication containing articles and pictures.", "Learning"),
    DictionaryEntry("mail", "koreo / liham", "Letters or parcels sent through a postal service.", "Technology"),
    DictionaryEntry("male", "lalaki", "Of or relating to men or boys.", "People"),
    DictionaryEntry("manager", "tagapamahala", "A person responsible for managing work or people.", "Work"),
    DictionaryEntry("many", "marami", "A large number of.", "Numbers"),
    DictionaryEntry("map", "mapa", "A drawing that shows places and locations.", "Places"),
    DictionaryEntry("market", "palengke / pamilihan", "A place where goods are bought and sold.", "Places"),
    DictionaryEntry("married", "may asawa", "Joined to another person in marriage.", "Family"),
    DictionaryEntry("meal", "pagkain / kainan", "Food eaten at a particular time.", "Food"),
    DictionaryEntry("meat", "karne", "Animal flesh eaten as food.", "Food"),
    DictionaryEntry("medicine", "gamot", "A substance used to prevent or treat illness.", "Health"),
    DictionaryEntry("meet", "makipagkita", "To come together with someone.", "Actions"),
    DictionaryEntry("meeting", "pulong / pagpupulong", "A gathering of people for a purpose.", "Work"),
    DictionaryEntry("member", "miyembro", "A person who belongs to a group.", "People"),
    DictionaryEntry("memory", "alaala / memorya", "The ability to remember or something remembered.", "Learning"),
    DictionaryEntry("menu", "menu / talaan ng pagkain", "A list of food and drinks available.", "Food"),
    DictionaryEntry("middle", "gitna", "The center part.", "Places"),
    DictionaryEntry("milk", "gatas", "A white liquid produced by mammals.", "Food"),
    DictionaryEntry("minute", "minuto", "A unit of time equal to 60 seconds.", "Time & Nature"),
    DictionaryEntry("mirror", "salamin", "A surface that reflects an image.", "Objects"),
    DictionaryEntry("Monday", "Lunes", "The day of the week after Sunday.", "Time & Nature"),
    DictionaryEntry("money", "pera", "Something used to pay for goods and services.", "Money"),
    DictionaryEntry("month", "buwan", "One of the twelve named periods of the year.", "Time & Nature"),
    DictionaryEntry("moon", "buwan", "The natural satellite of Earth.", "Nature"),
    DictionaryEntry("mother", "ina / nanay", "A female parent.", "Family"),
    DictionaryEntry("mountain", "bundok", "A high natural area of land.", "Nature"),
    DictionaryEntry("mouse", "daga / mouse", "A small rodent or a computer pointing device.", "Objects"),
    DictionaryEntry("mouth", "bibig", "The opening used for eating and speaking.", "Body"),
    DictionaryEntry("move", "gumalaw / ilipat", "To change position or cause something to change position.", "Actions"),
    DictionaryEntry("movie", "pelikula", "A story or event shown as moving images.", "Entertainment"),
    DictionaryEntry("music", "musika", "Sounds arranged in a pleasing or expressive way.", "Entertainment"),
    DictionaryEntry("name", "pangalan", "A word used to identify a person or thing.", "People"),
    DictionaryEntry("nearby", "malapit", "Not far away.", "Places"),
    DictionaryEntry("neck", "leeg", "The part joining the head to the body.", "Body"),
    DictionaryEntry("need", "kailangan / mangailangan", "Something required or wanted.", "General"),
    DictionaryEntry("neighbor", "kapitbahay", "A person who lives nearby.", "People"),
    DictionaryEntry("nephew", "pamangking lalaki", "A son of one's sibling.", "Family"),
    DictionaryEntry("niece", "pamangking babae", "A daughter of one's sibling.", "Family"),
    DictionaryEntry("noise", "ingay", "A sound, especially one that is loud or unwanted.", "Sounds"),
    DictionaryEntry("north", "hilaga", "A main direction opposite south.", "Places"),
    DictionaryEntry("nose", "ilong", "The part of the face used for smelling and breathing.", "Body"),
    DictionaryEntry("notebook", "kuwaderno", "A book of blank or lined pages for writing.", "School"),
    DictionaryEntry("number", "numero / bilang", "A symbol or word used to count or identify.", "Numbers"),
    DictionaryEntry("ocean", "karagatan", "A very large body of salt water.", "Nature"),
    DictionaryEntry("office", "opisina", "A place where administrative or professional work is done.", "Work"),
    DictionaryEntry("often", "madalas", "Many times or frequently.", "Time & Nature"),
    DictionaryEntry("oil", "langis", "A liquid fat or fuel.", "Food"),
    DictionaryEntry("old", "luma / matanda", "Having existed for a long time.", "Descriptions"),
    DictionaryEntry("once", "minsan / isang beses", "One time.", "Time & Nature"),
    DictionaryEntry("onion", "sibuyas", "A vegetable with a strong flavor.", "Food"),
    DictionaryEntry("open", "buksan / bukas", "To make accessible or not closed.", "Actions"),
    DictionaryEntry("orange", "kahel / orange", "A citrus fruit or a color between red and yellow.", "Food"),
    DictionaryEntry("outside", "labas / sa labas", "On or to the outer side of something.", "Places"),
    DictionaryEntry("page", "pahina", "One side of a sheet in a book.", "Learning"),
    DictionaryEntry("pain", "sakit", "An unpleasant physical feeling.", "Health"),
    DictionaryEntry("paint", "pintura / magpinta", "A colored substance or to apply it to a surface.", "Actions"),
    DictionaryEntry("pair", "pares", "Two things that belong together.", "Numbers"),
    DictionaryEntry("paper", "papel", "A thin material used for writing or printing.", "School"),
    DictionaryEntry("parent", "magulang", "A mother or father.", "Family"),
    DictionaryEntry("park", "parke", "A public green area for recreation.", "Places"),
    DictionaryEntry("part", "bahagi", "A piece of a whole.", "General"),
    DictionaryEntry("party", "salu-salo / handaan", "A social gathering or celebration.", "Family"),
    DictionaryEntry("pass", "ipasa / makapasa", "To hand something over or succeed in a test.", "Actions"),
    DictionaryEntry("past", "nakaraan", "The time before now.", "Time & Nature"),
    DictionaryEntry("pay", "magbayad", "To give money for goods, services, or a debt.", "Money"),
    DictionaryEntry("peace", "kapayapaan", "A state without war or disturbance.", "People"),
    DictionaryEntry("pen", "panulat / bolpen", "A tool used for writing with ink.", "School"),
    DictionaryEntry("pencil", "lapis", "A tool for writing or drawing.", "School"),
    DictionaryEntry("people", "mga tao", "Human beings in general or a group of persons.", "People"),
    DictionaryEntry("person", "tao", "A human being.", "People"),
    DictionaryEntry("phone", "telepono", "A device used to communicate over a distance.", "Technology"),
    DictionaryEntry("photo", "larawan", "An image made by a camera.", "Technology"),
    DictionaryEntry("picture", "larawan", "An image or drawing.", "Learning"),
    DictionaryEntry("piece", "piraso", "A part of something.", "General"),
    DictionaryEntry("place", "lugar", "A particular area or location.", "Places"),
    DictionaryEntry("plan", "plano / magplano", "An idea for how to do something.", "Actions"),
    DictionaryEntry("plant", "halaman / magtanim", "A living thing that grows in soil or water.", "Nature"),
    DictionaryEntry("plate", "pinggan", "A flat dish used for serving food.", "Objects"),
    DictionaryEntry("police", "pulis", "People responsible for enforcing the law.", "Safety"),
    DictionaryEntry("poor", "mahirap / dukha", "Having little money or resources.", "People"),
    DictionaryEntry("popular", "popular / tanyag", "Liked or known by many people.", "Descriptions"),
    DictionaryEntry("possible", "posible", "Able to happen or be done.", "General"),
    DictionaryEntry("potato", "patatas", "A starchy vegetable that grows underground.", "Food"),
    DictionaryEntry("practice", "pagsasanay / magsanay", "Repeated activity to improve a skill.", "Learning"),
    DictionaryEntry("prepare", "maghanda", "To make ready for something.", "Actions"),
    DictionaryEntry("present", "kasalukuyan / regalo", "The time now or a gift.", "General"),
    DictionaryEntry("pretty", "maganda", "Attractive or pleasing in appearance.", "Descriptions"),
    DictionaryEntry("price", "presyo", "The amount of money needed to buy something.", "Money"),
    DictionaryEntry("problem", "suliranin / problema", "A situation that needs a solution.", "Learning"),
    DictionaryEntry("promise", "pangako / mangako", "A statement that someone will do something.", "People"),
    DictionaryEntry("protect", "protektahan", "To keep someone or something safe.", "Safety"),
    DictionaryEntry("public", "pampubliko", "Open or available to everyone.", "General"),
    DictionaryEntry("pull", "hilahin", "To move something toward oneself.", "Actions"),
    DictionaryEntry("push", "itulak", "To move something away by applying force.", "Actions"),
    DictionaryEntry("put", "ilagay", "To move something to a place or position.", "Actions"),
    DictionaryEntry("queen", "reyna", "A female ruler or the wife of a king.", "People"),
    DictionaryEntry("quick", "mabilis", "Moving or happening in a short time.", "Descriptions"),
    DictionaryEntry("quickly", "nang mabilis", "At a fast speed.", "Descriptions"),
    DictionaryEntry("quiet", "tahimik", "Making little or no noise.", "Descriptions"),
    DictionaryEntry("rabbit", "kuneho", "A small mammal with long ears.", "Animals"),
    DictionaryEntry("rain", "ulan", "Water that falls from clouds.", "Nature"),
    DictionaryEntry("rainbow", "bahaghari", "A colored arc that can appear after rain.", "Nature"),
    DictionaryEntry("rainy", "maulan", "Having a lot of rain.", "Nature"),
    DictionaryEntry("raise", "itaas / palakihin", "To lift something or increase an amount.", "Actions"),
    DictionaryEntry("reach", "maabot", "To arrive at or extend to something.", "Actions"),
    DictionaryEntry("ready", "handa", "Prepared for something.", "Descriptions"),
    DictionaryEntry("real", "totoo", "Actually existing or happening.", "Descriptions"),
    DictionaryEntry("reason", "dahilan", "An explanation for an action or event.", "Learning"),
    DictionaryEntry("receive", "tumanggap", "To get or be given something.", "Actions"),
    DictionaryEntry("remember", "tandaan / maalala", "To keep something in memory or bring it back to mind.", "Learning"),
    DictionaryEntry("repair", "ayusin / kumpunihin", "To fix something that is broken.", "Actions"),
    DictionaryEntry("repeat", "ulitin", "To say or do something again.", "Actions"),
    DictionaryEntry("reply", "sumagot / tugon", "To respond to a message or question.", "Actions"),
    DictionaryEntry("rest", "pahinga / magpahinga", "A period of relaxation or to relax.", "Health"),
    DictionaryEntry("restaurant", "restawran / kainan", "A place where meals are prepared and served.", "Places"),
    DictionaryEntry("return", "bumalik / ibalik", "To come back or give something back.", "Actions"),
    DictionaryEntry("rich", "mayaman", "Having a lot of money or valuable resources.", "People"),
    DictionaryEntry("ride", "sumakay", "To travel in or on a vehicle or animal.", "Transport"),
    DictionaryEntry("right", "kanan / tama", "The side opposite left; also correct.", "General"),
    DictionaryEntry("river", "ilog", "A large natural stream of water.", "Nature"),
    DictionaryEntry("road", "kalsada / daan", "A route for vehicles or people.", "Places"),
    DictionaryEntry("rock", "bato", "A hard natural mineral material.", "Nature"),
    DictionaryEntry("roof", "bubong", "The covering on top of a building.", "Objects"),
    DictionaryEntry("room", "kuwarto / silid", "A part of a building enclosed by walls.", "Places"),
    DictionaryEntry("round", "bilog", "Shaped like a circle or sphere.", "Descriptions"),
    DictionaryEntry("rule", "patakaran / tuntunin", "An instruction that says what is allowed or expected.", "Learning"),
    DictionaryEntry("safe", "ligtas", "Protected from danger.", "Safety"),
    DictionaryEntry("salt", "asin", "A mineral used to season food.", "Food"),
    DictionaryEntry("same", "pareho", "Not different.", "Descriptions"),
    DictionaryEntry("Saturday", "Sabado", "The day of the week after Friday.", "Time & Nature"),
    DictionaryEntry("save", "mag-ipon / iligtas / i-save", "To keep for later, rescue, or store digital information.", "Actions"),
    DictionaryEntry("say", "sabihin", "To express something in words.", "Actions"),
    DictionaryEntry("school", "paaralan", "A place where people learn.", "School"),
    DictionaryEntry("science", "agham", "The systematic study of the natural world.", "Learning"),
    DictionaryEntry("sea", "dagat", "A large area of salt water.", "Nature"),
    DictionaryEntry("season", "panahon / season", "One of the main periods of the year or a recurring time.", "Time & Nature"),
    DictionaryEntry("second", "ikalawa / segundo", "Coming after first; also a unit of time.", "Numbers"),
    DictionaryEntry("secret", "lihim", "Something kept from others.", "General"),
    DictionaryEntry("see", "makita", "To notice something with the eyes.", "Actions"),
    DictionaryEntry("sell", "magbenta", "To exchange something for money.", "Money"),
    DictionaryEntry("send", "ipadala", "To cause something to go to another person or place.", "Actions"),
    DictionaryEntry("sentence", "pangungusap", "A group of words expressing a complete thought.", "Learning"),
    DictionaryEntry("seven", "pito", "The number 7.", "Numbers"),
    DictionaryEntry("share", "ibahagi", "To give part of something to others.", "People"),
    DictionaryEntry("sharp", "matalas", "Having an edge or point that can cut easily.", "Descriptions"),
    DictionaryEntry("sheep", "tupa", "A farm animal raised for wool or meat.", "Animals"),
    DictionaryEntry("shirt", "kamiseta", "A garment worn on the upper body.", "Clothing"),
    DictionaryEntry("shoe", "sapatos", "A covering worn on the foot.", "Clothing"),
    DictionaryEntry("shop", "tindahan / mamili", "A place to buy things or the act of shopping.", "Money"),
    DictionaryEntry("short", "maikli / pandak", "Not long or not tall.", "Descriptions"),
    DictionaryEntry("shoulder", "balikat", "The body part joining the arm and torso.", "Body"),
    DictionaryEntry("sick", "may sakit", "Not in good health.", "Health"),
    DictionaryEntry("side", "gilid", "A surface or area of an object.", "Places"),
    DictionaryEntry("sign", "palatandaan / pumirma", "A mark or symbol, or to write one's signature.", "General"),
    DictionaryEntry("silent", "walang tunog", "Making no sound.", "Descriptions"),
    DictionaryEntry("silver", "pilak", "A shiny gray-white metal.", "Objects"),
    DictionaryEntry("sister", "kapatid na babae", "A female sibling.", "Family"),
    DictionaryEntry("six", "anim", "The number 6.", "Numbers"),
    DictionaryEntry("skin", "balat", "The outer covering of the body.", "Body"),
    DictionaryEntry("sky", "kalangitan", "The space above the earth.", "Nature"),
    DictionaryEntry("sleep", "matulog", "To rest with the eyes closed and reduced awareness.", "Health"),
    DictionaryEntry("sleepy", "inaantok", "Feeling that one needs to sleep.", "Health"),
    DictionaryEntry("smell", "amoy / umamoy", "The sense of odor or to notice an odor.", "Body"),
    DictionaryEntry("smile", "ngiti / ngumiti", "A happy or friendly expression on the face.", "Feelings"),
    DictionaryEntry("snake", "ahas", "A long, legless reptile.", "Animals"),
    DictionaryEntry("snow", "niyebe", "Frozen ice crystals that fall from clouds.", "Nature"),
    DictionaryEntry("soap", "sabon", "A substance used for washing.", "Health"),
    DictionaryEntry("sock", "medyas", "A garment worn on the foot inside a shoe.", "Clothing"),
    DictionaryEntry("some", "ilan / kaunti", "An unspecified amount or number.", "Numbers"),
    DictionaryEntry("someone", "isang tao / may isang tao", "An unknown or unspecified person.", "People"),
    DictionaryEntry("son", "anak na lalaki", "A male child in relation to a parent.", "Family"),
    DictionaryEntry("song", "awit / kanta", "A piece of music with or without words.", "Entertainment"),
    DictionaryEntry("soon", "sa lalong madaling panahon", "In a short time.", "Time & Nature"),
    DictionaryEntry("sound", "tunog", "Something heard.", "Sounds"),
    DictionaryEntry("south", "timog", "A main direction opposite north.", "Places"),
    DictionaryEntry("space", "kalawakan / espasyo", "The area beyond Earth or an empty area.", "Science"),
    DictionaryEntry("spoon", "kutsara", "A utensil used for eating or stirring.", "Objects"),
    DictionaryEntry("spring", "tagsibol / bukal", "A season in some countries or water flowing from the ground.", "Nature"),
    DictionaryEntry("square", "parisukat", "A shape with four equal sides and four right angles.", "Science"),
    DictionaryEntry("stand", "tumayo", "To be upright on the feet.", "Actions"),
    DictionaryEntry("star", "bituin", "A bright object in space that produces light.", "Nature"),
    DictionaryEntry("start", "simula / magsimula", "To begin.", "Actions"),
    DictionaryEntry("stay", "manatili", "To remain in a place or condition.", "Actions"),
    DictionaryEntry("stomach", "tiyan", "The body part that helps digest food.", "Body"),
    DictionaryEntry("stone", "bato", "A small piece of rock.", "Nature"),
    DictionaryEntry("stop", "huminto / ihinto", "To cease moving or doing something.", "Actions"),
    DictionaryEntry("store", "tindahan / mag-imbak", "A place to buy things or to keep items.", "Money"),
    DictionaryEntry("story", "kuwento", "An account of events, real or imagined.", "Learning"),
    DictionaryEntry("street", "kalye", "A public road in a town or city.", "Places"),
    DictionaryEntry("study", "mag-aral / pag-aaral", "To learn about a subject by reading or practicing.", "Learning"),
    DictionaryEntry("successful", "matagumpay", "Having achieved a desired result.", "Descriptions"),
    DictionaryEntry("sugar", "asukal", "A sweet substance used in food.", "Food"),
    DictionaryEntry("summer", "tag-init", "The warmest season in some countries.", "Time & Nature"),
    DictionaryEntry("Sunday", "Linggo", "The day of the week after Saturday.", "Time & Nature"),
    DictionaryEntry("sun", "araw", "The star that gives Earth light and heat.", "Nature"),
    DictionaryEntry("sunny", "maaraw", "Having plenty of sunshine.", "Nature"),
    DictionaryEntry("supermarket", "supermarket / malaking pamilihan", "A large store selling food and household goods.", "Places"),
    DictionaryEntry("surprise", "sorpresa / magulat", "An unexpected event or feeling of sudden wonder.", "Feelings"),
    DictionaryEntry("sweet", "matamis", "Having the taste of sugar; also kind or pleasant.", "Food"),
    DictionaryEntry("table", "mesa", "A piece of furniture with a flat top.", "Objects"),
    DictionaryEntry("take", "kunin / dalhin", "To get, carry, or remove something.", "Actions"),
    DictionaryEntry("talk", "makipag-usap", "To speak with someone.", "Actions"),
    DictionaryEntry("tall", "matangkad", "Having greater than average height.", "Descriptions"),
    DictionaryEntry("tea", "tsaa", "A drink made by steeping leaves in hot water.", "Food"),
    DictionaryEntry("team", "koponan / pangkat", "A group working or playing together.", "Sports"),
    DictionaryEntry("ten", "sampu", "The number 10.", "Numbers"),
    DictionaryEntry("test", "pagsusulit / subukan", "An assessment or an act of checking something.", "School"),
    DictionaryEntry("than", "kaysa", "Used to compare things.", "Connectors"),
    DictionaryEntry("then", "pagkatapos / noon", "At that time or next in sequence.", "Time & Nature"),
    DictionaryEntry("there", "doon", "In or at that place.", "Places"),
    DictionaryEntry("thick", "makapal", "Having a large distance between opposite sides.", "Descriptions"),
    DictionaryEntry("thin", "manipis / payat", "Having little thickness or a slender body.", "Descriptions"),
    DictionaryEntry("thing", "bagay", "An object, idea, or matter.", "General"),
    DictionaryEntry("thirsty", "uhaw", "Needing or wanting a drink.", "Health"),
    DictionaryEntry("thousand", "libo", "The number 1,000.", "Numbers"),
    DictionaryEntry("three", "tatlo", "The number 3.", "Numbers"),
    DictionaryEntry("Thursday", "Huwebes", "The day of the week before Friday.", "Time & Nature"),
    DictionaryEntry("ticket", "tiket", "A pass or document allowing entry or travel.", "Transport"),
    DictionaryEntry("tired", "pagod", "Needing rest.", "Health"),
    DictionaryEntry("tooth", "ngipin", "A hard structure in the mouth used for biting and chewing.", "Body"),
    DictionaryEntry("toothbrush", "sipilyo", "A brush used to clean teeth.", "Health"),
    DictionaryEntry("town", "bayan", "A settlement smaller than a city in many contexts.", "Places"),
    DictionaryEntry("train", "tren / magsanay", "A rail vehicle or to teach/practice a skill.", "Transport"),
    DictionaryEntry("travel", "maglakbay", "To go from one place to another.", "Transport"),
    DictionaryEntry("tree", "puno", "A tall plant with a woody trunk.", "Nature"),
    DictionaryEntry("trip", "biyahe", "A journey to a place.", "Transport"),
    DictionaryEntry("true", "totoo", "In accordance with facts or reality.", "Descriptions"),
    DictionaryEntry("Tuesday", "Martes", "The day of the week after Monday.", "Time & Nature"),
    DictionaryEntry("turn", "lumiko / umikot", "To change direction or rotate.", "Actions"),
    DictionaryEntry("twelve", "labindalawa", "The number 12.", "Numbers"),
    DictionaryEntry("twenty", "dalawampu", "The number 20.", "Numbers"),
    DictionaryEntry("two", "dalawa", "The number 2.", "Numbers"),
    DictionaryEntry("umbrella", "payong", "An object used for protection from rain or sun.", "Objects"),
    DictionaryEntry("uncle", "tiyo / tito", "The brother of a parent or an aunt's spouse.", "Family"),
    DictionaryEntry("under", "sa ilalim", "In a lower position than something.", "Places"),
    DictionaryEntry("unhappy", "malungkot / hindi masaya", "Not happy.", "Feelings"),
    DictionaryEntry("uniform", "uniporme", "A set of clothes worn by members of a group.", "Clothing"),
    DictionaryEntry("university", "unibersidad", "An institution for higher education.", "School"),
    DictionaryEntry("until", "hanggang", "Up to a particular time or point.", "Time & Nature"),
    DictionaryEntry("up", "itaas / pataas", "Toward a higher place.", "Places"),
    DictionaryEntry("use", "gamitin", "To do something with a tool or object.", "Actions"),
    DictionaryEntry("useful", "kapaki-pakinabang", "Able to help or serve a purpose.", "Descriptions"),
    DictionaryEntry("usually", "karaniwan", "In most cases or most of the time.", "Time & Nature"),
    DictionaryEntry("vacation", "bakasyon", "A period away from work or school for rest or travel.", "Time & Nature"),
    DictionaryEntry("vegetable", "gulay", "An edible plant or part of a plant.", "Food"),
    DictionaryEntry("vehicle", "sasakyan", "A machine used to transport people or goods.", "Transport"),
    DictionaryEntry("very", "napaka- / lubha", "To a high degree.", "General"),
    DictionaryEntry("village", "nayon / baryo", "A small community in a rural area.", "Places"),
    DictionaryEntry("visit", "bumisita", "To go to see a person or place.", "Actions"),
    DictionaryEntry("visitor", "bisita", "A person who visits a place.", "People"),
    DictionaryEntry("voice", "boses", "The sound produced when a person speaks or sings.", "Sounds"),
    DictionaryEntry("wait", "maghintay", "To stay until something happens.", "Actions"),
    DictionaryEntry("wall", "dingding", "A vertical structure enclosing or dividing a space.", "Objects"),
    DictionaryEntry("wallet", "pitaka", "A small case for carrying money and cards.", "Objects"),
    DictionaryEntry("want", "gustuhin / gusto", "To wish to have or do something.", "Feelings"),
    DictionaryEntry("war", "digmaan", "Armed conflict between groups or countries.", "People"),
    DictionaryEntry("wash", "maghugas / labhan", "To clean with water or another liquid.", "Actions"),
    DictionaryEntry("watch", "relo / manood", "A timepiece or to look at something over time.", "Objects"),
    DictionaryEntry("weather", "panahon", "The condition of the atmosphere at a time and place.", "Nature"),
    DictionaryEntry("Wednesday", "Miyerkules", "The day of the week after Tuesday.", "Time & Nature"),
    DictionaryEntry("week", "linggo", "A period of seven days.", "Time & Nature"),
    DictionaryEntry("weekend", "katapusan ng linggo", "Saturday and Sunday in many calendars.", "Time & Nature"),
    DictionaryEntry("welcome", "maligayang pagdating / walang anuman", "A greeting to someone arriving or a response to thanks.", "Greetings"),
    DictionaryEntry("west", "kanluran", "A main direction opposite east.", "Places"),
    DictionaryEntry("wet", "basa", "Covered or soaked with liquid.", "Descriptions"),
    DictionaryEntry("white", "puti", "The lightest color.", "Colors"),
    DictionaryEntry("wide", "malapad", "Having a large distance from side to side.", "Descriptions"),
    DictionaryEntry("wife", "asawa (babae)", "A married woman in relation to a spouse.", "Family"),
    DictionaryEntry("wind", "hangin", "Moving air.", "Nature"),
    DictionaryEntry("window", "bintana", "An opening in a wall that lets in light or air.", "Objects"),
    DictionaryEntry("windy", "mahangin", "Having a lot of wind.", "Nature"),
    DictionaryEntry("winter", "taglamig", "The coldest season in some countries.", "Time & Nature"),
    DictionaryEntry("woman", "babae", "An adult female person.", "People"),
    DictionaryEntry("wonderful", "kahanga-hanga", "Extremely good or enjoyable.", "Descriptions"),
    DictionaryEntry("wood", "kahoy", "The hard material that forms tree trunks and branches.", "Nature"),
    DictionaryEntry("world", "mundo", "The Earth and its people; all that exists.", "Nature"),
    DictionaryEntry("wrong", "mali", "Not correct or not right.", "Descriptions"),
    DictionaryEntry("year", "taon", "A period of twelve months.", "Time & Nature"),
    DictionaryEntry("yellow", "dilaw", "A bright color like ripe bananas or sunlight.", "Colors"),
    DictionaryEntry("young", "bata / bata pa", "Having lived for only a short time.", "Descriptions"),
    DictionaryEntry("zero", "sero / wala", "The number 0; no quantity.", "Numbers"),
    DictionaryEntry("above all", "higit sa lahat", "More importantly than anything else.", "Connectors"),
    DictionaryEntry("as soon as", "sa sandaling", "Immediately when something happens.", "Connectors"),
    DictionaryEntry("at least", "kahit man lang", "Not less than a particular amount.", "Connectors"),
    DictionaryEntry("by the way", "siya nga pala", "Used to introduce an additional point.", "Connectors"),
    DictionaryEntry("for example", "halimbawa", "Used to introduce an example.", "Connectors"),
    DictionaryEntry("in front of", "sa harap ng", "At the forward side of something.", "Places"),
    DictionaryEntry("in the morning", "sa umaga", "During the morning.", "Time & Nature"),
    DictionaryEntry("of course", "siyempre", "Used to show that something is expected or obvious.", "Connectors"),
    DictionaryEntry("on time", "sa tamang oras", "At the planned or expected time.", "Time & Nature"),
    DictionaryEntry("take care", "mag-ingat / alagaan", "To be careful or look after someone.", "People"),
    DictionaryEntry("thank you very much", "maraming salamat", "A strong expression of gratitude.", "Greetings"),
    DictionaryEntry("you are welcome", "walang anuman", "A polite response to thanks.", "Greetings"),
    DictionaryEntry("how much", "magkano / gaano karami", "Used to ask about price or amount.", "Questions"),
    DictionaryEntry("how many", "ilan", "Used to ask about number.", "Questions"),
    DictionaryEntry("how long", "gaano katagal / kahaba", "Used to ask about duration or length.", "Questions"),
    DictionaryEntry("how often", "gaano kadalas", "Used to ask about frequency.", "Questions"),
    DictionaryEntry("I am sorry", "pasensya na", "An expression used to apologize.", "Greetings"),
    DictionaryEntry("I do not know", "hindi ko alam", "A phrase expressing lack of knowledge.", "Expressions"),
    DictionaryEntry("I understand", "naiintindihan ko", "A phrase expressing understanding.", "Expressions"),
    DictionaryEntry("I love you", "mahal kita", "An expression of love.", "Expressions"),
    DictionaryEntry("excuse me", "makikiraan / paumanhin", "A polite phrase to get attention or pass by.", "Greetings"),
    DictionaryEntry("good luck", "swertehin ka sana", "A phrase wishing someone success.", "Greetings"),
    DictionaryEntry("congratulations", "binabati kita / pagbati", "A phrase used to praise an achievement.", "Greetings"),
    DictionaryEntry("take your time", "huwag magmadali", "A phrase telling someone not to hurry.", "Expressions"),
    DictionaryEntry("be careful", "mag-ingat", "A phrase warning someone to avoid danger.", "Safety"),
    DictionaryEntry("see you later", "kita tayo mamaya", "A phrase used when expecting to meet again soon.", "Greetings"),
    DictionaryEntry("see you tomorrow", "kita tayo bukas", "A phrase used when expecting to meet the next day.", "Greetings"),
    DictionaryEntry("good night", "magandang gabi / tulog nang mahimbing", "A phrase said when parting at night or before sleep.", "Greetings"),
    DictionaryEntry("well done", "mahusay / magaling", "A phrase praising a good effort or result.", "Greetings"),
    DictionaryEntry("never mind", "huwag na / bale wala", "A phrase meaning something is not important or need not be done.", "Expressions"),
    DictionaryEntry("no problem", "walang problema", "A phrase saying something is not difficult or troublesome.", "Expressions"),
    DictionaryEntry("right now", "ngayon na / sa ngayon", "At this very moment.", "Time & Nature"),
    DictionaryEntry("from time to time", "paminsan-minsan", "Occasionally.", "Time & Nature"),
    DictionaryEntry("as usual", "gaya ng dati", "In the way that normally happens.", "Time & Nature"),
    DictionaryEntry("in a hurry", "nagmamadali", "Moving or acting quickly because of limited time.", "Expressions"),
    DictionaryEntry("make sure", "tiyakin", "To check that something is correct or certain.", "Actions"),
    DictionaryEntry("look after", "alagaan", "To care for someone or something.", "Actions"),
    DictionaryEntry("find out", "alamin", "To discover information.", "Actions"),
    DictionaryEntry("give up", "sumuko", "To stop trying to do something.", "Actions"),
    DictionaryEntry("keep going", "magpatuloy", "To continue moving or doing something.", "Actions"),
    DictionaryEntry("look for", "hanapin", "To try to find someone or something.", "Actions"),
    DictionaryEntry("pick up", "pulutin / sunduin", "To lift something or collect someone; context matters.", "Actions"),
    DictionaryEntry("put away", "itabi", "To return something to its proper place.", "Actions"),
    DictionaryEntry("turn on", "buksan", "To activate a device or light.", "Technology"),
    DictionaryEntry("turn off", "patayin / i-off", "To deactivate a device or light.", "Technology"),
    DictionaryEntry("log in", "mag-log in / mag-sign in", "To enter an account using credentials.", "Technology"),
    DictionaryEntry("log out", "mag-log out / mag-sign out", "To exit an account.", "Technology"),
    DictionaryEntry("download", "i-download", "To copy data from an online source to a device.", "Technology"),
    DictionaryEntry("upload", "i-upload", "To send data from a device to an online service.", "Technology"),
    DictionaryEntry("password", "password / salitang-panseguridad", "A secret string used to access an account.", "Technology"),
    DictionaryEntry("username", "username / pangalan ng account", "A name used to identify an account.", "Technology"),
    DictionaryEntry("website", "website / pook-sapot", "A collection of related pages on the internet.", "Technology"),
    DictionaryEntry("application", "aplikasyon", "A program designed to perform a task.", "Technology"),
    DictionaryEntry("computer", "kompyuter", "An electronic device that processes information.", "Technology"),
    DictionaryEntry("keyboard", "keyboard / tipaan", "A set of keys used to type or control a device.", "Technology"),
    DictionaryEntry("screen", "screen / tabing", "The surface on which digital images are displayed.", "Technology"),
    DictionaryEntry("battery", "baterya", "A device that stores energy for electrical use.", "Technology"),
    DictionaryEntry("charger", "charger / pang-charge", "A device used to replenish a battery.", "Technology"),
    DictionaryEntry("connection", "koneksiyon / koneksyon", "A link or relationship between devices or people.", "Technology"),
    DictionaryEntry("offline", "offline / hindi nakakonekta sa internet", "Not connected to the internet or a network.", "Technology"),
    DictionaryEntry("data", "datos", "Information stored or used by a computer or person.", "Technology"),
    DictionaryEntry("file", "file / talaksan", "A collection of digital information stored as a unit.", "Technology"),
    DictionaryEntry("folder", "folder", "A place used to organize files.", "Technology"),
    DictionaryEntry("update", "pag-update / i-update", "A change that improves or adds to something.", "Technology"),
    DictionaryEntry("settings", "mga setting / mga pagsasaayos", "Options that control how a device or app works.", "Technology"),
    DictionaryEntry("notification", "abiso / notipikasyon", "A message that alerts someone to an event.", "Technology"),
    DictionaryEntry("privacy", "pagkapribado", "The right to keep personal information or activities private.", "Safety"),
    DictionaryEntry("secure", "ligtas / protektado", "Protected against danger or unauthorized access.", "Safety"),
    DictionaryEntry("account", "account / akawnt", "A personal record used to access a service.", "Technology"),
    DictionaryEntry("profile", "profile / personal na tala", "Information describing a person or account.", "Technology"),
    DictionaryEntry("score", "iskor / puntos", "The number of points earned in a test or game.", "Games"),
    DictionaryEntry("leaderboard", "talahanayan ng ranggo", "A list ranking players by score or achievement.", "Games"),
    DictionaryEntry("backup", "backup / kopyang panseguridad", "A copy of data kept in case the original is lost.", "Technology"),
    DictionaryEntry("restore", "ibalik / i-restore", "To bring something back to an earlier state.", "Technology"),
    DictionaryEntry("dictionary", "diksyunaryo", "A reference tool listing words and meanings.", "Learning"),
    DictionaryEntry("vocabulary", "talasalitaan", "The words known or used in a language.", "Learning"),
    DictionaryEntry("translation", "salin / pagsasalin", "The expression of meaning in another language.", "Learning"),
    DictionaryEntry("pronunciation", "pagbigkas", "The way a word is spoken.", "Learning"),
    DictionaryEntry("grammar", "balarila / gramatika", "Rules describing how a language is structured.", "Learning"),
    DictionaryEntry("synonym", "kasingkahulugan", "A word with the same or a similar meaning.", "Learning"),
    DictionaryEntry("antonym", "kasalungat na salita", "A word with an opposite meaning.", "Learning"),
    DictionaryEntry("noun", "pangngalan", "A word naming a person, place, thing, or idea.", "Learning"),
    DictionaryEntry("verb", "pandiwa", "A word expressing an action, state, or occurrence.", "Learning"),
    DictionaryEntry("adjective", "pang-uri", "A word that describes a noun.", "Learning"),
    DictionaryEntry("adverb", "pang-abay", "A word that modifies a verb, adjective, or another adverb.", "Learning"),
    DictionaryEntry("pronoun", "panghalip", "A word used instead of a noun.", "Learning"),
    DictionaryEntry("preposition", "pang-ukol", "A word showing a relationship between a noun and other words.", "Learning"),
    DictionaryEntry("conjunction", "pangatnig", "A word that connects words, phrases, or clauses.", "Learning"),
    DictionaryEntry("punctuation", "bantas", "Marks such as commas and periods used in writing.", "Learning"),
    DictionaryEntry("spelling", "pagbaybay", "The order of letters in a written word.", "Learning"),
    DictionaryEntry("alphabet", "alpabeto", "A set of letters used in a language.", "Learning"),
    DictionaryEntry("syllable", "pantig", "A unit of pronunciation containing one vowel sound.", "Learning"),
    DictionaryEntry("meaning", "kahulugan", "The idea or message expressed by a word or sign.", "Learning"),
    DictionaryEntry("definition", "depinisyon / kahulugan", "A statement explaining what a word or concept means.", "Learning"),
    DictionaryEntry("essay", "sanaysay", "A short piece of writing about a subject.", "Learning"),
    DictionaryEntry("paragraph", "talata", "A group of sentences about one main idea.", "Learning"),
    DictionaryEntry("research", "pananaliksik", "Careful study to discover or confirm information.", "Learning"),
    DictionaryEntry("assignment", "takdang-aralin", "A task given to be completed.", "School"),
    DictionaryEntry("homework", "takdang-aralin", "School work done outside class.", "School"),
    DictionaryEntry("exam", "pagsusulit", "A formal test of knowledge or skill.", "School"),
    DictionaryEntry("grade", "grado / marka", "A score or level showing performance.", "School"),
    DictionaryEntry("semester", "semestre", "One of two main academic periods in a year.", "School"),
    DictionaryEntry("subject", "asignatura", "A topic or course studied at school.", "School"),
    DictionaryEntry("schedule", "iskedyul / talaorasan", "A plan showing when activities will happen.", "Time & Nature"),
    DictionaryEntry("deadline", "takdang oras / huling araw", "The latest time or date to finish something.", "Work"),
    DictionaryEntry("goal", "layunin", "Something a person wants to achieve.", "Learning"),
    DictionaryEntry("progress", "pag-unlad", "Movement toward a goal or improvement over time.", "Learning"),
    DictionaryEntry("achievement", "tagumpay / nagawa", "Something successfully completed.", "Learning"),
    DictionaryEntry("improve", "pagbutihin / umunlad", "To make or become better.", "Actions"),
    DictionaryEntry("mistake", "pagkakamali", "An action or judgment that is wrong.", "Learning"),
    DictionaryEntry("correct", "tama / iwasto", "Free from errors or to fix an error.", "Learning"),
    DictionaryEntry("incorrect", "mali", "Not correct.", "Learning"),
    DictionaryEntry("compare", "ihambing", "To examine similarities and differences.", "Learning"),
    DictionaryEntry("explain", "ipaliwanag", "To make an idea clear by giving details.", "Learning"),
    DictionaryEntry("solve", "lutasin", "To find an answer to a problem.", "Learning"),
    DictionaryEntry("solution", "solusyon / kalutasan", "An answer to a problem.", "Learning"),
    DictionaryEntry("calculate", "kalkulahin", "To work out a number using mathematics.", "Learning"),
    DictionaryEntry("mathematics", "matematika", "The study of numbers, quantities, shapes, and patterns.", "Learning"),
    DictionaryEntry("addition", "pagdaragdag", "The mathematical operation of combining numbers.", "Learning"),
    DictionaryEntry("subtraction", "pagbabawas", "The mathematical operation of taking one amount away from another.", "Learning"),
    DictionaryEntry("multiplication", "pagpaparami", "The mathematical operation of repeated addition.", "Learning"),
    DictionaryEntry("division", "paghahati / dibisyon", "The mathematical operation of splitting into equal parts.", "Learning"),
    DictionaryEntry("fraction", "praksiyon / bahagi", "A number representing part of a whole.", "Learning"),
    DictionaryEntry("percentage", "porsiyento", "A rate or proportion out of one hundred.", "Learning"),
    DictionaryEntry("angle", "anggulo", "The space between two lines or surfaces that meet.", "Science"),
    DictionaryEntry("circle", "bilog", "A round shape whose points are equally distant from its center.", "Science"),
    DictionaryEntry("triangle", "tatsulok", "A shape with three sides.", "Science"),
    DictionaryEntry("rectangle", "parihaba", "A four-sided shape with four right angles.", "Science"),
    DictionaryEntry("measurement", "pagsukat", "The size, amount, or dimension of something.", "Science"),
    DictionaryEntry("length", "haba", "The measurement from one end to another.", "Science"),
    DictionaryEntry("width", "lapad", "The measurement from side to side.", "Science"),
    DictionaryEntry("weight", "bigat", "How heavy something is.", "Science"),
    DictionaryEntry("distance", "distansiya / layo", "The amount of space between two points.", "Science"),
    DictionaryEntry("speed", "bilis", "How fast something moves.", "Science"),
    DictionaryEntry("energy", "enerhiya", "The ability to do work or cause change.", "Science"),
    DictionaryEntry("force", "puwersa", "A push or pull that can change motion.", "Science"),
    DictionaryEntry("gravity", "grabidad", "The force that attracts objects toward one another.", "Science"),
    DictionaryEntry("matter", "materya", "Anything that has mass and occupies space.", "Science"),
    DictionaryEntry("experiment", "eksperimento", "A test performed to learn or check something.", "Science"),
    DictionaryEntry("environment", "kapaligiran", "The surroundings in which people and other living things exist.", "Nature"),
    DictionaryEntry("pollution", "polusyon", "Harmful substances or waste in the environment.", "Nature"),
    DictionaryEntry("recycle", "i-recycle / muling gamitin", "To process used materials so they can be used again.", "Nature"),
    DictionaryEntry("plastic", "plastik", "A material that can be shaped and is often used in products.", "Objects"),
    DictionaryEntry("earthquake", "lindol", "Sudden shaking of the ground.", "Nature"),
    DictionaryEntry("storm", "bagyo / unos", "A period of severe weather with strong winds or rain.", "Nature"),
    DictionaryEntry("thunder", "kulog", "The sound caused by lightning.", "Nature"),
    DictionaryEntry("flood", "baha", "An overflow of water onto normally dry land.", "Nature"),
    DictionaryEntry("drought", "tagtuyot", "A long period with little or no rain.", "Nature"),
    DictionaryEntry("volcano", "bulkan", "A mountain or opening through which lava and gases can escape.", "Nature"),
    DictionaryEntry("soil", "lupa", "The upper layer of earth where plants grow.", "Nature"),
    DictionaryEntry("seed", "binhi", "The part of a plant that can grow into a new plant.", "Nature"),
    DictionaryEntry("root", "ugat", "The part of a plant that usually grows underground.", "Nature"),
    DictionaryEntry("branch", "sanga", "A part of a tree growing from the trunk.", "Nature"),
    DictionaryEntry("reptile", "reptilya", "A vertebrate such as a snake or lizard.", "Animals"),
    DictionaryEntry("mammal", "mamalya", "An animal group whose females feed young with milk.", "Animals"),
    DictionaryEntry("insect", "insekto", "A small animal with six legs and usually three body sections.", "Animals"),
    DictionaryEntry("butterfly", "paru-paro", "An insect with large colorful wings.", "Animals"),
    DictionaryEntry("bee", "bubuyog", "A flying insect that collects nectar and can pollinate flowers.", "Animals"),
    DictionaryEntry("mosquito", "lamok", "A small flying insect; some species spread disease.", "Animals"),
    DictionaryEntry("ant", "langgam", "A small social insect.", "Animals"),
    DictionaryEntry("horse", "kabayo", "A large animal often used for riding or work.", "Animals"),
    DictionaryEntry("cow", "baka", "A farm animal raised for milk or meat.", "Animals"),
    DictionaryEntry("pig", "baboy", "A farm animal with a stout body and snout.", "Animals"),
    DictionaryEntry("chicken", "manok", "A bird commonly raised for eggs and meat.", "Animals"),
    DictionaryEntry("duck", "pato", "A water bird with a broad bill.", "Animals"),
    DictionaryEntry("frog", "palaka", "An amphibian that often lives near water.", "Animals"),
    DictionaryEntry("turtle", "pagong", "A reptile with a protective shell.", "Animals"),
    DictionaryEntry("lizard", "butiki", "A reptile with four legs in many species.", "Animals"),
    DictionaryEntry("monkey", "unggoy", "A primate with hands and feet adapted for climbing.", "Animals"),
    DictionaryEntry("elephant", "elepante", "A very large mammal with a trunk.", "Animals"),
    DictionaryEntry("tiger", "tigre", "A large wild cat with dark stripes.", "Animals"),
    DictionaryEntry("whale", "balyena", "A very large marine mammal.", "Animals"),
    DictionaryEntry("dolphin", "lumba-lumba", "An intelligent marine mammal.", "Animals"),
    DictionaryEntry("shark", "pating", "A fish with a skeleton made of cartilage.", "Animals"),
    DictionaryEntry("eagle", "agila", "A large bird of prey.", "Animals"),
    DictionaryEntry("owl", "kuwago", "A bird often active at night with large eyes.", "Animals"),
    DictionaryEntry("parrot", "loro", "A bird known for a curved beak and often colorful feathers.", "Animals"),
    DictionaryEntry("spider", "gagamba", "An eight-legged animal that often spins webs.", "Animals"),
    DictionaryEntry("shrimp", "hipon", "A small crustacean often used as food.", "Food"),
    DictionaryEntry("crab", "alimango / alimasag", "A crustacean with pincers; local names vary by species.", "Animals"),
    DictionaryEntry("squid", "pusit", "A sea animal with a soft body and tentacles.", "Food"),
    DictionaryEntry("octopus", "pugita", "A sea animal with eight arms.", "Animals"),
    DictionaryEntry("coconut", "niyog", "A large fruit with a hard shell and edible flesh and water.", "Food"),
    DictionaryEntry("mango", "mangga", "A sweet tropical fruit.", "Food"),
    DictionaryEntry("papaya", "papaya", "A tropical fruit with orange flesh and black seeds.", "Food"),
    DictionaryEntry("pineapple", "pinya", "A tropical fruit with rough skin and sweet flesh.", "Food"),
    DictionaryEntry("watermelon", "pakwan", "A large fruit with watery flesh, often red.", "Food"),
    DictionaryEntry("grape", "ubas", "A small round fruit that grows in clusters.", "Food"),
    DictionaryEntry("strawberry", "presa", "A red fruit with small seeds on its surface.", "Food"),
    DictionaryEntry("tomato", "kamatis", "A red or other-colored fruit used as a vegetable in cooking.", "Food"),
    DictionaryEntry("carrot", "karot", "An orange root vegetable.", "Food"),
    DictionaryEntry("cabbage", "repolyo", "A leafy vegetable with a compact head.", "Food"),
    DictionaryEntry("soup", "sabaw", "A liquid food usually made by cooking ingredients in water or stock.", "Food"),
    DictionaryEntry("spicy", "maanghang", "Having a hot or pungent flavor.", "Food"),
    DictionaryEntry("sour", "maasim", "Having an acidic taste.", "Food"),
    DictionaryEntry("bitter", "mapait", "Having a sharp, unpleasant taste.", "Food"),
    DictionaryEntry("salty", "maalat", "Having the taste of salt.", "Food"),
    DictionaryEntry("bland", "matabang", "Having little flavor.", "Food"),
    DictionaryEntry("crispy", "malutong", "Firm and making a cracking sound when bitten.", "Food"),
    DictionaryEntry("chewy", "makunat", "Requiring much chewing.", "Food"),
    DictionaryEntry("juicy", "makatas", "Containing a lot of liquid.", "Food"),
    DictionaryEntry("ripe", "hinog", "Fully developed and ready to eat or use.", "Food"),
    DictionaryEntry("unripe", "hilaw", "Not yet fully developed or ready to eat.", "Food"),
    DictionaryEntry("rotten", "bulok", "Decayed or spoiled.", "Food"),
    DictionaryEntry("ingredient", "sangkap", "One of the items used to make a dish.", "Food"),
    DictionaryEntry("recipe", "resipe / paraan ng pagluluto", "Instructions for preparing a dish.", "Food"),
    DictionaryEntry("stir", "haluin", "To mix something by moving it around.", "Actions"),
    DictionaryEntry("chop", "tadtarin", "To cut something into pieces.", "Actions"),
    DictionaryEntry("slice", "hiwain", "To cut into thin or flat pieces.", "Actions"),
    DictionaryEntry("peel", "balatan", "To remove the outer skin of a fruit or vegetable.", "Actions"),
    DictionaryEntry("pour", "ibuhos", "To cause liquid to flow from one container to another.", "Actions"),
    DictionaryEntry("towel", "tuwalya", "A piece of absorbent cloth used for drying.", "Objects"),
    DictionaryEntry("tissue", "tisyu", "Soft paper used for cleaning or wiping.", "Objects"),
    DictionaryEntry("comb", "suklay", "A tool used to arrange hair.", "Objects"),
    DictionaryEntry("shampoo", "shampoo / panghugas ng buhok", "A liquid used to wash hair.", "Health"),
    DictionaryEntry("broom", "walis", "A tool with bristles used for sweeping.", "Objects"),
    DictionaryEntry("dustpan", "pandampot ng basura / dustpan", "A flat container used to collect swept dirt.", "Objects"),
    DictionaryEntry("trash bin", "basurahan", "A container for waste.", "Objects"),
    DictionaryEntry("bucket", "timba", "A container with a handle used to carry liquid or materials.", "Objects"),
    DictionaryEntry("mug", "malaking tasa", "A cup with a handle, often used for hot drinks.", "Objects"),
    DictionaryEntry("lunch box", "baunan", "A container used to carry a meal.", "Objects"),
    DictionaryEntry("refrigerator", "refrigerator / pridyider", "An appliance that keeps food cold.", "Technology"),
    DictionaryEntry("washing machine", "makinang panlaba", "A machine that washes clothes.", "Technology"),
    DictionaryEntry("television", "telebisyon", "A device that displays programs and moving images.", "Technology"),
    DictionaryEntry("radio", "radyo", "A device or system for receiving audio broadcasts.", "Technology"),
    DictionaryEntry("speaker", "ispiker / loudspeaker", "A device that produces sound.", "Technology"),
    DictionaryEntry("headphones", "headphones / panakip sa tainga", "A device worn over or in the ears for listening.", "Technology"),
    DictionaryEntry("microphone", "mikropono", "A device that converts sound into an electrical signal.", "Technology"),
    DictionaryEntry("printer", "printer / makinang panlimbag", "A device that prints text or images on paper.", "Technology"),
    DictionaryEntry("Wi-Fi", "Wi-Fi", "A wireless network technology.", "Technology"),
    DictionaryEntry("mobile data", "mobile data", "Internet access provided by a mobile network.", "Technology"),
    DictionaryEntry("QR code", "QR code", "A square barcode that stores information.", "Technology"),
    DictionaryEntry("application update", "update ng aplikasyon", "A newer version of an app.", "Technology"),
    DictionaryEntry("error message", "mensahe ng error", "A message explaining that a problem occurred.", "Technology"),
    DictionaryEntry("loading screen", "loading screen / screen ng pagkarga", "A screen shown while an app or task loads.", "Technology"),
    DictionaryEntry("sound effect", "epekto ng tunog", "A sound added to signal or enhance an event.", "Entertainment"),
    DictionaryEntry("background music", "musika sa likuran", "Music that plays behind the main activity.", "Entertainment"),
    DictionaryEntry("volume", "lakas ng tunog", "How loud or soft a sound is.", "Sounds"),
    DictionaryEntry("mute", "patahimikin / i-mute", "To turn off or silence sound.", "Sounds"),
    DictionaryEntry("timer", "timer / orasan", "A device or feature that measures a set period of time.", "Time & Nature"),
    DictionaryEntry("stopwatch", "segundometro", "A device used to measure elapsed time.", "Time & Nature"),
    DictionaryEntry("reminder", "paalala", "Something that helps someone remember a task or event.", "Learning"),
    DictionaryEntry("correct answer", "tamang sagot", "An answer that is right.", "School"),
    DictionaryEntry("wrong answer", "maling sagot", "An answer that is not correct.", "School"),
    DictionaryEntry("final score", "huling iskor", "The total score at the end of a game or test.", "Games"),
    DictionaryEntry("personal best", "pinakamataas na sariling marka", "The best result a person has achieved.", "Games"),
    DictionaryEntry("daily challenge", "pang-araw-araw na hamon", "A task or contest offered each day.", "Games"),
    DictionaryEntry("achievement badge", "badge ng tagumpay", "A digital symbol awarded for completing a goal.", "Games"),
    DictionaryEntry("reward", "gantimpala", "Something given in recognition of effort or success.", "Games"),
    DictionaryEntry("claim", "kunin / angkinin", "To request or collect something one is entitled to.", "Actions"),
    DictionaryEntry("purchase", "pagbili / bumili", "To buy something.", "Money"),
    DictionaryEntry("balance", "balanse / natitirang halaga", "The amount remaining in an account or the state of stability.", "Money"),
    DictionaryEntry("virtual coin", "virtual na barya", "A digital unit used inside an app or game.", "Games"),
    DictionaryEntry("level", "antas / level", "A stage or degree of progress.", "Learning"),
    DictionaryEntry("experience points", "puntos ng karanasan", "Points earned to represent progress in a game or learning app.", "Games"),
    DictionaryEntry("streak", "sunod-sunod na araw", "A series of consecutive days or events.", "Learning"),
    DictionaryEntry("study plan", "plano sa pag-aaral", "A plan for when and what to study.", "Learning"),
    DictionaryEntry("study session", "sesyon ng pag-aaral", "A period set aside for studying.", "Learning"),
    DictionaryEntry("flashcard", "flashcard / kard sa pag-aaral", "A card showing a question or term on one side and an answer on the other.", "Learning"),
    DictionaryEntry("review", "pagbabalik-aral", "The act of studying material again.", "Learning"),
    DictionaryEntry("memorize", "isaulo", "To learn something so it can be recalled from memory.", "Learning"),
    DictionaryEntry("concentrate", "magpokus / magtuon", "To direct attention toward one thing.", "Learning"),
    DictionaryEntry("focus", "pokus / magtuon", "The center of attention or to concentrate.", "Learning"),
    DictionaryEntry("motivation", "motibasyon", "The reason or desire that makes someone act.", "Feelings"),
    DictionaryEntry("confidence", "tiwala sa sarili", "Belief in one's own ability.", "Feelings"),
    DictionaryEntry("patience", "pagtitiyaga / pasensya", "The ability to wait or endure difficulty calmly.", "People"),
    DictionaryEntry("effort", "pagsisikap", "Physical or mental energy used to do something.", "Learning"),
    DictionaryEntry("discipline", "disiplina", "The ability to control behavior and follow rules or a plan.", "Learning"),
    DictionaryEntry("curious", "mausisa", "Wanting to know or learn something.", "Feelings"),
    DictionaryEntry("creative", "malikhain", "Able to make new or original ideas.", "Descriptions"),
    DictionaryEntry("imagination", "imahinasyon", "The ability to form ideas or pictures in the mind.", "Learning"),
    DictionaryEntry("talent", "talento", "A natural ability to do something well.", "People"),
    DictionaryEntry("skill", "kasanayan", "The ability to do something well through learning or practice.", "Learning"),
    DictionaryEntry("strength", "lakas", "The quality of being strong.", "Descriptions"),
    DictionaryEntry("weakness", "kahinaan", "A lack of strength or an area needing improvement.", "Descriptions"),
    DictionaryEntry("success", "tagumpay", "The achievement of a desired goal.", "Learning"),
    DictionaryEntry("failure", "pagkabigo", "A lack of success.", "Learning"),
    DictionaryEntry("opportunity", "pagkakataon", "A situation that makes something possible.", "General"),
    DictionaryEntry("challenge", "hamon", "A difficult task that tests ability.", "Learning"),
    DictionaryEntry("choice", "pagpili / pagpipilian", "An act or possibility of choosing.", "General"),
    DictionaryEntry("decision", "desisyon / pasya", "A conclusion reached after considering options.", "Actions"),
    DictionaryEntry("responsibility", "responsibilidad / pananagutan", "A duty or obligation.", "People"),
    DictionaryEntry("freedom", "kalayaan", "The state of being free.", "People"),
    DictionaryEntry("justice", "katarungan", "Fair treatment according to rights and laws.", "People"),
    DictionaryEntry("truth", "katotohanan", "The quality of being in accordance with facts.", "People"),
    DictionaryEntry("lie", "kasinungalingan / magsinungaling", "A false statement made intentionally.", "People"),
    DictionaryEntry("forgive", "magpatawad", "To stop feeling anger toward someone for a wrong.", "Feelings"),
    DictionaryEntry("grateful", "nagpapasalamat", "Feeling or showing thanks.", "Feelings"),
    DictionaryEntry("gratitude", "pasasalamat", "The feeling of being thankful.", "Feelings"),
    DictionaryEntry("jealous", "nagseselos / naiinggit", "Feeling upset because of a rival or another person's advantage.", "Feelings"),
    DictionaryEntry("lonely", "nalulungkot / nag-iisa", "Feeling sad because one is alone or disconnected.", "Feelings"),
    DictionaryEntry("nervous", "kinakabahan", "Worried or uneasy about something.", "Feelings"),
    DictionaryEntry("relaxed", "relaks / panatag", "Calm and free from tension.", "Feelings"),
    DictionaryEntry("worried", "nag-aalala", "Feeling concerned or anxious.", "Feelings"),
    DictionaryEntry("peaceful", "mapayapa", "Calm and free from disturbance.", "Descriptions"),
    DictionaryEntry("friendly", "palakaibigan", "Kind and pleasant toward others.", "People"),
    DictionaryEntry("generous", "mapagbigay", "Willing to give or share.", "People"),
    DictionaryEntry("selfish", "makasarili", "Thinking mainly of one's own needs.", "People"),
    DictionaryEntry("teamwork", "pagtutulungan", "People working together toward a shared goal.", "People"),
    DictionaryEntry("cooperation", "kooperasyon / pagtutulungan", "Working together to reach a goal.", "People"),
    DictionaryEntry("community", "komunidad / pamayanan", "People living in the same area or sharing interests.", "People"),
    DictionaryEntry("culture", "kultura", "The customs, arts, and way of life of a group.", "People"),
    DictionaryEntry("tradition", "tradisyon", "A custom passed down through generations.", "People"),
    DictionaryEntry("festival", "pista / pagdiriwang", "A public celebration or special event.", "People"),
    DictionaryEntry("government", "pamahalaan", "The system or group that governs a community or country.", "People"),
    DictionaryEntry("law", "batas", "A rule made and enforced by an authority.", "People"),
    DictionaryEntry("citizen", "mamamayan", "A legal member of a country.", "People"),
    DictionaryEntry("emergency", "emerhensiya / kagipitan", "A serious situation requiring immediate action.", "Safety"),
    DictionaryEntry("first aid", "pangunang lunas", "Immediate basic help given to an injured or ill person.", "Health"),
    DictionaryEntry("fever", "lagnat", "A higher-than-normal body temperature.", "Health"),
    DictionaryEntry("cough", "ubo / umubo", "A sudden release of air from the lungs; often a symptom.", "Health"),
    DictionaryEntry("headache", "sakit ng ulo", "Pain in the head.", "Health"),
    DictionaryEntry("stomachache", "sakit ng tiyan", "Pain in the stomach area.", "Health"),
    DictionaryEntry("toothache", "sakit ng ngipin", "Pain in a tooth.", "Health"),
    DictionaryEntry("wound", "sugat", "An injury to the body.", "Health"),
    DictionaryEntry("nutrition", "nutrisyon", "The process of getting and using nutrients from food.", "Health"),
    DictionaryEntry("protein", "protina", "A nutrient needed for growth and repair of the body.", "Food"),
    DictionaryEntry("vitamin", "bitamina", "A nutrient needed in small amounts for health.", "Food"),
    DictionaryEntry("calcium", "kaltsyum", "A mineral important for bones and teeth.", "Food"),
    DictionaryEntry("carbohydrate", "karbohidrat", "A nutrient that provides energy.", "Food"),
    DictionaryEntry("fiber", "hibla", "Plant material that supports digestion.", "Food"),
    DictionaryEntry("school principal", "punong-guro", "The person in charge of a school.", "School"),
    DictionaryEntry("graduation", "pagtatapos", "The completion of a course of study.", "School"),
    DictionaryEntry("certificate", "sertipiko", "An official document confirming something.", "School"),
    DictionaryEntry("diploma", "diploma", "A document awarded for completing a course of study.", "School"),
    DictionaryEntry("career", "karera / propesyon", "A person's long-term work or professional path.", "Work"),
    DictionaryEntry("employee", "empleyado", "A person who works for an employer.", "Work"),
    DictionaryEntry("employer", "nagpapatrabaho / employer", "A person or organization that hires workers.", "Work"),
    DictionaryEntry("salary", "suweldo", "Regular payment for work.", "Money"),
    DictionaryEntry("wage", "sahod", "Payment for work, often calculated by hour or day.", "Money"),
    DictionaryEntry("business", "negosyo", "An activity or organization that sells goods or services.", "Work"),
    DictionaryEntry("interview", "panayam", "A meeting to ask questions, often for a job or research.", "Work"),
    DictionaryEntry("resume", "resume / tala ng karanasan", "A document summarizing education and work experience.", "Work"),
    DictionaryEntry("project", "proyekto", "A planned piece of work with a goal.", "Work"),
    DictionaryEntry("task", "gawain", "A piece of work that needs to be done.", "Work"),
    DictionaryEntry("budget", "badyet", "A plan for how money will be spent.", "Money"),
    DictionaryEntry("expense", "gastos", "Money spent to pay for something.", "Money"),
    DictionaryEntry("income", "kita", "Money received from work, business, or other sources.", "Money"),
    DictionaryEntry("borrow", "humiram", "To take something temporarily with the intention of returning it.", "Money"),
    DictionaryEntry("lend", "magpahiram", "To allow someone to use something temporarily.", "Money"),
    DictionaryEntry("debt", "utang", "Money or something owed.", "Money"),
    DictionaryEntry("discount", "diskuwento", "A reduction in the usual price.", "Money"),
    DictionaryEntry("cash", "salapi / cash", "Money in physical form, such as bills and coins.", "Money"),
    DictionaryEntry("credit card", "credit card / kard ng pautang", "A card used to borrow money for purchases.", "Money"),
    DictionaryEntry("savings", "ipon", "Money kept for future use.", "Money"),
    DictionaryEntry("profit", "tubo", "Money gained after costs are deducted.", "Money"),
    DictionaryEntry("loss", "pagkalugi / pagkawala", "Money lost or the state of losing something.", "Money"),
    DictionaryEntry("investment", "pamumuhunan", "Money or resources put into something for future benefit.", "Money"),
    DictionaryEntry("loan", "utang / pautang", "Money borrowed that is expected to be repaid.", "Money"),
    DictionaryEntry("invoice", "invoice / tala ng bayarin", "A document listing goods or services and the amount owed.", "Money"),
    DictionaryEntry("receipt", "resibo", "A document showing that payment was made.", "Money"),
    DictionaryEntry("refund", "refund / pagsasauli ng bayad", "Money returned to a customer.", "Money"),
    DictionaryEntry("affordable", "abot-kaya", "Not too expensive for someone to pay.", "Money"),
    DictionaryEntry("product", "produkto", "Something made or offered for sale.", "Money"),
    DictionaryEntry("service", "serbisyo", "Work or assistance provided to others.", "Work"),
    DictionaryEntry("quality", "kalidad", "How good or well-made something is.", "Descriptions"),
    DictionaryEntry("quantity", "dami", "The amount or number of something.", "Numbers"),
    DictionaryEntry("available", "available / mayroon", "Able to be used, obtained, or reached.", "General"),
    DictionaryEntry("unavailable", "hindi available / walang bakante", "Not able to be used or obtained.", "General"),
    DictionaryEntry("delivery", "paghahatid", "The act of bringing goods to someone.", "Work"),
    DictionaryEntry("package", "pakete", "An item or group of items wrapped for transport.", "Objects"),
    DictionaryEntry("warehouse", "bodega", "A building used for storing goods.", "Places"),
    DictionaryEntry("inventory", "imbentaryo", "A list or supply of goods held by a business.", "Work"),
    DictionaryEntry("supply", "suplay / panustos", "An amount of something available for use.", "Work"),
    DictionaryEntry("demand", "pangangailangan sa pamilihan", "The desire or need for a product or service.", "Money"),
    DictionaryEntry("value", "halaga", "The worth or importance of something.", "Money"),
    DictionaryEntry("worthwhile", "sulit / kapaki-pakinabang", "Worth the time, effort, or cost.", "Descriptions"),
    DictionaryEntry("subscription", "subscription / bayad sa patuloy na serbisyo", "An arrangement to receive a service regularly.", "Money"),
    DictionaryEntry("membership", "pagiging miyembro", "The state of belonging to a group or organization.", "People"),
    DictionaryEntry("recommendation", "rekomendasyon / mungkahi", "A suggestion about what may be best.", "General"),
    DictionaryEntry("reviewer", "reviewer / materyal sa pagbabalik-aral", "Material used to prepare for a test or a person who reviews something.", "Learning"),
    DictionaryEntry("study material", "materyal sa pag-aaral", "Resources used to learn a subject.", "Learning"),
    DictionaryEntry("learning objective", "layunin sa pagkatuto", "A statement describing what a learner should know or do.", "Learning"),
    DictionaryEntry("lesson plan", "banghay-aralin", "A plan for teaching a lesson.", "School"),
    DictionaryEntry("assessment", "pagtataya", "A process of measuring knowledge, skill, or progress.", "Learning"),
    DictionaryEntry("evaluation", "pagsusuri / ebalwasyon", "The process of judging quality or value.", "Learning"),
    DictionaryEntry("rubric", "rubrik / pamantayan sa pagmamarka", "A set of criteria used to assess work.", "Learning"),
    DictionaryEntry("summary", "buod", "A short statement of the main points.", "Learning"),
    DictionaryEntry("summarize", "ibuod", "To express the main points briefly.", "Learning"),
    DictionaryEntry("outline", "balangkas", "A plan showing the main points of a text or presentation.", "Learning"),
    DictionaryEntry("mind map", "mapang pangkaisipan", "A diagram organizing ideas around a central topic.", "Learning"),
    DictionaryEntry("study group", "pangkat sa pag-aaral", "A group of people who study together.", "Learning"),
    DictionaryEntry("tutor", "tutor / tagapagturo", "A person who gives individual or small-group instruction.", "School"),
    DictionaryEntry("tutoring", "pagtuturo bilang tutor", "Teaching or support given to help someone learn.", "School"),
    DictionaryEntry("attendance", "pagdalo", "The act of being present at a place or event.", "School"),
    DictionaryEntry("absent", "liban / wala", "Not present at a place or event.", "School"),
    DictionaryEntry("scholarship", "iskolarship / tulong-pinansiyal sa pag-aaral", "Financial support for education.", "School"),
    DictionaryEntry("tuition fee", "matrikula", "The fee charged for instruction.", "School"),
    DictionaryEntry("enroll", "magpatala", "To register for a course or school.", "School"),
    DictionaryEntry("enrollment", "pagpapatala", "The act or process of registering for a course or school.", "School"),
    DictionaryEntry("critical thinking", "mapanuring pag-iisip", "Careful analysis used to form a judgment.", "Learning"),
    DictionaryEntry("problem solving", "paglutas ng problema", "The process of finding ways to resolve a difficulty.", "Learning"),
    DictionaryEntry("decision making", "paggawa ng desisyon", "The process of choosing among options.", "Learning"),
    DictionaryEntry("memory game", "laro sa memorya", "A game that tests the ability to remember information.", "Games"),
    DictionaryEntry("word puzzle", "palaisipan sa salita", "A puzzle based on words or letters.", "Games"),
    DictionaryEntry("crossword", "krosword", "A word puzzle with answers fitting across and down clues.", "Games"),
    DictionaryEntry("word search", "paghahanap ng salita", "A puzzle where words are found in a grid of letters.", "Games"),
    DictionaryEntry("multiple choice", "maramihang pagpipilian", "A question format with several possible answers.", "School"),
    DictionaryEntry("true or false", "tama o mali", "A question format asking whether a statement is true.", "School"),
    DictionaryEntry("fill in the blank", "punan ang patlang", "An exercise where missing words must be supplied.", "School"),
    DictionaryEntry("answer key", "susi ng mga sagot", "A list of correct answers to a test or exercise.", "Learning"),
    DictionaryEntry("passing score", "pumapasang marka", "The minimum score needed to pass a test.", "School"),
    DictionaryEntry("perfect score", "perpektong marka", "The highest possible score with no mistakes.", "School"),
    DictionaryEntry("time limit", "takdang oras", "The maximum time allowed for a task.", "Time & Nature"),
    DictionaryEntry("countdown", "pagbibilang pababa", "A count showing the time remaining before an event.", "Time & Nature"),
    DictionaryEntry("pause", "i-pause / pansamantalang ihinto", "To stop an activity temporarily.", "Actions"),
    DictionaryEntry("resume", "ipagpatuloy", "To continue after a pause.", "Actions"),
    DictionaryEntry("game over", "tapos na ang laro", "A message showing that a game has ended.", "Games"),
    DictionaryEntry("winner", "nagwagi / panalo", "A person or team that wins.", "Games"),
    DictionaryEntry("runner-up", "pumangalawa", "A person or team finishing second.", "Games"),
    DictionaryEntry("champion", "kampeon", "The winner of a competition.", "Sports"),
    DictionaryEntry("tournament", "paligsahan / torneo", "A series of contests to determine a winner.", "Sports"),
    DictionaryEntry("player", "manlalaro", "A person taking part in a game or sport.", "Games"),
    DictionaryEntry("opponent", "kalaban", "A person or team competing against another.", "Sports"),
    DictionaryEntry("referee", "reperi", "An official who ensures that game rules are followed.", "Sports"),
    DictionaryEntry("sportsmanship", "pagiging patas sa palakasan", "Fair and respectful behavior in sports.", "Sports"),
    DictionaryEntry("basketball", "basketbol", "A team sport in which players shoot a ball through a hoop.", "Sports"),
    DictionaryEntry("volleyball", "balibol", "A team sport in which players hit a ball over a net.", "Sports"),
    DictionaryEntry("badminton", "badminton", "A racket sport played with a shuttlecock.", "Sports"),
    DictionaryEntry("soccer", "futbol", "A team sport played by kicking a ball into a goal.", "Sports"),
    DictionaryEntry("chess", "ahedres", "A strategy board game played by two people.", "Games"),
    DictionaryEntry("video game", "larong bidyo", "A game played using an electronic screen.", "Games"),
    DictionaryEntry("photography", "potograpiya", "The art or practice of taking photographs.", "Entertainment"),
    DictionaryEntry("singer", "mang-aawit", "A person who sings.", "Entertainment"),
    DictionaryEntry("guitar", "gitara", "A stringed musical instrument.", "Entertainment"),
    DictionaryEntry("piano", "piyano", "A musical instrument with keys.", "Entertainment"),
    DictionaryEntry("drum", "tambol", "A percussion instrument played by striking.", "Entertainment"),
    DictionaryEntry("melody", "melodiya", "A sequence of musical notes forming a tune.", "Entertainment"),
    DictionaryEntry("rhythm", "ritmo", "A pattern of beats or movement.", "Entertainment"),
    DictionaryEntry("lyrics", "liriko / mga salita ng kanta", "The words of a song.", "Entertainment"),
    DictionaryEntry("concert", "konsiyerto", "A live performance of music.", "Entertainment"),
    DictionaryEntry("audience", "manonood / tagapakinig", "People who watch or listen to a performance.", "Entertainment"),
    DictionaryEntry("actor", "aktor / artista", "A person who performs a role.", "Entertainment"),
    DictionaryEntry("episode", "episodyo", "One part of a series.", "Entertainment"),
    DictionaryEntry("series", "serye", "A set of related episodes or items.", "Entertainment"),
    DictionaryEntry("documentary", "dokumentaryo", "A film or program presenting factual information.", "Entertainment"),
    DictionaryEntry("news", "balita", "Reports about recent events.", "Learning"),
    DictionaryEntry("headline", "ulo ng balita", "The title of a news story.", "Learning"),
    DictionaryEntry("newspaper", "pahayagan", "A publication containing news and articles.", "Learning"),
    DictionaryEntry("article", "artikulo", "A piece of writing on a particular topic.", "Learning"),
    DictionaryEntry("social media", "social media / midyang panlipunan", "Online services where people share content and communicate.", "Technology"),
    DictionaryEntry("comment", "komento / magkomento", "A remark or response to content.", "Technology"),
    DictionaryEntry("online safety", "kaligtasan online", "Practices that help protect people when using the internet.", "Safety"),
    DictionaryEntry("cyberbullying", "pambu-bully online", "Bullying carried out using digital devices or online services.", "Safety"),
    DictionaryEntry("scam", "panloloko / scam", "A dishonest scheme intended to trick someone.", "Safety"),
    DictionaryEntry("strong password", "malakas na password", "A password that is difficult to guess.", "Safety"),
    DictionaryEntry("digital footprint", "digital na bakas", "The trace of activities a person leaves online.", "Technology"),
    DictionaryEntry("screen time", "oras sa harap ng screen", "The time spent using a device with a screen.", "Health"),
    DictionaryEntry("video call", "video call / tawag na may bidyo", "A call that transmits both sound and video.", "Technology"),
    DictionaryEntry("voice call", "tawag na boses", "A call that transmits audio.", "Technology"),
    DictionaryEntry("text message", "text message / mensaheng teksto", "A short written message sent electronically.", "Technology"),
    DictionaryEntry("group chat", "group chat / panggrupong usapan", "A messaging conversation with several participants.", "Technology"),
    DictionaryEntry("calendar", "kalendaryo", "A system showing days, weeks, and months.", "Time & Nature"),
    DictionaryEntry("event", "pangyayari / kaganapan", "Something that happens, especially a planned occasion.", "General"),
    DictionaryEntry("wedding", "kasal", "A ceremony celebrating a marriage.", "Family"),
    DictionaryEntry("funeral", "libing", "A ceremony for a person who has died.", "Family"),
    DictionaryEntry("anniversary", "anibersaryo", "The yearly return of an important date.", "Time & Nature"),
    DictionaryEntry("ancestor", "ninuno", "A person from whom someone is descended.", "Family"),
    DictionaryEntry("generation", "henerasyon", "People born and living around the same period.", "Family"),
    DictionaryEntry("relative", "kamag-anak", "A person connected by family.", "Family"),
    DictionaryEntry("sibling", "kapatid", "A brother or sister.", "Family"),
    DictionaryEntry("twin", "kambal", "One of two children born in the same birth.", "Family"),
    DictionaryEntry("grandchild", "apo", "The child of someone's son or daughter.", "Family"),
    DictionaryEntry("household", "sambahayan", "The people living together in one home.", "Family"),
    DictionaryEntry("chore", "gawaing-bahay", "A small routine task, especially at home.", "Family"),
    DictionaryEntry("laundry", "labahin / paglalaba", "Clothes to be washed or the act of washing clothes.", "Family"),
    DictionaryEntry("dishwashing", "paghuhugas ng pinggan", "The act of cleaning dishes.", "Family"),
    DictionaryEntry("sweep", "magwalis", "To clean a floor using a broom.", "Actions"),
    DictionaryEntry("mop", "lampaso / maglampaso", "A tool for cleaning floors or the act of using it.", "Actions"),
    DictionaryEntry("organize", "isaayos", "To arrange things in an orderly way.", "Actions"),
    DictionaryEntry("tidy", "maayos / mag-ayos", "Neat or to make something neat.", "Descriptions"),
    DictionaryEntry("hammer", "martilyo", "A tool used for hitting nails or other objects.", "Objects"),
    DictionaryEntry("screwdriver", "distornilyador", "A tool used to turn screws.", "Objects"),
    DictionaryEntry("scissors", "gunting", "A tool with two blades for cutting.", "Objects"),
    DictionaryEntry("needle", "karayom", "A small pointed tool used for sewing.", "Objects"),
    DictionaryEntry("thread", "sinulid", "A thin strand used for sewing or weaving.", "Objects"),
    DictionaryEntry("fabric", "tela", "Material made by weaving or knitting fibers.", "Clothing"),
    DictionaryEntry("button", "butones / pindutan", "A small fastener on clothing or a control on a device.", "Objects"),
    DictionaryEntry("zipper", "siper", "A fastening device with interlocking teeth.", "Clothing"),
    DictionaryEntry("pocket", "bulsa", "A small pouch sewn into clothing or a bag.", "Clothing"),
    DictionaryEntry("belt", "sinturon", "A band worn around the waist.", "Clothing"),
    DictionaryEntry("jacket", "dyaket", "A short outer garment.", "Clothing"),
    DictionaryEntry("trousers", "pantalon", "A garment covering the body from the waist to the ankles.", "Clothing"),
    DictionaryEntry("skirt", "palda", "A garment hanging from the waist.", "Clothing"),
    DictionaryEntry("sandal", "sandalyas", "An open shoe held by straps.", "Clothing"),
    DictionaryEntry("slipper", "tsinelas", "A light shoe usually worn indoors or casually.", "Clothing"),
    DictionaryEntry("raincoat", "kapote", "A waterproof coat worn in rain.", "Clothing"),
    DictionaryEntry("bedroom", "kuwarto-tulugan", "A room used for sleeping.", "Places"),
    DictionaryEntry("living room", "sala", "A room in a home used for relaxing or entertaining guests.", "Places"),
    DictionaryEntry("dining room", "silid-kainan", "A room where people eat meals.", "Places"),
    DictionaryEntry("garage", "garahe", "A building or space used for parking vehicles.", "Places"),
    DictionaryEntry("yard", "bakuran", "An outdoor area around a house or building.", "Places"),
    DictionaryEntry("fence", "bakod", "A barrier around an area.", "Objects"),
    DictionaryEntry("gate", "tarangkahan", "A movable barrier at an entrance.", "Objects"),
    DictionaryEntry("rent", "upa / umupa", "Money paid to use a property or the act of paying it.", "Money"),
    DictionaryEntry("landlord", "may-ari ng paupahan", "A person who rents property to others.", "People"),
    DictionaryEntry("tenant", "nangungupahan", "A person who rents a home or property.", "People"),
    DictionaryEntry("property", "ari-arian / pag-aari", "Something owned, especially land or a building.", "Money"),
    DictionaryEntry("grocery", "groseri / pamilihan ng pagkain", "A store selling food and household items.", "Places"),
    DictionaryEntry("pharmacy", "botika / parmasya", "A place where medicines are sold and dispensed.", "Health"),
    DictionaryEntry("clinic", "klinika", "A place where people receive medical care.", "Health"),
    DictionaryEntry("health center", "sentro ng kalusugan", "A facility offering community health services.", "Health"),
    DictionaryEntry("fire station", "istasyon ng bumbero", "A building where firefighters work and keep equipment.", "Places"),
    DictionaryEntry("police station", "istasyon ng pulis", "A local office for police services.", "Places"),
    DictionaryEntry("post office", "tanggapan ng koreo", "A place where mail is sent and received.", "Places"),
    DictionaryEntry("city hall", "munisipyo / bulwagan ng lungsod", "The main administrative building of a city or town.", "Places"),
    DictionaryEntry("barangay hall", "bulwagang barangay", "A local government office in a barangay.", "Places"),
    DictionaryEntry("church", "simbahan", "A building used for Christian worship.", "Places"),
    DictionaryEntry("mosque", "moske", "A place of worship for Muslims.", "Places"),
    DictionaryEntry("temple", "templo", "A building used for religious worship in some traditions.", "Places"),
    DictionaryEntry("playground", "palaruan", "An outdoor area designed for children's play.", "Places"),
    DictionaryEntry("swimming pool", "palanguyan", "A pool filled with water for swimming.", "Places"),
    DictionaryEntry("bus terminal", "terminal ng bus", "A station where buses begin or end routes.", "Transport"),
    DictionaryEntry("train station", "istasyon ng tren", "A place where trains stop for passengers.", "Transport"),
    DictionaryEntry("seaport", "daungan", "A place where ships load and unload passengers or goods.", "Transport"),
    DictionaryEntry("ferry", "lantsa", "A boat that carries people or vehicles across water.", "Transport"),
    DictionaryEntry("airplane", "eroplano", "A powered aircraft with wings.", "Transport"),
    DictionaryEntry("helicopter", "helikopter", "An aircraft with rotating blades.", "Transport"),
    DictionaryEntry("ship", "barko", "A large vessel that travels on water.", "Transport"),
    DictionaryEntry("boat", "bangka", "A small vessel used on water.", "Transport"),
    DictionaryEntry("tourist", "turista", "A person traveling for pleasure.", "People"),
    DictionaryEntry("tour guide", "gabay-panturista", "A person who guides visitors around a place.", "Work"),
    DictionaryEntry("sightseeing", "paglilibot upang mamasyal", "Visiting places of interest.", "Travel"),
    DictionaryEntry("souvenir", "pasalubong / alaala", "An item kept to remember a place or event.", "Travel"),
    DictionaryEntry("local food", "lokal na pagkain", "Food associated with a particular place.", "Food"),
    DictionaryEntry("street food", "pagkaing kalye", "Ready-to-eat food sold in public places.", "Food"),
    DictionaryEntry("noodles", "pansit / noodles", "Long thin strips of dough cooked as food.", "Food"),
    DictionaryEntry("fried rice", "sinangag", "Cooked rice fried with oil and other ingredients.", "Food"),
    DictionaryEntry("grilled fish", "inihaw na isda", "Fish cooked over direct heat.", "Food"),
    DictionaryEntry("boiled egg", "nilagang itlog", "An egg cooked in boiling water.", "Food"),
    DictionaryEntry("vegetable soup", "sabaw ng gulay", "Soup made with vegetables.", "Food"),
    DictionaryEntry("fragrance", "halimuyak / bango", "A pleasant smell.", "Descriptions"),
    DictionaryEntry("perfume", "pabango", "A liquid used to give a pleasant smell.", "Objects"),
    DictionaryEntry("laundry detergent", "detergent sa paglalaba", "A product used to clean clothes.", "Objects"),
    DictionaryEntry("dish soap", "sabon panghugas ng pinggan", "A cleaning product for dishes.", "Objects"),
    DictionaryEntry("sponge", "espongha", "A soft material used for washing or absorbing liquid.", "Objects"),
    DictionaryEntry("shopping cart", "kariton sa pamimili", "A wheeled basket used to carry items in a store.", "Objects"),
    DictionaryEntry("cashier", "kahera / kahero", "A person who handles payments in a store.", "Work"),
    DictionaryEntry("queue", "pila", "A line of people waiting for something.", "Places"),
    DictionaryEntry("bargain", "baratilyo / tawaran", "Something bought at a good price or an agreement on price.", "Money"),
    DictionaryEntry("expiration date", "petsa ng pag-expire", "The date after which something may no longer be safe or valid.", "General"),
    DictionaryEntry("net weight", "netong bigat", "The weight of a product excluding packaging.", "Money"),
    DictionaryEntry("serving size", "laki ng isang serving", "The suggested amount considered one serving.", "Food"),
    DictionaryEntry("allergen", "sangkap na maaaring magdulot ng allergy", "A substance that can trigger an allergic reaction.", "Health"),
    DictionaryEntry("allergy", "alerdyi", "An immune reaction to a substance that is usually harmless to others.", "Health"),
    DictionaryEntry("symptom", "sintomas", "A sign or feeling that may indicate an illness.", "Health"),
    DictionaryEntry("diagnosis", "diyagnosis", "The identification of an illness or condition.", "Health"),
    DictionaryEntry("treatment", "paggamot", "Medical care given for an illness or injury.", "Health"),
    DictionaryEntry("prescription", "reseta", "An instruction from an authorized health professional for medicine or treatment.", "Health"),
    DictionaryEntry("side effect", "di-inaasahang epekto", "An additional effect of a medicine or treatment.", "Health"),
    DictionaryEntry("patient", "pasyente", "A person receiving medical care.", "Health"),
    DictionaryEntry("caregiver", "tagapag-alaga", "A person who provides care to someone needing assistance.", "Health"),
    DictionaryEntry("recovery", "paggaling / pagbawi", "The process of returning to health or normal function.", "Health"),
    DictionaryEntry("mental health", "kalusugang pangkaisipan", "A person's emotional, psychological, and social well-being.", "Health"),
    DictionaryEntry("stress management", "pamamahala ng stress", "Ways to reduce or cope with stress.", "Health"),
    DictionaryEntry("mindfulness", "mulat na kamalayan", "Paying attention to the present moment.", "Health"),
    DictionaryEntry("meditation", "meditasyon", "A practice of focusing attention to calm the mind.", "Health"),
    DictionaryEntry("routine", "nakagawiang gawain", "A sequence of actions done regularly.", "Learning"),
    DictionaryEntry("priority", "prayoridad", "Something considered more important than others.", "Learning"),
    DictionaryEntry("procrastination", "pagpapaliban", "The act of delaying tasks unnecessarily.", "Learning"),
    DictionaryEntry("productivity", "pagiging produktibo", "The rate at which useful work is completed.", "Learning"),
    DictionaryEntry("milestone", "mahalagang yugto", "An important stage in progress.", "Learning"),
    DictionaryEntry("long-term goal", "pangmatagalang layunin", "A goal that takes a long time to achieve.", "Learning"),
    DictionaryEntry("short-term goal", "panandaliang layunin", "A goal intended to be achieved soon.", "Learning"),
    DictionaryEntry("lifelong learning", "pagkatuto sa buong buhay", "Continuing to learn throughout one's life.", "Learning"),
    DictionaryEntry("growth mindset", "pananaw na maaaring umunlad ang kakayahan", "The belief that abilities can develop through effort and learning.", "Learning"),
    DictionaryEntry("perseverance", "pagtitiyaga", "Continued effort despite difficulty.", "People"),
    DictionaryEntry("resilience", "katatagan", "The ability to recover from difficulty.", "People"),
    DictionaryEntry("adaptability", "kakayahang makibagay", "The ability to adjust to change.", "Learning"),
    DictionaryEntry("consistency", "pagiging palagian", "The quality of being steady over time.", "Learning"),
    DictionaryEntry("punctuality", "pagiging nasa oras", "The habit of being on time.", "Learning"),
    DictionaryEntry("efficiency", "kahusayan", "The ability to achieve results with little waste.", "Learning"),
    DictionaryEntry("effectiveness", "pagiging mabisa", "The degree to which something achieves its purpose.", "Learning"),
    DictionaryEntry("innovation", "inobasyon", "A new idea, method, or product.", "Learning"),
    DictionaryEntry("leadership", "pamumuno", "The ability or act of guiding others.", "People"),
    DictionaryEntry("collaborate", "makipagtulungan", "To work jointly with others.", "Actions"),
    DictionaryEntry("accountability", "pananagutan", "The responsibility to explain or answer for actions.", "People"),
    DictionaryEntry("initiative", "kusang pagkilos / inisyatiba", "The ability to act without being told.", "People"),
    DictionaryEntry("negotiation", "negosasyon", "Discussion intended to reach an agreement.", "People"),
    DictionaryEntry("compromise", "kompromiso", "An agreement in which each side gives up something.", "People"),
    DictionaryEntry("conflict", "alitan", "A disagreement or struggle between people or groups.", "People"),
    DictionaryEntry("compliment", "papuri", "A polite expression of praise.", "People"),
    DictionaryEntry("criticism", "pagpuna", "An evaluation that points out strengths or faults.", "People"),
    DictionaryEntry("encourage", "palakasin ang loob", "To give someone support or confidence.", "People"),
    DictionaryEntry("inspire", "magbigay-inspirasyon", "To motivate someone to act or create.", "People"),
    DictionaryEntry("support", "suporta / suportahan", "Help or encouragement given to someone.", "People"),
    DictionaryEntry("sympathy", "pakikiramay / simpatiya", "Care or concern for someone else's suffering.", "Feelings"),
    DictionaryEntry("empathy", "empatiya", "The ability to understand another person's feelings.", "Feelings"),
    DictionaryEntry("compassion", "habag / malasakit", "Concern for the suffering of others and a wish to help.", "Feelings"),
    DictionaryEntry("respectful", "magalang", "Showing respect for others.", "People"),
    DictionaryEntry("diversity", "pagkakaiba-iba", "The presence of different types of people or things.", "People"),
    DictionaryEntry("inclusion", "pagsasama / inklusyon", "The practice of ensuring people can participate.", "People"),
    DictionaryEntry("identity", "pagkakakilanlan", "The qualities that make a person or thing distinct.", "People"),
    DictionaryEntry("consent", "pahintulot", "Agreement or permission given voluntarily.", "People"),
    DictionaryEntry("boundary", "hangganan", "A limit that defines what is acceptable or where something ends.", "People"),
    DictionaryEntry("well-being", "kagalingan / mabuting kalagayan", "The state of being healthy, comfortable, and content.", "Health"),
    DictionaryEntry("physical activity", "pisikal na aktibidad", "Movement of the body that uses energy.", "Health"),
    DictionaryEntry("balanced diet", "balanseng diyeta", "A diet that provides a suitable range of nutrients.", "Health"),
    DictionaryEntry("personal hygiene", "personal na kalinisan", "Habits that help keep the body clean and healthy.", "Health"),
    DictionaryEntry("handwashing", "paghuhugas ng kamay", "Cleaning hands with soap and water or an appropriate cleanser.", "Health"),
    DictionaryEntry("public health", "kalusugang pampubliko", "Efforts to protect and improve the health of communities.", "Health"),
    DictionaryEntry("emergency contact", "taong tatawagan sa emerhensiya", "A person to contact in an emergency.", "Safety"),
    DictionaryEntry("fire extinguisher", "pamatay-sunog", "A device used to put out small fires.", "Safety"),
    DictionaryEntry("first aid kit", "kahon ng pangunang lunas", "A container holding supplies for basic first aid.", "Safety"),
    DictionaryEntry("warning sign", "palatandaan ng babala", "A sign that alerts people to danger.", "Safety"),
    DictionaryEntry("reliable", "maaasahan", "Able to be trusted or depended on.", "Descriptions"),
    DictionaryEntry("accurate", "tumpak", "Correct and free from mistakes.", "Descriptions"),
    DictionaryEntry("uncertain", "hindi tiyak", "Not known or decided for sure.", "Descriptions"),
    DictionaryEntry("evidence", "ebidensiya / katibayan", "Information or objects that support a claim.", "Learning"),
    DictionaryEntry("opinion", "opinyon / palagay", "A belief or judgment that may not be a fact.", "People"),
    DictionaryEntry("discussion", "talakayan", "A conversation in which people share ideas.", "People"),
    DictionaryEntry("debate", "debate / pagtatalo", "A structured discussion of opposing views.", "Learning"),
    DictionaryEntry("communication", "komunikasyon", "The sharing of information, ideas, or feelings.", "People"),
    DictionaryEntry("body language", "wika ng katawan", "Communication through posture, gestures, and facial expressions.", "People"),
    DictionaryEntry("gesture", "kumpas / galaw ng kamay", "A movement used to express an idea or feeling.", "People"),
    DictionaryEntry("active listening", "aktibong pakikinig", "Listening carefully and responding to show understanding.", "Learning"),
    DictionaryEntry("agreement", "kasunduan", "A shared decision or arrangement.", "People"),
    DictionaryEntry("disagreement", "hindi pagkakasundo", "A difference in opinion or belief.", "People"),
    DictionaryEntry("honesty", "katapatan", "The quality of being truthful.", "People"),
    DictionaryEntry("humility", "pagpapakumbaba", "The quality of being humble.", "People"),
    DictionaryEntry("arrogant", "mayabang", "Having an exaggerated sense of one's own importance.", "People"),
    DictionaryEntry("optimistic", "positibo ang pananaw", "Expecting good things to happen.", "Feelings"),
    DictionaryEntry("pessimistic", "negatibo ang pananaw", "Expecting bad outcomes.", "Feelings"),
    DictionaryEntry("determined", "determinado", "Firmly decided to do something.", "Feelings"),
    DictionaryEntry("flexible", "madaling umangkop", "Able to adapt to new conditions.", "Descriptions"),
    DictionaryEntry("organized", "organisado / maayos", "Arranged in an orderly way.", "Descriptions"),
    DictionaryEntry("productive", "produktibo", "Producing useful results.", "Descriptions"),
    DictionaryEntry("innovative", "makabago / mapamaraan", "Introducing new ideas or methods.", "Descriptions"),
    DictionaryEntry("entrepreneur", "negosyante / entreprenyur", "A person who starts and runs a business.", "Work"),
    DictionaryEntry("entrepreneurship", "pagnenegosyo", "The activity of starting and running a business.", "Work"),
    DictionaryEntry("delegate", "magtalaga ng gawain", "To assign responsibility to another person.", "Work"),
    DictionaryEntry("workload", "dami ng gawain", "The amount of work assigned to a person or group.", "Work"),
    DictionaryEntry("remote work", "malayuang pagtatrabaho", "Work performed away from a central workplace.", "Work"),
    DictionaryEntry("overtime", "dagdag na oras ng trabaho", "Work done beyond normal working hours.", "Work"),
    DictionaryEntry("customer service", "serbisyo sa mamimili", "Help given to customers before or after a purchase.", "Work"),
    DictionaryEntry("customer satisfaction", "kasiyahan ng mamimili", "How satisfied a customer is with a product or service.", "Work"),
    DictionaryEntry("feature request", "kahilingan para sa bagong feature", "A suggestion for adding functionality to a product.", "Technology"),
    DictionaryEntry("bug fix", "pag-aayos ng bug", "A change that corrects a software problem.", "Technology"),
    DictionaryEntry("source code", "source code / kodigo ng programa", "Human-readable instructions written by a programmer.", "Technology"),
    DictionaryEntry("repository", "repository / imbakan ng code", "A place where project files and their history are stored.", "Technology"),
    DictionaryEntry("commit", "commit / pagtatala ng pagbabago", "A saved set of changes in version control.", "Technology"),
    DictionaryEntry("workflow", "daloy ng trabaho", "A sequence of automated or manual steps.", "Technology"),
    DictionaryEntry("build status", "katayuan ng build", "The result or state of a software build.", "Technology"),
    DictionaryEntry("APK file", "APK file", "An Android application package that can be installed on a device.", "Technology"),
    DictionaryEntry("application ID", "ID ng aplikasyon", "The unique identifier used to identify an Android app.", "Technology"),
    DictionaryEntry("package name", "pangalan ng package", "The namespace identifying an Android application or code package.", "Technology"),
    DictionaryEntry("Kotlin", "Kotlin", "A programming language commonly used for Android development.", "Technology"),
    DictionaryEntry("Jetpack Compose", "Jetpack Compose", "An Android toolkit for building user interfaces with Kotlin.", "Technology"),
    DictionaryEntry("user interface", "interface ng gumagamit", "The screens and controls through which a person uses software.", "Technology"),
    DictionaryEntry("user experience", "karanasan ng gumagamit", "How a person experiences and interacts with a product.", "Technology"),
    DictionaryEntry("responsive design", "disenyong umaangkop", "A design that adapts to different screen sizes.", "Technology"),
    DictionaryEntry("accessibility", "pagiging madaling ma-access", "Design that helps people with different abilities use something.", "Technology"),
    DictionaryEntry("screen reader", "screen reader / bumabasang programa", "Software that reads screen content aloud.", "Technology"),
    DictionaryEntry("text to speech", "pagbabasa ng teksto sa pamamagitan ng boses", "Technology that converts written text into spoken audio.", "Technology"),
    DictionaryEntry("speech to text", "pagsasalin ng boses sa teksto", "Technology that converts spoken words into written text.", "Technology"),
    DictionaryEntry("artificial intelligence", "artipisyal na intelihensiya", "Computer systems performing tasks associated with human intelligence.", "Technology"),
    DictionaryEntry("algorithm", "algoritmo", "A defined sequence of steps for solving a problem.", "Technology"),
    DictionaryEntry("dataset", "set ng datos", "A collection of related data.", "Technology"),
    DictionaryEntry("prediction", "prediksiyon / hula", "A statement about what may happen.", "Learning"),
    DictionaryEntry("autocomplete", "awtomatikong pagkumpleto", "A feature that predicts or completes text as it is typed.", "Technology"),
    DictionaryEntry("auto-correct", "awtomatikong pagwawasto", "A feature that automatically corrects possible typing errors.", "Technology"),
    DictionaryEntry("proofread", "basahing muli at iwasto", "To check writing for errors.", "Learning"),
    DictionaryEntry("paraphrase", "muling ipahayag sa ibang salita", "To express the same idea using different words.", "Learning"),
    DictionaryEntry("plagiarism", "pangongopya nang walang pagkilala", "Using another person's work or ideas without proper acknowledgment.", "Learning"),
    DictionaryEntry("copyright", "karapatang-ari", "Legal rights protecting original creative work.", "People"),
    DictionaryEntry("open source", "bukas na source code", "Software whose source code is available under a license allowing use and modification.", "Technology"),
    DictionaryEntry("misinformation", "maling impormasyon", "False or inaccurate information, regardless of intent.", "Learning"),
    DictionaryEntry("disinformation", "sadyang maling impormasyon", "False information deliberately spread to mislead.", "Learning"),
    DictionaryEntry("verify information", "patunayan ang impormasyon", "To check whether information is accurate.", "Learning"),
    DictionaryEntry("primary source", "pangunahing sanggunian", "An original record or firsthand account.", "Learning"),
    DictionaryEntry("secondary source", "pangalawang sanggunian", "A source that interprets or discusses primary material.", "Learning"),
    DictionaryEntry("bibliography", "bibliograpiya", "A list of sources used or consulted in a work.", "Learning"),
    DictionaryEntry("glossary", "talahuluganan", "A list of terms with definitions.", "Learning"),
    DictionaryEntry("beginner", "baguhan", "A person who is just starting to learn something.", "Learning"),
    DictionaryEntry("intermediate", "katamtamang antas", "At a level between beginner and advanced.", "Learning"),
    DictionaryEntry("advanced", "mataas na antas", "At a high level of skill or knowledge.", "Learning"),
    DictionaryEntry("fluent", "matatas", "Able to speak or write a language smoothly and easily.", "Learning"),
    DictionaryEntry("fluency", "kasanayan sa matatas na pagsasalita", "The ability to use a language smoothly and easily.", "Learning"),
    DictionaryEntry("language barrier", "hadlang sa wika", "A difficulty in communication caused by language differences.", "People"),
    DictionaryEntry("mother tongue", "unang wika / katutubong wika", "The language a person learns first or uses from early childhood.", "Learning"),
    DictionaryEntry("native speaker", "katutubong tagapagsalita", "A person who learned a language from early childhood.", "Learning"),
    DictionaryEntry("foreign language", "wikang banyaga", "A language that is not native to a person or community.", "Learning"),
    DictionaryEntry("source language", "wikang pinagmulan", "The language from which text is translated.", "Learning"),
    DictionaryEntry("target language", "wikang patutunguhan", "The language into which text is translated.", "Learning"),
    DictionaryEntry("context", "konteksto", "The circumstances that help explain meaning.", "Learning"),
    DictionaryEntry("idiom", "idyoma", "An expression whose meaning may not be understood from the literal words.", "Learning"),
    DictionaryEntry("proverb", "salawikain", "A short saying that expresses a general truth or advice.", "Learning"),
    DictionaryEntry("slang", "balbal na salita", "Very informal words or expressions used by a group.", "Learning"),
    DictionaryEntry("abbreviation", "daglat", "A shortened form of a word or phrase.", "Learning"),
    DictionaryEntry("acronym", "akronim", "A word formed from the initial letters of other words.", "Learning"),
    DictionaryEntry("contraction", "pinaikling anyo", "A shortened form of words, often using an apostrophe in English.", "Learning"),
    DictionaryEntry("homonym", "homonim", "A word sharing its form with another word but having a different meaning.", "Learning"),
    DictionaryEntry("homophone", "homopono", "A word that sounds like another word but has a different meaning or spelling.", "Learning"),
    DictionaryEntry("semantics", "semantika", "The study of meaning in language.", "Learning"),
    DictionaryEntry("syntax", "sintaks", "The rules governing how words form phrases and sentences.", "Learning"),
    DictionaryEntry("linguistics", "lingguwistika", "The scientific study of language.", "Learning"),
    DictionaryEntry("bilingual", "bilingguwal / gumagamit ng dalawang wika", "Able to use two languages.", "Learning"),
    DictionaryEntry("multilingual", "maraming wika ang alam", "Able to use several languages.", "Learning"),
    DictionaryEntry("code-switching", "pagpapalit ng wika sa usapan", "Alternating between languages in a conversation or sentence.", "Learning"),
    DictionaryEntry("English to Tagalog", "Ingles patungong Tagalog", "Translation from English into Tagalog.", "Learning"),
    DictionaryEntry("Tagalog to English", "Tagalog patungong Ingles", "Translation from Tagalog into English.", "Learning"),
    DictionaryEntry("bilingual dictionary", "diksyunaryong dalawang wika", "A dictionary giving equivalents between two languages.", "Learning"),
    DictionaryEntry("monolingual dictionary", "diksyunaryong iisang wika", "A dictionary that defines words in the same language.", "Learning"),
    DictionaryEntry("thesaurus", "talahuluganan ng magkakaugnay na salita", "A reference tool listing synonyms and related words.", "Learning"),
    DictionaryEntry("phrasal verb", "pandiwang may kasamang particle", "An English verb combined with an adverb or preposition.", "Learning"),
    DictionaryEntry("word family", "pamilya ng mga salita", "Words sharing a common root or related form.", "Learning"),
    DictionaryEntry("word origin", "pinagmulan ng salita", "The history or source of a word.", "Learning"),
    DictionaryEntry("etymology", "etimolohiya", "The study of word origins and historical development.", "Learning"),
    DictionaryEntry("regional language", "wikang panrehiyon", "A language used in a particular region.", "Learning"),
    DictionaryEntry("Filipino", "Pilipino / wikang Filipino", "Relating to the Philippines, its people, or its national language.", "Learning"),
    DictionaryEntry("Tagalog", "Tagalog", "A major language of the Philippines and the basis of Filipino.", "Learning"),
    DictionaryEntry("English", "Ingles", "A language widely used internationally.", "Learning"),
    DictionaryEntry("Philippines", "Pilipinas", "An archipelagic country in Southeast Asia.", "Places"),
    DictionaryEntry("province", "lalawigan", "A regional division of a country.", "Places"),
    DictionaryEntry("capital city", "kabisera", "The city where a country's or region's government is based.", "Places"),
    DictionaryEntry("barangay", "barangay", "The smallest local government unit in the Philippines.", "Places"),
    DictionaryEntry("archipelago", "kapuluan", "A group or chain of islands.", "Places"),
    DictionaryEntry("peninsula", "tangway", "Land surrounded by water on most sides.", "Nature"),
    DictionaryEntry("strait", "kipot", "A narrow passage of water between land areas.", "Nature"),
    DictionaryEntry("bay", "look", "A part of a sea or lake partly enclosed by land.", "Nature"),
    DictionaryEntry("gulf", "golpo", "A large part of the sea partly enclosed by land.", "Nature"),
    DictionaryEntry("cape", "tangos", "A point of land extending into a body of water.", "Nature"),
    DictionaryEntry("shore", "baybayin", "The land along the edge of a sea, lake, or river.", "Nature"),
    DictionaryEntry("coastline", "linya ng baybayin", "The outline of land along the sea.", "Nature"),
    DictionaryEntry("urban", "panglungsod", "Relating to a city.", "Places"),
    DictionaryEntry("rural", "pambukid", "Relating to the countryside.", "Places"),
    DictionaryEntry("tropical", "tropikal", "Relating to regions near the equator.", "Nature"),
    DictionaryEntry("humid", "maalinsangan", "Containing a lot of moisture in the air.", "Nature"),
    DictionaryEntry("evaporation", "pagsingaw", "The process of changing from liquid to gas.", "Science"),
    DictionaryEntry("condensation", "kondensasyon", "The process of changing from gas to liquid.", "Science"),
    DictionaryEntry("photosynthesis", "potosintesis", "The process by which plants use light to make food.", "Science"),
    DictionaryEntry("chlorophyll", "kloropila", "The green pigment plants use to absorb light.", "Science"),
    DictionaryEntry("pollination", "polinasyon", "The transfer of pollen that enables plant reproduction.", "Nature"),
    DictionaryEntry("germination", "pagsibol", "The process by which a seed begins to grow.", "Nature"),
    DictionaryEntry("predator", "mandaragit", "An animal that hunts other animals.", "Animals"),
    DictionaryEntry("prey", "biktima ng mandaragit", "An animal hunted by another animal.", "Animals"),
    DictionaryEntry("herbivore", "hayop na kumakain ng halaman", "An animal that mainly eats plants.", "Animals"),
    DictionaryEntry("carnivore", "hayop na kumakain ng karne", "An animal that mainly eats other animals.", "Animals"),
    DictionaryEntry("omnivore", "hayop na kumakain ng halaman at karne", "An animal that eats both plants and animals.", "Animals"),
    DictionaryEntry("habitat", "tirahan ng mga hayop", "The natural home of a plant or animal.", "Nature"),
    DictionaryEntry("ecosystem", "ekosistema", "Living organisms interacting with their environment.", "Nature"),
    DictionaryEntry("biodiversity", "biodibersidad", "The variety of living organisms in an area or on Earth.", "Nature"),
    DictionaryEntry("conservation", "pangangalaga sa kalikasan", "Protection and careful management of natural resources.", "Nature"),
    DictionaryEntry("coral reef", "bahura ng korales", "An underwater ecosystem built largely by coral organisms.", "Nature"),
    DictionaryEntry("mangrove", "bakawan", "A salt-tolerant tree or shrub growing in coastal wetlands.", "Nature"),
    DictionaryEntry("wetland", "latian", "Land saturated with water for all or part of the year.", "Nature"),
    DictionaryEntry("waterfall", "talon", "Water falling from a height over a steep drop.", "Nature"),
    DictionaryEntry("cave", "kuweba", "A natural underground space or hollow in rock.", "Nature"),
    DictionaryEntry("cliff", "bangin / matarik na gilid", "A steep rock face or slope.", "Nature"),
    DictionaryEntry("valley", "lambak", "Low land between hills or mountains.", "Nature"),
    DictionaryEntry("plain", "kapatagan", "A large area of flat land.", "Nature"),
    DictionaryEntry("plateau", "talampas", "An elevated area of relatively flat land.", "Nature"),
    DictionaryEntry("desert", "disyerto", "A very dry region with little rainfall.", "Nature"),
    DictionaryEntry("tide", "taas-baba ng tubig-dagat", "The regular rise and fall of sea level.", "Nature"),
    DictionaryEntry("wave", "alon", "A moving ridge on the surface of water.", "Nature"),
    DictionaryEntry("current", "agos", "A continuous movement of water or air.", "Nature"),
    DictionaryEntry("tsununami", "tsunami", "A series of large sea waves often caused by underwater disturbances.", "Safety"),
    DictionaryEntry("storm surge", "daluyong ng bagyo", "An abnormal rise of seawater caused by a storm.", "Safety"),
    DictionaryEntry("landslide", "pagguho ng lupa", "The movement of rock and soil down a slope.", "Safety"),
    DictionaryEntry("aftershock", "kasunod na lindol", "A smaller earthquake following a larger one.", "Nature"),
    DictionaryEntry("fault line", "linya ng bitak sa lupa", "A fracture in Earth's crust along which movement can occur.", "Science"),
    DictionaryEntry("tectonic plate", "platong tektoniko", "A large section of Earth's outer shell.", "Science"),
    DictionaryEntry("heat index", "antas ng init na nararamdaman", "A measure combining temperature and humidity to describe perceived heat.", "Science"),
    DictionaryEntry("humidity", "halumigmig", "The amount of water vapor in the air.", "Science"),
    DictionaryEntry("meteorologist", "meteorologo", "A scientist who studies weather and the atmosphere.", "Work"),
    DictionaryEntry("weather forecast", "pagtataya ng panahon", "A prediction of future weather conditions.", "Nature"),
    DictionaryEntry("climate change", "pagbabago ng klima", "Long-term changes in average weather patterns.", "Nature"),
    DictionaryEntry("global warming", "pag-init ng mundo", "The long-term rise in Earth's average temperature.", "Nature"),
    DictionaryEntry("renewable energy", "nababagong enerhiya", "Energy from sources naturally replenished.", "Nature"),
    DictionaryEntry("solar energy", "enerhiyang solar", "Energy from sunlight.", "Science"),
    DictionaryEntry("wind energy", "enerhiyang hangin", "Energy generated from moving air.", "Science"),
    DictionaryEntry("natural resources", "likas na yaman", "Materials and features provided by nature.", "Nature"),
    DictionaryEntry("wildlife", "mga ligaw na hayop", "Animals and other living things in their natural habitats.", "Nature"),
    DictionaryEntry("endangered species", "nanganganib na uri ng hayop o halaman", "A species at risk of extinction.", "Nature"),
    DictionaryEntry("solar system", "sistemang solar", "The Sun and the objects that orbit it.", "Science"),
    DictionaryEntry("planet", "planeta", "A large celestial body that orbits a star.", "Science"),
    DictionaryEntry("atmosphere", "atmospera", "The layer of gases surrounding a planet.", "Science"),
    DictionaryEntry("brain", "utak", "The organ that controls thought, memory, and many body functions.", "Body"),
    DictionaryEntry("lung", "baga", "An organ used for breathing.", "Body"),
    DictionaryEntry("blood", "dugo", "The fluid that circulates through the body.", "Body"),
    DictionaryEntry("muscle", "kalamnan", "Body tissue that contracts to create movement.", "Body"),
    DictionaryEntry("joint", "kasukasuan", "The place where two bones meet.", "Body"),
    DictionaryEntry("elbow", "siko", "The joint between the upper and lower arm.", "Body"),
    DictionaryEntry("wrist", "pulso", "The joint between the hand and forearm.", "Body"),
    DictionaryEntry("ankle", "bukong-bukong", "The joint connecting the foot and leg.", "Body"),
    DictionaryEntry("forehead", "noo", "The part of the face above the eyebrows.", "Body"),
    DictionaryEntry("eyebrow", "kilay", "The strip of hair above the eye.", "Body"),
    DictionaryEntry("eyelash", "pilikmata", "A hair growing at the edge of an eyelid.", "Body"),
    DictionaryEntry("ear", "tainga", "The body part used for hearing.", "Body"),
    DictionaryEntry("throat", "lalamunan", "The passage in the neck used for breathing and swallowing.", "Body"),
    DictionaryEntry("chest", "dibdib", "The front part of the body between the neck and abdomen.", "Body"),
    DictionaryEntry("back pain", "sakit ng likod", "Pain in the back.", "Health"),
    DictionaryEntry("stressed", "nai-stress / tensiyonado", "Experiencing mental or emotional pressure.", "Feelings"),
    DictionaryEntry("hydration", "pagpapanatili ng sapat na tubig sa katawan", "Having enough water in the body.", "Health"),
    DictionaryEntry("dehydration", "dehydrasyon", "A condition caused by losing more fluid than the body takes in.", "Health"),
    DictionaryEntry("sleep schedule", "iskedyul ng pagtulog", "A regular plan for sleeping and waking.", "Health"),
    DictionaryEntry("balanced lifestyle", "balanseng pamumuhay", "A way of living that balances responsibilities, rest, and well-being.", "Health"),
    DictionaryEntry("emergency kit", "kagamitang pang-emerhensiya", "A collection of supplies prepared for emergencies.", "Safety"),
    DictionaryEntry("go bag", "go bag / bag pang-emerhensiya", "A bag of essentials prepared for quick evacuation.", "Safety"),
    DictionaryEntry("evacuation center", "sentro ng paglikas", "A place where people go for safety during a disaster.", "Safety"),
    DictionaryEntry("evacuate", "lumikas", "To leave a dangerous area for safety.", "Safety"),
    DictionaryEntry("rescue", "iligtas / pagsagip", "To save someone from danger.", "Safety"),
    DictionaryEntry("relief goods", "mga panustos na tulong", "Food and supplies provided to people in need.", "Safety"),
    DictionaryEntry("volunteer", "boluntaryo / magboluntaryo", "A person who offers help without being paid.", "People"),
    DictionaryEntry("food security", "seguridad sa pagkain", "Reliable access to sufficient safe and nutritious food.", "People"),
    DictionaryEntry("poverty", "kahirapan", "The condition of lacking enough resources for basic needs.", "People"),
    DictionaryEntry("homeless", "walang tirahan", "Without a stable place to live.", "People"),
    DictionaryEntry("shelter", "silungan / tirahan", "A place that provides protection or housing.", "Places"),
    DictionaryEntry("senior citizen", "nakatatanda / senior citizen", "An older adult, often eligible for specific benefits.", "People"),
    DictionaryEntry("wheelchair", "silyang de-gulong", "A chair with wheels used by someone who cannot walk easily.", "Health"),
    DictionaryEntry("sign language", "wikang pasenyas", "A language using hand signs, facial expressions, and body movement.", "Learning"),
    DictionaryEntry("Braille", "Braille", "A tactile writing system used by many people who are blind or have low vision.", "Learning"),
    DictionaryEntry("assistive technology", "pantulong na teknolohiya", "Technology that helps people perform tasks or access information.", "Technology"),
    DictionaryEntry("artificial intelligence", "artipisyal na intelihensiya", "Computer systems performing tasks associated with human intelligence.", "Technology"),
    DictionaryEntry("machine learning", "pagkatuto ng makina", "Methods that allow computers to learn patterns from data.", "Technology"),
    DictionaryEntry("language model", "modelo ng wika", "A system trained to process or generate language.", "Technology"),
    DictionaryEntry("recommendation system", "sistema ng rekomendasyon", "A system that suggests items based on information or preferences.", "Technology"),
    DictionaryEntry("speech recognition", "pagkilala sa pagsasalita", "Technology that identifies spoken words.", "Technology"),
    DictionaryEntry("voice assistant", "katulong na gumagamit ng boses", "Software that responds to spoken commands.", "Technology"),
    DictionaryEntry("pronunciation guide", "gabay sa pagbigkas", "Information showing how to pronounce words.", "Learning"),
    DictionaryEntry("phonetic spelling", "ponetikong baybay", "A spelling guide representing how a word sounds.", "Learning"),
    DictionaryEntry("accent", "diin / punto", "A way of pronouncing words associated with a region or group.", "Learning"),
    DictionaryEntry("vowel", "patinig", "A speech sound produced without major blockage of airflow.", "Learning"),
    DictionaryEntry("consonant", "katinig", "A speech sound produced with some narrowing or closure of airflow.", "Learning"),
    DictionaryEntry("capital letter", "malaking titik", "An uppercase letter.", "Learning"),
    DictionaryEntry("lowercase letter", "maliit na titik", "A letter that is not uppercase.", "Learning"),
    DictionaryEntry("question mark", "tandang pananong", "The punctuation mark used at the end of a direct question.", "Learning"),
    DictionaryEntry("period", "tuldok", "A punctuation mark used to end a statement.", "Learning"),
    DictionaryEntry("comma", "kuwit", "A punctuation mark used to separate parts of a sentence.", "Learning"),
    DictionaryEntry("exclamation mark", "tandang padamdam", "A punctuation mark showing strong feeling or emphasis.", "Learning"),
    DictionaryEntry("plural", "maramihan", "A form referring to more than one.", "Learning"),
    DictionaryEntry("singular", "isahan", "A form referring to one person or thing.", "Learning"),
    DictionaryEntry("past tense", "panahong nakaraan", "A verb form referring to an earlier time.", "Learning"),
    DictionaryEntry("present tense", "panahong kasalukuyan", "A verb form referring to the present or general facts.", "Learning"),
    DictionaryEntry("future tense", "panahong hinaharap", "A verb form referring to a time yet to come.", "Learning"),
    DictionaryEntry("active voice", "tahas na tinig", "A sentence form where the subject performs the action.", "Learning"),
    DictionaryEntry("passive voice", "balintiyak na tinig", "A sentence form where the subject receives the action.", "Learning"),
    DictionaryEntry("compound word", "tambalang salita", "A word formed by combining two or more words.", "Learning"),
    DictionaryEntry("root word", "salitang-ugat", "The basic form from which related words are made.", "Learning"),
    DictionaryEntry("prefix", "unlapi", "A word part added to the beginning of a word.", "Learning"),
    DictionaryEntry("suffix", "hulapi", "A word part added to the end of a word.", "Learning"),
    DictionaryEntry("infix", "gitlapi", "A word part inserted inside a word; common in Filipino morphology.", "Learning"),
    DictionaryEntry("affix", "panlapi", "A word part added to a root word.", "Learning"),
    DictionaryEntry("word formation", "pagbuo ng salita", "The process of creating words from roots and affixes.", "Learning"),
    DictionaryEntry("formal", "pormal", "Following accepted rules or conventions.", "Descriptions"),
    DictionaryEntry("informal", "di-pormal", "Relaxed or not following formal conventions.", "Descriptions"),
    DictionaryEntry("literal", "literal / tuwiran", "Using the ordinary or exact meaning of words.", "Learning"),
    DictionaryEntry("figurative", "matalinghaga", "Using words in an imaginative or non-literal way.", "Learning"),
    DictionaryEntry("connotation", "konotasyon", "The additional feeling or association a word carries.", "Learning"),
    DictionaryEntry("denotation", "denotasyon", "The direct, dictionary meaning of a word.", "Learning"),
    DictionaryEntry("collocation", "karaniwang tambalan ng salita", "A combination of words that commonly occur together.", "Learning"),
    DictionaryEntry("false friend", "salitang magkahawig ngunit magkaiba ang kahulugan", "Words in two languages that look similar but mean different things.", "Learning"),
    DictionaryEntry("loanword", "salitang hiram", "A word borrowed from another language.", "Learning"),
    DictionaryEntry("regional variation", "pagkakaiba ayon sa rehiyon", "A difference in language use across regions.", "Learning"),
    DictionaryEntry("formal letter", "pormal na liham", "A letter written for official or professional purposes.", "Learning"),
    DictionaryEntry("greeting", "bati / pagbati", "Words used when meeting or addressing someone.", "Greetings"),
    DictionaryEntry("farewell", "pamamaalam", "Words or actions used when leaving.", "Greetings"),
    DictionaryEntry("permission", "pahintulot", "Approval to do something.", "People"),
    DictionaryEntry("invitation", "paanyaya", "A request asking someone to attend an event.", "People"),
    DictionaryEntry("appointment", "tipanan / appointment", "An agreed time to meet someone.", "Time & Nature"),
    DictionaryEntry("I am learning English", "nag-aaral ako ng Ingles", "A sentence about studying English.", "Expressions"),
    DictionaryEntry("please speak slowly", "pakisuyo, magsalita nang dahan-dahan", "A polite request for slower speech.", "Expressions"),
    DictionaryEntry("can you help me", "matutulungan mo ba ako", "A question asking whether someone can help.", "Expressions"),
    DictionaryEntry("I do not understand", "hindi ko naiintindihan", "A phrase expressing that something is unclear.", "Expressions"),
    DictionaryEntry("please repeat that", "pak ulitin iyon", "A polite request for someone to say something again.", "Expressions"),
    DictionaryEntry("what does this mean", "ano ang ibig sabihin nito", "A question asking for meaning.", "Expressions"),
    DictionaryEntry("how do you say this in English", "paano ito sabihin sa Ingles", "A question asking for an English translation.", "Expressions"),
    DictionaryEntry("how do you say this in Tagalog", "paano ito sabihin sa Tagalog", "A question asking for a Tagalog translation.", "Expressions"),
    DictionaryEntry("I agree with you", "sang-ayon ako sa iyo", "A sentence expressing agreement.", "Expressions"),
    DictionaryEntry("I disagree", "hindi ako sang-ayon", "A sentence expressing disagreement.", "Expressions"),
    DictionaryEntry("it depends", "depende", "A phrase saying the answer changes according to circumstances.", "Expressions"),
    DictionaryEntry("that is correct", "tama iyan", "A phrase confirming that something is right.", "Expressions"),
    DictionaryEntry("that is incorrect", "mali iyan", "A phrase saying that something is wrong.", "Expressions"),
    DictionaryEntry("I will try", "susubukan ko", "A phrase expressing an intention to make an effort.", "Expressions"),
    DictionaryEntry("do not give up", "huwag kang sumuko", "A phrase encouraging someone to continue trying.", "Expressions"),
    DictionaryEntry("keep learning", "patuloy na matuto", "A phrase encouraging ongoing learning.", "Expressions"),
    DictionaryEntry("practice makes perfect", "napapahusay ng pagsasanay ang kakayahan", "A saying that repeated practice improves performance.", "Expressions"),
    DictionaryEntry("knowledge is power", "ang kaalaman ay kapangyarihan", "A saying emphasizing the value of knowledge.", "Expressions"),
    DictionaryEntry("words have power", "may kapangyarihan ang mga salita", "A phrase emphasizing the influence of language.", "Expressions"),
    DictionaryEntry("think before you speak", "mag-isip bago magsalita", "A phrase encouraging careful communication.", "Expressions"),
    DictionaryEntry("better late than never", "mas mabuti ang huli kaysa hindi kailanman", "A saying that doing something late is better than not doing it.", "Expressions"),
    DictionaryEntry("as a result", "bilang resulta", "Because of something that happened.", "Connectors"),
    DictionaryEntry("even though", "kahit na", "Despite the fact that.", "Connectors"),
    DictionaryEntry("unless", "maliban kung", "Except if; if not.", "Connectors"),
    DictionaryEntry("instead of", "sa halip na", "As a replacement for.", "Connectors"),
    DictionaryEntry("in spite of", "sa kabila ng", "Despite something.", "Connectors"),
    DictionaryEntry("according to", "ayon sa", "As stated by a source or person.", "Connectors"),
    DictionaryEntry("on the other hand", "sa kabilang banda", "Used to introduce a contrasting point.", "Connectors"),
    DictionaryEntry("in conclusion", "bilang konklusyon", "Used to introduce the final summary.", "Connectors"),
    DictionaryEntry("in other words", "sa ibang salita", "Used to explain something in a different way.", "Connectors"),
    DictionaryEntry("from now on", "mula ngayon", "Starting at the present time and continuing into the future.", "Time & Nature"),
    DictionaryEntry("so far", "sa ngayon / hanggang ngayon", "Up to the present point.", "Time & Nature"),
    DictionaryEntry("right away", "kaagad", "Immediately.", "Time & Nature"),
    DictionaryEntry("in advance", "nang mas maaga", "Before a particular time or event.", "Time & Nature"),
    DictionaryEntry("one by one", "isa-isa", "Separately, in sequence.", "General"),
    DictionaryEntry("all over the world", "sa buong mundo", "In every part of the world.", "Places"),
    DictionaryEntry("worldwide", "sa buong mundo", "Across the world.", "General"),
    DictionaryEntry("international", "pandaigdigang", "Involving more than one country.", "General"),
    DictionaryEntry("national", "pambansa", "Relating to a whole nation.", "General"),
    DictionaryEntry("local", "lokal", "Relating to a particular nearby area.", "General"),
    DictionaryEntry("entrance", "pasukan / entrada", "A place or way to enter.", "Places"),
    DictionaryEntry("exit", "labasan / lumabas", "A way out of a place.", "Places"),
    DictionaryEntry("elevator", "elevator / asensor", "A platform or compartment that moves between floors.", "Places"),
    DictionaryEntry("stairs", "hagdan", "A set of steps between levels.", "Places"),
    DictionaryEntry("neighborhood", "kapitbahayan", "An area where people live near one another.", "Places"),
    DictionaryEntry("passport", "pasaporte", "An official document used for international travel.", "Travel"),
    DictionaryEntry("luggage", "bagahe", "Bags and belongings taken on a trip.", "Travel"),
    DictionaryEntry("reservation", "reserbasyon", "An arrangement to keep a place or service for someone.", "Travel"),
    DictionaryEntry("destination", "patutunguhan", "The place someone is traveling to.", "Travel"),
    DictionaryEntry("direction", "direksiyon", "The way to go or the course something faces.", "Places"),
    DictionaryEntry("northwest", "hilagang-kanluran", "The direction between north and west.", "Places"),
    DictionaryEntry("northeast", "hilagang-silangan", "The direction between north and east.", "Places"),
    DictionaryEntry("southwest", "timog-kanluran", "The direction between south and west.", "Places"),
    DictionaryEntry("southeast", "timog-silangan", "The direction between south and east.", "Places"),
    DictionaryEntry("opening hours", "oras ng pagbubukas", "The hours during which a place is open.", "Time & Nature"),
    DictionaryEntry("closing time", "oras ng pagsasara", "The time when a place closes.", "Time & Nature"),
    DictionaryEntry("business hours", "oras ng operasyon", "The usual hours a business operates.", "Work"),
    DictionaryEntry("fully booked", "puno na ang reserbasyon", "No more appointments or reservations are available.", "Travel"),
    DictionaryEntry("waiting list", "listahan ng naghihintay", "A list of people waiting for a place or service.", "General"),
    DictionaryEntry("queue number", "numero sa pila", "A number showing a person's place in line.", "General"),
    DictionaryEntry("your turn", "ikaw na ang susunod", "The time when someone is expected to act.", "Expressions"),
    DictionaryEntry("please wait", "pakihintay", "A polite request to remain until something happens.", "Expressions"),
    DictionaryEntry("sorry for the delay", "paumanhin sa pagkaantala", "A phrase apologizing for a delay.", "Expressions"),
    DictionaryEntry("how can I help you", "paano kita matutulungan", "A question offering assistance.", "Expressions"),
    DictionaryEntry("may I help you", "maaari ba kitang tulungan", "A polite offer of assistance.", "Expressions"),
    DictionaryEntry("I would like", "nais ko / gusto ko", "A polite way to express a preference or request.", "Expressions"),
    DictionaryEntry("I would like to order", "nais kong umorder", "A polite phrase for ordering something.", "Expressions"),
    DictionaryEntry("I appreciate it", "pinahahalagahan ko ito", "A phrase expressing gratitude or recognition.", "Expressions"),
    DictionaryEntry("I am not sure", "hindi ako sigurado", "A phrase expressing uncertainty.", "Expressions"),
    DictionaryEntry("let me think", "pag-isipan ko muna", "A phrase asking for time to consider something.", "Expressions"),
    DictionaryEntry("give me a moment", "sandali lang", "A phrase asking for a short amount of time.", "Expressions"),
    DictionaryEntry("I am on my way", "papunta na ako", "A phrase saying one has started traveling to a destination.", "Expressions"),
    DictionaryEntry("I have arrived", "dumating na ako", "A phrase saying one has reached the destination.", "Expressions"),
    DictionaryEntry("I am lost", "naliligaw ako", "A phrase saying one cannot find the way.", "Expressions"),
    DictionaryEntry("keep in touch", "manatiling nakikipag-ugnayan", "A phrase encouraging continued communication.", "Greetings"),
    DictionaryEntry("have a nice day", "magandang araw sa iyo", "A phrase wishing someone a pleasant day.", "Greetings"),
    DictionaryEntry("safe travels", "ingat sa biyahe", "A phrase wishing someone a safe journey.", "Greetings"),
    DictionaryEntry("get well soon", "pagaling ka agad", "A phrase wishing someone a quick recovery.", "Greetings"),
    DictionaryEntry("I am proud of you", "ipinagmamalaki kita", "A phrase expressing pride in another person.", "Expressions"),
    DictionaryEntry("believe in yourself", "maniwala ka sa iyong sarili", "A phrase encouraging self-confidence.", "Expressions"),
    DictionaryEntry("you can do it", "kaya mo iyan", "A phrase expressing confidence in someone's ability.", "Expressions"),
    DictionaryEntry("never stop learning", "huwag tumigil sa pagkatuto", "A phrase encouraging lifelong learning.", "Expressions"),
    DictionaryEntry("make every minute count", "gawing mahalaga ang bawat minuto", "A phrase encouraging effective use of time.", "Expressions"),
    DictionaryEntry("learn from mistakes", "matuto mula sa mga pagkakamali", "To use errors as opportunities to improve.", "Learning"),
    DictionaryEntry("share knowledge", "ibahagi ang kaalaman", "To pass knowledge to others.", "Actions"),
    DictionaryEntry("ask for feedback", "humingi ng puna", "To request comments that can help improve work.", "Actions"),
    DictionaryEntry("continuous learning", "tuloy-tuloy na pagkatuto", "Learning that continues over time.", "Learning"),
    DictionaryEntry("self-improvement", "pagpapabuti sa sarili", "The process of developing one's skills or habits.", "Learning"),
    DictionaryEntry("personal development", "personal na pag-unlad", "The process of improving one's abilities and qualities.", "Learning"),
    DictionaryEntry("challenge yourself", "hamunin ang sarili", "To attempt something that tests one's abilities.", "Actions"),
    DictionaryEntry("pros and cons", "mga kalamangan at kahinaan", "The advantages and disadvantages of something.", "Learning"),
    DictionaryEntry("make a plan", "gumawa ng plano", "To decide how to achieve a goal.", "Actions"),
    DictionaryEntry("follow through", "tapusin ang sinimulan", "To complete what one has planned or promised.", "Actions"),
    DictionaryEntry("commitment", "pangako / dedikasyon", "A firm promise or dedication to something.", "People"),
    DictionaryEntry("dedication", "dedikasyon", "The quality of giving time and effort to a purpose.", "People"),
    DictionaryEntry("hard work", "masipag na paggawa", "Work requiring effort and dedication.", "Work"),
    DictionaryEntry("save time", "makatipid ng oras", "To reduce the time needed for a task.", "Actions"),
    DictionaryEntry("run out of time", "maubusan ng oras", "To have no time remaining.", "Expressions"),
    DictionaryEntry("ahead of schedule", "mas maaga sa iskedyul", "Completed or happening earlier than planned.", "Time & Nature"),
    DictionaryEntry("behind schedule", "nahuhuli sa iskedyul", "Later than planned.", "Time & Nature"),
    DictionaryEntry("postpone", "ipagpaliban", "To move something to a later time.", "Actions"),
    DictionaryEntry("carry out", "isagawa", "To perform or complete a task.", "Actions"),
    DictionaryEntry("set up", "i-set up / ihanda", "To prepare or arrange something for use.", "Actions"),
    DictionaryEntry("look into", "siyasatin", "To investigate or examine something.", "Actions"),
    DictionaryEntry("get along", "magkasundo", "To have a friendly relationship.", "People"),
    DictionaryEntry("get over", "makabawi / malampasan", "To recover from a difficulty or illness.", "Actions"),
    DictionaryEntry("look forward to", "abangan nang may pananabik", "To feel pleased about something expected.", "Feelings"),
    DictionaryEntry("take part", "makilahok", "To participate in an activity.", "Actions"),
    DictionaryEntry("take place", "maganap", "To happen or occur.", "Actions"),
    DictionaryEntry("turn up", "dumating / lakasan", "To arrive or increase volume, depending on context.", "Actions"),
    DictionaryEntry("turn down", "tanggihan / hinaan", "To refuse or reduce volume, depending on context.", "Actions"),
    DictionaryEntry("bring up", "banggitin / palakihin", "To mention a topic or raise a child, depending on context.", "Actions"),
    DictionaryEntry("hand in", "ipasa", "To submit work or an item.", "Actions"),
    DictionaryEntry("fill out", "punan", "To complete a form.", "Actions"),
    DictionaryEntry("check in", "mag-check in / magparehistro ng pagdating", "To register arrival at a hotel, airport, or event.", "Travel"),
    DictionaryEntry("check out", "mag-check out / umalis matapos magbayad", "To register departure or examine something, depending on context.", "Travel"),
    DictionaryEntry("hang out", "tumambay / magpalipas-oras", "To spend time relaxing with others.", "Actions"),
    DictionaryEntry("figure out", "maunawaan / mahanap ang sagot", "To understand or solve something.", "Actions"),
    DictionaryEntry("point out", "ituro / banggitin", "To draw attention to something.", "Actions"),
    DictionaryEntry("make up your mind", "magpasya", "To make a decision.", "Actions"),
    DictionaryEntry("change your mind", "magbago ng isip", "To decide differently from before.", "Actions"),
    DictionaryEntry("keep in mind", "tandaan", "To remember or consider something.", "Actions"),
    DictionaryEntry("pass away", "pumanaw", "A polite expression meaning to die.", "People"),
    DictionaryEntry("put up with", "tiisin", "To tolerate something unpleasant.", "Actions"),
    DictionaryEntry("zero in on", "ituon ang pansin sa", "To focus closely on something.", "Actions"),
)).distinctBy { it.english.lowercase() }

@Composable
private fun DictionaryPage() {
    var query by rememberSaveable { mutableStateOf("") }
    val filteredWords = remember(query) {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) tagalogEnglishWords
        else tagalogEnglishWords.filter {
            it.english.lowercase().contains(normalized) ||
                it.tagalog.lowercase().contains(normalized) ||
                it.meaning.lowercase().contains(normalized)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Heading("📖 Tagalog–English Dictionary", "Search English or Tagalog words and phrases offline.")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search English or Tagalog") },
            placeholder = { Text("e.g. love, salamat, kaibigan") },
            singleLine = true,
            trailingIcon = {
                if (query.isNotBlank()) {
                    TextButton(onClick = { query = "" }) { Text("Clear") }
                }
            }
        )
        Text(
            "${filteredWords.size} word${if (filteredWords.size == 1) "" else "s"} found",
            color = muted,
            style = MaterialTheme.typography.bodySmall
        )

        if (filteredWords.isEmpty()) {
            CardBox("No matching word", "Try another spelling or search using the English or Tagalog word.") {}
        } else {
            filteredWords.forEach { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = panel),
                    border = androidx.compose.foundation.BorderStroke(1.dp, cyan.copy(alpha = 0.24f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(entry.english.replaceFirstChar { it.uppercase() }, color = cyan, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        Text("Tagalog: ${entry.tagalog}", color = pink, fontWeight = FontWeight.Bold)
                        Text(entry.meaning, color = muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Text(
            "Note: This is an expanded offline starter dictionary, not every Tagalog and English word. Translations can vary by context and region.",
            color = muted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun VocabularyBuilderPage() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("studymate_data", Context.MODE_PRIVATE) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("All") }
    var showFavoritesOnly by rememberSaveable { mutableStateOf(false) }
    val favorites = remember {
        mutableStateListOf<String>().apply {
            addAll(decodeStrings(prefs.getString("vocabularyFavorites", "[]") ?: "[]"))
        }
    }
    val categories = remember { listOf("All") + tagalogEnglishWords.map { it.category }.distinct().sorted() }
    val dailyIndex = remember { (java.time.LocalDate.now().dayOfYear - 1) % tagalogEnglishWords.size }
    val wordOfTheDay = tagalogEnglishWords[dailyIndex]
    val filteredWords = remember(query, selectedCategory, showFavoritesOnly, favorites.toList()) {
        val normalized = query.trim().lowercase()
        tagalogEnglishWords.filter { entry ->
            val matchesQuery = normalized.isBlank() || entry.english.lowercase().contains(normalized) ||
                entry.tagalog.lowercase().contains(normalized) || entry.meaning.lowercase().contains(normalized) ||
                entry.example.lowercase().contains(normalized)
            val matchesCategory = selectedCategory == "All" || entry.category == selectedCategory
            val matchesFavorites = !showFavoritesOnly || entry.english in favorites
            matchesQuery && matchesCategory && matchesFavorites
        }.sortedBy { it.english }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Heading("🧠 Vocabulary Builder", "Build your Tagalog–English vocabulary with meanings, categories, and example sentences.")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = panel),
            border = androidx.compose.foundation.BorderStroke(1.dp, pink.copy(alpha = 0.45f))
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("🌟 WORD OF THE DAY", color = pink, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                Text(wordOfTheDay.english.replaceFirstChar { it.uppercase() }, color = cyan, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("Tagalog: ${wordOfTheDay.tagalog}", color = pink, fontWeight = FontWeight.Bold)
                Text(wordOfTheDay.meaning, color = muted)
                if (wordOfTheDay.example.isNotBlank()) Text("Example: ${wordOfTheDay.example}", color = Color(0xFFE6F7FF), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    if (wordOfTheDay.english !in favorites) favorites.add(wordOfTheDay.english)
                    else favorites.remove(wordOfTheDay.english)
                    prefs.edit().putString("vocabularyFavorites", encodeStrings(favorites.toList())).apply()
                }) { Text(if (wordOfTheDay.english in favorites) "★ Saved to Favorites" else "☆ Save Word") }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search English, Tagalog, or meaning") },
            placeholder = { Text("e.g. grateful, pasasalamat, school") },
            singleLine = true,
            trailingIcon = { if (query.isNotBlank()) TextButton(onClick = { query = "" }) { Text("Clear") } }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showFavoritesOnly, onClick = { showFavoritesOnly = false }, label = { Text("All Words") })
            FilterChip(selected = showFavoritesOnly, onClick = { showFavoritesOnly = true }, label = { Text("★ Favorites (${favorites.size})") })
        }
        Text("Categories", color = muted, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { category ->
                FilterChip(selected = selectedCategory == category, onClick = { selectedCategory = category }, label = { Text(category) })
            }
        }
        Text("${filteredWords.size} words available • ${tagalogEnglishWords.size} built-in entries", color = muted, style = MaterialTheme.typography.bodySmall)
        if (filteredWords.isEmpty()) {
            CardBox("No matching words", "Try another search, select All categories, or save some words to Favorites.") {}
        } else {
            filteredWords.forEach { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = panel),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (entry.english in favorites) pink.copy(alpha = 0.55f) else cyan.copy(alpha = 0.22f))
                ) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.english.replaceFirstChar { it.uppercase() }, color = cyan, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                Text("Tagalog: ${entry.tagalog}", color = pink, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = {
                                if (entry.english in favorites) favorites.remove(entry.english) else favorites.add(entry.english)
                                prefs.edit().putString("vocabularyFavorites", encodeStrings(favorites.toList())).apply()
                            }) { Text(if (entry.english in favorites) "★" else "☆") }
                        }
                        Text(entry.meaning, color = muted, style = MaterialTheme.typography.bodyMedium)
                        if (entry.example.isNotBlank()) Text("Example: ${entry.example}", color = Color(0xFFE6F7FF), style = MaterialTheme.typography.bodySmall)
                        Surface(color = purple.copy(alpha = 0.13f), shape = RoundedCornerShape(10.dp)) {
                            Text(entry.category, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), color = purple, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        Text("This is an expandable starter collection, not every Tagalog and English word in the world. Translations can vary by context; more entries can be added in future updates.", color = muted, style = MaterialTheme.typography.bodySmall)
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
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.ExtraBold
        )
        Text(subtitle, color = muted)
    }
}

@Composable
private fun CardBox(title: String, subtitle: String, onClick: () -> Unit = {}) {
    val accent = when {
        title.contains("🔥") || title.contains("Daily") -> Color(0xFFFFC45C)
        title.contains("🛒") || title.contains("Coins") -> cyan
        title.contains("🏆") || title.contains("Level") -> pink
        else -> purple
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.38f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp, pressedElevation = 7.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.13f), Color(0xFF121D32), Color(0xFF0B1425))
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .heightIn(min = 34.dp)
                    .background(
                        Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.22f))),
                        RoundedCornerShape(8.dp)
                    )
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFF2F7FF))
                Text(subtitle, color = muted, style = MaterialTheme.typography.bodyMedium)
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
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF101A2D),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.62f)),
        shadowElevation = 7.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.16f), Color(0xFF111C30), Color(0xFF0D1729))
                    )
                )
                .padding(horizontal = 14.dp, vertical = 17.dp),
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
                Text(title, fontWeight = FontWeight.ExtraBold, color = Color(0xFFF3F8FF), style = MaterialTheme.typography.titleSmall)
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
    var searchQuery by rememberSaveable { mutableStateOf("") }

    Heading("📝 My Notes", "Create, search, edit, and save your notes on this device.")
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
        minLines = 5,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            autoCorrect = true
        )
    )
    Text(
        "${body.trim().split(Regex("\\s+")).count { it.isNotBlank() }} words • ${body.length} characters",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall
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

    if (editingId != null) {
        Text("Editing note — tap Update Note to save your changes.", color = MaterialTheme.colorScheme.secondary)
    }

    OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        label = { Text("Search saved notes") },
        placeholder = { Text("Search title or note text") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    val visibleNotes = notes.toList().asReversed().filter { note ->
        searchQuery.isBlank() || note.title.contains(searchQuery, ignoreCase = true) ||
            note.body.contains(searchQuery, ignoreCase = true)
    }

    if (notes.isEmpty()) {
        Text("No notes yet. Add your first note above.", color = muted)
    } else if (visibleNotes.isEmpty()) {
        Text("No notes match your search.", color = muted)
    } else {
        Text("${visibleNotes.size} note${if (visibleNotes.size == 1) "" else "s"}", color = muted)
    }

    visibleNotes.forEach { note ->
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
