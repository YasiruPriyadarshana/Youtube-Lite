package com.slsilverlight.youtubelite.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slsilverlight.youtubelite.data.Video
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    state: FeedState,
    recentSearches: List<String>,
    loadSuggestions: suspend (String) -> List<String>,
    onSearch: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onRefresh: () -> Unit,
    onVideoClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    // Suggestions show while the user is typing and hide once a search runs.
    var showSuggestions by rememberSaveable { mutableStateOf(true) }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (!state.loaded) focus.requestFocus() }

    val query = field.text.trim()
    LaunchedEffect(query) {
        if (query.isEmpty()) {
            suggestions = emptyList()
        } else {
            delay(250) // Wait for a pause in typing before asking.
            suggestions = loadSuggestions(query)
        }
    }

    fun runSearch(text: String) {
        field = TextFieldValue(text, TextRange(text.length))
        showSuggestions = false
        keyboard?.hide()
        focusManager.clearFocus()
        onSearch(text)
    }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = field,
            onValueChange = {
                if (it.text != field.text) showSuggestions = true
                field = it
            },
            placeholder = { Text("Search YouTube") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (field.text.isNotEmpty()) {
                    IconButton(onClick = {
                        field = TextFieldValue("")
                        showSuggestions = true
                        focus.requestFocus()
                    }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (query.isNotEmpty()) runSearch(query) }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .focusRequester(focus)
                .onFocusChanged { if (it.isFocused) showSuggestions = true },
        )

        if (showSuggestions) {
            val showingRecent = query.isEmpty()
            val rows = if (showingRecent) recentSearches else suggestions
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows, key = { it }) { text ->
                    ListItem(
                        headlineContent = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = {
                            Icon(if (showingRecent) Icons.Filled.History else Icons.Filled.Search, contentDescription = null)
                        },
                        trailingContent = {
                            if (showingRecent) {
                                IconButton(onClick = { onRemoveRecent(text) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove from recent searches")
                                }
                            } else {
                                // Put the suggestion in the box to keep typing, like the YouTube app.
                                IconButton(onClick = { field = TextFieldValue("$text ", TextRange(text.length + 1)) }) {
                                    Icon(Icons.Filled.NorthWest, contentDescription = "Edit this search")
                                }
                            }
                        },
                        modifier = Modifier.clickable { runSearch(text) },
                    )
                }
            }
        } else if (state.loaded || state.loading) {
            FeedScreen(
                state = state,
                emptyMessage = "No videos found.",
                onRefresh = onRefresh,
                onVideoClick = onVideoClick,
            )
        }
    }
}
