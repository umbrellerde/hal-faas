package hal_faas.serializables

import kotlinx.serialization.*

@Serializable
class Invocation(val uniqueId: String, val workloadName: String, val parameters: String, val returnQueue: String)