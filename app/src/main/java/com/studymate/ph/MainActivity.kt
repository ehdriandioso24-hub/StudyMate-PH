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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

private enum class Screen {
    HOME, SUBJECTS, QUIZ_MENU, QUIZ, NOTES, FLASHCARDS, TIMER, PROGRESS
}

private data class Question(
    val question: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String
)

private data class Flashcard(
    val front: String,
    val back: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            StudyMateApp(this)
        }
    }
}

@Composable
fun StudyMateApp(context: Context) {

    val prefs = remember {
        context.getSharedPreferences("studymate_data", Context.MODE_PRIVATE)
    }

    var screen by remember { mutableStateOf(Screen.HOME) }
    var selectedSubject by remember { mutableStateOf("Math") }

    var notes by remember {
        mutableStateOf(prefs.getString("notes", "") ?: "")
    }

    var completedQuizzes by remember {
        mutableStateOf(prefs.getInt("completed_quizzes", 0))
    }

    var bestScore by remember {
        mutableStateOf(prefs.getInt("best_score", 0))
    }

    var customSubjects by remember {
        mutableStateOf(
            prefs.getString("custom_subjects", "")
                ?.split("|")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        )
    }

    val subjects = listOf(
        "Math",
        "Science",
        "English",
        "Filipino"
    ) + customSubjects

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFFC084FC),
            secondary = androidx.compose.ui.graphics.Color(0xFFF472D0),
            background = androidx.compose.ui.graphics.Color(0xFF080D1D),
            surface = androidx.compose.ui.graphics.Color(0xFF111A30)
        )
    ) {

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {

            when (screen) {

                Screen.HOME -> HomeScreen(
                    completedQuizzes,
                    bestScore
                ) {
                    screen = it
                }

                Screen.SUBJECTS -> SubjectsScreen(
                    subjects,
                    selectedSubject,
                    onSelect = {
                        selectedSubject = it
                        screen = Screen.QUIZ_MENU
                    },
                    onAddSubject = { name ->
                        if (
                            name.isNotBlank() &&
                            !subjects.contains(name.trim())
                        ) {
                            val updated = customSubjects + name.trim()

                            customSubjects = updated

                            prefs.edit()
                                .putString(
                                    "custom_subjects",
                                    updated.joinToString("|")
                                )
                                .apply()
                        }
                    }
                ) {
                    screen = Screen.HOME
                }

                Screen.QUIZ_MENU -> QuizMenuScreen(
                    selectedSubject,
                    onStart = {
                        screen = Screen.QUIZ
                    }
                ) {
                    screen = Screen.SUBJECTS
                }

                Screen.QUIZ -> QuizScreen(
                    selectedSubject,
                    onFinished = { score, total ->

                        completedQuizzes++

                        bestScore = maxOf(bestScore, score)

                        prefs.edit()
                            .putInt(
                                "completed_quizzes",
                                completedQuizzes
                            )
                            .putInt(
                                "best_score",
                                bestScore
                            )
                            .apply()

                        screen = Screen.PROGRESS
                    }
                ) {
                    screen = Screen.QUIZ_MENU
                }

                Screen.NOTES -> NotesScreen(
                    notes,
                    onNotesChanged = {
                        notes = it

                        prefs.edit()
                            .putString("notes", it)
                            .apply()
                    }
                ) {
                    screen = Screen.HOME
                }

                Screen.FLASHCARDS -> FlashcardsScreen(
                    selectedSubject
                ) {
                    screen = Screen.HOME
                }

                Screen.TIMER -> StudyTimerScreen {
                    screen = Screen.HOME
                }

                Screen.PROGRESS -> ProgressScreen(
                    completedQuizzes,
                    bestScore
                ) {
                    screen = Screen.HOME
                }
            }
        }
    }
}

