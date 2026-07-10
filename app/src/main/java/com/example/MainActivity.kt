package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.alarm.AlarmScheduler
import com.example.alarm.AlarmStateManager
import com.example.data.api.InferredSkill
import com.example.data.api.JournalAnalysisResult
import com.example.data.db.JournalEntry
import com.example.data.db.Skill
import com.example.data.db.SkillProgressHistory
import com.example.ui.JournalViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AlertLightRed
import com.example.ui.theme.AlertBorderRed
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate100
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.core.*
import java.text.SimpleDateFormat
import java.util.*

data class ScribeTier(val name: String, val color: Color, val progress: Float, val multiplier: String)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize & schedule the default alarm on first run
        AlarmScheduler.scheduleDailyAlarm(applicationContext)

        // 2. Request Notification Permission (Tiramisu+)
        requestNotificationPermission()

        setContent {
            MyApplicationTheme {
                val viewModel: JournalViewModel = viewModel()
                SkillJournalApp(viewModel)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }
}

@Composable
fun SkillJournalApp(viewModel: JournalViewModel) {
    val context = LocalContext.current
    val analysisResult by viewModel.analysisResult.collectAsState()

    // Check today's status whenever we open the app
    LaunchedEffect(Unit) {
        viewModel.checkTodayStatus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main Screen content
        MainScreenContent(viewModel)

        // 2. Analysis Result Dialog (Shows beautiful extracted results)
        if (analysisResult != null) {
            AnalysisResultDialog(
                result = analysisResult!!,
                onDismiss = { viewModel.clearAnalysisResult() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(viewModel: JournalViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val isTodayWritten by viewModel.isTodayWritten.collectAsState()
    val isRinging by viewModel.isAlarmRinging.collectAsState()
    val context = LocalContext.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compactNav = maxWidth < 380.dp

        Scaffold(
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { if (!compactNav) Text("Home", maxLines = 1, softWrap = false, fontSize = 10.sp) },
                    alwaysShowLabel = false,
                    modifier = Modifier.testTag("tab_dashboard")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Skills") },
                    label = { Text("Skills", maxLines = 1, softWrap = false, fontSize = 10.sp) },
                    alwaysShowLabel = false,
                    modifier = Modifier.testTag("tab_skills")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = "Buffs") },
                    label = { Text("Buffs", maxLines = 1, softWrap = false, fontSize = 10.sp) },
                    alwaysShowLabel = false,
                    modifier = Modifier.testTag("tab_buffs")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Build, contentDescription = "Inventory") },
                    label = { if (!compactNav) Text("Items", maxLines = 1, softWrap = false, fontSize = 10.sp) },
                    alwaysShowLabel = false,
                    modifier = Modifier.testTag("tab_inventory")
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Connections") },
                    label = { if (!compactNav) Text("People", maxLines = 1, softWrap = false, fontSize = 10.sp) },
                    alwaysShowLabel = false,
                    modifier = Modifier.testTag("tab_connections")
                )
                NavigationBarItem(
                    selected = selectedTab == 5,
                    onClick = { selectedTab = 5 },
                    icon = {
                        BadgedBox(badge = {
                            if (!isTodayWritten) {
                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                    Text("!", color = Color.White)
                                }
                            }
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Daily Journal")
                        }
                    },
                    label = { Text("Journal", maxLines = 1, softWrap = false, fontSize = 10.sp) },
                    alwaysShowLabel = false,
                    modifier = Modifier.testTag("tab_journal")
                )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    0 -> DashboardTab(viewModel, onNavigateToJournal = { selectedTab = 5 })
                    1 -> SkillsTab(viewModel)
                    2 -> BuffsTab(viewModel)
                    3 -> InventoryTab(viewModel)
                    4 -> ConnectionsTab(viewModel)
                    5 -> DailyJournalTab(viewModel)
                }

                // 1. Overlay for ACTIVE ringing alarm (Mandatory Takeover Screen)
                if (isRinging) {
                    AlarmActiveOverlay(
                        onSnooze = {
                            viewModel.snoozeAlarm()
                            Toast.makeText(context, "Alarm snoozed for 5 minutes", Toast.LENGTH_SHORT).show()
                        },
                        onDismissClicked = {
                            // Tapping Dismiss directs them to complete their journal entry
                            viewModel.stopAlarmSound()
                            selectedTab = 5 // Switch directly to Journal tab!
                            Toast.makeText(context, "Sound silenced! Complete your journal entry to permanently dismiss.", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardTab(viewModel: JournalViewModel, onNavigateToJournal: () -> Unit) {
    val isTodayWritten by viewModel.isTodayWritten.collectAsState()
    val entries by viewModel.journalEntries.collectAsState()
    val skillsList by viewModel.skills.collectAsState()
    val inventoryItems by viewModel.inventoryItems.collectAsState()
    val connectionsList by viewModel.connections.collectAsState()
    val buffsList by viewModel.allBuffs.collectAsState()
    
    val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    // RPG Calculations
    val heroLevel = (skillsList.sumOf { it.level } / 5) + 1
    val xpFraction = (skillsList.sumOf { it.level } % 5) / 5f

    val intellect = (skillsList.filter { it.category.lowercase() == "technical" || it.category.lowercase() == "creative" || it.category.lowercase() == "mindset" }.sumOf { it.level } * 10) + 100
    val stamina = (skillsList.filter { it.category.lowercase() == "fitness" }.sumOf { it.level } * 10) + (buffsList.filter { it.type == "BENEFIT" && it.aspectAffected.contains("Energy") }.size * 10) + 100
    val charisma = (connectionsList.sumOf { it.affinityLevel } * 10) + 100

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 380.dp
        val horizontalPadding = if (compact) 12.dp else 16.dp
        val sectionGap = if (compact) 10.dp else 16.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
        // 1. Hero Image / Visual Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 132.dp else 180.dp)
            ) {
                // Background generated image
                Image(
                    painter = painterResource(id = R.drawable.img_hero_banner_1782641152250),
                    contentDescription = "Personal Growth Banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Dark overlay gradient to make text super readable
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xAA0D0D19)),
                                startY = 100f
                            )
                        )
                )
                // App Title Heading with Glowing Avatar
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontalPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SKILL TRACKER & PROGRESS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "Daily Growth Journal",
                            fontSize = if (compact) 18.sp else 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Daily Quest Sync Active",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Gorgeous glowing circular Avatar
                    Box(
                        modifier = Modifier
                            .size(if (compact) 42.dp else 54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    colors = listOf(PrimaryIndigo, AccentCyan, PrimaryIndigo)
                                )
                            )
                            .padding(2.5.dp) // The glow thickness
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color(0xFF0F172A)), // dark slate background inside avatar
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⚡",
                                fontSize = 24.sp,
                                color = AccentCyan
                            )
                        }
                    }
                }
            }
        }

        // 2. Alarm Control Card & Quick Test Panel
        item {
            Spacer(modifier = Modifier.height(sectionGap))
            AlarmControlCard(viewModel)
        }

        // 3. Today's Mandatory Habit Status Card
        item {
            Spacer(modifier = Modifier.height(sectionGap))
            val hour by viewModel.alarmHour.collectAsState()
            val minute by viewModel.alarmMinute.collectAsState()
            TodayStatusCard(isTodayWritten, hour, minute, onNavigateToJournal)
        }

        // 3.5. Today's Active Quests (Task Management Widget)
        item {
            Spacer(modifier = Modifier.height(sectionGap))
            Box(modifier = Modifier.padding(horizontal = horizontalPadding)) {
                DailyTaskChecklist(
                    viewModel = viewModel,
                    dateStr = todayDateStr
                )
            }
        }

        // Character Sheet Stats Card
        item {
            Spacer(modifier = Modifier.height(sectionGap))
            CharacterSheetHeader(
                heroLevel = heroLevel,
                xpFraction = xpFraction,
                entriesCount = entries.size,
                skillsCount = skillsList.size,
                itemsCount = inventoryItems.size,
                connectionsCount = connectionsList.size,
                intellect = intellect,
                stamina = stamina,
                charisma = charisma
            )
        }

            if (!compact) {
                // Custom Canvas Charts for Seductive Visual Stats
                item {
                    Spacer(modifier = Modifier.height(sectionGap))
                    SeductiveStatsCanvasChart(entries = entries)
                }

                // Firebase Cloud Sync Settings & Control Card
                item {
                    Spacer(modifier = Modifier.height(sectionGap))
                    FirebaseSyncCard(viewModel = viewModel)
                }

                // 4. Tome of Knowledge Panel
                item {
                    Spacer(modifier = Modifier.height(sectionGap))
                    TomeOfKnowledgePanel()
                }
            }
        }
    }
}

