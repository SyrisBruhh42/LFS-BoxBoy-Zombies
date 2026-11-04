package com.example.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

data class FirestoreErrorReport(
    val operationType: OperationType,
    val path: String?,
    val message: String,
    val isTransientRetryable: Boolean,
    val jsonPayload: String
)

fun classifyFirestoreRetryability(message: String): Boolean {
    val normalized = message.uppercase()
    return "UNAVAILABLE" in normalized ||
        "DEADLINE_EXCEEDED" in normalized ||
        "ABORTED" in normalized ||
        "RESOURCE_EXHAUSTED" in normalized
}

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val auth = try {
        FirebaseAuth.getInstance()
    } catch (_: Throwable) {
        null
    }
    val currentUser = auth?.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorMessage = exception.message ?: exception.toString()
    val errorInfoJson = JSONObject().apply {
        put("error", errorMessage)
        put("operationType", operationType.value)
        put("path", path)
        put("retryable", classifyFirestoreRetryability(errorMessage))
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    try {
        Log.e("FirestoreError", "Firestore Error: $jsonString")
    } catch (_: Throwable) {
        // Unit test fallback when Android Log is stubbed
    }
    return jsonString
}
