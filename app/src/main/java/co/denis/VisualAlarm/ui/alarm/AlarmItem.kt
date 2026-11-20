package co.denis.VisualAlarm.ui.alarm

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.denis.VisualAlarm.StoredAlarm
import java.util.Locale



@Composable
fun AlarmItem(
    alarm: StoredAlarm,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .combinedClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time display - takes most space
        Text(
            text = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour.value, alarm.minute.value),
            style = MaterialTheme.typography.displayLarge,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f)
        )

        // Enable/Disable switch
        Switch(
            checked = alarm.enabled,
            onCheckedChange = if (enabled) { newEnabled ->
                onToggle(alarm.id, newEnabled)
            } else null,
            enabled = enabled
        )

        // Delete alarm button
        Button(
            onClick = { onDelete(alarm.id) },
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete alarm"
            )
        }
    }
}