package org.yanjing.app.core.semantic

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.nio.LongBuffer
import kotlin.math.sqrt

class SentenceEmbedder(modelPath: String, private val tokenizer: WordPieceTokenizer) : Closeable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session = OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(2)
        options.setInterOpNumThreads(1)
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        environment.createSession(modelPath, options)
    }
    fun encode(text: String): FloatArray {
        val ids = tokenizer.encode(text)
        val shape = longArrayOf(1, ids.size.toLong())
        OnnxTensor.createTensor(environment, LongBuffer.wrap(ids), shape).use { input ->
            OnnxTensor.createTensor(environment, LongBuffer.wrap(LongArray(ids.size) { 1 }), shape).use { attention ->
                OnnxTensor.createTensor(environment, LongBuffer.wrap(LongArray(ids.size)), shape).use { types ->
                    session.run(mapOf("input_ids" to input, "attention_mask" to attention, "token_type_ids" to types)).use { result ->
                        val embedding = FloatArray(512)
                        (result[0] as OnnxTensor).floatBuffer.get(embedding)
                        val norm = sqrt(embedding.sumOf { it.toDouble() * it }).toFloat().coerceAtLeast(1e-12f)
                        embedding.indices.forEach { embedding[it] /= norm }
                        return embedding
                    }
                }
            }
        }
    }
    override fun close() { session.close() }
}
