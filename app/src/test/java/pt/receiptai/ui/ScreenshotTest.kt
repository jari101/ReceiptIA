package pt.receiptai.ui

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
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
import pt.receiptai.data.db.ReceiptAiDatabase
import pt.receiptai.data.repo.ReceiptRepository
import pt.receiptai.data.repo.RecipeRepository
import pt.receiptai.data.repo.ShoppingRepository
import pt.receiptai.domain.StatsCalculator
import pt.receiptai.domain.StatsPeriod
import pt.receiptai.ui.home.HomeScreen
import pt.receiptai.ui.home.HomeUiState
import pt.receiptai.ui.home.HomeViewModel
import pt.receiptai.ui.pantry.PantryScreen
import pt.receiptai.ui.pantry.PantryViewModel
import pt.receiptai.ui.recipes.RecipesScreen
import pt.receiptai.ui.recipes.RecipesViewModel
import pt.receiptai.ui.records.RecordsScreen
import pt.receiptai.ui.records.RecordsUiState
import pt.receiptai.ui.records.RecordsViewModel
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
    fun setUp() = runBlocking {
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
            today,
        )
    }

    private fun statsState(period: StatsPeriod) = runBlocking {
        val repo = ReceiptRepository(db) { today }
        StatsUiState(period, StatsCalculator.summarize(period, today, repo.receiptSummaries.first(), repo.purchaseLines.first()), canGoForward = false)
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
    @Test fun inicioEscuro() = shot("1_inicio_escuro", dark = true) { HomeScreen(homeState(), {}, {}, {}, {}, {}) }
    @Test fun inicioVazio() = shot("1_inicio_vazio") { HomeScreen(HomeUiState(loading = false, isEmpty = true), {}, {}, {}, {}, {}) }
    @Test fun estatisticasMes() = shot("2_estatisticas_mes") { StatsScreen(statsState(StatsPeriod.MES), {}, {}, {}) }
    @Test fun estatisticasAno() = shot("2_estatisticas_ano") { StatsScreen(statsState(StatsPeriod.ANO), {}, {}, {}) }
    @Test fun estatisticasEscuro() = shot("2_estatisticas_escuro", dark = true) { StatsScreen(statsState(StatsPeriod.SEMANA), {}, {}, {}) }

    /** O popup do gráfico abre numa janela própria, por isso a captura é do ecrã inteiro. */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun estatisticasPopup() {
        compose.setContent {
            ReceiptAiTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background) { StatsScreen(statsState(StatsPeriod.MES), {}, {}, {}) }
            }
        }
        compose.onNodeWithTag(LineChartTag).performTouchInput { click(Offset(width * 0.62f, centerY)) }
        compose.waitForIdle()
        captureScreenRoboImage("../docs/screenshots/2_estatisticas_popup.png")
    }

    @Test fun registos() = shot("3_registos") { RecordsScreen(recordsState(), {}, {}, {}) }
    @Test fun registosEscuro() = shot("3_registos_escuro", dark = true) { RecordsScreen(recordsState(), {}, {}, {}) }
    @Test fun receitas() = shot("4_receitas") { RecipesScreen(recipesState(cookMode = true), {}, {}, {}, {}, {}, {}, {}) }
    @Test fun despensa() = shot("5_despensa") { PantryScreen(pantryState(), initialTab = 0) }
    @Test fun listaDeCompras() = shot("5_lista_compras") { PantryScreen(pantryState(), initialTab = 1) }
}