@Composable
fun SkillsTab(viewModel: JournalViewModel) {
    val skills by viewModel.skills.collectAsState()
    val history by viewModel.history.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(PrimaryIndigo, Color(0xFF3B82F6), AccentCyan)
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Column {
                    Text(
                        text = "YOUR SKILLS PORTFOLIO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Inferred Skills",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Skills automatically built and leveled up by analyzing your daily journal logs.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (skills.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Skills",
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Skills Extracted Yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Your daily growth journals will trigger automated skill logging here once you complete an entry.",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        } else {
            items(skills) { skill ->
                SkillItemCard(skill, history.filter { it.skillName == skill.name })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkillItemCard(skill: Skill, updates: List<SkillProgressHistory>) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = skill.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = skill.category.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryIndigo,
                        letterSpacing = 1.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryIndigo.copy(alpha = 0.1f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Lvl ${skill.level}/10",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = PrimaryIndigo
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            // Skill progress bar
            LinearProgressIndicator(
                progress = { skill.level / 10f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = PrimaryIndigo,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = skill.description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            // Historical progress updates
            if (updates.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExpanded) "Hide History" else "Show History (${updates.size} updates)",
                        fontSize = 11.sp,
                        color = PrimaryIndigo,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (isExpanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    updates.forEach { update ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Milestone",
                                tint = AccentCyan,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = update.date,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Lvl ${update.previousLevel} → ${update.newLevel}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = AccentCyan
                                    )
                                }
                                Text(
                                    text = update.explanation,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JournalEntryCard(entry: JournalEntry, onEdit: () -> Unit, onDelete: () -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PrimaryIndigo.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Journal",
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = entry.date,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit entry",
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete entry",
                            tint = AlertRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = entry.summary,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Full Entry:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = PrimaryIndigo,
                    letterSpacing = 1.sp
                )
                Text(
                    text = entry.content,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Gemini Progress Analysis:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = AccentCyan,
                    letterSpacing = 1.sp
                )
                Text(
                    text = entry.progressAnalysis,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun TodayStatusCard(isWritten: Boolean, alarmHour: Int, alarmMinute: Int, onNavigateToJournal: () -> Unit) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    
    val infiniteTransition = rememberInfiniteTransition(label = "quest_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    if (isWritten) {
        val completedBg = if (isDark) Color(0xFF061F12) else Color(0xFFF0FDF4)
        val completedBorder = if (isDark) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFBBF7D0)
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = completedBg),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, completedBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF10B981), Color(0xFF34D399))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🏆 DAILY QUEST COMPLETE",
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        color = Color(0xFF10B981),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Writing Chronicles: Accomplished",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isDark) Color.White else Slate900
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Habit alarm silenced! +150 XP rewarded.",
                        fontSize = 12.sp,
                        color = if (isDark) Color(0xFFA7F3D0) else Color(0xFF065F46)
                    )
                }
            }
        }
    } else {
        val activeBg = if (isDark) Color(0xFF1C1313) else Color(0xFFFFF5F5)
        val activeBorderBrush = Brush.linearGradient(
            colors = listOf(
                AlertRed.copy(alpha = pulseAlpha),
                Color(0xFFF97316).copy(alpha = pulseAlpha)
            )
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = activeBg),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(2.dp, activeBorderBrush),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(AlertRed, Color(0xFFF97316))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Active Quest",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AlertRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CRITICAL DAILY QUEST",
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            color = AlertRed,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Harness the Habit",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isDark) Color.White else Slate900
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Write your daily journal entry to silence the alarm & earn XP!",
                        fontSize = 12.sp,
                        color = if (isDark) Color(0xFFFECACA) else Color(0xFF991B1B)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Button(
                        onClick = onNavigateToJournal,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryIndigo
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Log Today's Entry",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = String.format("%02d:%02d", alarmHour, alarmMinute),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = AlertRed
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "ALARM TIME",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmControlCard(viewModel: JournalViewModel) {
    val context = LocalContext.current
    val hour by viewModel.alarmHour.collectAsState()
    val minute by viewModel.alarmMinute.collectAsState()
    val isEnabled by viewModel.isAlarmEnabled.collectAsState()
    val isRinging by viewModel.isAlarmRinging.collectAsState()

    var showAdjuster by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 380.dp
        val outerPadding = if (compact) 12.dp else 16.dp
        val innerPadding = if (compact) 14.dp else 20.dp

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = outerPadding),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(innerPadding)) {
                FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PrimaryIndigo.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Alarm",
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "Daily Wake Alarm",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = String.format("Rings every day at %02d:%02d", hour, minute),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    }
                    Switch(
                    checked = isEnabled,
                    onCheckedChange = { viewModel.setAlarmEnabled(it) },
                    modifier = Modifier.testTag("alarm_switch")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val buttonModifier = if (compact) Modifier.fillMaxWidth() else Modifier.weight(1f)
                OutlinedButton(
                    onClick = { showAdjuster = !showAdjuster },
                    modifier = buttonModifier,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (showAdjuster) "Hide Config" else "Set Alarm Time", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        viewModel.triggerTestAlarm()
                        Toast.makeText(context, "Test Alarm triggered! App is ringing.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = buttonModifier,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Trigger Test Alarm", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (showAdjuster) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Adjust Daily Alarm Time:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Hour", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                val nextHour = if (hour == 0) 23 else hour - 1
                                viewModel.updateAlarmTime(nextHour, minute)
                            }) {
                                Text("-", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = String.format("%02d", hour),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(onClick = {
                                val nextHour = if (hour == 23) 0 else hour + 1
                                viewModel.updateAlarmTime(nextHour, minute)
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Increase Hour", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Minute", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                val nextMin = if (minute == 0) 55 else ((minute - 5) + 60) % 60
                                viewModel.updateAlarmTime(hour, nextMin)
                            }) {
                                Text("-", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = String.format("%02d", minute),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(onClick = {
                                val nextMin = if (minute == 55) 0 else (minute + 5) % 60
                                viewModel.updateAlarmTime(hour, nextMin)
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Increase Minute", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DailyJournalTab(viewModel: JournalViewModel) {
    val isTodayWritten by viewModel.isTodayWritten.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val editingEntry by viewModel.editingEntry.collectAsState()
    val aiQuestions by viewModel.aiQuestions.collectAsState()
    val context = LocalContext.current

    var journalText by remember { mutableStateOf("") }
    var simulationMode by remember { mutableStateOf(false) }
    var forceShowWriteForm by remember { mutableStateOf(false) }
    var zenMode by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) }

    val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    LaunchedEffect(editingEntry) {
        if (editingEntry != null) {
            journalText = editingEntry!!.content
        } else {
            journalText = ""
        }
    }

    val displayDate = editingEntry?.date ?: todayDateStr
    val displayTitle = if (editingEntry != null) "Edit Growth Entry" else "Daily Growth Entry"
    val showForm = editingEntry != null || !isTodayWritten || forceShowWriteForm

    val wordCount = remember(journalText) { journalText.split(Regex("\\s+")).filter { it.isNotBlank() }.size }
    val charCount = remember(journalText) { journalText.length }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 380.dp
        val pagePadding = if (compact) 12.dp else 16.dp
        val cardPadding = if (compact) 14.dp else 20.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pagePadding)
                .verticalScroll(rememberScrollState())
        ) {
        Text(
            text = displayTitle,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Date: $displayDate",
            fontSize = 13.sp,
            color = PrimaryIndigo,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 2.0.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Daily task checklist integrated with the growth entry
        DailyTaskChecklist(
            viewModel = viewModel,
            dateStr = displayDate,
            onAppendToJournal = { taskSummary ->
                if (!journalText.contains(taskSummary.trim())) {
                    journalText = journalText + taskSummary
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (!showForm) {
            val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
            val completedCardBg = if (isDarkTheme) Color(0xFF0F241A) else Color(0xFFF0FDF4)
            val completedCardBorder = if (isDarkTheme) Color(0xFF1B5E20).copy(alpha = 0.5f) else Color(0xFFDCFCE7)
            val completedTextTitle = if (isDarkTheme) Color(0xFF81C784) else Color(0xFF14532D)
            val completedTextDesc = if (isDarkTheme) Color(0xFFA5D6A7) else Color(0xFF15803D)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                colors = CardDefaults.cardColors(containerColor = completedCardBg),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, completedCardBorder)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "You are all set for today!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = completedTextTitle
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your skills have been extracted, your daily summary saved, and today's alarm has been dismissed. Check the Dashboard to review your progress!",
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = completedTextDesc,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { forceShowWriteForm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("write_another_entry_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = "Add Icon", tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Write Another Entry", color = Color.White)
                        }
                    }
                }
            }
        } else {
            val infiniteTransition = rememberInfiniteTransition(label = "zen_glow")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "glow_alpha"
            )

            val zenBorderBrush = Brush.linearGradient(
                colors = listOf(
                    PrimaryIndigo.copy(alpha = pulseAlpha),
                    AccentCyan.copy(alpha = pulseAlpha)
                )
            )

            val zenBorder = if (zenMode) {
                BorderStroke(1.5.dp, zenBorderBrush)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                colors = CardDefaults.cardColors(
                    containerColor = if (zenMode) {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                shape = RoundedCornerShape(24.dp),
                border = zenBorder
            ) {
                Column(modifier = Modifier.padding(cardPadding)) {
                    // Header Row with Title and Zen Toggle
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (zenMode) "🧘 Zen Writing Sanctum" else "✍️ Daily Growth Journal",
                            fontSize = if (compact) 14.sp else 15.sp,
                            fontWeight = FontWeight.Black,
                            color = if (zenMode) AccentCyan else PrimaryIndigo
                        )

                        Card(
                            modifier = Modifier
                                .clickable { zenMode = !zenMode }
                                .testTag("zen_mode_toggle"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (zenMode) AccentCyan.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (zenMode) AccentCyan else MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (zenMode) Icons.Default.CheckCircle else Icons.Default.FavoriteBorder,
                                    contentDescription = "Zen mode toggle",
                                    tint = if (zenMode) AccentCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Zen Focus",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (zenMode) AccentCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Elegant Tab Switcher
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val tabs = listOf("✍️ Write & Assist", "📖 Live Scroll Preview")
                        tabs.forEachIndexed { index, title ->
                            val selected = activeTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) {
                                            if (zenMode) AccentCyan.copy(alpha = 0.25f) else PrimaryIndigo.copy(alpha = 0.2f)
                                        } else Color.Transparent
                                    )
                                    .clickable { activeTab = index }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (compact) title.substringAfter(' ') else title,
                                    fontSize = if (compact) 11.sp else 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) {
                                        if (zenMode) AccentCyan else PrimaryIndigo
                                    } else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Dynamic Real-Time Skill Synergy Indicator
                    val lowerText = journalText.lowercase()
                    val activeSynergies = remember(journalText) {
                        val list = mutableListOf<Pair<String, String>>() // Name to category
                        if (lowerText.contains("program") || lowerText.contains("code") || lowerText.contains("develop") || lowerText.contains("kotlin") || lowerText.contains("java") || lowerText.contains("android")) {
                            list.add("💻 Software Engineering" to "Technical")
                        }
                        if (lowerText.contains("learn") || lowerText.contains("read") || lowerText.contains("study") || lowerText.contains("skill")) {
                            list.add("📚 Continuous Learning" to "Mindset")
                        }
                        if (lowerText.contains("exercise") || lowerText.contains("run") || lowerText.contains("gym") || lowerText.contains("workout") || lowerText.contains("health") || lowerText.contains("sport")) {
                            list.add("🏋️ Physical Fitness" to "Fitness")
                        }
                        if (lowerText.contains("feel") || lowerText.contains("happy") || lowerText.contains("sad") || lowerText.contains("anxious") || lowerText.contains("stress") || lowerText.contains("calm") || lowerText.contains("meditat")) {
                            list.add("🧘 Emotional Intelligence" to "Mindset")
                        }
                        if (lowerText.contains("work") || lowerText.contains("project") || lowerText.contains("task") || lowerText.contains("manage")) {
                            list.add("🚀 Productivity & Execution" to "Technical")
                        }
                        if (list.isEmpty() && lowerText.isNotBlank()) {
                            list.add("🔍 Self-Reflection" to "Mindset")
                        }
                        list
                    }

                    if (activeSynergies.isNotEmpty() && !zenMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Synergizing:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            activeSynergies.forEach { (name, category) ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (category == "Technical") PrimaryIndigo.copy(alpha = 0.12f) else AccentCyan.copy(alpha = 0.12f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(0.5.dp, if (category == "Technical") PrimaryIndigo.copy(alpha = 0.3f) else AccentCyan.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (category == "Technical") PrimaryIndigo else AccentCyan)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = name,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (category == "Technical") PrimaryIndigo else AccentCyan
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Render TAB 1: Live Scroll Preview
                    if (activeTab == 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (zenMode) 340.dp else 260.dp)
                                .background(
                                    if (androidx.compose.foundation.isSystemInDarkTheme()) {
                                        Color(0xFF1E1E2C)
                                    } else {
                                        Color(0xFFFCF8F2) // Vintage parchment color
                                    },
                                    RoundedCornerShape(16.dp)
                                )
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (zenMode) AccentCyan.copy(alpha = 0.4f) else PrimaryIndigo.copy(alpha = 0.2f)
                                    ),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (journalText.isBlank()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Your Adventure Scroll is empty.\nType something in the 'Write & Assist' tab!",
                                        textAlign = TextAlign.Center,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val lines = journalText.split("\n")
                                    lines.forEach { line ->
                                        when {
                                            line.startsWith("# ") -> {
                                                val clean = line.removePrefix("# ").trim()
                                                Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                                                    Text(
                                                        text = clean,
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = if (zenMode) AccentCyan else PrimaryIndigo
                                                    )
                                                    HorizontalDivider(
                                                        modifier = Modifier.padding(top = 4.dp),
                                                        thickness = 1.dp,
                                                        color = (if (zenMode) AccentCyan else PrimaryIndigo).copy(alpha = 0.25f)
                                                    )
                                                }
                                            }
                                            line.startsWith("## ") -> {
                                                val clean = line.removePrefix("## ").trim()
                                                Text(
                                                    text = clean,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (zenMode) AccentCyan else PrimaryIndigo,
                                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                                )
                                            }
                                            line.startsWith("- [x] ") || line.startsWith("- [X] ") -> {
                                                val clean = line.substring(6).trim()
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(vertical = 1.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Completed",
                                                        tint = Color(0xFF10B981),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = clean,
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                        style = androidx.compose.ui.text.TextStyle(
                                                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                                                        )
                                                    )
                                                }
                                            }
                                            line.startsWith("- [ ] ") -> {
                                                val clean = line.substring(6).trim()
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(vertical = 1.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.FavoriteBorder,
                                                        contentDescription = "Pending",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = clean,
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                            line.startsWith("- ") || line.startsWith("• ") -> {
                                                val clean = if (line.startsWith("- ")) line.removePrefix("- ").trim() else line.removePrefix("• ").trim()
                                                Row(
                                                    modifier = Modifier.padding(start = 4.dp).padding(vertical = 1.dp),
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    Text(
                                                        text = "•",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (zenMode) AccentCyan else PrimaryIndigo,
                                                        modifier = Modifier.padding(end = 6.dp)
                                                    )
                                                    Text(
                                                        text = clean,
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        lineHeight = 18.sp
                                                    )
                                                }
                                            }
                                            line.startsWith("> ") -> {
                                                val clean = line.removePrefix("> ").trim()
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 4.dp)
                                                        .padding(vertical = 2.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(3.dp)
                                                            .height(32.dp)
                                                            .background(if (zenMode) AccentCyan.copy(alpha = 0.6f) else PrimaryIndigo.copy(alpha = 0.6f))
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = clean,
                                                        fontSize = 13.sp,
                                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        lineHeight = 18.sp
                                                    )
                                                }
                                            }
                                            else -> {
                                                if (line.isNotBlank()) {
                                                    Text(
                                                        text = line,
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        lineHeight = 18.sp
                                                    )
                                                } else {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Render TAB 0: Write & Assist (Prompts, speed prefixes, text formatting, text field)
                    if (activeTab == 0) {
                        if (zenMode) {
                            val zenQuotes = listOf(
                                "\"Silence is the canvas of the mind. Express today's victories freely.\"",
                                "\"Your growth is recorded word by word, task by task.\"",
                                "\"Take a breath. Reflect deeply on what you built, learned, or overcome.\"",
                                "\"Every legend was once a novice logging their daily practice.\"",
                                "\"Write with intention. Your future self will study this archive.\""
                            )
                            val selectedQuote = remember(displayDate) { zenQuotes.random() }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = AccentCyan.copy(alpha = 0.05f)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.2f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = selectedQuote,
                                        fontSize = 12.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Record your accomplishments, studied topics, work projects, emotional thoughts, exercises, or any activity. The more detail you enter, the richer the skills Gemini extracts!",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Prompts Section
                        if (aiQuestions.isNotEmpty() && !zenMode) {
                            Text(
                                text = "💡 Gemini's Daily Growth Prompts",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = PrimaryIndigo,
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                            )
                            Text(
                                text = "Tap a prompt to insert it instantly as a helper template:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                aiQuestions.forEach { question ->
                                    Card(
                                        modifier = Modifier
                                            .width(220.dp)
                                            .clickable {
                                                val promptText = "\n\n*Prompt: ${question.question}*\nAnswer: "
                                                if (!journalText.contains(question.question)) {
                                                    journalText = journalText + promptText
                                                }
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = AccentCyan,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Growth Prompt",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PrimaryIndigo
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = question.question,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                lineHeight = 15.sp,
                                                maxLines = 2,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Fast-Typing Action Chips Row
                        if (!zenMode) {
                            Text(
                                text = "⚡ Speed-Dial Action Prefixes",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = PrimaryIndigo,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = "Tap a speed chip to instantly insert a starter sentence block:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            val quickTags = listOf(
                                "💻 Code" to "I coded in Kotlin today, working on ",
                                "📚 Study" to "I studied concepts regarding ",
                                "🏋️ Gym" to "I did a physical workout, exercises included ",
                                "🧘 Mindfulness" to "Spent some time meditating, focusing on ",
                                "🚀 Quests" to "Accomplished several key tasks today: ",
                                "💡 Discovery" to "Had an inspiring breakthrough/idea about ",
                                "🌱 Habits" to "Invested time in my personal development, ",
                                "🧠 Review" to "Reflected on today's events, specifically "
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                quickTags.forEach { (label, phrase) ->
                                    Card(
                                        modifier = Modifier
                                            .clickable {
                                                val delimiter = if (journalText.isBlank() || journalText.endsWith(" ") || journalText.endsWith("\n")) "" else " "
                                                if (!journalText.contains(phrase)) {
                                                    journalText = journalText + delimiter + phrase
                                                }
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = PrimaryIndigo.copy(alpha = 0.08f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, PrimaryIndigo.copy(alpha = 0.15f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryIndigo)
                                        }
                                    }
                                }
                            }
                        }

                        // Markdown Formatting Helper Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Format:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val formatBtns = listOf(
                                "H1" to "# ",
                                "H2" to "## ",
                                "• List" to "- ",
                                "✔ Check" to "- [ ] ",
                                "\"" to "> "
                            )

                            formatBtns.forEach { (label, prefix) ->
                                Card(
                                    modifier = Modifier
                                        .clickable {
                                            val lastChar = if (journalText.isEmpty() || journalText.endsWith("\n")) "" else "\n"
                                            journalText = journalText + lastChar + prefix
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryIndigo,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Card(
                                modifier = Modifier
                                    .clickable { journalText = "" },
                                colors = CardDefaults.cardColors(
                                    containerColor = AlertLightRed
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, AlertBorderRed)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear", tint = AlertRed, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AlertRed)
                                }
                            }
                        }

                         // Editor Text Field
                        OutlinedTextField(
                            value = journalText,
                            onValueChange = { journalText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (zenMode) 300.dp else if (compact) 180.dp else 220.dp)
                                .testTag("journal_input"),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                autoCorrect = true,
                                imeAction = ImeAction.Default
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = if (zenMode) 16.sp else 14.sp,
                                lineHeight = if (zenMode) 24.sp else 20.sp
                            ),
                            placeholder = {
                                Text(
                                    text = if (zenMode) "🧘 Write freely in the quiet... Tell your story..." else "✍️ What did you work on or learn today?",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = if (zenMode) 16.sp else 14.sp
                                )
                            },
                            supportingText = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (zenMode) "🧘 Distraction-Free Sanctum" else "💡 Try writing 25+ words for bonus multiplier!",
                                        fontSize = 10.sp,
                                        color = if (zenMode) AccentCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "$wordCount w | $charCount c",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (wordCount >= 60) Color(0xFF10B981) else if (wordCount >= 25) PrimaryIndigo else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            maxLines = 25,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = if (zenMode) AccentCyan.copy(alpha = 0.02f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                focusedBorderColor = if (zenMode) AccentCyan else PrimaryIndigo,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                            ),
                            enabled = !isAnalyzing
                        )
                    }

                    val scribeTier = when {
                        wordCount == 0 -> ScribeTier("Empty Log", MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), 0.05f, "1.0x Base")
                        wordCount < 10 -> ScribeTier("Initiate's Note (Needs ${10 - wordCount} more words)", AlertRed, (wordCount / 10f) * 0.25f, "1.0x Base")
                        wordCount < 25 -> ScribeTier("Commoner's Log (Basic Skill Extraction)", AccentCyan, 0.25f + ((wordCount - 10) / 15f) * 0.25f, "1.2x Boost")
                        wordCount < 60 -> ScribeTier("Epic Chronicle (Rich Skill Extraction)", PrimaryIndigo, 0.5f + ((wordCount - 25) / 35f) * 0.3f, "1.5x Premium")
                        else -> ScribeTier("Legendary Saga (Maximum XP Potential! 🏆)", Color(0xFF10B981), 1f, "2.0x Godlike")
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(scribeTier.color)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Scribe Level: ${scribeTier.name}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = scribeTier.color
                                )
                            }
                            
                            // Multiplier Badge
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = scribeTier.color.copy(alpha = 0.15f)
                                ),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.5.dp, scribeTier.color.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = scribeTier.multiplier,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = scribeTier.color,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "XP progress to next tier",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$wordCount words | $charCount chars",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        LinearProgressIndicator(
                            progress = { scribeTier.progress },
                            color = scribeTier.color,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = simulationMode,
                            onCheckedChange = { simulationMode = it },
                            enabled = !isAnalyzing,
                            colors = CheckboxDefaults.colors(checkedColor = PrimaryIndigo)
                        )
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(
                                "Offline Simulation Mode",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Bypasses Gemini API request to extract skills locally.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (isAnalyzing) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = PrimaryIndigo)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Gemini is analyzing your skills...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = PrimaryIndigo
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                if (journalText.trim().length < 10) {
                                    Toast.makeText(context, "Please write a slightly longer entry (min 10 chars)", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.submitJournalEntry(
                                        content = journalText,
                                        dateStr = displayDate,
                                        isSimulation = simulationMode,
                                        editingEntryId = editingEntry?.id
                                    )
                                    forceShowWriteForm = false
                                    viewModel.cancelEditing()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("save_journal_button"),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                        ) {
                            Text(
                                text = if (editingEntry != null) "Update Entry & Re-analyze" else "Analyze Entry & Stop Alarm",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }

                        if (editingEntry != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { viewModel.cancelEditing() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(26.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Text("Cancel Editing", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        // --- History Section ---
        val journalEntries by viewModel.journalEntries.collectAsState()
        if (journalEntries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Your Daily Progress History",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            journalEntries.forEach { entry ->
                JournalEntryCard(
                    entry = entry,
                    onEdit = { 
                        viewModel.startEditing(entry)
                        Toast.makeText(context, "Editing entry for ${entry.date}", Toast.LENGTH_SHORT).show()
                    },
                    onDelete = { 
                        viewModel.deleteJournal(entry.id)
                        Toast.makeText(context, "Deleted entry for ${entry.date}", Toast.LENGTH_SHORT).show()
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun AlarmActiveOverlay(onSnooze: () -> Unit, onDismissClicked: () -> Unit) {
    // High-intensity full screen modal takeover screen with Slate900 styling
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900.copy(alpha = 0.96f))
            .clickable(enabled = false) {}, // eat taps
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = AlertLightRed),
            border = BorderStroke(1.dp, AlertBorderRed),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Flashing notification-like alarm clock visual
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(AlertRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Ringing!",
                        tint = AlertRed,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "DAILY HABIT ALARM!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF7F1D1D), // dark red
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "The alarm will continue to ring and won't stop until you write your daily growth journal entry! Keep your skills and habits growing daily.",
                    fontSize = 13.sp,
                    color = Color(0xFF991B1B), // medium red
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onSnooze,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("alarm_snooze_button"),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5)), // light red border
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF991B1B))
                    ) {
                        Text("Snooze (5m)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onDismissClicked,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("alarm_dismiss_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                    ) {
                        Text("Silence & Write", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AnalysisResultDialog(result: JournalAnalysisResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = PrimaryIndigo,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Entry Analyzed Successfully!", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Slate900)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Summary of reflection:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = PrimaryIndigo,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(result.summary, fontSize = 13.sp, color = Slate600, lineHeight = 18.sp)
                }

                item {
                    Text(
                        "Progress & Growth Analysis:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = AccentCyan,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(result.progressAnalysis, fontSize = 13.sp, color = Slate600, lineHeight = 18.sp)
                }

                item {
                    Text(
                        "Extracted Skills:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = PrimaryIndigo,
                        letterSpacing = 1.sp
                    )
                }

                items(result.skillsInferred) { skill ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Slate200)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    skill.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Slate900
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(PrimaryIndigo.copy(alpha = 0.1f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "Lvl ${skill.level}/10",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        color = PrimaryIndigo
                                    )
                                }
                            }
                            Text(skill.description, fontSize = 12.sp, color = Slate600, modifier = Modifier.padding(top = 4.dp), lineHeight = 16.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(skill.progressExplanation, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Amazing, thanks!", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun BuffsTab(viewModel: JournalViewModel) {
    val buffs by viewModel.allBuffs.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(PrimaryIndigo, Color(0xFF3B82F6), AccentCyan)
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Column {
                    Text(
                        text = "REAL-LIFE BENEFITS & HARMS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Real-Life Effects Tracker",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Analyzes your physical and mental state as logged in your daily journals.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (buffs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Buffs",
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Real-Life Effects Analyzed Yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Log benefits (e.g. chamomile calm) or harms (e.g. lack of sleep) in your journals to track them here.",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        } else {
            val benefits = buffs.filter { it.type.uppercase() == "BENEFIT" }
            val harms = buffs.filter { it.type.uppercase() == "HARM" }

            if (benefits.isNotEmpty()) {
                item {
                    Text(
                        text = "🟢 Positive Benefits Logged",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                items(benefits) { buff ->
                    BuffItemCard(buff = buff, onDelete = { viewModel.removeBuff(buff.id) })
                }
            }

            if (harms.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "🔴 Negative Factors / Harms Tracked",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                items(harms) { buff ->
                    BuffItemCard(buff = buff, onDelete = { viewModel.removeBuff(buff.id) })
                }
            }
        }
    }
}

@Composable
fun BuffItemCard(buff: com.example.data.db.Buff, onDelete: () -> Unit) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isBenefit = buff.type.uppercase() == "BENEFIT"

    val themeColor = if (isBenefit) {
        if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
    } else {
        if (isDark) Color(0xFFE57373) else Color(0xFFC62828)
    }

    val bgColor = if (isBenefit) {
        if (isDark) Color(0xFF0F241A) else Color(0xFFE8F5E9)
    } else {
        if (isDark) Color(0xFF2D1F1F) else Color(0xFFFFEBEE)
    }

    val borderColor = if (isBenefit) {
        if (isDark) Color(0xFF1B5E20).copy(alpha = 0.5f) else Color(0xFFA5D6A7)
    } else {
        if (isDark) Color(0xFFC62828).copy(alpha = 0.5f) else Color(0xFFEF9A9A)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = buff.aspectAffected.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = themeColor
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Intensity: ${buff.intensity}/10",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = buff.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = buff.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = "Logged on: ${buff.date}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun InventoryTab(viewModel: JournalViewModel) {
    val items by viewModel.inventoryItems.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(PrimaryIndigo, Color(0xFF3B82F6), AccentCyan)
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Column {
                    Text(
                        text = "REAL-WORLD PHYSICAL INVENTORY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Equipment & Assets",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Owns and measures real equipment, specification buffs, and upgrades tracked from daily text entries.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (items.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Assets",
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Physical Equipment Logged Yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Mention real-world equipment (e.g. upgraded to an i5 computer) in your journal entries to automatically analyze specs and track buffs.",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        } else {
            items(items) { item ->
                InventoryItemCard(item = item, onDelete = { viewModel.deleteInventoryItem(item.id) })
            }
        }
    }
}

@Composable
fun InventoryItemCard(item: com.example.data.db.InventoryItem, onDelete: () -> Unit) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val statusColor = when (item.status.uppercase()) {
        "ACTIVE" -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
        "UPGRADED" -> PrimaryIndigo
        "SOLD" -> if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
        "DESTROYED" -> if (isDark) Color(0xFFE57373) else Color(0xFFDC2626)
        "UNUSABLE" -> if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
        else -> if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
    }
    val statusBg = when (item.status.uppercase()) {
        "ACTIVE" -> if (isDark) Color(0xFF0F241A) else Color(0xFFE8F5E9)
        "UPGRADED" -> PrimaryIndigo.copy(alpha = 0.1f)
        "SOLD" -> if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
        "DESTROYED" -> if (isDark) Color(0xFF2D1F1F) else Color(0xFFFFEBEE)
        "UNUSABLE" -> if (isDark) Color(0xFF2D231F) else Color(0xFFFFF3E0)
        else -> if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(statusBg)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = item.status.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                        if (item.statusDetails.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.statusDetails,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Specification: ${item.specification}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryIndigo,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Item",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "CALCULATED BUFF RATING",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "${item.buffRating}/10",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = PrimaryIndigo
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                item.buffRating >= 9 -> "🔥 Godlike Spec"
                                item.buffRating >= 7 -> "⚡ Premium Upgrade"
                                item.buffRating >= 4 -> "✨ Efficient baseline"
                                else -> "💤 Low baseline"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentCyan
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        text = "SPECIFICATION BENEFIT EFFECT:",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryIndigo
                    )
                    Text(
                        text = item.specificationBenefit,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 2.dp),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Added: ${item.dateAdded}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Updated: ${item.lastUpdatedDate}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TomeOfKnowledgePanel() {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PrimaryIndigo.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Tome of Knowledge",
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Tome of Knowledge (System Guide)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "How specification analysis & real-world logs work",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand Guide",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                // Section 1: How does Local/Cloud AI analysis work?
                Text(
                    text = "🧠 Real-World Specification Analysis",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = PrimaryIndigo
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "When you write your daily growth journal, our AI engine (Google Gemini API with fallbacks) or Local Heuristic scanner parses your text to identify real physical items you purchased, owned, upgraded, sold, or lost. It reads technical details (like 'Intel Core i5' vs 'Apple M3 Max') and automatically ranks the item specification rating on a 1-10 scale, applying a corresponding custom real-life productivity buff!",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                // Section 2: Benefits & Harms Tracking
                Text(
                    text = "📈 Benefits and Harms logging",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = PrimaryIndigo
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "If something benefitted you in the real world (e.g. meditating, sleeping 8 hours, reading, chamomile tea), the system extracts it as a positive BENEFIT showing how much it helped. If something gave unbeneficial side-effects (e.g. coffee jitters, severe anxiety, 4 hours of screen fatigue), it logs that too as a HARM with an intensity scale, tracking real-life outcomes from your daily journaling.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                // Section 3: Sample Diary Entries
                Text(
                    text = "✍️ Example Diary Sentences",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = PrimaryIndigo
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Try writing things like:\n" +
                            "• \"I upgraded my workstation to an Apple M3 Max computer today.\"\n" +
                            "• \"I sold my old workstation which had an Intel Core i3 processor.\"\n" +
                            "• \"My old computer was destroyed when I spilled coffee over it.\"\n" +
                            "• \"Had an amazing sleep last night, giving me great energy.\"\n" +
                            "• \"Staring at my monitor for 6 hours gave me severe screen fatigue.\"",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun CharacterSheetHeader(
    heroLevel: Int,
    xpFraction: Float,
    entriesCount: Int,
    skillsCount: Int,
    itemsCount: Int,
    connectionsCount: Int,
    intellect: Int,
    stamina: Int,
    charisma: Int
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.5.dp, if (isDark) PrimaryIndigo.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Block
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "HERO CHARACTER SHEET",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = AccentCyan,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Level $heroLevel Self-Reflector",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(PrimaryIndigo.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBox,
                        contentDescription = "Character Class",
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // XP Bar Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isDark) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "XP Progress to Next Level",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(xpFraction * 100).toInt()}/100 XP (${(xpFraction * 100).toInt()}%)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = PrimaryIndigo
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { xpFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = PrimaryIndigo,
                    trackColor = if (isDark) Color(0xFF334155) else MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(20.dp))

            // Stats row (Intellect, Stamina, Charisma)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // INTELLECT Card
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (isDark) Color(0xFF1E293B).copy(alpha = 0.4f) else Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(
                            1.dp, 
                            if (isDark) Color(0xFF334155) else Slate200, 
                            RoundedCornerShape(16.dp)
                        )
                        .padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(AccentCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Intellect Icon",
                            tint = AccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "🧠 INTELLECT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "$intellect",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Mini stat indicator
                    LinearProgressIndicator(
                        progress = { minOf(intellect / 15f, 1f) },
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(CircleShape),
                        color = AccentCyan,
                        trackColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Learning Spec",
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }

                // STAMINA Card
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (isDark) Color(0xFF1E293B).copy(alpha = 0.4f) else Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(
                            1.dp, 
                            if (isDark) Color(0xFF334155) else Slate200, 
                            RoundedCornerShape(16.dp)
                        )
                        .padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Stamina Icon",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "⚡ STAMINA",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "$stamina",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Mini stat indicator
                    LinearProgressIndicator(
                        progress = { minOf(stamina / 15f, 1f) },
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(CircleShape),
                        color = Color(0xFFEF4444),
                        trackColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Fitness Perks",
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }

                // CHARISMA Card
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (isDark) Color(0xFF1E293B).copy(alpha = 0.4f) else Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(
                            1.dp, 
                            if (isDark) Color(0xFF334155) else Slate200, 
                            RoundedCornerShape(16.dp)
                        )
                        .padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEAB308).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Charisma Icon",
                            tint = Color(0xFFEAB308),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "🌸 CHARISMA",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "$charisma",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Mini stat indicator
                    LinearProgressIndicator(
                        progress = { minOf(charisma / 15f, 1f) },
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(CircleShape),
                        color = Color(0xFFEAB308),
                        trackColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Active Nodes",
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(18.dp))

            // Stat counters grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatCounter(title = "Entries Written", count = entriesCount)
                StatCounter(title = "Skills Tracked", count = skillsCount)
                StatCounter(title = "Equipped Specs", count = itemsCount)
                StatCounter(title = "Social Nodes", count = connectionsCount)
            }
        }
    }
}

@Composable
fun StatCounter(title: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "$count",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryIndigo
        )
    }
}

@Composable
fun SeductiveStatsCanvasChart(entries: List<JournalEntry>) {
    val last7Entries = entries.take(7).reversed()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "REFLECTION VOLUMETRICS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AccentCyan,
                letterSpacing = 1.sp
            )
            Text(
                text = "Writing Focus Trend (Word Counts)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (last7Entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Write a few journal entries to start charting your focus metrics!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val wordCounts = last7Entries.map { it.content.split("\\s+".toRegex()).size }
                val maxCount = maxOf(25, wordCounts.maxOrNull() ?: 25)

                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    val paddingLeft = 40f
                    val paddingRight = 40f
                    val paddingTop = 20f
                    val paddingBottom = 40f

                    val chartWidth = canvasWidth - paddingLeft - paddingRight
                    val chartHeight = canvasHeight - paddingTop - paddingBottom

                    val barCount = last7Entries.size
                    val spacing = if (barCount > 1) chartWidth / (barCount - 1) else chartWidth

                    // Draw grid guidelines
                    for (i in 0..2) {
                        val gridY = paddingTop + (chartHeight * (i / 2f))
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.1f),
                            start = androidx.compose.ui.geometry.Offset(paddingLeft, gridY),
                            end = androidx.compose.ui.geometry.Offset(canvasWidth - paddingRight, gridY),
                            strokeWidth = 1f
                        )
                    }

                    // Draw the connection line (sleek area trend)
                    val points = last7Entries.mapIndexed { index, _ ->
                        val count = wordCounts[index]
                        val x = paddingLeft + (index * spacing)
                        val y = paddingTop + chartHeight - (chartHeight * (count.toFloat() / maxCount))
                        androidx.compose.ui.geometry.Offset(x, y)
                    }

                    if (points.size > 1) {
                        // Draw smooth translucent area fill under the trend line
                        val fillPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(points.first().x, paddingTop + chartHeight)
                            points.forEach { pt ->
                                lineTo(pt.x, pt.y)
                            }
                            lineTo(points.last().x, paddingTop + chartHeight)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(PrimaryIndigo.copy(alpha = 0.35f), Color.Transparent),
                                startY = points.map { it.y }.minOrNull() ?: paddingTop,
                                endY = paddingTop + chartHeight
                            )
                        )

                        for (i in 0 until points.size - 1) {
                            drawLine(
                                color = PrimaryIndigo,
                                start = points[i],
                                end = points[i + 1],
                                strokeWidth = 3.dp.toPx()
                            )
                        }
                    }

                    // Draw anchor circles
                    points.forEachIndexed { index, pt ->
                        drawCircle(
                            color = AccentCyan,
                            radius = 5.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = PrimaryIndigo,
                            radius = 3.dp.toPx(),
                            center = pt
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // X-Axis Labels
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    last7Entries.forEach { entry ->
                        val label = try {
                            val parts = entry.date.split("-")
                            if (parts.size >= 3) "${parts[1]}/${parts[2]}" else entry.date
                        } catch (e: Exception) {
                            entry.date
                        }
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionsTab(viewModel: JournalViewModel) {
    val connections by viewModel.connections.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(PrimaryIndigo, Color(0xFF3B82F6), AccentCyan)
                        )
                    )
                    .padding(24.dp)
            ) {
                Text(
                    text = "SOCIAL DYNAMICS & NODES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Personal Connections",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Mention real-life interactions (friends, family, coworkers) in your daily journal to map social nodes, track relationship affinity, and record key interactions.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Active Relationship Grid (${connections.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (connections.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(PrimaryIndigo.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Empty relationships",
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Inferred Connections Yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Write in your daily journal and describe social moments! Try sentences like: \n\n" +
                                "• \"Had an amazing coffee chat with Alice about software development.\"\n" +
                                "• \"Taught Bob some awesome tips on React coding.\"\n" +
                                "• \"Helped Mom plan her next trip, spent beautiful family time.\"",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                connections.forEach { connection ->
                    val badgeColor = when (connection.relationType.lowercase()) {
                        "family" -> Color(0xFFFEE2E2)
                        "coworker" -> Color(0xFFE0F2FE)
                        "mentee", "mentor" -> Color(0xFFFDF0D5)
                        else -> Color(0xFFFEF9C3)
                    }
                    val badgeTextColor = when (connection.relationType.lowercase()) {
                        "family" -> Color(0xFF991B1B)
                        "coworker" -> Color(0xFF075985)
                        "mentee", "mentor" -> Color(0xFF9A3412)
                        else -> Color(0xFF854D0E)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("connection_item_${connection.id}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryIndigo.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                val avatarIcon = when (connection.relationType.lowercase()) {
                                    "family" -> Icons.Default.Favorite
                                    "coworker" -> Icons.Default.Build
                                    else -> Icons.Default.Person
                                }
                                Icon(
                                    imageVector = avatarIcon,
                                    contentDescription = "Relation Icon",
                                    tint = PrimaryIndigo,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = connection.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = badgeColor),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = connection.relationType,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = badgeTextColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Affinity: ",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    for (i in 1..10) {
                                        val tint = if (i <= connection.affinityLevel) Color(0xFFEF4444) else Color.LightGray.copy(alpha = 0.4f)
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = "Affinity Node",
                                            tint = tint,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "Latest: ${connection.specification}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = "Last interacted: ${connection.lastInteractionDate}",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryIndigo
                                )
                            }

                            IconButton(
                                onClick = {
                                    viewModel.deleteConnection(connection.id)
                                    Toast.makeText(context, "Pruned connection Node: ${connection.name}", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Connection",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirebaseSyncCard(viewModel: JournalViewModel) {
    val context = LocalContext.current
    val dbUrl by viewModel.firebaseDbUrl.collectAsState()
    val syncKey by viewModel.firebaseSyncKey.collectAsState()
    val isAutoSync by viewModel.isAutoSyncEnabled.collectAsState()
    val syncState by viewModel.syncState.collectAsState()

    var showConfig by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    var urlInput by remember(dbUrl) { mutableStateOf(dbUrl) }
    var keyInput by remember(syncKey) { mutableStateOf(syncKey) }
    var autoSyncInput by remember(isAutoSync) { mutableStateOf(isAutoSync) }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    LaunchedEffect(syncState) {
        when (syncState) {
            is JournalViewModel.SyncState.Success -> {
                Toast.makeText(context, (syncState as JournalViewModel.SyncState.Success).message, Toast.LENGTH_LONG).show()
                viewModel.resetSyncState()
            }
            is JournalViewModel.SyncState.Error -> {
                Toast.makeText(context, (syncState as JournalViewModel.SyncState.Error).message, Toast.LENGTH_LONG).show()
                viewModel.resetSyncState()
            }
            else -> {}
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AccentCyan.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Cloud Storage Sync",
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Cloud Sync & Backup",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (syncKey.isBlank()) "Cloud syncing is not configured" else "Configured & Active",
                            fontSize = 12.sp,
                            color = if (syncKey.isBlank()) (if (isDark) Color(0xFFFDBA74) else Color(0xFFF59E0B)) else (if (isDark) Color(0xFF81C784) else Color(0xFF10B981)),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                if (syncState is JournalViewModel.SyncState.Syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = AccentCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (syncKey.isBlank() && !showConfig) {
                val warningBg = if (isDark) Color(0xFF2D231F) else Color(0xFFFEF3C7)
                val warningBorder = if (isDark) Color(0xFF451A03) else Color(0xFFFDE68A)
                val warningText = if (isDark) Color(0xFFFDBA74) else Color(0xFF92400E)
                val warningIconTint = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(warningBg, RoundedCornerShape(12.dp))
                        .border(1.dp, warningBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = warningIconTint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Your data is only stored locally and will be cleared when updating/reinstalling. Configure Firebase below to keep your progression safe!",
                        fontSize = 11.sp,
                        color = warningText,
                        lineHeight = 15.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (syncKey.isBlank()) {
                            showConfig = true
                            Toast.makeText(context, "Please set a Sync Key in the configuration.", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.backupDataToCloud()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    enabled = syncState !is JournalViewModel.SyncState.Syncing
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Backup Now", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = {
                        if (syncKey.isBlank()) {
                            showConfig = true
                            Toast.makeText(context, "Please set a Sync Key in the configuration.", Toast.LENGTH_SHORT).show()
                        } else {
                            showRestoreConfirm = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    enabled = syncState !is JournalViewModel.SyncState.Syncing
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restore Now", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { showConfig = !showConfig },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                contentPadding = PaddingValues(vertical = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Icon(
                    imageVector = if (showConfig) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (showConfig) "Hide Firebase Sync Configuration" else "Configure Firebase Sync Settings",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (showConfig) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "FIREBASE REALTIME DATABASE SETTINGS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryIndigo,
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Firebase Realtime Database URL", fontSize = 12.sp) },
                    placeholder = { Text("https://my-project-default-rtdb.firebaseio.com/", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("firebase_url_input"),
                    textStyle = TextStyle(fontSize = 13.sp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryIndigo,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("Personal Sync Passphrase / Key", fontSize = 12.sp) },
                    placeholder = { Text("e.g. user_growth_key_99", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("firebase_key_input"),
                    textStyle = TextStyle(fontSize = 13.sp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryIndigo,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                Text(
                    text = "A secret passphrase to locate your records in the cloud. Remember this key to restore your data on other devices or after reinstallations!",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto Cloud Sync",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Automatically sync and backup data to the cloud after writing new journal entries or deleting items.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 13.sp
                        )
                    }
                    Switch(
                        checked = autoSyncInput,
                        onCheckedChange = { autoSyncInput = it },
                        modifier = Modifier.testTag("auto_sync_switch")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val trimmedUrl = urlInput.trim()
                        val trimmedKey = keyInput.trim()
                        if (trimmedKey.isEmpty()) {
                            Toast.makeText(context, "Sync Key cannot be empty!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (trimmedUrl.isNotEmpty() && !trimmedUrl.startsWith("http")) {
                            Toast.makeText(context, "Invalid database URL format!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.updateFirebaseConfig(trimmedUrl, trimmedKey, autoSyncInput)
                        Toast.makeText(context, "Configuration saved successfully!", Toast.LENGTH_SHORT).show()
                        showConfig = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Configuration", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = {
                Text(
                    text = "Confirm Cloud Restoration?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Restoring from the cloud will wipe all current local journal entries, skills, buffs, inventory, and connections, and completely replace them with the data stored under the Sync Key '$syncKey'.\n\nThis action cannot be undone. Are you sure you want to proceed?",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirm = false
                        viewModel.restoreDataFromCloud()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFFEF5350) else Color(0xFFEF4444))
                ) {
                    Text("Wipe & Restore", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun DailyTaskChecklist(
    viewModel: JournalViewModel,
    dateStr: String,
    onAppendToJournal: ((String) -> Unit)? = null
) {
    val tasks by viewModel.getTasksForDateFlow(dateStr).collectAsState(initial = emptyList())
    var newTaskTitle by remember { mutableStateOf("") }
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Quests Icon",
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "⚔️ Daily Quests (Task Checklist)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (tasks.isNotEmpty()) {
                    val completedCount = tasks.count { it.isCompleted }
                    Text(
                        text = "$completedCount/${tasks.size} Done",
                        fontSize = 12.sp,
                        color = PrimaryIndigo,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .background(PrimaryIndigo.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Add task row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    placeholder = { Text("Add new quest / task...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("new_task_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryIndigo,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (newTaskTitle.isNotBlank()) {
                            viewModel.addTask(newTaskTitle.trim(), dateStr)
                            newTaskTitle = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryIndigo)
                        .testTag("add_task_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Task", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tasks List
            if (tasks.isEmpty()) {
                Text(
                    text = "No quests set for today. Plan your day by adding some tasks above!",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    tasks.forEach { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.isCompleted,
                                onCheckedChange = { isChecked ->
                                    viewModel.toggleTask(task.id, isChecked)
                                },
                                colors = CheckboxDefaults.colors(checkedColor = PrimaryIndigo),
                                modifier = Modifier.testTag("task_checkbox_${task.id}")
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = task.title,
                                fontSize = 13.sp,
                                fontWeight = if (task.isCompleted) FontWeight.Normal else FontWeight.Medium,
                                color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.deleteTask(task.id) },
                                modifier = Modifier.size(36.dp).testTag("delete_task_${task.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Quest",
                                    tint = if (isDark) Color(0xFFEF5350) else Color(0xFFEF4444),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Add button to append quests list summary to the journal input
                if (onAppendToJournal != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val completedList = tasks.filter { it.isCompleted }
                            val pendingList = tasks.filter { !it.isCompleted }
                            
                            val sb = java.lang.StringBuilder()
                            sb.append("\n\nToday's Quests Accomplished:\n")
                            if (completedList.isNotEmpty()) {
                                completedList.forEach { sb.append("- [x] ${it.title}\n") }
                            } else {
                                sb.append("- (None completed yet)\n")
                            }
                            if (pendingList.isNotEmpty()) {
                                sb.append("\nQuests still in progress:\n")
                                pendingList.forEach { sb.append("- [ ] ${it.title}\n") }
                            }
                            
                            onAppendToJournal(sb.toString())
                        },
                        modifier = Modifier.fillMaxWidth().testTag("append_quests_button"),
                        border = BorderStroke(1.dp, PrimaryIndigo.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryIndigo)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit icon",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Append Quests to Journal Content", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
