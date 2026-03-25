package com.example.docscanner.presentation.apphome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.docscanner.domain.model.ApplicationType

private val Coral = Color(0xFFE8603C)
private val Ink = Color(0xFF1A1A2E)
private val InkMid = Color(0xFF6B6878)
private val BgBase = Color(0xFFFAF8F5)
private val BgCard = Color(0xFFFFFFFF)
private val StrokeLight = Color(0xFFE5E2DD)

@Composable
fun ApplicationTypeScreen(
    onBack: () -> Unit,
    onTypeSelected: (ApplicationType) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(BgBase)
            .systemBarsPadding()
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Ink, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text(
                    "Application Type",
                    color = Ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Select the type of application",
                    color = InkMid,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(ApplicationType.entries) { type ->
                ApplicationTypeCard(type = type, onClick = { onTypeSelected(type) })
            }
        }
    }
}

@Composable
private fun ApplicationTypeCard(
    type: ApplicationType,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, StrokeLight, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(type.icon, fontSize = 32.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            type.displayName,
            color = Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}