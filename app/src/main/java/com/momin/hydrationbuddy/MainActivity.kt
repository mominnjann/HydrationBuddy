package com.momin.hydrationbuddy

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ContactMail
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.momin.hydrationbuddy.ui.theme.HydrationBuddyTheme
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import android.content.SharedPreferences
import androidx.compose.ui.geometry.Offset
import android.widget.Toast

/**
 * MainActivity for Hydration Buddy
 * Modular, readable, and ready for open source.
 */

enum class Screen(val title: String, val icon: ImageVector) {
    Home("Home", Icons.Default.LocalDrink),
    Yesterday("Yesterday's Intake", Icons.Default.History),
    Monthly("Monthly Stats", Icons.Default.BarChart),
    Policy("Privacy Policy", Icons.Default.Info),
    Contact("Contact Us", Icons.Default.ContactMail),
    FAQ("FAQ", Icons.Default.Info)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        setContent {
            HydrationBuddyTheme {
                var showWelcomeDialog by remember { mutableStateOf(true) }
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box {
                        HydrationScreen()
                        if (showWelcomeDialog) {
                            WelcomeDialog { showWelcomeDialog = false }
                        }
                    }
                }
            }
        }

        // Send a test notification to activate the channel (only once per app launch)
        val channelId = "hydration_reminder"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Hydration Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Hydration Buddy Test")
            .setContentText("This is a test notification.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        notificationManager.notify(1, notification)

        // Schedule repeating alarm at the top of every hour
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, HydrationAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis < System.currentTimeMillis()) {
                add(java.util.Calendar.HOUR_OF_DAY, 1)
            }
        }

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_HOUR,
            pendingIntent
        )
    }
}

@Composable
fun WelcomeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Let's Go!", fontWeight = FontWeight.Bold, color = Color(0xFF2196F3))
            }
        },
        icon = {
            Icon(
                Icons.Default.LocalDrink,
                contentDescription = "Welcome",
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                "Welcome to Hydration Buddy!",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFF2196F3)
            )
        },
        text = {
            Text(
                "Stay healthy and hydrated with hourly reminders to drink water. Let's reach your daily goal together!",
                textAlign = TextAlign.Center
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HydrationScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("hydration_prefs", Context.MODE_PRIVATE)
    val totalGlasses = 10

    val yesterdayIntake = prefs.getInt("yesterday_glasses", -1)
    val monthlyStats = remember { List(30) { prefs.getInt("day_${it + 1}_glasses", -1) } }

    var selectedScreen by remember { mutableStateOf(Screen.Home) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(selectedScreen, onScreenSelected = {
                selectedScreen = it
                scope.launch { drawerState.close() }
            })
        },
        scrimColor = Color.Transparent
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(selectedScreen.title) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color(0xFF2196F3))
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(Modifier.padding(innerPadding).fillMaxSize()) {
                when (selectedScreen) {
                    Screen.Home -> HomeContent(prefs)
                    Screen.Yesterday -> YesterdayContent(yesterdayIntake)
                    Screen.Monthly -> MonthlyStatsContent(monthlyStats, totalGlasses)
                    Screen.Policy -> PolicyContent()
                    Screen.Contact -> ContactContent()
                    Screen.FAQ -> FAQContent()
                }
            }
        }
    }
}

@Composable
fun DrawerContent(selectedScreen: Screen, onScreenSelected: (Screen) -> Unit) {
    Box(
        Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(Color.White.copy(alpha = 0.85f))
    ) {
        Column {
            Text(
                "Hydration Buddy",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color(0xFF2196F3),
                modifier = Modifier.padding(24.dp)
            )
            Divider()
            Screen.values().forEach { screen ->
                NavigationDrawerItem(
                    icon = { Icon(screen.icon, contentDescription = screen.title) },
                    label = { Text(screen.title) },
                    selected = selectedScreen == screen,
                    onClick = { onScreenSelected(screen) },
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .clip(RectangleShape),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = Color(0xFF2196F3).copy(alpha = 0.15f),
                        unselectedContainerColor = Color.Transparent,
                        selectedIconColor = Color(0xFF2196F3),
                        selectedTextColor = Color(0xFF2196F3)
                    ),
                    shape = RectangleShape
                )
            }
        }
    }
}

