package com.example.phoneguard.parent.ui

import com.example.phoneguard.parent.R
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.phoneguard.core.InstalledAppInfo

@Composable
internal fun ParentAppIcon(
  app: InstalledAppInfo,
  modifier: Modifier = Modifier,
) {
  val image =
    remember(app.iconBase64) {
      app.iconBase64
        ?.takeIf { it.isNotBlank() }
        ?.let { encoded ->
          runCatching {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
          }.getOrNull()
        }
    }

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center,
    ) {
      if (image != null) {
        Image(
          bitmap = image,
          contentDescription = app.label + " icon",
          modifier =
            Modifier
              .fillMaxSize()
              .padding(3.dp),
          contentScale = ContentScale.Fit,
        )
      } else {
        Icon(
          painter = painterResource(R.drawable.pg_icon_apps),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(9.dp),
        )
      }
    }
  }
}
