package com.example.uistatereplaysdk

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.replaysdk.replay.Replay
import com.example.replaysdk.replay.Session
import com.example.uistatereplaysdk.ui.theme.UIStateReplaySDKTheme
import kotlinx.coroutines.launch
import androidx.compose.material3.CenterAlignedTopAppBar
import org.json.JSONObject

// Simple screens for the demo
sealed class DemoScreen {
    data object Login : DemoScreen()
    data object Shop : DemoScreen()
    data class Product(val productId: String) : DemoScreen()
    data object Checkout : DemoScreen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cloud base URL (Render)
        Replay.init("https://ui-state-replay-sdk.onrender.com/")

        enableEdgeToEdge()
        setContent {
            UIStateReplaySDKTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DemoApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // -------------------------
    // App state
    // -------------------------
    var screen by remember { mutableStateOf<DemoScreen>(DemoScreen.Login) }
    var cart by remember { mutableStateOf(listOf<String>()) } // productIds

    // -------------------------
    // SDK state
    // -------------------------
    var isRecording by remember { mutableStateOf(false) }
    var lastSession by remember { mutableStateOf<Session?>(null) }
    var lastResponse by remember { mutableStateOf<String?>(null) }
    var isReplaying by remember { mutableStateOf(false) }

    // Banner text (replay status / current event)
    var replayLabel by remember { mutableStateOf<String?>(null) }

    // Highlight state (for "glow" during replay)
    var highlightKey by remember { mutableStateOf<String?>(null) }

    fun flash(key: String, ms: Long = 450) {
        scope.launch {
            highlightKey = key
            kotlinx.coroutines.delay(ms)
            if (highlightKey == key) highlightKey = null
        }
    }

    fun navigate(to: DemoScreen, recordName: String) {
        if (isRecording) Replay.log("NAVIGATE", recordName)
        screen = to
    }

    fun addToCart(productId: String) {
        if (isRecording) Replay.log("ADD_TO_CART", productId)
        cart = cart + productId
    }

    fun resetDemo() {
        screen = DemoScreen.Login
        cart = emptyList()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("UI State Replay – Demo") }
            )
        },
        floatingActionButton = {
            DebugFab(
                isRecording = isRecording,
                isReplaying = isReplaying,
                hasSession = lastSession != null,
                onStart = {
                    Replay.start()
                    isRecording = true
                    lastSession = null
                    lastResponse = null
                    replayLabel = null
                    Toast.makeText(context, "Recording started", Toast.LENGTH_SHORT).show()
                },
                onStopUpload = {
                    val session = Replay.stop()
                    isRecording = false
                    lastSession = session

                    scope.launch {
                        try {
                            val response = Replay.upload(session)
                            lastResponse = response

                            // Optional: extract sessionId for debugging/logs (not shown in UI)
                            val id = runCatching {
                                JSONObject(response).getString("sessionId")
                            }.getOrNull()

                            if (id != null) {
                                Toast.makeText(context, "Uploaded ✓ id: $id", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Uploaded ✓", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(context, "Upload failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onReplay = {
                    val session = lastSession ?: run {
                        Toast.makeText(context, "No session to replay", Toast.LENGTH_SHORT).show()
                        return@DebugFab
                    }

                    scope.launch {
                        isReplaying = true
                        replayLabel = "REPLAYING..."
                        highlightKey = null

                        // Reset to known state before replay
                        resetDemo()

                        Toast.makeText(context, "Replay started", Toast.LENGTH_SHORT).show()

                        Replay.replay(session, delayMs = 650) { e ->
                            replayLabel = "${e.type} → ${e.screen}"

                            when (e.type) {
                                "NAVIGATE" -> {
                                    flash("nav_${e.screen}")
                                    screen = when (e.screen) {
                                        "Login" -> DemoScreen.Login
                                        "Shop" -> DemoScreen.Shop
                                        "Checkout" -> DemoScreen.Checkout
                                        else -> screen
                                    }
                                }

                                "OPEN_PRODUCT" -> {
                                    // e.screen holds productId
                                    flash("open_${e.screen}")
                                    screen = DemoScreen.Product(e.screen)
                                }

                                "ADD_TO_CART" -> {
                                    // e.screen holds productId
                                    flash("add_${e.screen}")
                                    cart = cart + e.screen
                                }

                                "RESET" -> {
                                    flash("reset")
                                    resetDemo()
                                }
                            }
                        }

                        isReplaying = false
                        highlightKey = null
                        replayLabel = null
                        Toast.makeText(context, "Replay finished", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    ) { innerPadding ->

        Column(modifier = Modifier.padding(innerPadding)) {

            // Banner of replay status / current event
            if (isReplaying || replayLabel != null) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(
                        text = replayLabel ?: "REPLAYING…",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            when (val s = screen) {
                DemoScreen.Login -> LoginScreen(
                    onLogin = { navigate(DemoScreen.Shop, "Shop") }
                )

                DemoScreen.Shop -> ShopScreen(
                    products = demoProducts,
                    cartCount = cart.size,
                    highlightKey = highlightKey,
                    onOpenProduct = { product ->
                        if (isRecording) Replay.log("OPEN_PRODUCT", product.id)
                        screen = DemoScreen.Product(product.id)
                    },
                    onGoCheckout = { navigate(DemoScreen.Checkout, "Checkout") }
                )

                is DemoScreen.Product -> ProductScreen(
                    product = findProduct(s.productId),
                    highlightKey = highlightKey,
                    onAddToCart = { addToCart(s.productId) },
                    onBackToShop = { navigate(DemoScreen.Shop, "Shop") },
                    onGoCheckout = { navigate(DemoScreen.Checkout, "Checkout") }
                )

                DemoScreen.Checkout -> CheckoutScreen(
                    items = cart.map { findProduct(it) },
                    highlightKey = highlightKey,
                    onBackToShop = { navigate(DemoScreen.Shop, "Shop") },
                    onReset = {
                        if (isRecording) Replay.log("RESET", "Login")
                        resetDemo()
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Last response: ${lastResponse ?: "none"}",
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun DebugFab(
    isRecording: Boolean,
    isReplaying: Boolean,
    hasSession: Boolean,
    onStart: () -> Unit,
    onStopUpload: () -> Unit,
    onReplay: () -> Unit
) {
    Column(horizontalAlignment = Alignment.End) {

        SmallFloatingActionButton(
            onClick = {
                if (!isRecording && !isReplaying) onStart()
            },
            containerColor = if (!isRecording && !isReplaying)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text("Start")
        }

        Spacer(modifier = Modifier.height(10.dp))

        SmallFloatingActionButton(
            onClick = {
                if (isRecording && !isReplaying) onStopUpload()
            },
            containerColor = if (isRecording && !isReplaying)
                MaterialTheme.colorScheme.secondary
            else
                MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text("Stop")
        }

        Spacer(modifier = Modifier.height(10.dp))

        SmallFloatingActionButton(
            onClick = {
                if (hasSession && !isRecording && !isReplaying) onReplay()
            },
            containerColor = if (hasSession && !isRecording && !isReplaying)
                MaterialTheme.colorScheme.tertiary
            else
                MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text("Replay")
        }
    }
}

@Composable
fun LoginScreen(onLogin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Welcome back 👋", style = MaterialTheme.typography.headlineSmall)
        Text("Demo login for the shop flow", style = MaterialTheme.typography.bodyMedium)

        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
            Text("Login")
        }
    }
}

@Composable
fun ShopScreen(
    products: List<Product>,
    cartCount: Int,
    highlightKey: String?,
    onOpenProduct: (Product) -> Unit,
    onGoCheckout: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {

        Text("Shop", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        // Cart badge (visual)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Cart: ")
            BadgedBox(
                badge = { if (cartCount > 0) Badge { Text(cartCount.toString()) } }
            ) {
                Text("🛒")
            }
        }

        Spacer(Modifier.height(16.dp))

        products.forEach { p ->
            GlowBox(
                active = highlightKey == "open_${p.id}",
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text("${p.price}₪")
                    }
                    Button(onClick = { onOpenProduct(p) }) {
                        Text("Open")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(16.dp))

        GlowBox(active = highlightKey == "nav_Checkout") {
            Button(
                onClick = onGoCheckout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text("Go to Checkout")
            }
        }
    }
}

@Composable
fun ProductScreen(
    product: Product,
    highlightKey: String?,
    onAddToCart: () -> Unit,
    onBackToShop: () -> Unit,
    onGoCheckout: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Product", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        Text(product.name, style = MaterialTheme.typography.titleLarge)
        Text("${product.price}₪")

        Spacer(Modifier.height(20.dp))

        GlowBox(active = highlightKey == "add_${product.id}") {
            Button(
                onClick = onAddToCart,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) { Text("Add to cart") }
        }

        Spacer(Modifier.height(10.dp))

        GlowBox(active = highlightKey == "nav_Checkout") {
            Button(
                onClick = onGoCheckout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) { Text("Checkout") }
        }

        Spacer(Modifier.height(10.dp))

        GlowBox(active = highlightKey == "nav_Shop") {
            Button(
                onClick = onBackToShop,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) { Text("Back to Shop") }
        }
    }
}

@Composable
fun CheckoutScreen(
    items: List<Product>,
    highlightKey: String?,
    onBackToShop: () -> Unit,
    onReset: () -> Unit
) {
    val total = items.sumOf { it.price }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Checkout", style = MaterialTheme.typography.headlineSmall)

        if (items.isEmpty()) {
            Text("Cart is empty.", style = MaterialTheme.typography.bodyMedium)
        } else {
            items.forEach { p ->
                ElevatedCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${p.emoji}  ${p.name}")
                        Text("₪${p.price}")
                    }
                }
            }
        }

        ElevatedCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Total", style = MaterialTheme.typography.titleMedium)
                Text("₪$total", style = MaterialTheme.typography.titleMedium)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            GlowBox(
                active = highlightKey == "nav_Shop",
                modifier = Modifier.weight(1f)
            ) {
                OutlinedButton(
                    onClick = onBackToShop,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back to Shop")
                }
            }

            GlowBox(
                active = highlightKey == "reset",
                modifier = Modifier.weight(1f)
            ) {
                Button(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset")
                }
            }
        }
    }
}

@Composable
fun GlowBox(
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        tonalElevation = if (active) 6.dp else 0.dp,
        shadowElevation = if (active) 10.dp else 0.dp,
        shape = MaterialTheme.shapes.medium,
        color = if (active) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface
    ) {
        content()
    }
}
