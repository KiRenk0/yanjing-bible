package org.yanjing.app.data

import java.io.Closeable

interface SemanticEncoder : Closeable {
    suspend fun encode(text: String): FloatArray
    override fun close() {}
}
