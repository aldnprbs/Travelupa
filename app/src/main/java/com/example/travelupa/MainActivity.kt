package com.example.travelupa

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.* // Menggunakan Material 3
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.travelupa.ui.theme.TravelupaTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

// --- WARNA TEMA CUSTOM ---
val PrimaryColor = Color(0xFF00695C) // Teal Tua
val SecondaryColor = Color(0xFF4DB6AC) // Teal Muda
val BackgroundColor = Color(0xFFF5F5F5) // Abu-abu sangat muda (bersih)
val SurfaceColor = Color.White

// Definisi Navigasi
sealed class Screen(val route: String) {
    object Greeting : Screen("greeting")
    object Login : Screen("login")
    object RekomendasiTempat : Screen("rekomendasi_tempat")
}

// Data Class Utama
data class TempatWisata(
    val nama: String = "",
    val deskripsi: String = "",
    val gambarUriString: String? = null,
    val gambarResId: Int? = null
)

class MainActivity : ComponentActivity() {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var imageDao: ImageDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Full screen modern
        FirebaseApp.initializeApp(this)

        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "travelupa-database"
        ).build()
        imageDao = db.imageDao()

        val currentUser: FirebaseUser? = FirebaseAuth.getInstance().currentUser

        setContent {
            TravelupaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundColor
                ) {
                    AppNavigation(currentUser, firestore, storage, imageDao)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    currentUser: FirebaseUser?,
    firestore: FirebaseFirestore,
    storage: FirebaseStorage,
    imageDao: ImageDao
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = if (currentUser != null) Screen.RekomendasiTempat.route else Screen.Greeting.route
    ) {
        composable(Screen.Greeting.route) {
            GreetingScreen(
                onStart = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Greeting.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.RekomendasiTempat.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.RekomendasiTempat.route) {
            RekomendasiTempatScreen(
                firestore = firestore,
                onBackToLogin = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.RekomendasiTempat.route) { inclusive = true }
                    }
                },
                onGallerySelected = {
                    navController.navigate("gallery")
                }
            )
        }
        composable("gallery") {
            GalleryScreen(
                imageDao = imageDao,
                onImageSelected = { /* Handle image selection */ },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(PrimaryColor, SecondaryColor)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon Header
                Icon(
                    imageVector = Icons.Filled.FlightTakeoff,
                    contentDescription = null,
                    tint = PrimaryColor,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Travelupa",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryColor
                )
                Text(
                    text = "Solusi untuk anda yang lupa ke mana mana",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Inputs
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Outlined.Mail, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Button
                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            errorMessage = "Please enter email and password"
                            return@Button
                        }
                        isLoading = true
                        errorMessage = null
                        coroutineScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).await()
                                }
                                isLoading = false
                                onLoginSuccess()
                            } catch (e: Exception) {
                                isLoading = false
                                errorMessage = "Login failed: ${e.localizedMessage}"
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    else Text("LOGIN", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                errorMessage?.let {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// --- SCREEN: GREETING ---
@Composable
fun GreetingScreen(onStart: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground), // Ganti jika punya gambar logo
                contentDescription = null,
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(SecondaryColor.copy(alpha = 0.1f))
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Travelupa",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold,
                color = PrimaryColor
            )
            Text(
                text = "Teman perjalananmu yang setia",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
            ) {
                Text("Mulai Petualangan", fontSize = 18.sp)
            }
        }
    }
}

