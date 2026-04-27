package com.devorbit.weatherx

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.devorbit.weatherx.ui.theme.WeatherXTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val dataStore = remember { DataStore(context) }
            val themeMode by dataStore.themeModeFlow.collectAsState(initial = "SYSTEM")
            val isDarkTheme = when (themeMode) {
                "ON" -> true
                "OFF" -> false
                else -> isSystemInDarkTheme()
            }

            WeatherXTheme(useDarkTheme = isDarkTheme) {
                WeatherApp(dataStore, isDarkTheme)
            }
        }
    }
}

@Composable
fun WeatherApp(dataStore: DataStore, isDarkTheme: Boolean) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val locationHelper = remember { LocationHelper(context) }
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 600

    val theme = if (isDarkTheme) {
        WeatherTheme(
            cardColor = Color(0x4DFFFFFF),
            onCardColor = Color.White,
            accentColor = Color(0xFF81D4FA)
        )
    } else {
        WeatherTheme(
            cardColor = Color(0x99FFFFFF),
            onCardColor = Color(0xFF1A1A1B),
            accentColor = Color(0xFF0071BC)
        )
    }

    val bgColors = if (isDarkTheme) {
        listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
    } else {
        listOf(Color(0xFFE0F7FA), Color(0xFFB2EBF2), Color(0xFF81D4FA))
    }

    var homeData by remember { mutableStateOf<WeatherApi.HomeJsonData?>(null) }
    var forecastData by remember { mutableStateOf<WeatherApi.ForecastJsonData?>(null) }
    var aqiValue by remember { mutableStateOf<Int?>(null) }
    var lastUpdated by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun fetchData() {
        scope.launch {
            isLoading = true
            val city = dataStore.cityFlow.first()
            val useGps = dataStore.useGpsFlow.first()

            try {
                if (useGps && locationHelper.hasLocationPermission()) {
                    locationHelper.getCurrentLocation(
                        onResult = { lat, lon ->
                            scope.launch {
                                homeData = WeatherApi.readMainDataByCoords(lat, lon)
                                forecastData = WeatherApi.readForecastDataByCoords(lat, lon)
                                val aqiResponse = WeatherApi.readAQIData()
                                aqiResponse.list.firstOrNull()?.let {
                                    val aqiCalc = AQI(
                                        pm10 = it.components.pm10,
                                        pm2_5 = it.components.pm25,
                                        o3 = it.components.o3,
                                        no2 = it.components.no2
                                    )
                                    aqiValue = aqiCalc.getAQI()
                                }
                                lastUpdated = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()))
                                isLoading = false
                            }
                        },
                        onError = {
                            scope.launch {
                                homeData = WeatherApi.readMainData(city)
                                forecastData = WeatherApi.readForecastData(city)
                                lastUpdated = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()))
                                isLoading = false
                            }
                        }
                    )
                } else {
                    homeData = WeatherApi.readMainData(city)
                    forecastData = WeatherApi.readForecastData(city)
                    val aqiResponse = WeatherApi.readAQIData()
                    aqiResponse.list.firstOrNull()?.let {
                        val aqiCalc = AQI(
                            pm10 = it.components.pm10,
                            pm2_5 = it.components.pm25,
                            o3 = it.components.o3,
                            no2 = it.components.no2
                        )
                        aqiValue = aqiCalc.getAQI()
                    }
                    lastUpdated = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()))
                    isLoading = false
                }
            } catch (e: Exception) {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchData()
    }

    Box(modifier = Modifier.fillMaxSize().background(if (isDarkTheme) Color.Black else Color.White)) {
        DynamicBackground(bgColors)
        WeatherAnimationOverlay(homeData?.weather?.getOrNull(0)?.main)

        Row(modifier = Modifier.fillMaxSize()) {
            if (isWideScreen) {
                AppNavigationRail(navController, theme)
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = Color.Transparent,
                bottomBar = {
                    if (!isWideScreen) {
                        AppBottomBar(navController, theme, isDarkTheme)
                    }
                }
            ) { padding ->
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = theme.accentColor)
                    }
                } else {
                    Box(modifier = Modifier.padding(padding)) {
                        AppNavHost(
                            navController = navController,
                            theme = theme,
                            homeData = homeData,
                            forecastData = forecastData,
                            aqiValue = aqiValue,
                            lastUpdated = lastUpdated,
                            dataStore = dataStore,
                            onSearch = {
                                scope.launch {
                                    dataStore.writeCity(it)
                                    fetchData()
                                }
                            },
                            onRefresh = { fetchData() },
                            onUpdate = { fetchData() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppNavigationRail(navController: NavHostController, theme: WeatherTheme) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationRail(
        containerColor = Color.Transparent,
        header = {
            Icon(
                painter = painterResource(id = R.drawable._01d),
                contentDescription = null,
                tint = theme.accentColor,
                modifier = Modifier.size(48.dp).padding(8.dp)
            )
        },
        modifier = Modifier.padding(vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val items = listOf(
                NavigationItem(Screen.Main.route, Icons.Rounded.Home, R.string.home),
                NavigationItem(Screen.Forecast.route, Icons.AutoMirrored.Rounded.List, R.string.forecast),
                NavigationItem(Screen.Emergency.route, Screen.Emergency.icon, R.string.emergency),
                NavigationItem(Screen.Settings.route, Icons.Rounded.Settings, R.string.settings)
            )

            items.forEach { item ->
                NavigationRailItem(
                    icon = { Icon(item.icon, contentDescription = stringResource(item.labelRes)) },
                    label = { Text(stringResource(item.labelRes)) },
                    selected = currentRoute == item.route,
                    onClick = { navController.navigate(item.route) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = theme.accentColor,
                        selectedTextColor = theme.accentColor,
                        unselectedIconColor = theme.onCardColor.copy(alpha = 0.6f),
                        unselectedTextColor = theme.onCardColor.copy(alpha = 0.6f),
                        indicatorColor = theme.accentColor.copy(alpha = 0.1f)
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun AppBottomBar(navController: NavHostController, theme: WeatherTheme, isDarkTheme: Boolean) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    BottomAppBar(
        containerColor = if (isDarkTheme) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.4f),
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(32.dp)),
        contentPadding = PaddingValues(0.dp),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavBarItem(
                icon = Icons.Rounded.Home,
                label = stringResource(R.string.home),
                isSelected = currentRoute == Screen.Main.route,
                onClick = { navController.navigate(Screen.Main.route) },
                theme = theme
            )
            NavBarItem(
                icon = Icons.AutoMirrored.Rounded.List,
                label = stringResource(R.string.forecast),
                isSelected = currentRoute == Screen.Forecast.route,
                onClick = { navController.navigate(Screen.Forecast.route) },
                theme = theme
            )
            NavBarItem(
                icon = Screen.Emergency.icon,
                label = stringResource(R.string.emergency),
                isSelected = currentRoute == Screen.Emergency.route,
                onClick = { navController.navigate(Screen.Emergency.route) },
                theme = theme
            )
            NavBarItem(
                icon = Icons.Rounded.Settings,
                label = stringResource(R.string.settings),
                isSelected = currentRoute == Screen.Settings.route,
                onClick = { navController.navigate(Screen.Settings.route) },
                theme = theme
            )
        }
    }
}

data class NavigationItem(val route: String, val icon: ImageVector, val labelRes: Int)

@Composable
fun AppNavHost(
    navController: NavHostController,
    theme: WeatherTheme,
    homeData: WeatherApi.HomeJsonData?,
    forecastData: WeatherApi.ForecastJsonData?,
    aqiValue: Int?,
    lastUpdated: String?,
    dataStore: DataStore,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onUpdate: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        composable(Screen.Main.route) {
            HomeScreen(theme, homeData, forecastData, aqiValue, lastUpdated, onSearch = onSearch, onRefresh = onRefresh)
        }
        composable(Screen.Forecast.route) {
            ForecastScreen(theme, forecastData)
        }
        composable(Screen.Emergency.route) {
            EmergencyScreen(theme)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(theme, dataStore, onUpdate = onUpdate)
        }
    }
}

@Composable
fun WeatherAnimationOverlay(condition: String?) {
    when {
        condition?.contains("Rain", ignoreCase = true) == true -> RainAnimation()
        condition?.contains("Snow", ignoreCase = true) == true -> SnowAnimation()
        condition?.contains("Cloud", ignoreCase = true) == true -> CloudAnimation()
    }
}

@Composable
fun RainAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "rain")
    val rainAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainAnim"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val dropCount = 50
        for (i in 0 until dropCount) {
            val x = (i * size.width / dropCount)
            val startY = (rainAnim * size.height + (i * 100)) % size.height
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = Offset(x, startY),
                end = Offset(x - 5f, startY + 20f),
                strokeWidth = 2f
            )
        }
    }
}

