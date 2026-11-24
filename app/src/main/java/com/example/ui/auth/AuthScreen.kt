package com.example.ui.auth

import android.util.Patterns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.firebase.attemptAutoSignIn
import com.example.data.firebase.onGoogleSignInClicked
import com.example.data.settings.AppLanguage
import com.example.data.settings.LanguagePreferenceStore
import com.example.data.settings.LocalAppLanguage
import com.example.ui.components.LanguageSettingsDialog
import com.example.ui.theme.BrandtAuthCardBorder
import com.example.ui.theme.BrandtAuthFieldFill
import com.example.ui.theme.BrandtAuthFieldPlaceholder
import com.example.ui.theme.BrandtAuthInk
import com.example.ui.theme.BrandtAuthLinkBlue
import com.example.ui.theme.BrandtAuthMutedInk
import com.example.ui.theme.BrandtHeroTopScrim
import com.example.ui.theme.BrandtPureWhite
import com.example.ui.theme.BrandtWordmarkFontFamily
import com.google.firebase.Firebase
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit = {},
    onEnterPreviewMode: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val activeLanguage = LocalAppLanguage.current
    val isThai = activeLanguage == AppLanguage.THAI

    var isAttemptingAutoSignIn by remember { mutableStateOf(true) }
    var isSigningIn by remember { mutableStateOf(false) }
    var isCreateAccountMode by remember { mutableStateOf(true) }

    var nameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isAttemptingAutoSignIn = false
                onAuthSuccess()
            },
            onUnauthenticated = {
                isAttemptingAutoSignIn = false
            },
            scope = scope
        )
    }

    val launchGoogleAuth: () -> Unit = {
        focusManager.clearFocus()
        errorMessage = null
        infoMessage = null
        isSigningIn = true
        onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isSigningIn = false
                onAuthSuccess()
            },
            onAuthError = { msg ->
                isSigningIn = false
                errorMessage = msg
            },
            scope = scope,
            onAuthCancelled = {
                isSigningIn = false
            }
        )
    }

    val handleEmailFormSubmit: () -> Unit = {
        focusManager.clearFocus()
        errorMessage = null
        infoMessage = null
        val trimmedName = nameInput.trim()
        val trimmedEmail = emailInput.trim()

        when {
            isCreateAccountMode && trimmedName.isEmpty() -> {
                errorMessage = if (isThai) {
                    "กรุณากรอกชื่อของคุณ"
                } else {
                    "Please enter your name."
                }
            }
            trimmedEmail.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches() -> {
                errorMessage = if (isThai) {
                    "กรุณากรอกอีเมลให้ถูกต้อง"
                } else {
                    "Please enter a valid email address."
                }
            }
            passwordInput.length < 6 -> {
                errorMessage = if (isThai) {
                    "รหัสผ่านต้องมีอย่างน้อย 6 ตัวอักษร"
                } else {
                    "Password must be at least 6 characters."
                }
            }
            isCreateAccountMode -> {
                isSigningIn = true
                scope.launch {
                    try {
                        val result = Firebase.auth
                            .createUserWithEmailAndPassword(trimmedEmail, passwordInput)
                            .await()
                        val profileUpdate = UserProfileChangeRequest.Builder()
                            .setDisplayName(trimmedName)
                            .build()
                        result.user?.updateProfile(profileUpdate)?.await()
                        isSigningIn = false
                        onAuthSuccess()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        isSigningIn = false
                        errorMessage = if (isThai) {
                            "ไม่สามารถสร้างบัญชีด้วยอีเมลได้ในขณะนี้ (ต้องเปิดใช้งาน Email/Password ใน Firebase Console ก่อน) หรือกดไอคอน Google (G) ด้านบน"
                        } else {
                            e.localizedMessage
                                ?: "Email sign-up requires enabling Email/Password in Firebase Console, or sign in with Google (G) above."
                        }
                    }
                }
            }
            else -> {
                infoMessage = if (isThai) {
                    "การเข้าสู่ระบบด้วยอีเมล/รหัสผ่านต้องเปิดใช้งานผู้ให้บริการ Email/Password ใน Firebase Console ก่อน กรุณากดไอคอน Google (G) ด้านบนหรือทดลองใช้งานแบบ Preview"
                } else {
                    "Email/Password sign-in requires enabling the Email/Password provider in Firebase Console. Use Google (G) above or Preview Mode below."
                }
            }
        }
    }

    if (showLanguageDialog) {
        LanguageSettingsDialog(
            currentLanguage = activeLanguage,
            onSelectLanguage = { selected ->
                LanguagePreferenceStore.setLanguage(context, selected, scope)
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showForgotPasswordDialog) {
        ForgotPasswordDialog(
            initialEmail = emailInput.trim(),
            isThai = isThai,
            onDismiss = { showForgotPasswordDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandtPureWhite)
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Full-bleed edge-to-edge highway coupe hero with BRANDT wordmark & smooth white fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(276.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.hero_brandt_coupe),
                contentDescription = "BRANDT Highway Sport Coupe",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to BrandtHeroTopScrim,
                            1f to Color.Transparent
                        )
                    )
            )

            IconButton(
                onClick = { showLanguageDialog = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 4.dp, end = 8.dp)
                    .size(48.dp)
                    .testTag("auth_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Language settings",
                    tint = BrandtPureWhite.copy(alpha = 0.85f)
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 34.dp, start = 16.dp, end = 16.dp)
                    .align(Alignment.TopCenter),
                contentAlignment = Alignment.Center
            ) {
                val wordmarkSize = (maxWidth.value / 9.0f).coerceIn(32f, 54f).sp
                Text(
                    text = "BRANDT",
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = BrandtWordmarkFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = wordmarkSize,
                        lineHeight = wordmarkSize,
                        letterSpacing = 1.sp,
                        shadow = Shadow(
                            color = BrandtHeroTopScrim,
                            offset = Offset(0f, 3f),
                            blurRadius = 10f
                        )
                    ),
                    color = BrandtPureWhite,
                    modifier = Modifier.testTag("brandt_hero_wordmark")
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.6f to BrandtPureWhite.copy(alpha = 0.80f),
                            1f to BrandtPureWhite
                        )
                    )
            )
        }

        // Symmetrical clean white form body
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(top = 2.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when {
                    isCreateAccountMode && isThai -> "สร้างบัญชีผู้ใช้"
                    isCreateAccountMode -> "Create Account"
                    isThai -> "เข้าสู่ระบบ"
                    else -> "Sign in Account"
                },
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 24.sp
                ),
                color = BrandtAuthInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("auth_mode_title")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Symmetrical 2-provider square social auth row: Google & Facebook
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SocialSquareCard(
                    enabled = !isSigningIn && !isAttemptingAutoSignIn,
                    contentDescription = "Sign in with Google",
                    testTag = "google_sign_in_button",
                    onClick = launchGoogleAuth
                ) {
                    if (isSigningIn || isAttemptingAutoSignIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = BrandtAuthInk,
                            strokeWidth = 2.dp
                        )
                    } else {
                        MonochromeGoogleGIcon(modifier = Modifier.size(24.dp))
                    }
                }

                SocialSquareCard(
                    enabled = !isSigningIn && !isAttemptingAutoSignIn,
                    contentDescription = "Sign in with Facebook",
                    testTag = "facebook_sign_in_button",
                    onClick = {
                        focusManager.clearFocus()
                        errorMessage = null
                        infoMessage = if (isThai) {
                            "การเข้าสู่ระบบด้วย Facebook ต้องตั้งค่า Facebook App ID และเปิดใช้งาน Facebook Provider ใน Firebase Console ก่อน กรุณาใช้ Google (G) หรือโหมด Preview"
                        } else {
                            "Facebook Sign-In requires configuring your Facebook App ID & enabling Facebook in Firebase Console. Use Google (G) or Preview Mode for now."
                        }
                    }
                ) {
                    MonochromeFacebookFIcon(modifier = Modifier.size(22.dp))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isThai) {
                    "หรือเข้าสู่ระบบด้วยอีเมลและรหัสผ่าน"
                } else {
                    "Sign in With E-mail & Password"
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = BrandtAuthMutedInk,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Create Account -> Name + Email + Password; Sign in -> Email + Password
            AnimatedVisibility(visible = isCreateAccountMode) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    BrandtFlatField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it.take(60)
                            errorMessage = null
                        },
                        placeholder = if (isThai) "ชื่อผู้ใช้" else "Name",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        testTag = "auth_name_input"
                    )
                    Spacer(modifier = Modifier.height(11.dp))
                }
            }

            BrandtFlatField(
                value = emailInput,
                onValueChange = {
                    emailInput = it.take(120)
                    errorMessage = null
                    infoMessage = null
                },
                placeholder = if (isThai) "กรอกอีเมล" else "Enter E-mail",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                testTag = "auth_email_input"
            )

            Spacer(modifier = Modifier.height(11.dp))

            BrandtFlatField(
                value = passwordInput,
                onValueChange = {
                    passwordInput = it.take(64)
                    errorMessage = null
                    infoMessage = null
                },
                placeholder = if (isThai) "กรอกรหัสผ่าน" else "Enter Password",
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.testTag("auth_password_visibility_toggle")
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = BrandtAuthFieldPlaceholder
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { handleEmailFormSubmit() }
                ),
                testTag = "auth_password_input"
            )

            if (errorMessage != null || infoMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (errorMessage != null) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        BrandtAuthFieldFill
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_feedback_banner")
                ) {
                    Text(
                        text = (errorMessage ?: infoMessage).orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (errorMessage != null) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            BrandtAuthInk
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary clean dark pill submit CTA matching luxury automotive minimalism
            Button(
                onClick = handleEmailFormSubmit,
                enabled = !isSigningIn && !isAttemptingAutoSignIn,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandtAuthInk,
                    contentColor = BrandtPureWhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("auth_submit_button")
            ) {
                Text(
                    text = when {
                        isCreateAccountMode && isThai -> "สร้างบัญชี"
                        isCreateAccountMode -> "Create Account"
                        isThai -> "เข้าสู่ระบบ"
                        else -> "Sign in"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { showForgotPasswordDialog = true },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("auth_forgot_password_button")
            ) {
                Text(
                    text = if (isThai) "ลืมรหัสผ่าน?" else "Forget Password?",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = BrandtAuthMutedInk
                )
            }

            TextButton(
                onClick = {
                    errorMessage = null
                    infoMessage = null
                    isCreateAccountMode = !isCreateAccountMode
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("auth_toggle_mode_button")
            ) {
                Text(
                    text = when {
                        isCreateAccountMode && isThai -> "มีบัญชีอยู่แล้ว?"
                        isCreateAccountMode -> "Have an Account Already?"
                        isThai -> "สร้างบัญชีใหม่"
                        else -> "Create Account"
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    ),
                    color = BrandtAuthLinkBlue
                )
            }

            TextButton(
                onClick = {
                    focusManager.clearFocus()
                    onEnterPreviewMode()
                },
                enabled = !isSigningIn,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("preview_mode_button")
            ) {
                Text(
                    text = if (isThai) "ทดลองใช้งานโดยไม่ล็อกอิน" else "Preview Without Sign-In",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = BrandtAuthMutedInk
                )
            }
        }
    }
}

@Composable
private fun SocialSquareCard(
    enabled: Boolean,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = BrandtPureWhite,
        border = BorderStroke(1.dp, BrandtAuthCardBorder),
        modifier = Modifier
            .size(54.dp)
            .semantics { this.contentDescription = contentDescription }
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
        }
    }
}