// --- SCREEN: LIST MODERN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RekomendasiTempatScreen(
    firestore: FirebaseFirestore,
    onBackToLogin: (() -> Unit)? = null,
    onGallerySelected: () -> Unit
) {
    var daftarTempatWisata by remember { mutableStateOf(listOf<TempatWisata>()) }
    var showTambahDialog by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        firestore.collection("tempat_wisata").addSnapshotListener { snapshot, _ ->
            val tempatWisataList = mutableListOf<TempatWisata>()
            if (snapshot != null) {
                for (document in snapshot) {
                    val tempatWisata = document.toObject(TempatWisata::class.java)
                    tempatWisataList.add(tempatWisata)
                }
                daftarTempatWisata = tempatWisataList
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text("Travelupa Menu", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall, color = PrimaryColor)
                NavigationDrawerItem(
                    label = { Text("Gallery") },
                    selected = false,
                    icon = { Icon(Icons.Default.PhotoLibrary, null) },
                    onClick = { onGallerySelected() }
                )
                NavigationDrawerItem(
                    label = { Text("Logout") },
                    selected = false,
                    icon = { Icon(Icons.Default.ExitToApp, null) },
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            onBackToLogin?.invoke()
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Destinasi Wisata", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PrimaryColor,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showTambahDialog = true },
                    containerColor = PrimaryColor,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Filled.Add, "Tambah")
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .background(BackgroundColor),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(daftarTempatWisata) { tempat ->
                    TempatItemCard(tempat) {
                        // Delete logic
                        firestore.collection("tempat_wisata").whereEqualTo("nama", tempat.nama).get()
                            .addOnSuccessListener { querySnapshot ->
                                for (doc in querySnapshot) {
                                    firestore.collection("tempat_wisata").document(doc.id).delete()
                                }
                            }
                    }
                }
            }
        }
    }

    if (showTambahDialog) {
        TambahTempatWisataDialog(
            firestore = firestore,
            context = context,
            onDismiss = { showTambahDialog = false },
            onTambah = { _, _, _ -> showTambahDialog = false }
        )
    }
}

// --- MODERN CARD ITEM ---
@Composable
fun TempatItemCard(tempat: TempatWisata, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            Box {
                Image(
                    painter = tempat.gambarUriString?.let { uriString ->
                        rememberAsyncImagePainter(
                            ImageRequest.Builder(LocalContext.current).data(Uri.parse(uriString)).build()
                        )
                    } ?: painterResource(id = R.drawable.ic_launcher_background),
                    contentDescription = tempat.nama,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = ContentScale.Crop
                )
                // Overlay Gradient Text
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                startY = 300f
                            )
                        )
                )

                // Option Menu
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    IconButton(
                        onClick = { expanded = true },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.7f))
                    ) {
                        Icon(Icons.Default.MoreVert, "Options")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Hapus") },
                            onClick = {
                                expanded = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = tempat.nama,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Jawa Timur, Indonesia", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = tempat.deskripsi,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.DarkGray
                )
            }
        }
    }
}

