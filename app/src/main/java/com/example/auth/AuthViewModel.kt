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
import com.example.data.remote.model.AccessRequestRemote
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

  private val _pendingRequests = MutableStateFlow<List<AccessRequestRemote>>(emptyList())
  val pendingRequests: StateFlow<List<AccessRequestRemote>> = _pendingRequests.asStateFlow()

  private val _teamMembers = MutableStateFlow<List<ApprovedUserRemote>>(emptyList())
  val teamMembers: StateFlow<List<ApprovedUserRemote>> = _teamMembers.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  // In-memory mock storage for immediate offline/dev testing
  private val mockApprovedUsers = mutableListOf(
    ApprovedUserRemote(
      id = "admin-1",
      email = "admin@navgurukul.org",
      name = "NavGrade Admin",
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

  private val mockAccessRequests = mutableListOf(
    AccessRequestRemote(
      id = "req-1",
      email = "priya.sharma@gmail.com",
      name = "Priya Sharma",
      status = "pending",
      requestedAt = "2026-09-14 10:30 AM"
    ),
    AccessRequestRemote(
      id = "req-2",
      email = "rahul.verma@gmail.com",
      name = "Rahul Verma",
      status = "pending",
      requestedAt = "2026-09-14 11:15 AM"
    )
  )

  init {
    // Check if there was an active session or initial state
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

        if (credential is GoogleIdTokenCredential) {
          val email = credential.id
          val name = credential.displayName ?: email.substringBefore("@")
          processUserLogin(email, name)
        } else {
          _errorMessage.value = "Unexpected credential received."
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
   * Evaluates user approval status against database (or dev mock if unconfigured).
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
            val req = remoteRepo.checkAccessRequest(cleanEmail)
            if (req?.status == "denied") {
              _authState.value = AuthState.AccessDenied(cleanEmail)
            } else {
              if (req == null) {
                remoteRepo.submitAccessRequest(cleanEmail, name)
              }
              _authState.value = AuthState.PendingApproval(cleanEmail, name)
            }
          }
        } catch (e: Exception) {
          Log.e(tag, "Error processing login for $cleanEmail: ${e.message}", e)
          _errorMessage.value = "Failed to verify access with database: ${e.message}"
        }
      } else {
        // Mock fallback mode:
        val mockUser = mockApprovedUsers.find { it.email.equals(cleanEmail, ignoreCase = true) }
        if (mockUser != null) {
          _authState.value = AuthState.Authenticated(mockUser)
        } else {
          val mockReq = mockAccessRequests.find { it.email.equals(cleanEmail, ignoreCase = true) }
          if (mockReq?.status == "denied") {
            _authState.value = AuthState.AccessDenied(cleanEmail)
          } else {
            if (mockReq == null) {
              mockAccessRequests.add(
                AccessRequestRemote(
                  id = "req-${System.currentTimeMillis()}",
                  email = cleanEmail,
                  name = name,
                  status = "pending",
                  requestedAt = "Just now"
                )
              )
            }
            _authState.value = AuthState.PendingApproval(cleanEmail, name)
          }
        }
      }
      _isLoading.value = false
    }
  }

  /**
   * Re-evaluates status (called by "Try Again" button on Pending Approval screen).
   */
  fun checkApprovalStatus(email: String, name: String) {
    viewModelScope.launch {
      _isLoading.value = true
      val cleanEmail = email.trim().lowercase()

      if (SupabaseConfig.isConfigured()) {
        val approved = remoteRepo.checkApprovedUser(cleanEmail)
        if (approved != null) {
          _authState.value = AuthState.Authenticated(approved)
        } else {
          val req = remoteRepo.checkAccessRequest(cleanEmail)
          if (req?.status == "denied") {
            _authState.value = AuthState.AccessDenied(cleanEmail)
          } else {
            _authState.value = AuthState.PendingApproval(cleanEmail, name)
          }
        }
      } else {
        val mockUser = mockApprovedUsers.find { it.email.equals(cleanEmail, ignoreCase = true) }
        if (mockUser != null) {
          _authState.value = AuthState.Authenticated(mockUser)
        } else {
          val mockReq = mockAccessRequests.find { it.email.equals(cleanEmail, ignoreCase = true) }
          if (mockReq?.status == "denied") {
            _authState.value = AuthState.AccessDenied(cleanEmail)
          } else {
            _authState.value = AuthState.PendingApproval(cleanEmail, name)
          }
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
        _pendingRequests.value = remoteRepo.getAllPendingRequests()
        _teamMembers.value = remoteRepo.getAllApprovedUsers()
      } else {
        _pendingRequests.value = mockAccessRequests.filter { it.status == "pending" }
        _teamMembers.value = mockApprovedUsers.toList()
      }
      _isLoading.value = false
    }
  }

  fun approveRequest(request: AccessRequestRemote, role: String = "member") {
    viewModelScope.launch {
      if (SupabaseConfig.isConfigured()) {
        remoteRepo.approveAccessRequest(request.id, request.email, request.name, role)
        loadAdminData()
      } else {
        mockAccessRequests.removeAll { it.id == request.id || it.email == request.email }
        mockApprovedUsers.add(
          ApprovedUserRemote(
            id = "user-${System.currentTimeMillis()}",
            email = request.email,
            name = request.name,
            role = role,
            approvedAt = "Today"
          )
        )
        loadAdminData()
      }
    }
  }

  fun denyRequest(requestId: String) {
    viewModelScope.launch {
      if (SupabaseConfig.isConfigured()) {
        remoteRepo.denyAccessRequest(requestId)
        loadAdminData()
      } else {
        val index = mockAccessRequests.indexOfFirst { it.id == requestId }
        if (index != -1) {
          val req = mockAccessRequests[index]
          mockAccessRequests[index] = req.copy(status = "denied")
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
