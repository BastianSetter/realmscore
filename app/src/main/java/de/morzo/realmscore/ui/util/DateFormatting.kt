package de.morzo.realmscore.ui.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.morzo.realmscore.R
import java.text.DateFormat
import java.util.Date

@Composable
fun formatRelativeDate(epochMillis: Long): String =
    formatRelativeDate(LocalContext.current, epochMillis)

fun formatRelativeDate(context: Context, epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - epochMillis
    val dayMillis = 24L * 60L * 60L * 1000L
    val days = diff / dayMillis
    return when {
        days < 1L -> context.getString(R.string.date_today)
        days < 2L -> context.getString(R.string.date_yesterday)
        days < 7L -> context.getString(R.string.date_days_ago, days.toInt())
        days < 30L -> context.getString(R.string.date_weeks_ago, (days / 7L).toInt())
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))
    }
}

fun formatShortDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(epochMillis))