@Composable
private fun BrandtFlatField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    testTag: String,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = BrandtAuthFieldPlaceholder
            )
        },
        singleLine = true,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(10.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = BrandtAuthFieldFill,
            unfocusedContainerColor = BrandtAuthFieldFill,
            disabledContainerColor = BrandtAuthFieldFill,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = BrandtAuthInk,
            unfocusedTextColor = BrandtAuthInk,
            cursorColor = BrandtAuthInk
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag(testTag)
    )
}

@Composable
private fun ForgotPasswordDialog(
    initialEmail: String,
    isThai: Boolean,
    onDismiss: () -> Unit
) {
    var resetEmail by remember { mutableStateOf(initialEmail) }
    var isSending by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("forgot_password_dialog"),
        title = {
            Text(
                text = if (isThai) "รีเซ็ตรหัสผ่าน" else "Reset Password",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isThai) {
                        "กรอกอีเมลที่ลงทะเบียนไว้เพื่อส่งลิงก์ตั้งรหัสผ่านใหม่"
                    } else {
                        "Enter your registered email address to send a password reset link."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = resetEmail,
                    onValueChange = {
                        resetEmail = it.take(120)
                        statusText = null
                    },
                    label = { Text(if (isThai) "อีเมล" else "Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("forgot_password_email_input")
                )
                if (statusText != null) {
                    Text(
                        text = statusText.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = resetEmail.trim()
                    if (trimmed.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
                        statusText = if (isThai) "กรุณากรอกอีเมลให้ถูกต้อง" else "Please enter a valid email."
                        return@Button
                    }
                    isSending = true
                    scope.launch {
                        try {
                            Firebase.auth.sendPasswordResetEmail(trimmed).await()
                            isSending = false
                            statusText = if (isThai) {
                                "ส่งอีเมลรีเซ็ตรหัสผ่านเรียบร้อยแล้ว"
                            } else {
                                "Password reset email sent."
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            isSending = false
                            statusText = e.localizedMessage ?: if (isThai) {
                                "ไม่สามารถส่งอีเมลรีเซ็ตได้ในขณะนี้"
                            } else {
                                "Unable to send password reset email right now."
                            }
                        }
                    }
                },
                enabled = !isSending
            ) {
                Text(if (isThai) "ส่งลิงก์" else "Send Reset Link")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isThai) "ปิด" else "Close")
            }
        }
    )
}

