package com.music.konnekt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.konnekt.R
import com.music.konnekt.auth.KonnektCloud
import kotlinx.coroutines.launch

@Composable
fun KonnektCloudDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signedInAs by KonnektCloud.username.collectAsStateWithLifecycle()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var registering by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val savedMessage = stringResource(R.string.cloud_saved)
    val restoredMessage = stringResource(R.string.cloud_restored)
    val signedInMessage = stringResource(R.string.cloud_signed_in)

    fun runAction(success: String, action: suspend () -> Result<Unit>) {
        if (busy) return
        busy = true
        error = null
        status = null
        scope.launch {
            action().fold(
                onSuccess = { status = success },
                onFailure = { error = it.message ?: context.getString(R.string.unknown_error) },
            )
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.cloud_account_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (signedInAs == null) {
                    Text(
                        stringResource(R.string.cloud_account_intro),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.cloud_username)) },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.cloud_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = {
                                runAction(signedInMessage) {
                                    if (registering) {
                                        val created = KonnektCloud.register(context, username, password)
                                        if (created.isSuccess) KonnektCloud.save(context) else created
                                    } else KonnektCloud.signIn(context, username, password)
                                }
                            },
                            enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(if (registering) R.string.cloud_create else R.string.cloud_sign_in))
                        }
                        TextButton(onClick = { registering = !registering; error = null }, enabled = !busy) {
                            Text(stringResource(if (registering) R.string.cloud_have_account else R.string.cloud_new_account))
                        }
                    }
                    Text(
                        stringResource(R.string.cloud_password_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(stringResource(R.string.cloud_connected_as, signedInAs.orEmpty()))
                    Text(
                        stringResource(R.string.cloud_scope),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (confirmRestore) {
                        Text(stringResource(R.string.cloud_restore_warning))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = {
                                    confirmRestore = false
                                    runAction(restoredMessage) { KonnektCloud.restore(context) }
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.cloud_restore_confirm)) }
                            TextButton(onClick = { confirmRestore = false }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = { runAction(savedMessage) { KonnektCloud.save(context) } },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.cloud_save)) }
                            OutlinedButton(
                                onClick = { confirmRestore = true },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.cloud_restore))
                            }
                        }
                        TextButton(onClick = { KonnektCloud.signOut(context); status = null }, enabled = !busy) {
                            Text(stringResource(R.string.cloud_sign_out))
                        }
                    }
                }
                if (busy) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                status?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.close)) }
        },
    )
}