@Composable
fun SnowAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "snow")
    val snowAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "snowAnim"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val flakeCount = 40
        for (i in 0 until flakeCount) {
            val x = (i * size.width / flakeCount) + (kotlin.math.sin(snowAnim * 2 * kotlin.math.PI.toFloat() + i) * 20f)
            val startY = (snowAnim * size.height + (i * 150)) % size.height
            drawCircle(
                color = Color.White.copy(alpha = 0.5f),
                radius = 4f,
                center = Offset(x, startY)
            )
        }
    }
}

@Composable
fun CloudAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "clouds")
    val cloudAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cloudAnim"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cloudCount = 5
        for (i in 0 until cloudCount) {
            val x = (cloudAnim * size.width + (i * 300)) % (size.width + 400) - 200
            val y = 100f + (i * 150f)
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = 150f,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
fun NavBarItem(icon: ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit, theme: WeatherTheme) {
    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) theme.accentColor else theme.onCardColor.copy(alpha = 0.6f),
            modifier = Modifier.size(28.dp)
        )
        if (isSelected) {
            Text(
                text = label,
                color = theme.accentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun HomeScreen(
    theme: WeatherTheme,
    homeData: WeatherApi.HomeJsonData?,
    forecastData: WeatherApi.ForecastJsonData?,
    aqi: Int?,
    lastUpdated: String?,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 600

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.displaySmall.copy(
                brush = Brush.linearGradient(
                    colors = listOf(theme.accentColor, theme.onCardColor, theme.accentColor)
                )
            ),
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth(if (isWideScreen) 0.6f else 1f)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(16.dp)),
            placeholder = { Text(stringResource(R.string.search_placeholder), color = theme.onCardColor.copy(alpha = 0.5f)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = theme.accentColor) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        onSearch(searchQuery)
                        searchQuery = ""
                    }) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = theme.accentColor)
                    }
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = theme.accentColor)
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = theme.cardColor,
                unfocusedContainerColor = theme.cardColor,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = theme.onCardColor,
                unfocusedTextColor = theme.onCardColor,
                cursorColor = theme.accentColor
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        homeData?.let { data ->
            if (isWideScreen) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(modifier = Modifier.weight(1.5f)) {
                        WeatherHeader(data, lastUpdated, theme)
                        MainWeatherCard(
                            data = data,
                            theme = theme,
                            aqi = aqi ?: 0,
                            pop = forecastData?.list?.firstOrNull()?.pop ?: 0.0
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        SectionHeader(stringResource(R.string.smart_suggestions), theme)
                        SmartAssistantCard(data, aqi ?: 0, theme)
                    }
                }
            } else {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    WeatherHeader(data, lastUpdated, theme)
                    MainWeatherCard(
                        data = data,
                        theme = theme,
                        aqi = aqi ?: 0,
                        pop = forecastData?.list?.firstOrNull()?.pop ?: 0.0
                    )
                    SectionHeader(stringResource(R.string.smart_suggestions), theme)
                    SmartAssistantCard(data, aqi ?: 0, theme)
                }
            }
        }

        SectionHeader("Hourly Forecast", theme)
        forecastData?.list?.take(12)?.let { list ->
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(list) { TimelyWeatherItem(it, theme) }
            }
        }
    }
}

