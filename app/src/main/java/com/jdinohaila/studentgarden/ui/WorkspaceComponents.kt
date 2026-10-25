package com.jdinohaila.studentgarden.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jdinohaila.studentgarden.R
import com.jdinohaila.studentgarden.domain.*
import com.jdinohaila.studentgarden.ui.theme.*
import java.time.*
import java.time.format.*
import java.util.Locale

@Composable
fun CardsScreen(s: GardenState, p: PaddingValues, nav: (String, String, String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val decks = s.decks.filter { it.title.contains(search, true) }
    LazyColumn(contentPadding = pagePadding(p), modifier = Modifier.fillMaxSize()) {
        item {
            PageTitle(stringResource(R.string.cards), stringResource(R.string.add), "orange") { nav("edit", "deck", "") }
            SearchBox(search) { search = it }
        }
        if (decks.isEmpty() && s.cards.isEmpty()) item { EmptyState(if (search.isBlank()) R.string.empty_decks else R.string.no_results) }
        items(decks, key = { it.id }) { deck ->
            val cards = s.cards.filter { it.deckId == deck.id }
            DeckTile(deck.title, deck.color, cards, { nav("deck", deck.id, "") })
        }
        val unfiled = s.cards.filter { it.deckId == null || s.decks.none { d -> d.id == it.deckId } }
        if (unfiled.isNotEmpty() && (search.isBlank() || unfiled.any { (it.prompt + it.answer).contains(search, true) })) item {
            DeckTile(stringResource(R.string.unfiled), "orange", unfiled, { nav("deck", "", "") })
        }
    }
}

@Composable
private fun DeckTile(title: String, color: String, cards: List<Flashcard>, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = accent(color).copy(alpha = .18f), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Outlined.Style, null, Modifier.padding(12.dp).size(28.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(pluralStringResource(R.plurals.card_count, cards.size, cards.size), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                val due = cards.count { StudyRules.due(it, System.currentTimeMillis()) }
                if (due > 0) Text(stringResource(R.string.due_count, due), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
        }
    }
}

@Composable
fun NoteTile(note: Note, s: GardenState, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(Modifier.heightIn(min = 100.dp)) {
            Box(Modifier.width(5.dp).height(100.dp).background(accent("gold")))
            Column(Modifier.weight(1f).padding(16.dp)) {
                Text(note.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (note.preview.isNotBlank()) Text(note.preview, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (note.media.isNotEmpty()) Text(stringResource(R.string.audio_notes) + " · ${note.media.size}", style = MaterialTheme.typography.labelMedium)
                if (note.images.isNotEmpty()) Icon(Icons.Outlined.PhotoCamera, stringResource(R.string.video_frame), Modifier.size(18.dp))
                if (note.videoId != null) Icon(Icons.Outlined.OndemandVideo, stringResource(R.string.video_notes), Modifier.size(18.dp))
                val course = s.courses.find { it.id == note.courseId }
                if (course != null) Text(course.title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
fun CourseTile(course: Course, s: GardenState, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = accent(course.color).copy(alpha = .2f), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.padding(12.dp))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(course.title, style = MaterialTheme.typography.titleLarge)
                    val detail = listOf(course.code, course.term).filter { it.isNotBlank() }.joinToString(" · ")
                    if (detail.isNotBlank()) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${s.tasks.count { it.courseId == course.id && it.status != "done" }} ${stringResource(R.string.tasks)}", style = MaterialTheme.typography.bodySmall)
                Text("${s.notes.count { it.courseId == course.id && it.videoId == null }} ${stringResource(R.string.notes)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun WeekPicker(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(bottom = 16.dp)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSelect(selected.minusWeeks(1)) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.previous_week)) }
                Text(selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { onSelect(selected.plusWeeks(1)) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.next_week)) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                (0L..6L).forEach { offset ->
                    val day = StudyRules.week(selected).plusDays(offset)
                    val active = selected == day
                    Surface(onClick = { onSelect(day) }, shape = RoundedCornerShape(10.dp), color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent, modifier = Modifier.weight(1f).widthIn(min = 48.dp)) {
                        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = MaterialTheme.typography.labelMedium)
                            Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineBlock(block: StudyBlock, s: GardenState, onClick: () -> Unit, onCheck: (Boolean) -> Unit) {
    val course = s.courses.find { it.id == block.courseId }
    val background = accent(block.color).copy(alpha = .13f).compositeOver(MaterialTheme.colorScheme.surface)
    val marker = agendaAccent(block.color, background)
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.Top) {
        Text(timeLabel(block.startsAt), Modifier.width(64.dp).padding(top = 16.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(onClick = onClick, color = background, contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 10.dp).width(4.dp).height(48.dp).background(marker, RoundedCornerShape(2.dp)))
                Column(Modifier.weight(1f)) {
                    if (course != null) Text(course.title, style = MaterialTheme.typography.labelLarge)
                    Text(block.title, style = MaterialTheme.typography.titleMedium)
                    Text("${block.minutes} ${stringResource(R.string.minutes_unit)}", style = MaterialTheme.typography.bodySmall)
                }
                Checkbox(block.done, onCheck, colors = CheckboxDefaults.colors(
                    checkedColor = marker,
                    uncheckedColor = marker,
                    checkmarkColor = contrastingColor(marker),
                ))
            }
        }
    }
}
