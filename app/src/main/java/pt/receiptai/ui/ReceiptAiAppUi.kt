package pt.receiptai.ui

import android.Manifest
import android.app.Activity
import android.net.Uri
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import pt.receiptai.R
import pt.receiptai.data.repo.RecipeImportCount
import pt.receiptai.domain.ReceiptSource
import pt.receiptai.importer.ImportProgress
import pt.receiptai.ui.components.appViewModel
import pt.receiptai.ui.home.HomeRoute
import pt.receiptai.ui.importreview.ImportUiState
import pt.receiptai.ui.importreview.ImportViewModel
import pt.receiptai.ui.importreview.ReviewScreen
import pt.receiptai.ui.pantry.PantryRoute
import pt.receiptai.ui.pantry.TextInputDialog
import pt.receiptai.ui.recipes.NewRecipeScreen
import pt.receiptai.ui.recipes.RecipesRoute
import pt.receiptai.ui.records.RecordsRoute
import pt.receiptai.ui.stats.StatsRoute
import java.io.File

/** Os 5 separadores da barra de baixo. */
enum class Tab(val route: String, val icon: ImageVector, val label: Int) {
    INICIO("inicio", Icons.Outlined.Home, R.string.tab_home),
    ESTATISTICAS("estatisticas", Icons.Outlined.BarChart, R.string.tab_stats),
    REGISTOS("registos", Icons.AutoMirrored.Outlined.ReceiptLong, R.string.tab_records),
    RECEITAS("receitas", Icons.Outlined.RestaurantMenu, R.string.tab_recipes),
    DESPENSA("despensa", Icons.Outlined.Kitchen, R.string.tab_pantry),
}

