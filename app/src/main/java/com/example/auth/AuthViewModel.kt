package com.example.auth

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.RemoteNavGradeRepository
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.model.ApprovedUserRemote
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {
  private val tag = "AuthViewModel"
  private val remoteRepo = RemoteNavGradeRepository()

  private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
  val authState: StateFlow<AuthState> = _authState.asStateFlow()

  private val _teamMembers = MutableStateFlow<List<ApprovedUserRemote>>(emptyList())
  val teamMembers: StateFlow<List<ApprovedUserRemote>> = _teamMembers.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  init {
    _authState.value = AuthState.Unauthenticated
  }

  fun clearError() {
    _errorMessage.value = null
  }

  /**
   * Triggers the Google Sign-In flow using Android Credential Manager.
   */
  fun signInWithGoogle(context: Context, serverClientId: String = "") {
    viewModelScope.launch {
      _isLoading.value = true
      _errorMessage.value = null

      if (serverClientId.isBlank()) {
        Log.e(tag, "Google Web Client ID is not configured.")
        _errorMessage.value = "Authentication service configuration missing. Please contact administrator."
        _isLoading.value = false
        return@launch
      }

      try {
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
          .setFilterByAuthorizedAccounts(false)
          .setServerClientId(serverClientId)
          .setAutoSelectEnabled(false)
          .build()

        val request = GetCredentialRequest.Builder()
          .addCredentialOption(googleIdOption)
          .build()

        val result = credentialManager.getCredential(context = context, request = request)
        val credential = result.credential
        Log.d(tag, "Credential received of type: ${credential::class.java.name}, type=${credential.type}")

        val email: String? = when {
          credential is GoogleIdTokenCredential -> credential.id
          credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
            try {
              val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
              googleIdToken.id
            } catch (e: Exception) {
              Log.e(tag, "Failed to parse GoogleIdTokenCredential from data bundle", e)
              null
            }
          }
          else -> null
        }

        if (email != null) {
          val name = email.substringBefore("@")
          processUserLogin(email, name)
        } else {
          _errorMessage.value = "Unexpected credential type: ${credential.type}"
        }
      } catch (e: GetCredentialException) {
        Log.e(tag, "Google Sign-In failed: ${e.message}", e)
        _errorMessage.value = "Sign in cancelled or failed: ${e.message}"
      } catch (e: Exception) {
        Log.e(tag, "Unexpected error in sign-in: ${e.message}", e)
        _errorMessage.value = "Sign in error: ${e.message}"
      } finally {
        _isLoading.value = false
      }
    }
  }

  /**
   * Whitelist-only login check.
   * If the user's email exists in approved_users → Authenticated.
   * Otherwise → NoAccess. No requests are submitted, no pending state exists.
   */
  fun processUserLogin(email: String, name: String) {
    viewModelScope.launch {
      _isLoading.value = true
      val cleanEmail = email.trim().lowercase()

      try {
        val approved = remoteRepo.checkApprovedUser(cleanEmail)
        if (approved != null) {
          _authState.value = AuthState.Authenticated(approved)
        } else {
          _authState.value = AuthState.NoAccess(cleanEmail)
        }
      } catch (e: Exception) {
        Log.e(tag, "Error processing login for $cleanEmail: ${e.message}", e)
        _errorMessage.value = "Failed to verify access with database: ${e.message}"
      } finally {
        _isLoading.value = false
      }
    }
  }

  fun signOut() {
    _authState.value = AuthState.Unauthenticated
    _errorMessage.value = null
  }

  // -------------------------------------------------------------
  // Admin Operations
  // -------------------------------------------------------------
  fun loadAdminData() {
    viewModelScope.launch {
      _isLoading.value = true
      try {
        _teamMembers.value = remoteRepo.getAllApprovedUsers()
      } catch (e: Exception) {
        Log.e(tag, "Error loading admin data: ${e.message}", e)
      } finally {
        _isLoading.value = false
      }
    }
  }

  fun addTeamMember(email: String, name: String, role: String = "team") {
    viewModelScope.launch {
      try {
        remoteRepo.addApprovedUser(email, name, role)
        loadAdminData()
      } catch (e: Exception) {
        Log.e(tag, "Error adding team member: ${e.message}", e)
        _errorMessage.value = "Failed to add member: ${e.message}"
      }
    }
  }

  fun updateMemberRole(email: String, newRole: String) {
    viewModelScope.launch {
      try {
        remoteRepo.updateUserRole(email, newRole)
        loadAdminData()
      } catch (e: Exception) {
        Log.e(tag, "Error updating user role: ${e.message}", e)
        _errorMessage.value = "Failed to update role: ${e.message}"
      }
    }
  }

  fun revokeMemberAccess(email: String) {
    viewModelScope.launch {
      try {
        remoteRepo.revokeUserAccess(email)
        loadAdminData()
      } catch (e: Exception) {
        Log.e(tag, "Error revoking user access: ${e.message}", e)
        _errorMessage.value = "Failed to revoke access: ${e.message}"
      }
    }
  }
}
