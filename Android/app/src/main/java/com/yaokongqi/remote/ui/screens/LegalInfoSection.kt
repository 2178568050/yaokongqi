package com.yaokongqi.remote.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.yaokongqi.remote.legal.LegalTexts

@Composable
fun LegalInfoSection() {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "${LegalTexts.APP_NAME} (${LegalTexts.APP_NAME_EN}) v${LegalTexts.VERSION}",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            LegalTexts.COPYRIGHT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "许可：${LegalTexts.LICENSE_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

        LegalBlock(title = "开源许可", body = LegalTexts.LICENSE_SUMMARY)
        LegalBlock(title = "免责声明", body = LegalTexts.DISCLAIMER_SUMMARY)
        LegalBlock(title = "使用规范", body = LegalTexts.USAGE_SUMMARY)

        HorizontalDivider()

        Text("联系作者", style = MaterialTheme.typography.labelLarge)
        Text(
            "问题反馈、商业授权、安全漏洞（请私下报告）：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:${LegalTexts.CONTACT_EMAIL}")
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                LegalTexts.CONTACT_EMAIL,
                textDecoration = TextDecoration.Underline,
            )
        }

        HorizontalDivider()

        Text("开源仓库", style = MaterialTheme.typography.labelLarge)
        Text(
            LegalTexts.STAR_THANKS,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LegalTexts.REPO_URL))
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                LegalTexts.REPO_URL,
                textDecoration = TextDecoration.Underline,
            )
        }

        Text(
            LegalTexts.REPO_HINT,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LegalBlock(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        body,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