// -------------------- Home Screen --------------------
@Composable
fun HomeContent(prefs: SharedPreferences) {
    val context = LocalContext.current
    val today = remember { SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date()) }
    var glassesDrunk by remember { mutableStateOf(0) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var goalInput by remember { mutableStateOf("") }

    // Load goal from prefs, default to 17
    var totalGlasses by remember { mutableStateOf(prefs.getInt("goal", 10)) }

    // New states for dialogs
    var showStopDialog by remember { mutableStateOf(false) }
    var showCongratsDialog by remember { mutableStateOf(false) }
    var showIncompleteDialog by remember { mutableStateOf(false) }
    var showFinalWarningDialog by remember { mutableStateOf(false) }
    var yesEnabled by remember { mutableStateOf(false) }
    var showNotifDisabledDialog by remember { mutableStateOf(false) }

    // Animate background color
    val infiniteTransition = rememberInfiniteTransition()
    val animatedColor by infiniteTransition.animateValue(
        initialValue = Color(0xFF74ebd5),
        targetValue = Color(0xFF2196F3),
        typeConverter = TwoWayConverter(
            convertToVector = { color -> AnimationVector4D(color.red, color.green, color.blue, color.alpha) },
            convertFromVector = { vector -> Color(vector.v1, vector.v2, vector.v3, vector.v4) }
        ),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    LaunchedEffect(Unit) {
        val lastDate = prefs.getString("last_date", null)
        if (lastDate != today) {
            prefs.edit().putInt("glasses_drunk", 0).putString("last_date", today).apply()
            glassesDrunk = 0
        } else {
            glassesDrunk = prefs.getInt("glasses_drunk", 0)
        }

        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
        val disabledDate = prefs.getString("notifications_disabled_date", "")

        if (!notificationsEnabled && disabledDate == today) {
            // Do NOT show notification
            return@LaunchedEffect
        } else if (disabledDate != today) {
            // New day, re-enable notifications
            prefs.edit().putBoolean("notifications_enabled", true).apply()
        }
    }

    fun saveGlassesDrunk(value: Int) {
        prefs.edit().putInt("glasses_drunk", value).putString("last_date", today).apply()
    }

    fun saveGoal(newGoal: Int) {
        prefs.edit().putInt("goal", newGoal).apply()
        totalGlasses = newGoal
    }

    // Goal dialog
    if (showGoalDialog) {
        AlertDialog(
            containerColor = Color(0xFFE3F2FD),
            onDismissRequest = { showGoalDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newGoal = goalInput.toIntOrNull()
                        if (newGoal != null && newGoal > 0) {
                            saveGoal(newGoal)
                            showGoalDialog = false
                        }
                    }
                ) {
                    Text("Save", fontWeight = FontWeight.Bold, color = Color(0xFF2196F3))
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            title = {
                Text(
                    "Set Your Daily Goal 🎯",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color(0xFF845EC2),
                    letterSpacing = 1.sp
                )
            },
            text = {
                Column {
                    Text(
                        "How many glasses of water would you like to drink today?",
                        color = Color(0xFF2196F3),
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = goalInput,
                        onValueChange = { goalInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Number of glasses", color = Color(0xFF2196F3)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

    // Stop notifications dialog
    if (showStopDialog) {
        AlertDialog(
            containerColor = Color(0xFFE3F2FD),
            onDismissRequest = { showStopDialog = false },
            title = { Text("Have you completed your goal? 🎯", color = Color(0xFF2196F3), fontWeight = FontWeight.Bold) },
            text = { Text("Let us know if you've finished your daily hydration goal!", color = Color(0xFF2196F3)) },
            confirmButton = {
                TextButton(onClick = {
                    if (glassesDrunk >= totalGlasses) {
                        showStopDialog = false
                        showCongratsDialog = true
                    } else {
                        showStopDialog = false
                        showIncompleteDialog = true
                    }
                }) { Text("Yes", fontWeight = FontWeight.Bold, color = Color(0xFF2196F3)) }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) { Text("No", fontWeight = FontWeight.Bold, color = Color.Gray) }
            }
        )
    }

    // Congratulations dialog
    if (showCongratsDialog) {
        AlertDialog(
            containerColor = Color(0xFFE3F2FD),
            onDismissRequest = { showCongratsDialog = false },
            title = { Text("Congratulations! 🥳", fontWeight = FontWeight.Bold, color = Color(0xFF2196F3), fontSize = 20.sp) },
            text = { Text("You've achieved your hydration goal for today!\n\nWould you like to turn off reminder notifications until tomorrow?", fontSize = 16.sp, color = Color.Gray) },
            confirmButton = {
                TextButton(onClick = {
                    showCongratsDialog = false
                    showFinalWarningDialog = true
                    yesEnabled = false
                }) { Text("Yes, turn off", fontWeight = FontWeight.Bold, color = Color(0xFF2196F3)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCongratsDialog = false
                    Toast.makeText(context, "We'll keep reminding you to stay hydrated! 💧", Toast.LENGTH_SHORT).show()
                }) { Text("No, keep reminding me", fontWeight = FontWeight.Bold, color = Color(0xFF2196F3)) }
            }
        )
    }

    // Incomplete goal dialog
    if (showIncompleteDialog) {
        AlertDialog(
            containerColor = Color(0xFFE3F2FD),
            onDismissRequest = { showIncompleteDialog = false },
            title = { Text("Keep Going! 💧", fontWeight = FontWeight.Bold, color = Color(0xFF2196F3), fontSize = 20.sp) },
            text = { Text("You haven't completed your goal yet. Drink a bit more water to reach your target! 💪", fontSize = 16.sp, color = Color.Gray) },
            confirmButton = {
                TextButton(onClick = { showIncompleteDialog = false }) { Text("OK", fontWeight = FontWeight.Bold, color = Color(0xFF2196F3)) }
            }
        )
    }

    // Final warning dialog (with 5s delay)
    if (showFinalWarningDialog) {
        LaunchedEffect(showFinalWarningDialog) {
            yesEnabled = false
            kotlinx.coroutines.delay(5000)
            yesEnabled = true
        }
        AlertDialog(
            onDismissRequest = { showFinalWarningDialog = false },
            containerColor = Color(0xFFFFE0E0),
            title = {
                Text(
                    "⚠️ Warning",
                    color = Color(0xFFD32F2F),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Text(
                    "If you turn off notifications, they will not be re-enabled until tomorrow.\n\nWould you really like to turn off notifications?",
                    color = Color(0xFFD32F2F),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        prefs.edit().putBoolean("notifications_enabled", false)
                            .putString("notifications_disabled_date", today)
                            .apply()
                        showFinalWarningDialog = false
                        Toast.makeText(context, "Reminders are off for today. Enjoy your accomplishment! 🎉", Toast.LENGTH_LONG).show()
                    },
                    enabled = yesEnabled
                ) {
                    Text(
                        "Yes, turn off",
                        fontWeight = FontWeight.Bold,
                        color = if (yesEnabled) Color(0xFFD32F2F) else Color.Gray
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinalWarningDialog = false }) {
                    Text("No", fontWeight = FontWeight.Bold, color = Color(0xFF2196F3))
                }
            }
        )
    }

    // New dialog for notification disabled status
    if (showNotifDisabledDialog) {
        AlertDialog(
            containerColor = Color(0xFFFFE0E0),
            onDismissRequest = { showNotifDisabledDialog = false },
            confirmButton = {
                TextButton(onClick = { showNotifDisabledDialog = false }) {
                    Text("OK", fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                }
            },
            title = {
                Text(
                    "Notifications Already Off",
                    color = Color(0xFFD32F2F),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Text(
                    "You have already turned off notifications for today. They will be automatically re-enabled tomorrow.",
                    color = Color(0xFFD32F2F),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(animatedColor, Color.White)
                )
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            // Glass Icon
            Icon(
                Icons.Default.LocalDrink,
                contentDescription = "Glass",
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Daily Hydration",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                color = Color(0xFF2196F3),
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Goal: $totalGlasses glasses",
                    fontSize = 18.sp,
                    color = Color(0xFF2196F3).copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        goalInput = totalGlasses.toString()
                        showGoalDialog = true
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF2196F3)
                    ),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Set Goal", fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
            // Glasses Counter
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF2196F3).copy(alpha = 0.3f), Color.Transparent),
                            center = Offset(80f, 80f),
                            radius = 160f
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$glassesDrunk",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF2196F3),
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Glasses Drunk Today",
                fontSize = 16.sp,
                color = Color.Gray
            )
            Spacer(Modifier.height(32.dp))
            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    onClick = {
                        prefs.edit().putInt("glasses_drunk", 0).apply()
                        glassesDrunk = 0
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF2196F3)
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Reset")
                }
                Button(
                    onClick = {
                        glassesDrunk++
                        saveGlassesDrunk(glassesDrunk)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Add Glass")
                }
            }
            Spacer(Modifier.height(16.dp))
            // Notification status
            val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
            val disabledDate = prefs.getString("notifications_disabled_date", "")
            val isToday = disabledDate == today
            val notifOn = notificationsEnabled || !isToday

            Button(
                onClick = {
                    if (!notifOn) {
                        showNotifDisabledDialog = true
                    } else {
                        if (glassesDrunk >= totalGlasses) {
                            showCongratsDialog = true
                        } else {
                            showIncompleteDialog = true
                        }
                    }
                },
                enabled = notifOn,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF845EC2),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFBDBDBD),
                    disabledContentColor = Color.White
                ),
                shape = RoundedCornerShape(50)
            ) {
                Text("Stop Notifications 🚫", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            // Notification status alert
            val notificationsEnabled2 = prefs.getBoolean("notifications_enabled", true)
            val disabledDate2 = prefs.getString("notifications_disabled_date", "")
            val isToday2 = disabledDate2 == today
            val notifOn2 = notificationsEnabled2 || !isToday2

            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (notifOn2) Color(0xFFD0F5E8) else Color(0xFFFFE0E0),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = if (notifOn2)
                        "🔔 Notifications are ON for today"
                    else
                        "🔕 Notifications are OFF for today",
                    color = if (notifOn2) Color(0xFF2196F3) else Color(0xFFD32F2F),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                text = "Powered by Momin Jan",
                fontSize = 12.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// -------------------- Yesterday's Intake Screen --------------------
@Composable
fun YesterdayContent(yesterdayIntake: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Yesterday's Water Intake",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = Color(0xFF2196F3),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        // Display yesterday's intake or a message if not available
        if (yesterdayIntake != -1) {
            Text(
                "$yesterdayIntake glasses",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2196F3),
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                "No data available",
                fontSize = 18.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

// -------------------- Monthly Stats Screen --------------------
@Composable
fun MonthlyStatsContent(stats: List<Int>, totalGlasses: Int) {
    val context = LocalContext.current
    val averageIntake = if (stats.isNotEmpty()) stats.average() else 0.0
    val formattedAverage = String.format("%.1f", averageIntake)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Monthly Hydration Stats",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = Color(0xFF2196F3)
        )
        MonthlyBarGraph(stats, totalGlasses)

        // Average intake display
        Text(
            "Average Intake: $formattedAverage glasses",
            fontSize = 18.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 8.dp)
        )

        // Share button
        Button(
            onClick = {
                // Share logic
                val shareText = "I averaged $formattedAverage glasses of water intake this month. Stay hydrated! 💧"
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, null)
                context.startActivity(shareIntent)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2196F3),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(50)
        ) {
            Text("Share Your Achievement")
        }
    }
}

@Composable
fun MonthlyBarGraph(stats: List<Int>, goal: Int) {
    val barColor = Color(0xFF2196F3)
    val emptyBarColor = Color(0xFFE0E0E0)
    val maxGlasses = goal

    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            "Monthly Hydration Stats",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(180.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            stats.forEachIndexed { index, value ->
                val barHeight = if (value == -1) 0f else value.toFloat() / maxGlasses
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(barHeight)
                            .background(
                                if (value == -1) emptyBarColor else barColor,
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
        }
        // X-axis labels (1, 5, 10, 15, 20, 25, 30)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf(1, 5, 10, 15, 20, 25, 30).forEach { day ->
                Text("$day", fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}

// -------------------- Privacy Policy Screen --------------------
@Composable
fun PolicyContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Privacy Policy",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = Color(0xFF2196F3)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Your hydration data is stored only on your device and is never shared or uploaded. We respect your privacy and do not collect any personal information.",
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

// -------------------- Contact Screen --------------------
@Composable
fun ContactContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Contact the Developer",
            color = Color(0xFF2196F3),
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "@mominnjann",
            color = Color(0xFF405DE6),
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "For feedback, support, or collaboration, reach out on Instagram using the above user ID. Thank you for using Hydration Buddy!",
            color = Color.Gray,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

// -------------------- FAQ Screen --------------------
@Composable
fun FAQContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            "FAQ",
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            color = Color(0xFF2196F3),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = "Q: What does this app do?",
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            color = Color(0xFF1976D2)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "A: It will send you hourly reminders to drink a glass of water until you complete your daily drinking goal.",
            fontSize = 16.sp,
            color = Color(0xFF424242),
            modifier = Modifier.padding(bottom = 24.dp)
        )
        // Add more Q&A here in the future!
    }
}
