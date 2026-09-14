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

  // In-memory mock storage for offline/dev testing
  private val mockApprovedUsers = mutableListOf(
    ApprovedUserRemote(
      id = "admin-1",
      email = "admin@navgurukul.org",
      name = "NavGrades Admin",
      role = "admin",
      approvedAt = "2026-09-01"
    ),
    ApprovedUserRemote(
      id = "member-1",
      email = "team@navgurukul.org",
      name = "Assessment Evaluator",
      role = "team",
      approvedAt = "2026-09-05"
    )
  )

  init {
    _authState.value = AuthState.Unauthenticated
  }

  fun clearError() {
    _errorMessage.value = null
  }

  /**
   * Triggers the Google Sign-In flow using Android Credential Manager.
   * If credentials are not yet configured or Google Sign-In fails, falls back gracefully with an informative error.
   */
  fun signInWithGoogle(context: Context, serverClientId: String = "") {
    viewModelScope.launch {
      _isLoading.value = true
      _errorMessage.value = null

      if (serverClientId.isBlank()) {
        // Dev fallback: Simulate Google sign in with a demo user if no Web Client ID is configured yet
        Log.i(tag, "No Google serverClientId provided. Prompting demo selection.")
        _errorMessage.value = "Google Web Client ID is not configured yet. You can use Dev Quick Login below to test all screens!"
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

      if (SupabaseConfig.isConfigured()) {
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
        }
      } else {
        // Mock fallback mode: only whitelisted mock users can log in
        val mockUser = mockApprovedUsers.find { it.email.equals(cleanEmail, ignoreCase = true) }
        if (mockUser != null) {
          _authState.value = AuthState.Authenticated(mockUser)
        } else {
          _authState.value = AuthState.NoAccess(cleanEmail)
        }
      }
      _isLoading.value = false
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
      if (SupabaseConfig.isConfigured()) {
        _teamMembers.value = remoteRepo.getAllApprovedUsers()
      } else {
        _teamMembers.value = mockApprovedUsers.toList()
      }
      _isLoading.value = false
    }
  }

  fun addTeamMember(email: String, name: String, role: String = "team") {
    viewModelScope.launch {
      if (SupabaseConfig.isConfigured()) {
        remoteRepo.addApprovedUser(email, name, role)
        loadAdminData()
      } else {
        val exists = mockApprovedUsers.any { it.email.equals(email, ignoreCase = true) }
        if (!exists) {
          mockApprovedUsers.add(
            ApprovedUserRemote(
              id = "user-${System.currentTimeMillis()}",
              email = email.trim().lowercase(),
              name = name,
              role = role,
              approvedAt = "Today"
            )
          )
        }
        loadAdminData()
      }
    }
  }

  fun updateMemberRole(email: String, newRole: String) {
    viewModelScope.launch {
      if (SupabaseConfig.isConfigured()) {
        remoteRepo.updateUserRole(email, newRole)
        loadAdminData()
      } else {
        val index = mockApprovedUsers.indexOfFirst { it.email.equals(email, ignoreCase = true) }
        if (index != -1) {
          val user = mockApprovedUsers[index]
          mockApprovedUsers[index] = user.copy(role = newRole)
        }
        loadAdminData()
      }
    }
  }

  fun revokeMemberAccess(email: String) {
    viewModelScope.launch {
      if (SupabaseConfig.isConfigured()) {
        remoteRepo.revokeUserAccess(email)
        loadAdminData()
      } else {
        mockApprovedUsers.removeAll { it.email.equals(email, ignoreCase = true) }
        loadAdminData()
      }
    }
  }
}
