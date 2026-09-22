package com.example.phoneguard.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private enum class MockDeviceState {
  ALLOWED,
  LOCKED,
}

@Composable
fun ParentDashboardScreen(
  modifier: Modifier = Modifier,
) {
  var deviceState by remember { mutableStateOf(MockDeviceState.ALLOWED) }
  var lastCommand by remember { mutableStateOf("Nema poslatih komandi") }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 32.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    Text(
      text = "PhoneGuard Parent",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
    )

    Text(
      text = "Roditeljska kontrola",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Test uređaj",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
          text = "Redmi Note 10",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
        )

        Text(
          text =
            when (deviceState) {
              MockDeviceState.ALLOWED -> "● Telefon je dostupan"
              MockDeviceState.LOCKED -> "● Telefon je zaključan"
            },
          style = MaterialTheme.typography.bodyLarge,
        )

        Text(
          text = "Mock režim — backend još nije povezan",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "Brze komande",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )

        Button(
          onClick = {
            deviceState = MockDeviceState.LOCKED
            lastCommand = "LOCK NOW"
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("LOCK NOW")
        }

        OutlinedButton(
          onClick = {
            deviceState = MockDeviceState.ALLOWED
            lastCommand = "UNLOCK"
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("UNLOCK")
        }

        Text(
          text = "Dodatno vreme",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = {
              deviceState = MockDeviceState.ALLOWED
              lastCommand = "+15 min"
            },
            modifier = Modifier.weight(1f),
          ) {
            Text("+15")
          }

          OutlinedButton(
            onClick = {
              deviceState = MockDeviceState.ALLOWED
              lastCommand = "+30 min"
            },
            modifier = Modifier.weight(1f),
          ) {
            Text("+30")
          }

          OutlinedButton(
            onClick = {
              deviceState = MockDeviceState.ALLOWED
              lastCommand = "+60 min"
            },
            modifier = Modifier.weight(1f),
          ) {
            Text("+60")
          }
        }
      }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = "Poslednja komanda",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = lastCommand,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "Sledeći korak: uparivanje Parent i Child aplikacije i zamena mock komandi stvarnim daljinskim komandama.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun ParentDashboardPreview() {
  MaterialTheme {
    ParentDashboardScreen()
  }
}
