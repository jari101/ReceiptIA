package pt.receiptai.ui

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.receiptai.AppContainer
import pt.receiptai.data.db.ReceiptAiDatabase
import pt.receiptai.data.repo.ReceiptRepository
import pt.receiptai.data.repo.RecipeImportCount
import pt.receiptai.data.repo.RecipeRepository
import pt.receiptai.data.repo.ShoppingRepository
import pt.receiptai.domain.ChartStyle
import pt.receiptai.domain.ReceiptSource
import pt.receiptai.domain.StatsCalculator
import pt.receiptai.domain.StatsPeriod
import pt.receiptai.importer.ImportSummary
import pt.receiptai.parser.ReceiptFileType
import pt.receiptai.ui.home.HomeScreen
import pt.receiptai.ui.home.HomeUiState
import pt.receiptai.ui.home.HomeViewModel
import pt.receiptai.ui.importreview.DraftItem
import pt.receiptai.ui.importreview.DraftReceipt
import pt.receiptai.ui.importreview.ImportUiState
import pt.receiptai.ui.importreview.ImportViewModel
import pt.receiptai.ui.importreview.ReviewScreen
import pt.receiptai.ui.pantry.PantryScreen
import pt.receiptai.ui.pantry.PantryViewModel
import pt.receiptai.ui.recipes.RecipesScreen
import pt.receiptai.ui.recipes.RecipesViewModel
import pt.receiptai.ui.records.RecordsScreen
import pt.receiptai.ui.records.RecordsUiState
import pt.receiptai.ui.records.RecordsViewModel
import pt.receiptai.ui.stats.BarChartTag
import pt.receiptai.ui.stats.ChartStyleMenuTag
import pt.receiptai.ui.stats.LineChartTag
import pt.receiptai.ui.stats.StatsScreen
import pt.receiptai.ui.stats.StatsUiState
import pt.receiptai.ui.theme.ReceiptAiTheme
import java.time.LocalDate

