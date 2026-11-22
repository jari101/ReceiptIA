package pt.receiptai.data.db

import androidx.room.TypeConverter
import pt.receiptai.domain.DietTag
import java.time.LocalDate

/** Ensina o Room a guardar tipos que a base de dados (SQLite) não conhece. */
class Converters {
    @TypeConverter fun dateToLong(date: LocalDate?): Long? = date?.toEpochDay()
    @TypeConverter fun longToDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter fun tagsToString(tags: Set<DietTag>): String = tags.joinToString(",") { it.name }
    @TypeConverter fun stringToTags(value: String): Set<DietTag> =
        value.split(',').filter { it.isNotBlank() }.mapNotNull { runCatching { DietTag.valueOf(it) }.getOrNull() }.toSet()
}
