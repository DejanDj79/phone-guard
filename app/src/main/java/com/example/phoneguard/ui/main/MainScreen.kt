package com.example.phoneguard.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.phoneguard.theme.PhoneGuardTheme

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
) {
  var isLocked by rememberSaveable { mutableStateOf(false) }

  if (isLocked) {
    LockScreen(onUnlock = { isLocked = false })
  } else {
    ChildDashboard(
      modifier = modifier,
      onTestLock = { isLocked = true },
    )
  }
}

@Composable
private fun ChildDashboard(
  onTestLock: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    Text(
      text = "PhoneGuard",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
    )

    Text(
      text = "Child device",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Protection",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "● Not active",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Schedule",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "No schedule configured",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }

    Spacer(modifier = Modifier.weight(1f))

    Button(
      onClick = onTestLock,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("TEST LOCK")
    }
  }
}

@Composable
private fun LockScreen(
  onUnlock: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = "🔒",
        fontSize = 56.sp,
      )

      Spacer(modifier = Modifier.height(28.dp))

      Text(
        text = "Telefon je trenutno zaključan",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "Ponovo dostupno u",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Text(
        text = "07:00",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
      )

      Spacer(modifier = Modifier.height(40.dp))

      OutlinedButton(
        onClick = onUnlock,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Otključaj roditeljskim PIN-om")
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "Prototype: PIN provera se dodaje u sledećem koraku.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun ChildDashboardPreview() {
  PhoneGuardTheme {
    ChildDashboard(onTestLock = {})
  }
}

@Preview(showBackground = true)
@Composable
private fun LockScreenPreview() {
  PhoneGuardTheme {
    LockScreen(onUnlock = {})
  }
}
