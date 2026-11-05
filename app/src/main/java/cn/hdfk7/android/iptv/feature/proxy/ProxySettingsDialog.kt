package cn.hdfk7.android.iptv.feature.proxy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.hdfk7.android.iptv.R
import cn.hdfk7.android.iptv.core.data.local.ProxySettingsStore
import cn.hdfk7.android.iptv.core.ui.components.TvIconButton

@Composable
fun ProxySettingsDialog(store: ProxySettingsStore, onClose: () -> Unit, onSaved: () -> Unit) {
    var draft by remember(calculation = { mutableStateOf(store.current) })
    var error by remember(calculation = { mutableStateOf<Int?>(null) })
    val focus = remember(calculation = { FocusRequester() })
    LaunchedEffect(key1 = Unit, block = { focus.requestFocus() })
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false), content = {
        Column(modifier = Modifier.width(560.dp).heightIn(max = 480.dp).background(Color(0xFF111D2E), RoundedCornerShape(22.dp)).padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = {
            Text(text = stringResource(R.string.proxy_title), color = Color.White)
            TvIconButton(description = stringResource(if (draft.enabled) R.string.proxy_on else R.string.proxy_off), icon = R.drawable.ic_power, selected = draft.enabled, onClick = { draft = draft.copy(enabled = !draft.enabled) }, modifier = Modifier.focusRequester(focus))
            OutlinedTextField(value = draft.host, onValueChange = { draft = draft.copy(host = it.trim()) }, label = { Text(stringResource(R.string.proxy_ip)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = draft.port, onValueChange = { draft = draft.copy(port = it) }, label = { Text(stringResource(R.string.proxy_port)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = {
                OutlinedTextField(value = draft.username, onValueChange = { draft = draft.copy(username = it) }, label = { Text(stringResource(R.string.proxy_username)) }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = draft.password, onValueChange = { draft = draft.copy(password = it) }, label = { Text(stringResource(R.string.proxy_password)) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.weight(1f))
            })
            if (error != null) {
                Text(text = stringResource(error!!), color = Color(0xFFFF8A80))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = {
                TvIconButton(description = stringResource(R.string.proxy_save), icon = R.drawable.ic_check, onClick = {
                    if (!draft.valid()) {
                        error = R.string.proxy_invalid
                    } else {
                        try {
                            store.save(draft)
                            onSaved()
                            onClose()
                        } catch (exception: Exception) {
                            error = R.string.storage_error
                        }
                    }
                })
                TvIconButton(description = stringResource(R.string.proxy_cancel), icon = R.drawable.ic_close, onClick = onClose)
            })
        })
    })
}