@Composable
private fun MonochromeGoogleGIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.19f
        val inset = strokeWidth / 2f
        val arcSize = Size(
            width = size.width - strokeWidth,
            height = size.height - strokeWidth
        )
        drawArc(
            color = BrandtAuthInk,
            startAngle = 35f,
            sweepAngle = 290f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        val cy = size.height / 2f
        drawLine(
            color = BrandtAuthInk,
            start = Offset(size.width * 0.50f, cy),
            end = Offset(size.width - inset * 0.25f, cy),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Butt
        )
        drawArc(
            color = BrandtAuthInk,
            startAngle = 0f,
            sweepAngle = 42f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
    }
}

@Composable
private fun MonochromeFacebookFIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.62f, h * 0.96f)
            lineTo(w * 0.62f, h * 0.54f)
            lineTo(w * 0.77f, h * 0.54f)
            lineTo(w * 0.80f, h * 0.37f)
            lineTo(w * 0.62f, h * 0.37f)
            lineTo(w * 0.62f, h * 0.27f)
            cubicTo(w * 0.62f, h * 0.21f, w * 0.65f, h * 0.17f, w * 0.72f, h * 0.17f)
            lineTo(w * 0.82f, h * 0.17f)
            lineTo(w * 0.82f, h * 0.03f)
            cubicTo(w * 0.79f, h * 0.02f, w * 0.73f, h * 0.02f, w * 0.67f, h * 0.02f)
            cubicTo(w * 0.50f, h * 0.02f, w * 0.42f, h * 0.12f, w * 0.42f, h * 0.26f)
            lineTo(w * 0.42f, h * 0.37f)
            lineTo(w * 0.26f, h * 0.37f)
            lineTo(w * 0.26f, h * 0.54f)
            lineTo(w * 0.42f, h * 0.54f)
            lineTo(w * 0.42f, h * 0.96f)
            close()
        }
        drawPath(path = path, color = BrandtAuthInk)
    }
}
