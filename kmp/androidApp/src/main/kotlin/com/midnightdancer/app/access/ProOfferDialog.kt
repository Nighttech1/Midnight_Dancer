package com.midnightdancer.app.access

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.midnightdancer.app.BuildConfig

@Composable
fun ProOfferDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Midnight Dancer Pro") },
        text = {
            Text("Отрабатывай сложные переходы без перемотки: участок, проходы и отдых. " +
                "В Pro также доступен безлимит собственных постановок.\n\n" +
                "Покупка и восстановление подписки пока не подключены. Списаний нет." +
                if (BuildConfig.DEBUG) "\n\nДля проверки доступен тестовый Pro — это не подписка. После включения начни тренировку отдельным нажатием." else "")
        },
        confirmButton = {
            if (BuildConfig.DEBUG) TextButton(onClick = {
                ProductAccessRuntime.setTestPro(true)
                onDismiss()
            }) { Text("Включить тестовый Pro") }
            else TextButton(onClick = onDismiss) { Text("Понятно") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Вернуться") } },
    )
}