@Composable
fun WeatherHeader(data: WeatherApi.HomeJsonData, lastUpdated: String?, theme: WeatherTheme) {
    val date = ZonedDateTime.ofInstant(Instant.ofEpochSecond(data.dt.toLong()), ZoneId.systemDefault())
    Text(
        text = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
        color = theme.onCardColor.copy(alpha = 0.7f),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = theme.accentColor)
        Spacer(modifier = Modifier.width(8.dp))
        Text(data.name, color = theme.onCardColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
    lastUpdated?.let {
        Text(
            text = stringResource(R.string.last_updated, it),
            color = theme.onCardColor.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 32.dp)
        )
    }
}

@Composable
fun SmartAssistantCard(data: WeatherApi.HomeJsonData, aqi: Int, theme: WeatherTheme) {
    val suggestions = remember(data, aqi) {
        val temp = data.main.temp
        val condition = data.weather.getOrNull(0)?.main ?: ""
        val humidity = data.main.humidity
        val windSpeed = data.wind.speed * 3.6 // convert m/s to km/h
        
        val list = mutableListOf<String>()
        
        if (temp > 30) list.add("It's quite hot, stay hydrated \uD83D\uDCA7")
        else if (temp < 15 && temp > 5) list.add("Cool weather, wear a light jacket \uD83E\uDDE5")
        else if (temp <= 5) list.add("It's freezing, bundle up warmly \uD83E\uDDE3")
        
        when {
            condition.contains("Rain", ignoreCase = true) -> list.add("Carry an umbrella, it's raining \u2614")
            condition.contains("Clear", ignoreCase = true) -> list.add("Sunny day, wear sunglasses \uD83D\uDE0E")
            condition.contains("Cloud", ignoreCase = true) -> list.add("Cloudy skies, perfect for a walk \u2601\uFE0F")
            condition.contains("Snow", ignoreCase = true) -> list.add("Watch your step, it's snowing \u2744\uFE0F")
        }
        
        if (humidity > 75) list.add("High humidity, stay in cool areas \uD83E\uDDF2")
        if (windSpeed > 25) list.add("Hold onto your hat, it's windy! \uD83E\uDDE2")
        
        if (aqi > 100) list.add("Air quality is low, wear a mask outdoors \uD83D\uDE37")
        else if (aqi < 50 && condition.contains("Clear", ignoreCase = true)) {
            list.add("Air quality is great, enjoy the fresh air \uD83D\uDE43")
        }

        if (list.size < 3) list.add("Have a wonderful day ahead! \u2728")
        
        list.distinct().take(4)
    }

    InteractiveSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = theme.cardColor
    ) {
        Column(modifier = Modifier.padding(20.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = theme.accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                @Suppress("SpellCheckingInspection")
                Text(
                    stringResource(R.string.ai_assistant),
                    style = MaterialTheme.typography.labelLarge,
                    color = theme.accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(theme.accentColor)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = suggestion,
                        color = theme.onCardColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun EmergencyScreen(theme: WeatherTheme) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 600

    val emergencyContacts = listOf(
        EmergencyContact("National Helpline", "112", Icons.Rounded.HealthAndSafety),
        EmergencyContact("Police", "100", Icons.Rounded.LocalPolice),
        EmergencyContact("Ambulance", "108", Icons.Rounded.MedicalServices),
        EmergencyContact("Fire Brigade", "101", Icons.Rounded.FireTruck),
        EmergencyContact("Disaster Management", "1070", Icons.Rounded.Warning),
        EmergencyContact("Flood Relief", "1078", Icons.Rounded.Flood),
        EmergencyContact("Electricity Emergency", "1912", Icons.Rounded.FlashOn),
        EmergencyContact("Women Helpline", "1091", Icons.Rounded.Woman),
        EmergencyContact("Child Helpline", "1098", Icons.Rounded.ChildCare)
    )

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.emergency_helplines),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = theme.onCardColor,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (isWideScreen) 2 else 1),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(emergencyContacts) { contact ->
                EmergencyItem(contact, theme) {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.number}"))
                    context.startActivity(intent)
                }
            }
        }
    }
}

