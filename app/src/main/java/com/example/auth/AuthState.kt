package com.example.auth

import com.example.data.remote.model.ApprovedUserRemote

sealed interface AuthState {
  object Loading : AuthState
  object Unauthenticated : AuthState
  data class NoAccess(val email: String) : AuthState
  data class Authenticated(val user: ApprovedUserRemote) : AuthState
}