@Composable
fun Header(
    title: String,
    onBack: (() -> Unit)? = null
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        if (onBack != null) {

            Text(
                "‹",
                fontSize = 36.sp,
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(end = 12.dp)
            )
        }

        Text(
            title,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun HomeScreen(
    completed: Int,
    bestScore: Int,
    navigate: (Screen) -> Unit
) {

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {

        item {

            Spacer(Modifier.height(25.dp))

            Text(
                "👋 Hi, Student!",
                fontSize = 29.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Ready to learn today?",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {

                Column(
                    Modifier.padding(20.dp)
                ) {

                    Text(
                        "📊 Your Progress",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(8.dp))

                    Text("Quizzes completed: $completed")
                    Text("Best score: $bestScore")
                }
            }

            Spacer(Modifier.height(20.dp))

            HomeButton(
                "📚 Subjects",
                "Math, Science, English, Filipino"
            ) {
                navigate(Screen.SUBJECTS)
            }

            HomeButton(
                "🧠 Quick Quiz",
                "Test your knowledge"
            ) {
                navigate(Screen.QUIZ_MENU)
            }

            HomeButton(
                "📝 My Notes",
                "Save your notes offline"
            ) {
                navigate(Screen.NOTES)
            }

            HomeButton(
                "🃏 Flashcards",
                "Review important concepts"
            ) {
                navigate(Screen.FLASHCARDS)
            }

            HomeButton(
                "⏱️ Study Timer",
                "25-minute focus timer"
            ) {
                navigate(Screen.TIMER)
            }

            HomeButton(
                "🏆 Progress",
                "See your learning progress"
            ) {
                navigate(Screen.PROGRESS)
            }
        }
    }
}

@Composable
fun HomeButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(17.dp)
    ) {

        Column(
            Modifier.padding(18.dp)
        ) {

            Text(
                title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SubjectsScreen(
    subjects: List<String>,
    selectedSubject: String,
    onSelect: (String) -> Unit,
    onAddSubject: (String) -> Unit,
    onBack: () -> Unit
) {

    var showDialog by remember { mutableStateOf(false) }
    var newSubject by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {

        Header("📚 My Subjects", onBack)

        LazyColumn(
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {

            items(subjects) { subject ->

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clickable {
                            onSelect(subject)
                        },
                    shape = RoundedCornerShape(16.dp)
                ) {

                    Row(
                        Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            when (subject) {
                                "Math" -> "📐"
                                "Science" -> "🔬"
                                "English" -> "📖"
                                "Filipino" -> "🇵🇭"
                                else -> "📘"
                            },
                            fontSize = 25.sp
                        )

                        Spacer(Modifier.width(15.dp))

                        Text(
                            subject,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        showDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("＋ Add Custom Subject")
                }
            }
        }
    }

    if (showDialog) {

        AlertDialog(
            onDismissRequest = {
                showDialog = false
            },

            title = {
                Text("Add Subject")
            },

            text = {

                OutlinedTextField(
                    value = newSubject,
                    onValueChange = {
                        newSubject = it
                    },
                    label = {
                        Text("Subject name")
                    },
                    singleLine = true
                )
            },

            confirmButton = {

                TextButton(
                    onClick = {

                        onAddSubject(newSubject)

                        newSubject = ""

                        showDialog = false
                    }
                ) {
                    Text("Add")
                }
            },

            dismissButton = {

                TextButton(
                    onClick = {
                        showDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun QuizMenuScreen(
    subject: String,
    onStart: () -> Unit,
    onBack: () -> Unit
) {

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        Header("🧠 Quiz", onBack)

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {

            Column(
                Modifier.padding(22.dp)
            ) {

                Text("Subject")

                Text(
                    subject,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                Text("Answer the questions and get your score.")

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("▶ Start Quiz")
                }
            }
        }
    }
}

fun getQuestions(subject: String): List<Question> {

    return when (subject) {

        "Math" -> listOf(

            Question(
                "What is 12 × 5?",
                listOf("50", "60", "70", "80"),
                1,
                "12 × 5 = 60."
            ),

            Question(
                "What is 3²?",
                listOf("6", "8", "9", "12"),
                2,
                "3 × 3 = 9."
            ),

            Question(
                "What is 100 ÷ 4?",
                listOf("20", "25", "30", "40"),
                1,
                "100 ÷ 4 = 25."
            )
        )

        "Science" -> listOf(

            Question(
                "Which organ pumps blood?",
                listOf(
                    "Lungs",
                    "Heart",
                    "Brain",
                    "Stomach"
                ),
                1,
                "The heart pumps blood around the body."
            ),

            Question(
                "Which gas do humans need for respiration?",
                listOf(
                    "Oxygen",
                    "Helium",
                    "Carbon dioxide",
                    "Hydrogen"
                ),
                0,
                "Humans need oxygen for respiration."
            ),

            Question(
                "Which planet is called the Red Planet?",
                listOf(
                    "Venus",
                    "Mars",
                    "Jupiter",
                    "Mercury"
                ),
                1,
                "Mars is known as the Red Planet."
            )
        )

        "English" -> listOf(

            Question(
                "Which word is a noun?",
                listOf(
                    "Run",
                    "Beautiful",
                    "Teacher",
                    "Quickly"
                ),
                2,
                "Teacher is a noun."
            ),

            Question(
                "Choose the correct sentence.",
                listOf(
                    "She are happy.",
                    "She is happy.",
                    "She am happy.",
                    "She be happy."
                ),
                1,
                "The correct sentence is 'She is happy.'"
            ),

            Question(
                "What is the opposite of ancient?",
                listOf(
                    "Old",
                    "Modern",
                    "Historic",
                    "Past"
                ),
                1,
                "The opposite of ancient is modern."
            )
        )

        "Filipino" -> listOf(

            Question(
                "Alin ang pangngalan?",
                listOf(
                    "Tumakbo",
                    "Masaya",
                    "Guro",
                    "Mabilis"
                ),
                2,
                "Ang guro ay pangngalan."
            ),

            Question(
                "Ano ang kasingkahulugan ng maganda?",
                listOf(
                    "Marumi",
                    "Marikit",
                    "Maliit",
                    "Mabagal"
                ),
                1,
                "Ang marikit ay kasingkahulugan ng maganda."
            ),

            Question(
                "Ano ang kasalungat ng mataas?",
                listOf(
                    "Mababa",
                    "Mahaba",
                    "Malaki",
                    "Malawak"
                ),
                0,
                "Ang kasalungat ng mataas ay mababa."
            )
        )

        else -> listOf(

            Question(
                "What is a good study habit?",
                listOf(
                    "Take organized notes",
                    "Never review",
                    "Skip practice",
                    "Ignore examples"
                ),
                0,
                "Organized notes can help with review."
            ),

            Question(
                "What can improve learning?",
                listOf(
                    "Practice",
                    "Never studying",
                    "Ignoring lessons",
                    "Skipping review"
                ),
                0,
                "Regular practice can improve learning."
            )
        )
    }
}

@Composable
fun QuizScreen(
    subject: String,
    onFinished: (Int, Int) -> Unit,
    onBack: () -> Unit
) {

    val questions = remember(subject) {
        getQuestions(subject)
    }

    var current by remember {
        mutableStateOf(0)
    }

    var score by remember {
        mutableStateOf(0)
    }

    var selected by remember {
        mutableStateOf<Int?>(null)
    }

    val question = questions[current]

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {

        Header(
            "🧠 $subject Quiz",
            onBack
        )

        Text(
            "Question ${current + 1} of ${questions.size}"
        )

        Spacer(Modifier.height(12.dp))

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {

            Column(
                Modifier.padding(20.dp)
            ) {

                Text(
                    question.question,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(18.dp))

                question.options.forEachIndexed { index, option ->

                    Button(
                        onClick = {

                            if (selected == null) {

                                selected = index

                                if (index == question.answer) {
                                    score++
                                }
                            }
                        },

                        modifier = Modifier
          
