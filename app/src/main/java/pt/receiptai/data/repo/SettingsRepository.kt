package pt.receiptai.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.receiptai.domain.ChartStyle
import pt.receiptai.domain.DietTag
import pt.receiptai.domain.StatsPeriod

private val Context.dataStore by preferencesDataStore(name = "preferencias")

/** Preferências pequenas do utilizador (período e tipo de gráfico das estatísticas, dieta). */
class SettingsRepository(private val context: Context) {
    private val periodKey = stringPreferencesKey("periodo_estatisticas")
    private val dietKey = stringSetPreferencesKey("dieta")
    private val chartStyleKey = stringPreferencesKey("tipo_grafico")

    val statsPeriod: Flow<StatsPeriod> = context.dataStore.data.map { prefs ->
        prefs[periodKey]?.let { runCatching { StatsPeriod.valueOf(it) }.getOrNull() } ?: StatsPeriod.MES
    }
    /** Tipo de gráfico das estatísticas. Na primeira vez são as barras (pilares). */
    val chartStyle: Flow<ChartStyle> = context.dataStore.data.map { prefs ->
        prefs[chartStyleKey]?.let { runCatching { ChartStyle.valueOf(it) }.getOrNull() } ?: ChartStyle.BARRAS
    }
    val diets: Flow<Set<DietTag>> = context.dataStore.data.map { prefs ->
        prefs[dietKey].orEmpty().mapNotNull { runCatching { DietTag.valueOf(it) }.getOrNull() }.toSet()
    }

    suspend fun setStatsPeriod(period: StatsPeriod) { context.dataStore.edit { it[periodKey] = period.name } }
    suspend fun setChartStyle(style: ChartStyle) { context.dataStore.edit { it[chartStyleKey] = style.name } }
    suspend fun setDiets(diets: Set<DietTag>) { context.dataStore.edit { it[dietKey] = diets.map(DietTag::name).toSet() } }
}
