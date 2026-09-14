package com.example

import com.example.auth.AuthState
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.model.ApprovedUserRemote
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthFlowUnitTest {

  @Test
  fun testApprovedUserModelSerialization() {
    val user = ApprovedUserRemote(
      id = "user-123",
      email = "sujit@gmail.com",
      name = "Sujit Kumar",
      role = "admin",
      approvedAt = "2026-09-14"
    )

    val jsonString = Json.encodeToString(ApprovedUserRemote.serializer(), user)
    val decoded = Json.decodeFromString(ApprovedUserRemote.serializer(), jsonString)

    assertEquals(user.id, decoded.id)
    assertEquals(user.email, decoded.email)
    assertEquals(user.name, decoded.name)
    assertEquals(user.role, decoded.role)
    assertEquals(user.approvedAt, decoded.approvedAt)
  }

  @Test
  fun testSupabaseConfigDetectsPlaceholder() {
    // Should detect that placeholder is not a configured URL
    assertFalse(SupabaseConfig.isConfigured())
  }

  @Test
  fun testAuthStateAdminCheck() {
    val adminUser = ApprovedUserRemote(
      email = "admin@navgurukul.org",
      name = "Admin",
      role = "admin"
    )
    val state = AuthState.Authenticated(adminUser)

    assertTrue(state.user.role.equals("admin", ignoreCase = true))

    val teamUser = ApprovedUserRemote(
      email = "team@navgurukul.org",
      name = "Team Member",
      role = "team"
    )
    val teamState = AuthState.Authenticated(teamUser)
    assertFalse(teamState.user.role.equals("admin", ignoreCase = true))
  }

  @Test
  fun testNoAccessStateHoldsEmail() {
    // Whitelist-only: unknown email → NoAccess state
    val unknownEmail = "unknown@gmail.com"
    val state = AuthState.NoAccess(unknownEmail)
    assertEquals(unknownEmail, state.email)
  }

  @Test
  fun testAuthenticatedStateHoldsUser() {
    val user = ApprovedUserRemote(
      id = "admin-1",
      email = "admin@navgurukul.org",
      name = "NavGrade Admin",
      role = "admin"
    )
    val state = AuthState.Authenticated(user)
    assertEquals("admin@navgurukul.org", state.user.email)
    assertEquals("admin", state.user.role)
  }
}
