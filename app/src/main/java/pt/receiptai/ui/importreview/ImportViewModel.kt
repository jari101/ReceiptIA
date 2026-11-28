package pt.receiptai.ui.importreview

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pt.receiptai.AppContainer
import pt.receiptai.domain.Money
import pt.receiptai.domain.ReceiptSource
import pt.receiptai.domain.formatQuantity
import pt.receiptai.domain.parseQuantity
import pt.receiptai.data.repo.RecipeImportCount
import pt.receiptai.importer.ImportProgress
import pt.receiptai.importer.ImportResult
import pt.receiptai.importer.ImportSummary
import pt.receiptai.parser.ParsedItem
import pt.receiptai.parser.ParsedReceipt
import pt.receiptai.parser.ReceiptFileType
import pt.receiptai.ui.components.Dates
import kotlin.math.roundToLong

/** Versão EDITÁVEL de um produto (tudo em texto, como nos campos do formulário). */
data class DraftItem(val key: Long, val name: String, val quantity: String, val price: String)

data class DraftReceipt(val storeName: String, val date: String, val items: List<DraftItem>, val printedTotalCents: Long? = null) {
    val totalCents: Long get() = items.sumOf { Money.parseToCents(it.price) ?: 0 }
}

sealed interface ImportUiState {
    data object Idle : ImportUiState

    /** A ler. Com vários ficheiros (ou um .zip), [progress] diz qual vai a ser lido. */
    data class Loading(val progress: ImportProgress? = null) : ImportUiState

    data class Review(
        val drafts: List<DraftReceipt>,
        val rawText: String,
        val source: ReceiptSource,
        val type: ReceiptFileType,
        val error: String? = null,
        /** Resumo quando vieram vários ficheiros ou um .zip (quantos lidos, quais ignorados). */
        val summary: ImportSummary? = null,
        /** Receitas que vieram no meio dos recibos e já foram guardadas. */
        val recipes: RecipeImportCount? = null,
    ) : ImportUiState
    data class Error(val message: String) : ImportUiState
}

/**
 * Controla o fluxo: importar -> rever/corrigir -> guardar.
 * Fica "acima" da navegação, para ser partilhado pelo Início e pelo ecrã Rever.
 */
class ImportViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    /** Mensagem curta para mostrar depois de guardar ("2 recibos guardados"). */
    private val _saved = MutableStateFlow<Int?>(null)
    val saved: StateFlow<Int?> = _saved.asStateFlow()

    /** Receitas importadas SEM recibos: a app mostra a mensagem e abre o separador Receitas. */
    private val _recipesAdded = MutableStateFlow<RecipeImportCount?>(null)
    val recipesAdded: StateFlow<RecipeImportCount?> = _recipesAdded.asStateFlow()

    private var nextKey = 0L

    fun importUri(uri: Uri, source: ReceiptSource) = importUris(listOf(uri), source)

    /** Um ou vários ficheiros de uma vez (também .zip, com recibos e/ou receitas). */
    fun importUris(uris: List<Uri>, source: ReceiptSource) = run(source) { onProgress ->
        container.importer.fromUris(uris, onProgress)
    }
    fun importUrl(url: String) = run(ReceiptSource.LINK) { container.importer.fromUrl(url) }
    fun importScanned(pages: List<Uri>) = run(ReceiptSource.CAMARA) { container.importer.fromScannedPages(pages) }
    fun importSharedText(text: String) = run(ReceiptSource.PARTILHA) { container.importer.fromSharedText(text) }

    /** Recibo vazio para escrever à mão. */
    fun startManual() {
        _state.value = ImportUiState.Review(listOf(toDraft(ParsedReceipt("", null, emptyList()))), "", ReceiptSource.MANUAL, ReceiptFileType.TEXTO)
    }

    private fun run(source: ReceiptSource, block: suspend (onProgress: (ImportProgress) -> Unit) -> ImportResult) {
        _state.value = ImportUiState.Loading()
        viewModelScope.launch {
            val result = block { progress -> _state.value = ImportUiState.Loading(progress) }
            _state.value = when (result) {
                is ImportResult.Success -> {
                    // As receitas não precisam de revisão: são guardadas logo.
                    val recipes = if (result.recipes.isNotEmpty()) container.recipes.addImported(result.recipes) else null
                    if (result.receipts.isEmpty()) {
                        _recipesAdded.value = recipes
                        ImportUiState.Idle
                    } else {
                        ImportUiState.Review(
                            result.receipts.map(::toDraft), result.rawText, source, result.type,
                            summary = result.summary, recipes = recipes,
                        )
                    }
                }
                is ImportResult.Failure -> ImportUiState.Error(result.message)
            }
        }
    }

    private fun toDraft(r: ParsedReceipt) = DraftReceipt(
        storeName = r.storeName,
        date = Dates.input(r.date ?: container.today()),
        items = r.items.map { DraftItem(nextKey++, it.name, formatQuantity(it.quantity), Money.toPlain(it.totalCents)) },
        printedTotalCents = r.totalCents,
    )

    // ------- edição -------
    fun updateReceipt(index: Int, transform: (DraftReceipt) -> DraftReceipt) {
        val s = _state.value as? ImportUiState.Review ?: return
        _state.value = s.copy(drafts = s.drafts.toMutableList().also { it[index] = transform(it[index]) }, error = null)
    }

    fun updateItem(receipt: Int, key: Long, transform: (DraftItem) -> DraftItem) =
        updateReceipt(receipt) { r -> r.copy(items = r.items.map { if (it.key == key) transform(it) else it }) }

    fun addItem(receipt: Int) = updateReceipt(receipt) { it.copy(items = it.items + DraftItem(nextKey++, "", "1", "")) }
    fun removeItem(receipt: Int, key: Long) = updateReceipt(receipt) { r -> r.copy(items = r.items.filterNot { it.key == key }) }

    // ------- guardar / cancelar -------
    fun save() {
        val s = _state.value as? ImportUiState.Review ?: return
        val receipts = s.drafts.map { d ->
            ParsedReceipt(
                storeName = d.storeName.ifBlank { "Loja desconhecida" },
                date = Dates.parseInput(d.date) ?: container.today(),
                items = d.items.mapNotNull { item ->
                    val total = Money.parseToCents(item.price) ?: return@mapNotNull null
                    if (item.name.isBlank()) return@mapNotNull null
                    val qty = parseQuantity(item.quantity)?.takeIf { it > 0 } ?: 1.0
                    ParsedItem(item.name.trim(), qty, (total / qty).roundToLong(), total)
                },
            )
        }.filter { it.items.isNotEmpty() }

        if (receipts.isEmpty()) {
            _state.value = s.copy(error = "Adiciona pelo menos um produto com nome e preço.")
            return
        }
        viewModelScope.launch {
            val count = container.receipts.saveParsed(receipts, s.source, s.rawText.take(20_000))
            _saved.value = count
            _state.value = ImportUiState.Idle
        }
    }

    fun cancel() { _state.value = ImportUiState.Idle }
    fun consumeSaved() { _saved.value = null }
    fun consumeRecipesAdded() { _recipesAdded.value = null }
}
