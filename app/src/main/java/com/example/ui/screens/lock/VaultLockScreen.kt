package com.example.ui.screens.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DeepNavy800
import com.example.ui.theme.DeepNavy900
import com.example.ui.theme.DeepNavy950
import com.example.ui.theme.ElectricBlue400
import com.example.ui.theme.EmeraldVault

@Composable
fun VaultLockScreen(
    hasBiometrics: Boolean,
    hasVaultPin: Boolean,
    errorMessage: String?,
    onRequestBiometricUnlock: () -> Unit,
    onVerifyPin: (String) -> Unit,
    onBypassIfNoCredentials: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }

    LaunchedEffect(hasBiometrics) {
        if (hasBiometrics) {
            onRequestBiometricUnlock()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepNavy950, DeepNavy900, DeepNavy800)
                )
            )
            .systemBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(ElectricBlue400.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "Vault Locked",
                    tint = ElectricBlue400,
                    modifier = Modifier.size(36.dp)
                )
            }

            Text(
                text = "Notebook Vault Locked",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = EmeraldVault,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "AES-256-GCM Encrypted Local Storage",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFA7F3D0)
                )
            }

            if (hasBiometrics) {
                Button(
                    onClick = onRequestBiometricUnlock,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("unlock_biometric_btn")
                ) {
                    Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Unlock with Biometrics")
                }
            }

            if (hasVaultPin) {
                Surface(
                    color = Color.White.copy(alpha = 0.06f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Or unlock with Vault PIN",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 8) pinInput = it.filter(Char::isDigit) },
                            label = { Text("Vault PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vault_pin_input")
                        )
                        Button(
                            onClick = { onVerifyPin(pinInput) },
                            enabled = pinInput.length >= 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("unlock_pin_btn")
                        ) {
                            Text("Verify PIN")
                        }
                    }
                }
            }

            if (!hasBiometrics && !hasVaultPin) {
                OutlinedButton(
                    onClick = onBypassIfNoCredentials,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue (No biometrics or PIN set)")
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFCA5A5),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
