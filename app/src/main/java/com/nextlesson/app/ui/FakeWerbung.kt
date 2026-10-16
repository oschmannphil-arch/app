package com.nextlesson.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Spaßbanner "Gold Spin" im Casino-Look. Es ist nur ein Scherz: kein echtes Spiel, kein Link,
 * kein Geld. Ein Tipp zeigt das, das ✕ blendet das Banner bis zum nächsten App-Start aus;
 * ganz abschalten lässt es sich in den Einstellungen (Darstellung).
 */
@Composable
fun FakeWerbung(oben: Boolean, modifier: Modifier = Modifier) {
    val kontext = LocalContext.current
    var weg by remember { mutableStateOf(false) }
    if (weg) return
    val text = if (oben) "🎰 GOLD SPIN · 100 FREISPIELE" else "💰 GOLD SPIN · JETZT DREHEN & GEWINNEN"
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFFB8860B), Color(0xFFFFD700), Color(0xFFB8860B))))
                .clickable { Toast.makeText(kontext, "Gold Spin gibt es nicht – das ist nur Fake-Werbung 😄", Toast.LENGTH_SHORT).show() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, color = Color(0xFF3A2A00), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("✕", color = Color(0xFF3A2A00), fontWeight = FontWeight.Bold, modifier = Modifier.clickable { weg = true }.padding(start = 12.dp))
        }
        Text(
            "Fake-Werbung – kein echtes Spiel, kein Geld",
            fontSize = 10.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 1.dp)
        )
    }
}