data class EmergencyContact(val name: String, val number: String, val icon: ImageVector)

@Composable
fun EmergencyItem(contact: EmergencyContact, theme: WeatherTheme, onClick: () -> Unit) {
    InteractiveSurface(
        modifier = Modifier.fillMaxWidth(),
        color = theme.cardColor,
        shape = RoundedCornerShape(24.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(theme.onCardColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(contact.icon, contentDescription = null, tint = theme.accentColor, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(contact.name, color = theme.onCardColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(contact.number, color = theme.accentColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                }
            }
            Icon(Icons.Rounded.Call, contentDescription = null, tint = Color.Green.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun ForecastScreen(theme: WeatherTheme, forecastData: WeatherApi.ForecastJsonData?) {
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 600

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.seven_day_forecast),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = theme.onCardColor,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )

        forecastData?.list?.let { list ->
            val dailyForecast = list.distinctBy { 
                ZonedDateTime.ofInstant(Instant.ofEpochSecond(it.dt.toLong()), ZoneOffset.UTC).toLocalDate() 
            }.take(7)

            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isWideScreen) 2 else 1),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(dailyForecast) { item ->
                    ForecastRow(item, theme)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(theme: WeatherTheme, dataStore: DataStore, onUpdate: () -> Unit) {
    val scope = rememberCoroutineScope()
    val useGps by dataStore.useGpsFlow.collectAsState(initial = false)
    val themeMode by dataStore.themeModeFlow.collectAsState(initial = "SYSTEM")

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.settings),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = theme.onCardColor,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.height(32.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.GpsFixed, contentDescription = null, tint = theme.accentColor)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.use_gps), color = theme.onCardColor, fontWeight = FontWeight.Bold)
                }
                Switch(
                    checked = useGps,
                    onCheckedChange = {
                        scope.launch {
                            dataStore.write(dataStore.useGps, it)
                            onUpdate()
                        }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = theme.accentColor)
                )
            }

            HorizontalDivider(color = theme.onCardColor.copy(alpha = 0.1f))

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Palette, contentDescription = null, tint = theme.accentColor)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.theme_mode), color = theme.onCardColor, fontWeight = FontWeight.Bold)
                }
                
                Row {
                    ThemeModeButton(Icons.Rounded.LightMode, themeMode == "OFF", theme) {
                        scope.launch { dataStore.write(dataStore.themeMode, "OFF") }
                    }
                    ThemeModeButton(Icons.Rounded.DarkMode, themeMode == "ON", theme) {
                        scope.launch { dataStore.write(dataStore.themeMode, "ON") }
                    }
                    ThemeModeButton(Icons.Rounded.SettingsBrightness, themeMode == "SYSTEM", theme) {
                        scope.launch { dataStore.write(dataStore.themeMode, "SYSTEM") }
                    }
                }
            }

            HorizontalDivider(color = theme.onCardColor.copy(alpha = 0.1f))
            SettingsItem(Icons.Rounded.Info, stringResource(R.string.version), "WeatherX v1.0", theme)
            
            HorizontalDivider(color = theme.onCardColor.copy(alpha = 0.1f))
            SettingsItem(
                icon = Icons.Rounded.Code,
                title = "Developer",
                value = "Basit Manzoor\nDevOrbit Technologies",
                theme = theme
            )
        }
    }
}

