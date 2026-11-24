package pt.receiptai.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pt.receiptai.AppContainer
import pt.receiptai.ReceiptAiApp

/** Cria um ViewModel com acesso ao [AppContainer] (repositórios, importador...). */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as ReceiptAiApp).container
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}
