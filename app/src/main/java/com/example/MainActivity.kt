package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NaverGreen
import com.example.ui.theme.GoogleBlue

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Handle sharing intents on startup
        handleIntent(intent)

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = GoogleBlue,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Google",
                                        fontWeight = FontWeight.Bold,
                                        color = GoogleBlue,
                                        fontSize = 18.sp
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "to",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp).size(16.dp)
                                    )
                                    Text(
                                        text = "Naver Map",
                                        fontWeight = FontWeight.Bold,
                                        color = NaverGreen,
                                        fontSize = 18.sp
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                ) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent != null && intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                viewModel.handleReceivedText(sharedText)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if Naver Map is installed when returning to our app
        viewModel.checkNaverInstalled(this)
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sharedText by viewModel.sharedText.collectAsState()
    val parsedLocation by viewModel.parsedLocation.collectAsState()
    val isResolving by viewModel.isResolving.collectAsState()
    val isNaverInstalled by viewModel.isNaverInstalled.collectAsState()

    var manualInput by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    // Sync manual input text if sharing trigger is used
    LaunchedEffect(sharedText) {
        if (sharedText.isNotEmpty()) {
            manualInput = sharedText
        }
    }

    // Refresh Naver app status on start
    LaunchedEffect(Unit) {
        viewModel.checkNaverInstalled(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App intro and tips banner
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Tips",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "🚀 韓國自由行必備！",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "開啟 Google Maps 地圖 -> 點擊任何地點的「分享」 -> 選擇本軟體即可免搜尋直接在 Naver Map 開啟「即時導航」與「步行規劃」！",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Status Card for Naver Map availability
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isNaverInstalled) NaverGreen.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
                .border(
                    width = 1.dp,
                    color = if (isNaverInstalled) NaverGreen.copy(alpha = 0.6f) else MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isNaverInstalled) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isNaverInstalled) NaverGreen else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isNaverInstalled) "Naver Map App已正確安裝 ✓" else "未偵測到 Naver Map App! ⚠️",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = if (isNaverInstalled) NaverGreen else MaterialTheme.colorScheme.onErrorContainer
                )
            }
            if (!isNaverInstalled) {
                TextButton(
                    onClick = { viewModel.openPlayStoreForNaver(context) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("前往商店安裝", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Input payload / Manual input box
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "👇 直接編輯或貼上分享文字",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = manualInput,
                    onValueChange = { manualInput = it },
                    placeholder = { Text("貼上從 Google Maps 複製的分享內容...", fontSize = 13.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = TextStyle(fontSize = 13.sp, lineHeight = 16.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Paste from clipboard CTA
                    OutlinedButton(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text
                            if (!clipText.isNullOrBlank()) {
                                manualInput = clipText
                                viewModel.handleReceivedText(clipText)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("貼上並分析", fontSize = 13.sp)
                    }

                    // Process dynamic CTA
                    Button(
                        onClick = { viewModel.handleReceivedText(manualInput) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("開始轉換", fontSize = 13.sp)
                    }
                }
            }
        }

        // Resolving loading UI
        AnimatedVisibility(
            visible = isResolving,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "正在跟隨 Google Maps 短網址並解析座標...",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Result Location Preview Card
        AnimatedVisibility(
            visible = parsedLocation != null,
            enter = fadeIn() + slideInVertically()
        ) {
            parsedLocation?.let { location ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Card Header with Status & Badges
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = "Place Pin",
                                    tint = if (location.isKoreanLocation) NaverGreen else GoogleBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "已解析位置數據",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Korea specific badge helper
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (location.isKoreanLocation) NaverGreen.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (location.isKoreanLocation) "🇰🇷 位於韓國境內" else "🌏 非韓國 / 未知 coordinates",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (location.isKoreanLocation) NaverGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Editable Place Name Field
                        OutlinedTextField(
                            value = location.placeName,
                            onValueChange = { viewModel.updatePlaceName(it) },
                            label = { Text("地點名稱 (可自由搜尋與編輯修改 ✏️)", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (location.isKoreanLocation) NaverGreen else GoogleBlue,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        )

                        // Google Original Place Info (Read-only reference)
                        if (location.googlePlaceName.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Google Info",
                                            tint = GoogleBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Google 地圖原始名稱/資訊對照 (唯讀 ℹ️)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GoogleBlue
                                        )
                                    }
                                    Text(
                                        text = location.googlePlaceName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Editable Address Field
                        OutlinedTextField(
                            value = location.address,
                            onValueChange = { viewModel.updateAddress(it) },
                            label = { Text("地址 (可自由搜尋與編輯修改 ✏️)", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 14.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (location.isKoreanLocation) NaverGreen else GoogleBlue,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        )

                        // Coordinates if available
                        if (location.latitude != null && location.longitude != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "緯度 (Latitude)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = String.format(java.util.Locale.US, "%.6f", location.latitude),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "經度 (Longitude)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = String.format(java.util.Locale.US, "%.6f", location.longitude),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // NAVIGATION ACTION BUTTONS - High Visual Impact
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⚡️ 選擇跳轉 Naver Map 的模式",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // CAR ROUTE (Driving / Navigation)
                            Button(
                                onClick = { viewModel.launchNaverMap(context, "car") },
                                colors = ButtonDefaults.buttonColors(containerColor = NaverGreen),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (location.latitude != null) "🚗 開始 Naver 即時汽車導航" else "🔍 到 Naver Map 搜尋此地點名稱",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }

                            if (location.latitude != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // PUBLIC TRANSIT
                                    Button(
                                        onClick = { viewModel.launchNaverMap(context, "bus") },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(vertical = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.List,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("🚌 大眾運輸", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // WALKING
                                    Button(
                                        onClick = { viewModel.launchNaverMap(context, "walk") },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(vertical = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("🚶 步行路徑", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Fallback warning notice for non-Korean locations
                        if (!location.isKoreanLocation && location.latitude != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "溫馨提示：由於 Naver Map 的核心圖資僅支持韓國，此非韓國坐標在 Naver 地圖內可能不支援導航或無法正常渲染路徑。我們可以直接使用搜尋名稱進行尋路。",
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // What to do / Troubleshooting card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "💡 常見使用問題",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = "1. 我的地點如果不是在南韓可以導航嗎？\n   Naver 地圖主要是給在韓國旅遊、出差的使用者設計。如果非韓國地點，建議在 Google Maps 內直接導航才安全喔！\n\n2. 點擊按鈕沒有反應？\n   若未安裝 Naver Map APP，本助手將為您自動跳轉至 Naver 行動版網頁版 (Web Fallback) 解析檢視您的目標。您也可以點擊最頂部的通道來下載 Naver App。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
