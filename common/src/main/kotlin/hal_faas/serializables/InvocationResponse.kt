package hal_faas.serializables

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class InvocationResponse(val uniqueId: String, val response: String) {
    fun toJson(): String {
        return Json.encodeToString(this)
    }
}