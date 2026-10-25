package com.syntaxa.khata.core.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syntaxa.khata.R
import com.syntaxa.khata.core.ui.theme.KiranaGreenPrimary
import kotlinx.coroutines.delay

@Composable
fun AppLockScreen(
    onUnlock: () -> Unit,
    pinSecurityManager: PinSecurityManager,
    modifier: Modifier = Modifier
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lockoutSeconds by remember { mutableStateOf(pinSecurityManager.getLockoutRemainingSeconds()) }

    // Lockout countdown timer
    LaunchedEffect(lockoutSeconds) {
        if (lockoutSeconds > 0) {
            delay(1000)
            lockoutSeconds = pinSecurityManager.getLockoutRemainingSeconds()
            if (lockoutSeconds == 0L) {
                errorMessage = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = KiranaGreenPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (lockoutSeconds > 0) "Locked due to too many attempts" else stringResource(R.string.enter_pin),
                style = MaterialTheme.typography.bodyLarge,
                color = if (lockoutSeconds > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // PIN Dots Indicator
            if (lockoutSeconds > 0) {
                Text(
                    text = "Try again in ${lockoutSeconds}s",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (errorMessage != null) MaterialTheme.colorScheme.error
                                    else if (isFilled) KiranaGreenPrimary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }
            }

            if (errorMessage != null && lockoutSeconds == 0L) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Number Pad
            val keypadEnabled = lockoutSeconds == 0L
            val keypad = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("C", "0", "OK")
            )

            fun submitPin(pin: String) {
                when (val result = pinSecurityManager.verifyPinWithThrottling(pin)) {
                    is PinVerifyResult.Success -> {
                        errorMessage = null
                        onUnlock()
                    }
                    is PinVerifyResult.Incorrect -> {
                        enteredPin = ""
                        errorMessage = if (result.attemptsLeftBeforeLockout in 1..2) {
                            "Incorrect PIN. $result.attemptsLeftBeforeLockout attempt(s) remaining before temporary lockout."
                        } else {
                            "Incorrect PIN. Please try again."
                        }
                    }
                    is PinVerifyResult.LockedOut -> {
                        enteredPin = ""
                        lockoutSeconds = result.remainingSeconds
                        errorMessage = "Too many failed attempts. Try again in ${result.remainingSeconds}s."
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (row in keypad) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        for (digit in row) {
                            Button(
                                onClick = {
                                    if (!keypadEnabled) return@Button
                                    when (digit) {
                                        "C" -> {
                                            enteredPin = ""
                                            errorMessage = null
                                        }
                                        "OK" -> {
                                            if (enteredPin.length == 4) {
                                                submitPin(enteredPin)
                                            }
                                        }
                                        else -> {
                                            if (enteredPin.length < 4) {
                                                enteredPin += digit
                                                errorMessage = null
                                                if (enteredPin.length == 4) {
                                                    submitPin(enteredPin)
                                                }
                                            }
                                        }
                                    }
                                },
                                enabled = keypadEnabled,
                                modifier = Modifier.size(68.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = digit,
                                    fontSize = if (digit == "OK") 14.sp else 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
