// presentation/login/LoginScreen.kt
package com.example.docscanner.presentation.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private val BrandOrange = Color(0xFFE8603C)
private val BrandPink   = Color(0xFFD94860)
private val Ink         = Color(0xFF1A1A2E)
private val InkLight    = Color(0xFF6B6878)
private val Surface     = Color(0xFFFAF8F5)
private val Divider     = Color(0xFFE5E2DD)

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(state.loginSuccess) { if (state.loginSuccess) onLoginSuccess() }

    // Full screen — top half brand, bottom half form (portrait split)
    Column(
        Modifier
            .fillMaxSize()
            .background(Surface)
            .systemBarsPadding()
    ) {

        // ── TOP BRAND PANEL ───────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .weight(0.38f)
                .background(Ink)
                .padding(horizontal = 32.dp, vertical = 28.dp)
        ) {
            // Decorative accent circle — top right
            Box(
                Modifier
                    .size(140.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 40.dp, y = (-30).dp)
                    .clip(CircleShape)
                    .background(BrandOrange.copy(alpha = 0.12f))
            )
            // Small accent dot
            Box(
                Modifier
                    .size(48.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = (-24).dp, y = 24.dp)
                    .clip(CircleShape)
                    .background(BrandPink.copy(alpha = 0.25f))
            )

            Column(Modifier.align(Alignment.BottomStart)) {
                // App badge — pill shape, minimal
                Row(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(BrandOrange.copy(0.18f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(BrandOrange)
                    )
                    Text(
                        "DocScanner",
                        color = BrandOrange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    "Document\nManagement",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 36.sp,
                    letterSpacing = (-0.5).sp
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    "Scan, classify & organise\nloan documents with ease.",
                    color = Color.White.copy(0.45f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }

        // ── BOTTOM FORM PANEL ─────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .weight(0.62f)
                .background(Surface)
                .padding(horizontal = 32.dp)
        ) {
            Spacer(Modifier.height(36.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(initialAlpha = 0f) + slideInVertically { it / 3 }
            ) {
                Column {
                    Text(
                        "Welcome back",
                        color = Ink,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        "Sign in to your account",
                        color = InkLight,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(Modifier.height(32.dp))

                    // ── Username Field ──────────────────────────────────
                    FlatInputField(
                        value = state.username,
                        onValueChange = viewModel::onUsernameChanged,
                        label = "Username",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )

                    Spacer(Modifier.height(20.dp))

                    // ── Password Field ──────────────────────────────────
                    FlatInputField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChanged,
                        label = "Password",
                        isPassword = true,
                        passwordVisible = passwordVisible,
                        onTogglePassword = { passwordVisible = !passwordVisible },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus(); viewModel.onLogin() }
                        )
                    )

                    // ── Error ──────────────────────────────────────────
                    AnimatedVisibility(visible = state.errorMessage != null) {
                        Row(
                            Modifier
                                .padding(top = 14.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFD94040).copy(0.07f))
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline, null,
                                tint = Color(0xFFD94040),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                state.errorMessage ?: "",
                                color = Color(0xFFD94040),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    // ── Sign In Row — label left, button right ──────────
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Sign in", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("to your workspace", color = InkLight, fontSize = 12.sp)
                        }

                        // FAB-style circular button
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(if (state.isLoading) InkLight else Ink)
                                .clickable(
                                    enabled = !state.isLoading,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { viewModel.onLogin() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.ArrowForward, null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    // ── Bottom rule with version tag ───────────────────
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Divider(Modifier.weight(1f), color = Divider, thickness = 1.dp)
                        Text("v1.0", color = InkLight.copy(0.5f), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ── Flat underline-only text field ────────────────────────────────────────────
@Composable
private fun FlatInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val accentColor = BrandOrange
    val hasFocus = remember { mutableStateOf(false) }
    val lineColor by remember(hasFocus.value) {
        derivedStateOf { if (hasFocus.value) accentColor else Divider }
    }

    Column(Modifier.fillMaxWidth()) {
        Text(label, color = InkLight, fontSize = 11.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = if (hasFocus.value) 2f else 1.2f
                    )
                }
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f),
                textStyle = TextStyle(
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(accentColor),
                singleLine = true,
                visualTransformation = if (isPassword && !passwordVisible)
                    PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions
            )
            if (isPassword && onTogglePassword != null) {
                IconButton(
                    onClick = onTogglePassword,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null,
                        tint = InkLight,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