@Composable
fun ThemeModeButton(icon: ImageVector, isSelected: Boolean, theme: WeatherTheme, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.background(
            if (isSelected) theme.accentColor.copy(alpha = 0.2f) else Color.Transparent,
            CircleShape
        )
    ) {
        Icon(
            icon, 
            contentDescription = null, 
            tint = if (isSelected) theme.accentColor else theme.onCardColor.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, value: String, theme: WeatherTheme) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = theme.accentColor, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, color = theme.onCardColor, fontWeight = FontWeight.Bold)
            Text(value, color = theme.onCardColor.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun ForecastRow(item: WeatherApi.ListData, theme: WeatherTheme) {
    InteractiveSurface(
        modifier = Modifier.fillMaxWidth(),
        color = theme.cardColor,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val date = ZonedDateTime.ofInstant(Instant.ofEpochSecond(item.dt.toLong()), ZoneOffset.UTC)
                Column {
                    Text(date.format(DateTimeFormatter.ofPattern("EEEE")), color = theme.onCardColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(date.format(DateTimeFormatter.ofPattern("MMM d")), color = theme.onCardColor.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = getWeatherIcon(item.weather.firstOrNull()?.icon)),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("${item.main.temp.roundToInt()}°C", color = theme.onCardColor, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = theme.onCardColor.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ForecastParamItem(painterResource(R.drawable.thermometer), "${item.main.temp.roundToInt()}°", theme)
                ForecastParamItem(painterResource(R.drawable.humidity), "${(item.pop * 100).roundToInt()}%", theme)
                ForecastParamItem(painterResource(R.drawable.pressure), "${item.main.pressure}hPa", theme)
                ForecastParamItem(painterResource(R.drawable.speed), "${item.wind.speed.roundToInt()}m/s", theme)
            }
        }
    }
}

@Composable
fun ForecastParamItem(painter: androidx.compose.ui.graphics.painter.Painter, value: String, theme: WeatherTheme) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painter = painter, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = value, color = theme.onCardColor, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun DynamicBackground(colors: List<Color>) {
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val animValue by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bgAnim"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val brush = Brush.linearGradient(
                    colors = colors,
                    start = Offset(size.width * animValue, 0f),
                    end = Offset(size.width * (1f - animValue), size.height)
                )
                onDrawBehind {
                    drawRect(brush)
                }
            }
    )
}

