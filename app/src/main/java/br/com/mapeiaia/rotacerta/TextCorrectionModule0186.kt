package br.com.mapeiaia.rotacerta

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TextCorrectionModule0186(
    initialText: String?,
    replacementToken: String?,
    requestKey: String?,
) {
    val context = LocalContext.current
    var original by remember(requestKey) { mutableStateOf(initialText.orEmpty()) }
    var corrected by remember(requestKey) { mutableStateOf("") }
    var analyzed by remember(requestKey) { mutableStateOf(false) }
    var editing by remember(requestKey) { mutableStateOf(false) }
    var changeCount by remember(requestKey) { mutableStateOf(0) }

    DisposableEffect(replacementToken) {
        onDispose { replacementToken?.let(TextReplacementSession0186::clear) }
    }

    fun analyze() {
        if (original.isBlank()) {
            corrected = ""
            analyzed = false
            editing = false
            Toast.makeText(context, "Digite ou cole um texto para corrigir.", Toast.LENGTH_SHORT).show()
            return
        }
        val result = PortugueseTextCorrectionEngine0186.correct(original)
        corrected = result.corrected
        changeCount = result.changeCount
        analyzed = true
        editing = false
    }

    fun copyCorrected() {
        if (corrected.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Texto corrigido", corrected))
        Toast.makeText(context, "Texto corrigido copiado.", Toast.LENGTH_SHORT).show()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Correção de texto", fontWeight = FontWeight.Bold)
        Text(
            "Correção básica e offline de espaços, pontuação, maiúsculas e palavras comuns em português. Revise a sugestão antes de usar.",
        )
        OutlinedTextField(
            value = original,
            onValueChange = {
                original = it.take(12_000)
                analyzed = false
                corrected = ""
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Texto original") },
            minLines = 4,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    original = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty().take(12_000)
                    analyzed = false
                    corrected = ""
                    if (original.isBlank()) Toast.makeText(context, "A área de transferência está vazia.", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
            ) { Text("Colar") }
            Button(onClick = ::analyze, modifier = Modifier.weight(1f)) { Text("Analisar") }
        }

        if (analyzed) {
            Text("Texto corrigido", fontWeight = FontWeight.Bold)
            Text(
                if (changeCount == 0) "Nenhuma alteração básica foi sugerida." else "$changeCount alteração(ões) sugerida(s).",
            )
            OutlinedTextField(
                value = corrected,
                onValueChange = { if (editing) corrected = it.take(12_000) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Resultado revisável") },
                minLines = 4,
                readOnly = !editing,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editing = true }, modifier = Modifier.weight(1f)) { Text("Editar") }
                OutlinedButton(onClick = ::copyCorrected, modifier = Modifier.weight(1f)) { Text("Copiar") }
            }
            val canReplace = remember(replacementToken, corrected) {
                replacementToken != null && TextReplacementSession0186.canReplace(replacementToken)
            }
            if (canReplace) {
                Button(
                    onClick = {
                        val replaced = TextReplacementSession0186.replace(replacementToken, corrected)
                        if (replaced) {
                            Toast.makeText(context, "Texto substituído no campo original.", Toast.LENGTH_SHORT).show()
                        } else {
                            copyCorrected()
                            Toast.makeText(
                                context,
                                "O campo original não está mais disponível. O texto foi copiado para uso manual.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Substituir") }
            } else {
                Text("Use Copiar para aplicar manualmente. Substituir só aparece quando o campo original continua seguro e inalterado.")
            }
        }
    }
}