// --- DIALOG INPUT MODERN ---
@Composable
fun TambahTempatWisataDialog(
    firestore: FirebaseFirestore,
    context: Context,
    onDismiss: () -> Unit,
    onTambah: (String, String, String?) -> Unit
) {
    var nama by remember { mutableStateOf("") }
    var deskripsi by remember { mutableStateOf("") }
    var gambarUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val gambarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> gambarUri = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Destinasi", fontWeight = FontWeight.Bold, color = PrimaryColor) },
        text = {
            Column {
                OutlinedTextField(
                    value = nama,
                    onValueChange = { nama = it },
                    label = { Text("Nama Tempat") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = deskripsi,
                    onValueChange = { deskripsi = it },
                    label = { Text("Deskripsi Singkat") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 3
                )
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clickable { gambarLauncher.launch("image/*") },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.LightGray.copy(alpha = 0.3f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (gambarUri != null) {
                            Image(
                                painter = rememberAsyncImagePainter(gambarUri),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Image, null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                                Text("Pilih Gambar", color = Color.Gray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nama.isNotBlank() && deskripsi.isNotBlank() && gambarUri != null) {
                        isUploading = true
                        val tempatWisata = TempatWisata(nama, deskripsi)
                        uploadImageToFirestore(firestore, context, gambarUri!!, tempatWisata,
                            onSuccess = { uploadedTempat ->
                                isUploading = false
                                onTambah(nama, deskripsi, uploadedTempat.gambarUriString)
                                onDismiss()
                            },
                            onFailure = { isUploading = false }
                        )
                    }
                },
                enabled = !isUploading,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
            ) {
                if (isUploading) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                else Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal", color = Color.Gray) }
        }
    )
}

// --- GALLERY SCREEN MODERN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(imageDao: ImageDao, onImageSelected: (Uri) -> Unit, onBack: () -> Unit) {
    val images by imageDao.getAllImages().collectAsState(initial = emptyList())
    var showAddImageDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Galeri Foto") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddImageDialog = true },
                containerColor = SecondaryColor
            ) {
                Icon(Icons.Filled.CameraAlt, "Add Image", tint = Color.White)
            }
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.padding(paddingValues),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(images) { image ->
                Card(
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(image.localPath),
                        contentDescription = null,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { onImageSelected(Uri.parse(image.localPath)) },
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
        if (showAddImageDialog) {
            AddImageDialog(
                onDismiss = { showAddImageDialog = false },
                onImageAdded = { uri ->
                    try {
                        val localPath = saveImageLocally(context, uri)
                        val newImage = ImageEntity(localPath = localPath)
                        CoroutineScope(Dispatchers.IO).launch { imageDao.insert(newImage) }
                        showAddImageDialog = false
                    } catch (e: Exception) { Log.e("Gallery", "Error", e) }
                }
            )
        }
    }
}

// --- HELPER FUNCTIONS (Tetap sama, hanya formatting) ---
@Composable
fun AddImageDialog(onDismiss: () -> Unit, onImageAdded: (Uri) -> Unit) {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> imageUri = uri }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { imageUri = saveBitmapToUri(context, it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload Foto Baru") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (imageUri != null) {
                    Image(painter = rememberAsyncImagePainter(imageUri), contentDescription = null, modifier = Modifier.size(150.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { imagePickerLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(4.dp)); Text("Galeri") }
                    Button(onClick = { cameraLauncher.launch(null) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Camera, null); Spacer(Modifier.width(4.dp)); Text("Kamera") }
                }
            }
        },
        confirmButton = {
            Button(onClick = { imageUri?.let { onImageAdded(it) } }, enabled = imageUri != null) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

fun uploadImageToFirestore(
    firestore: FirebaseFirestore, context: Context, imageUri: Uri, tempatWisata: TempatWisata,
    onSuccess: (TempatWisata) -> Unit, onFailure: (Exception) -> Unit
) {
    val db = Room.databaseBuilder(context, AppDatabase::class.java, "travelupa-database").build()
    val imageDao = db.imageDao()
    try {
        val localPath = saveImageLocally(context, imageUri)
        CoroutineScope(Dispatchers.IO).launch {
            imageDao.insert(ImageEntity(localPath = localPath))
            val updatedTempat = tempatWisata.copy(gambarUriString = localPath)
            firestore.collection("tempat_wisata").add(updatedTempat)
                .addOnSuccessListener { onSuccess(updatedTempat) }
                .addOnFailureListener { onFailure(it) }
        }
    } catch (e: Exception) { onFailure(e) }
}

fun saveImageLocally(context: Context, uri: Uri): String {
    val inputStream = context.contentResolver.openInputStream(uri)
    val file = File(context.filesDir, "image_${System.currentTimeMillis()}.jpg")
    val outputStream = FileOutputStream(file)
    inputStream?.copyTo(outputStream)
    inputStream?.close()
    outputStream.close()
    return file.absolutePath
}

fun saveBitmapToUri(context: Context, bitmap: Bitmap): Uri {
    val file = File(context.cacheDir, "${UUID.randomUUID()}.jpg")
    val outputStream = FileOutputStream(file)
    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
    outputStream.close()
    return Uri.fromFile(file)
}