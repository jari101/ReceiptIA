package pt.receiptai.importer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import pt.receiptai.parser.OcrLine
import pt.receiptai.parser.OcrLineAssembler
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Espera pelo resultado de uma tarefa do Google Play Services dentro de uma coroutine. */
suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

/**
 * OCR com o Google ML Kit: funciona no telemóvel, sem Internet e é gratuito.
 */
class ReceiptOcr {
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(bitmap: Bitmap): String = read(InputImage.fromBitmap(bitmap, 0))

    /** A partir de um ficheiro: o ML Kit já corrige a rotação da foto (EXIF). */
    suspend fun read(context: Context, uri: Uri): String = read(InputImage.fromFilePath(context, uri))

    private suspend fun read(image: InputImage): String {
        val result = client.process(image).awaitResult()
        val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            OcrLine(line.text, box.left, box.top, box.right, box.bottom)
        }
        return OcrLineAssembler.assemble(lines)
    }
}