/**
 * Desenha cada ecrã com os dados de exemplo e guarda uma imagem em docs/screenshots.
 * Gerar as imagens:  ./gradlew recordRoborazziDebug
 * (No `testDebugUnitTest` normal, os ecrãs são desenhados mas nenhuma imagem é gravada.)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 19)
    private lateinit var db: ReceiptAiDatabase

    @Before
    fun setUp(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReceiptAiDatabase::class.java)
            .allowMainThreadQueries().build()
        ReceiptRepository(db) { today }.loadDemoData()
        RecipeRepository(db).seedIfEmpty()
        ShoppingRepository(db).run { add("Detergente da roupa"); add("Azeite") }
    }

    @After
    fun tearDown() = db.close()

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            ReceiptAiTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        compose.onRoot().captureRoboImage("../docs/screenshots/$name.png")
    }

    private fun homeState(): HomeUiState = runBlocking {
        val dao = db.receiptDao()
        HomeViewModel.buildState(
            dao.observeCount().first(),
            dao.observeReceipts().first().map { it.purchasedOn to it.totalCents },
            dao.observeLatest().first(),
            db.pantryDao().observeActive().first(),
            db.shoppingDao().observeAll().first(),
            today,
        )
    }

    private fun statsState(period: StatsPeriod, style: ChartStyle = ChartStyle.BARRAS) = runBlocking {
        val repo = ReceiptRepository(db) { today }
        StatsUiState(period, style, StatsCalculator.summarize(period, today, repo.receiptSummaries.first(), repo.purchaseLines.first()), canGoForward = false)
    }

    private fun recordsState(): RecordsUiState = runBlocking {
        val all = db.receiptDao().observeAll().first()
        RecordsUiState(loading = false, groups = RecordsViewModel.group(all), expanded = setOf(all.first().receipt.id), totalReceipts = all.size)
    }

    private fun pantryState() = runBlocking {
        PantryViewModel.buildState(
            db.pantryDao().observeActive().first(),
            db.shoppingDao().observeAll().first(),
            ReceiptRepository(db) { today }.purchaseLines.first(),
            today,
        )
    }

    private fun recipesState(cookMode: Boolean) = runBlocking {
        val recipes = db.recipeDao().observeAll().first()
        val state = RecipesViewModel.buildState(recipes, db.pantryDao().observeActive().first(), null, emptySet(), cookMode, emptySet(), today)
        state.copy(expanded = setOfNotNull(state.recipes.firstOrNull()?.match?.recipe?.id))
    }

    @Test fun inicio() = shot("1_inicio") { HomeScreen(homeState(), {}, {}, {}, {}, {}) }
    /** No tema escuro, o 1.º alerta já está na lista de compras (mostra o ✓ em vez do 🛒). */
    @Test fun inicioEscuro() = shot("1_inicio_escuro", dark = true) {
        val state = homeState()
        HomeScreen(state.copy(alerts = state.alerts.mapIndexed { i, a -> a.copy(onList = i == 0) }), {}, {}, {}, {}, {})
    }
    @Test fun inicioVazio() = shot("1_inicio_vazio") { HomeScreen(HomeUiState(loading = false, isEmpty = true), {}, {}, {}, {}, {}) }
    @Test fun estatisticasMes() = shot("2_estatisticas_mes") { StatsScreen(statsState(StatsPeriod.MES), {}, {}, {}) }
    @Test fun estatisticasAno() = shot("2_estatisticas_ano") { StatsScreen(statsState(StatsPeriod.ANO, ChartStyle.RETA), {}, {}, {}) }
    @Test fun estatisticasCurva() = shot("2_estatisticas_curva") { StatsScreen(statsState(StatsPeriod.MES, ChartStyle.CURVA), {}, {}, {}) }
    @Test fun estatisticasEscuro() = shot("2_estatisticas_escuro", dark = true) { StatsScreen(statsState(StatsPeriod.SEMANA), {}, {}, {}) }

    /** O popup e o menu abrem numa janela própria, por isso a captura é do ecrã inteiro. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun screenShot(name: String, state: StatsUiState, action: () -> Unit) {
        compose.setContent {
            ReceiptAiTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background) { StatsScreen(state, {}, {}, {}) }
            }
        }
        action()
        compose.waitForIdle()
        captureScreenRoboImage("../docs/screenshots/$name.png")
    }

    @Test
    fun estatisticasPopup() = screenShot("2_estatisticas_popup", statsState(StatsPeriod.MES, ChartStyle.RETA)) {
        compose.onNodeWithTag(LineChartTag).performTouchInput { click(Offset(width * 0.62f, centerY)) }
    }

    @Test
    fun estatisticasPopupBarras() = screenShot("2_estatisticas_popup_barras", statsState(StatsPeriod.MES)) {
        compose.onNodeWithTag(BarChartTag).performTouchInput { click(Offset(width * 0.5f, centerY)) }
    }

    @Test
    fun estatisticasMenu() = screenShot("2_estatisticas_menu", statsState(StatsPeriod.MES, ChartStyle.RETA)) {
        compose.onNodeWithTag(ChartStyleMenuTag).performClick()
    }

    @Test fun registos() = shot("3_registos") { RecordsScreen(recordsState(), {}, {}, {}) }
    @Test fun registosEscuro() = shot("3_registos_escuro", dark = true) { RecordsScreen(recordsState(), {}, {}, {}) }
    @Test fun receitas() = shot("4_receitas") { RecipesScreen(recipesState(cookMode = true), {}, {}, {}, {}, {}, {}, {}) }
    /** Uma receita a que falta um ingrediente OPCIONAL, com o balão aberto (tema escuro). */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun receitasOpcional() {
        val all = recipesState(cookMode = false)
        // A receita mais completa entre as que têm um ingrediente opcional em falta.
        val card = all.recipes.filter { c -> c.match.missing.any { it in c.match.recipe.optional } }
            .maxBy { it.match.requiredCoverage }
        val optional = card.match.missing.first { it in card.match.recipe.optional }
        val state = all.copy(recipes = listOf(card), expanded = setOf(card.match.recipe.id))
        compose.setContent {
            ReceiptAiTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background) { RecipesScreen(state, {}, {}, {}, {}, {}, {}, {}) }
            }
        }
        compose.onNodeWithText(optional).performClick()
        compose.waitForIdle()
        captureScreenRoboImage("../docs/screenshots/4_receitas_opcional.png")
    }

    /** Ecrã Rever depois de importar um .zip com recibos e receitas: o cartão de resumo no topo. */
    @Test
    fun importarResumo() {
        val vm = ImportViewModel(AppContainer(ApplicationProvider.getApplicationContext()) { today })
        val state = ImportUiState.Review(
            drafts = listOf(
                DraftReceipt(
                    "Mercado do Bairro", "15/11/2026",
                    listOf(DraftItem(1, "Leite meio gordo 1L", "6", "5,34"), DraftItem(2, "Bananas", "1,2", "1,55")),
                ),
                DraftReceipt("Padaria Aurora", "17/11/2026", listOf(DraftItem(3, "Pão de mistura", "1", "1,20"))),
            ),
            rawText = "",
            source = ReceiptSource.FICHEIRO,
            type = ReceiptFileType.ZIP,
            summary = ImportSummary("recibos-e-receitas.zip", fileCount = 7, readCount = 5, ignored = listOf("notas.docx", "foto-desfocada.jpg")),
            recipes = RecipeImportCount(added = 3, alreadyExisted = 1),
        )
        shot("6_importar_resumo", dark = true) { ReviewScreen(state, vm) }
    }

    @Test fun despensa() = shot("5_despensa") { PantryScreen(pantryState(), initialTab = 0) }
    @Test fun listaDeCompras() = shot("5_lista_compras") { PantryScreen(pantryState(), initialTab = 1) }
}
