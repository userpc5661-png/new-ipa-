package com.example.sls_assistant_pro.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.sls_assistant_pro.ui.viewmodel.AuthState
import com.example.sls_assistant_pro.ui.viewmodel.AuthViewModel

@Composable
fun SessionGateScreen(
    authViewModel: AuthViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val state by authViewModel.authState.collectAsState()

    when (state) {
        is AuthState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is AuthState.Authenticated -> {
            onNavigateToHome()
        }
        is AuthState.Unauthenticated -> {
            onNavigateToLogin()
        }
    }
}
