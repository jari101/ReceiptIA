package pt.receiptai

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import pt.receiptai.ui.ReceiptAiAppUi
import pt.receiptai.ui.SharedContent
import pt.receiptai.ui.theme.ReceiptAiTheme

class MainActivity : ComponentActivity() {

    /** Recibo que chegou de outra app ("Partilhar com ReceiptAI" / "Abrir com"). */
    private var shared by mutableStateOf<SharedContent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) shared = readIntent(intent)
        setContent {
            ReceiptAiTheme {
                ReceiptAiAppUi(shared = shared, onSharedConsumed = { shared = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        shared = readIntent(intent)
    }

    private fun readIntent(intent: Intent?): SharedContent? {
        intent ?: return null
        if (intent.getBooleanExtra(EXTRA_OPEN_PANTRY, false)) return SharedContent.OpenPantry
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream: Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
                stream?.let { SharedContent.File(it) }
                    ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { SharedContent.Text(it) }
            }
            // Vários ficheiros partilhados de uma vez.
            Intent.ACTION_SEND_MULTIPLE -> {
                val streams: List<Uri> = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }.orEmpty()
                when (streams.size) {
                    0 -> null
                    1 -> SharedContent.File(streams.single())
                    else -> SharedContent.Files(streams)
                }
            }
            Intent.ACTION_VIEW -> intent.data?.let { SharedContent.File(it) }
            else -> null
        }
    }

    companion object {
        const val EXTRA_OPEN_PANTRY = "abrir_despensa"
    }
}