@Composable
fun SectionHeader(title: String, theme: WeatherTheme) {
    Text(
        text = title,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        color = theme.onCardColor,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp
    )
}

@Composable
fun TimelyWeatherItem(item: WeatherApi.ListData, theme: WeatherTheme) {
    val time = ZonedDateTime.ofInstant(Instant.ofEpochSecond(item.dt.toLong()), ZoneOffset.UTC)
        .format(DateTimeFormatter.ofPattern("HH:mm"))

    InteractiveSurface(
        shape = RoundedCornerShape(28.dp),
        color = theme.cardColor,
        modifier = Modifier.width(100.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = time, color = theme.onCardColor.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Image(painter = painterResource(id = getWeatherIcon(item.weather.getOrNull(0)?.icon)), contentDescription = null, modifier = Modifier.size(44.dp))
            Text(text = "${item.main.temp.roundToInt()}°", color = theme.onCardColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun InteractiveSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    color: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    Surface(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick) else Modifier),
        shape = shape,
        color = color,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        content()
    }
}

@Composable
fun MainWeatherCard(data: WeatherApi.HomeJsonData, theme: WeatherTheme, aqi: Int, pop: Double) {
    Column(modifier = Modifier.padding(vertical = 20.dp)) {
        InteractiveSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(40.dp),
            color = theme.cardColor
        ) {
            Column(modifier = Modifier.padding(28.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(text = data.main.temp.roundToInt().toString(), fontSize = 100.sp, fontWeight = FontWeight.Black, color = theme.onCardColor, letterSpacing = (-4).sp)
                            Text(text = "°", fontSize = 54.sp, modifier = Modifier.padding(top = 16.dp), color = theme.accentColor, fontWeight = FontWeight.Black)
                        }
                        Text(
                            text = data.weather.getOrNull(0)?.description?.uppercase() ?: "",
                            color = theme.onCardColor.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                    Image(
                        painter = painterResource(id = getWeatherIcon(data.weather.getOrNull(0)?.icon)),
                        contentDescription = null,
                        modifier = Modifier.size(140.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    WeatherInfoItem(painterResource(R.drawable.humidity), "Rain", "${(pop * 100).roundToInt()}%", theme)
                    WeatherInfoItem(painterResource(R.drawable.pressure), "Pres", "${data.main.pressure}hPa", theme)
                    WeatherInfoItem(painterResource(R.drawable.speed), "Wind", "${data.wind.speed.roundToInt()}m/s", theme)
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    WeatherInfoItem(painterResource(R.drawable.thermometer), "Feels", "${data.main.feelsLike.roundToInt()}°", theme)
                    WeatherInfoItem(painterResource(R.drawable.visibility), "Vis", "${data.visibility / 1000}km", theme)
                    WeatherInfoItem(painterResource(R.drawable.aqi), "AQI", if (aqi > 0) aqi.toString() else "N/A", theme)
                }
            }
        }
    }
}

@Composable
fun WeatherInfoItem(icon: Any, label: String, value: String, theme: WeatherTheme, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        when (icon) {
            is ImageVector -> Icon(icon, contentDescription = label, tint = theme.accentColor, modifier = Modifier.size(24.dp))
            is androidx.compose.ui.graphics.painter.Painter -> Image(icon, contentDescription = label, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = theme.onCardColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = label, color = theme.onCardColor.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
    }
}

data class WeatherTheme(val cardColor: Color, val onCardColor: Color, val accentColor: Color)

fun getWeatherIcon(icon: String?): Int {
    return when (icon) {
        "01d", "01n" -> R.drawable._01d
        "02d", "02n" -> R.drawable._02d
        "03d", "03n" -> R.drawable._03d
        "04d", "04n" -> R.drawable._04d
        "09d", "09n" -> R.drawable._09d
        "10d", "10n" -> R.drawable._10d
        "11d", "11n" -> R.drawable._11d
        "13d", "13n" -> R.drawable._13d
        "50d", "50n" -> R.drawable._50d
        else -> R.drawable.cloud
    }
}
