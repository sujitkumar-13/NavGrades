package com.example

import com.example.auth.AuthState
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.model.AccessRequestRemote
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
  fun testAccessRequestModelSerialization() {
    val req = AccessRequestRemote(
      id = "req-456",
      email = "priya@gmail.com",
      name = "Priya Sharma",
      status = "pending",
      requestedAt = "2026-09-14 10:00"
    )

    val jsonString = Json.encodeToString(AccessRequestRemote.serializer(), req)
    val decoded = Json.decodeFromString(AccessRequestRemote.serializer(), jsonString)

    assertEquals("pending", decoded.status)
    assertEquals("priya@gmail.com", decoded.email)
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

    val memberUser = ApprovedUserRemote(
      email = "member@navgurukul.org",
      name = "Member",
      role = "member"
    )
    val memberState = AuthState.Authenticated(memberUser)
    assertFalse(memberState.user.role.equals("admin", ignoreCase = true))
  }
}