/** Conteúdo recebido de outra app (partilhar / abrir com). */
sealed interface SharedContent {
    data class File(val uri: Uri) : SharedContent
    /** Vários ficheiros partilhados de uma vez (ex.: 3 PDFs escolhidos no e-mail). */
    data class Files(val uris: List<Uri>) : SharedContent
    data class Text(val text: String) : SharedContent
    data object OpenPantry : SharedContent
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptAiAppUi(shared: SharedContent?, onSharedConsumed: () -> Unit) {
    val nav = rememberNavController()
    val importVm = appViewModel { ImportViewModel(it) }
    val importState by importVm.state.collectAsStateWithLifecycle()
    val saved by importVm.saved.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = LocalActivity.current

    var showSheet by rememberSaveable { mutableStateOf(false) }
    var showLinkDialog by rememberSaveable { mutableStateOf(false) }
    var pantryTab by rememberSaveable { mutableStateOf(0) }
    // Recibo a abrir nos Registos (tocado no popup do gráfico das Estatísticas).
    var focusReceipt by rememberSaveable { mutableStateOf<Long?>(null) }

    // ---------- Escolher ficheiros: um ou VÁRIOS de uma vez (PDF, imagem, CSV, JSON, XML, TXT, HTML, ZIP) ----------
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importVm.importUris(uris, ReceiptSource.FICHEIRO)
    }
    // ---------- Câmara: foto simples (se o scanner do Google não estiver disponível) ----------
    var photoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = photoUri
        if (ok && uri != null) importVm.importScanned(listOf(uri))
    }
    // ---------- Câmara: scanner de documentos do ML Kit (corta e endireita o recibo) ----------
    val scan = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages.orEmpty().map { it.imageUri }
            if (pages.isNotEmpty()) importVm.importScanned(pages)
        }
    }
    fun openCamera() {
        val fallback = {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val file = File(dir, "recibo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            photoUri = uri
            takePicture.launch(uri)
        }
        if (activity == null) { fallback(); return }
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(3)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { scan.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { fallback() }
    }

    // Pedir autorização para notificações (Android 13+), para os avisos de validade.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Conteúdo partilhado por outra app.
    LaunchedEffect(shared) {
        when (shared) {
            is SharedContent.File -> importVm.importUri(shared.uri, ReceiptSource.PARTILHA)
            is SharedContent.Files -> importVm.importUris(shared.uris, ReceiptSource.PARTILHA)
            is SharedContent.Text -> importVm.importSharedText(shared.text)
            SharedContent.OpenPantry -> { pantryTab = 0; nav.navigateTab(Tab.DESPENSA) }
            null -> Unit
        }
        if (shared != null) onSharedConsumed()
    }

    // Depois de guardar: mensagem + ir para os Registos.
    val savedMessage = saved?.let { pluralStringResource(R.plurals.receipts_saved, it, it) }
    LaunchedEffect(saved) {
        if (savedMessage != null) {
            nav.navigateTab(Tab.REGISTOS)
            snackbar.showSnackbar(savedMessage)
            importVm.consumeSaved()
        }
    }

    // Só vieram receitas (sem recibos): mensagem + ir para as Receitas.
    val recipesAdded by importVm.recipesAdded.collectAsStateWithLifecycle()
    val recipesMessage = recipesAdded?.let { recipesAddedText(it) }
    LaunchedEffect(recipesAdded) {
        if (recipesMessage != null) {
            nav.navigateTab(Tab.RECEITAS)
            snackbar.showSnackbar(recipesMessage)
            importVm.consumeRecipesAdded()
        }
    }

    // O ecrã "Rever recibo" aparece por cima de tudo enquanto há um recibo a rever.
    val review = importState as? ImportUiState.Review
    if (review != null) {
        ReviewScreen(review, importVm)
        return
    }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottomBar = Tab.entries.any { it.route == route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.navigateTab(tab) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(nav, startDestination = Tab.INICIO.route) {
                composable(Tab.INICIO.route) {
                    HomeRoute(
                        onImport = { showSheet = true },
                        onOpenRecords = { nav.navigateTab(Tab.REGISTOS) },
                        onOpenPantry = { pantryTab = 0; nav.navigateTab(Tab.DESPENSA) },
                        onOpenStats = { nav.navigateTab(Tab.ESTATISTICAS) },
                        snackbar = snackbar,
                    )
                }
                composable(Tab.ESTATISTICAS.route) {
                    StatsRoute(onOpenReceipt = { id -> focusReceipt = id; nav.navigateTab(Tab.REGISTOS) })
                }
                composable(Tab.REGISTOS.route) {
                    RecordsRoute(focusReceiptId = focusReceipt, onFocusHandled = { focusReceipt = null })
                }
                composable(Tab.RECEITAS.route) { RecipesRoute(onNewRecipe = { nav.navigate("receita/nova") }) }
                composable(Tab.DESPENSA.route) { PantryRoute(initialTab = pantryTab) }
                composable("receita/nova") { NewRecipeScreen(onClose = { nav.popBackStack() }) }
            }

            (importState as? ImportUiState.Loading)?.let { LoadingOverlay(it.progress) }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(stringResource(R.string.import_sheet_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                SheetOption(Icons.Outlined.UploadFile, R.string.import_file, R.string.import_file_hint) {
                    showSheet = false
                    pickFiles.launch(
                        arrayOf(
                            "application/pdf", "image/*", "text/*", "application/json", "application/xml",
                            "text/xml", "text/csv", "text/comma-separated-values", "text/html",
                            "application/zip", "application/x-zip-compressed",
                        ),
                    )
                }
                SheetOption(Icons.Outlined.CameraAlt, R.string.import_camera, R.string.import_camera_hint) { showSheet = false; openCamera() }
                SheetOption(Icons.Outlined.Link, R.string.import_link, R.string.import_link_hint) { showSheet = false; showLinkDialog = true }
                SheetOption(Icons.Outlined.EditNote, R.string.import_manual, R.string.import_manual_hint) { showSheet = false; importVm.startManual() }
            }
        }
    }

    if (showLinkDialog) {
        TextInputDialog(
            title = stringResource(R.string.import_link),
            label = stringResource(R.string.import_link_label),
            initial = "https://",
            onDismiss = { showLinkDialog = false },
            onConfirm = { showLinkDialog = false; importVm.importUrl(it) },
        )
    }

    (importState as? ImportUiState.Error)?.let { error ->
        AlertDialog(
            onDismissRequest = importVm::cancel,
            title = { Text(stringResource(R.string.import_error_title)) },
            text = { Text(error.message) },
            confirmButton = { TextButton(onClick = importVm::cancel) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { importVm.startManual() }) { Text(stringResource(R.string.import_manual)) } },
        )
    }
}

@Composable
private fun SheetOption(icon: ImageVector, title: Int, hint: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(hint)) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

/** Ecrã de espera. Com vários ficheiros mostra "A ler 3 de 12" e o nome do ficheiro. */
@Composable
private fun LoadingOverlay(progress: ImportProgress?) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.size(16.dp))
            Text(
                if (progress != null && progress.total > 1) stringResource(R.string.import_progress, progress.index, progress.total)
                else stringResource(R.string.import_loading),
                style = MaterialTheme.typography.titleMedium,
            )
            if (progress != null && progress.total > 1) {
                Text(
                    progress.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "3 receitas adicionadas às Receitas · 1 já existia" (ou "Estas receitas já existiam."). */
@Composable
fun recipesAddedText(count: RecipeImportCount): String {
    if (count.added == 0) return stringResource(R.string.recipes_import_none)
    val added = pluralStringResource(R.plurals.recipes_added, count.added, count.added)
    return if (count.alreadyExisted == 0) added
    else added + " · " + pluralStringResource(R.plurals.recipes_already_existed, count.alreadyExisted, count.alreadyExisted)
}

/** Navegar entre separadores sem empilhar ecrãs repetidos. */
fun NavHostController.navigateTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
