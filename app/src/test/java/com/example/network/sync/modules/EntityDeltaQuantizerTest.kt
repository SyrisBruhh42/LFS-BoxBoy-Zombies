package com.example.network.sync.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EntityDeltaQuantizerTest {

    @Test
    fun `test position quantization and dequantization nominal`() {
        val minVal = -2400f
        val maxVal = 2400f
        val pos = 1200.5f

        val quantized = EntityDeltaQuantizer.quantizePosition(pos, minVal, maxVal)
        val dequantized = EntityDeltaQuantizer.dequantizePosition(quantized, minVal, maxVal)

        // Tolerance is about (max - min) / 65535
        val tolerance = (maxVal - minVal) / 65535f
        assertTrue(abs(pos - dequantized) <= tolerance)
    }

    @Test
    fun `test angle quantization and dequantization nominal`() {
        val angle = Math.PI.toFloat() // 180 degrees
        val quantized = EntityDeltaQuantizer.quantizeAngle(angle)
        val dequantized = EntityDeltaQuantizer.dequantizeAngle(quantized)

        val tolerance = EntityDeltaQuantizer.TWO_PI / 65535f
        assertTrue(abs(angle - dequantized) <= tolerance)
    }

    @Test
    fun `test position bounds clamping`() {
        val minVal = -2400f
        val maxVal = 2400f

        val qMax = EntityDeltaQuantizer.quantizePosition(3000f, minVal, maxVal)
        val deqMax = EntityDeltaQuantizer.dequantizePosition(qMax, minVal, maxVal)
        assertEquals(maxVal, deqMax, 0.001f)

        val qMin = EntityDeltaQuantizer.quantizePosition(-3000f, minVal, maxVal)
        val deqMin = EntityDeltaQuantizer.dequantizePosition(qMin, minVal, maxVal)
        assertEquals(minVal, deqMin, 0.001f)
    }

    @Test
    fun `test angle normalization`() {
        val qNeg = EntityDeltaQuantizer.quantizeAngle(-EntityDeltaQuantizer.TWO_PI * 0.25f)
        val deqNeg = EntityDeltaQuantizer.dequantizeAngle(qNeg)
        assertEquals(EntityDeltaQuantizer.TWO_PI * 0.75f, deqNeg, 0.001f)
    }

    @Test
    fun `test NaN and Infinity injection recovery`() {
        val qNan = EntityDeltaQuantizer.quantizePosition(Float.NaN, -100f, 100f)
        val deqNan = EntityDeltaQuantizer.dequantizePosition(qNan, -100f, 100f)
        assertEquals(0f, deqNan, 0.1f) // Defaults to 0 clamped

        val qInf = EntityDeltaQuantizer.quantizePosition(Float.POSITIVE_INFINITY, -100f, 100f)
        val deqInf = EntityDeltaQuantizer.dequantizePosition(qInf, -100f, 100f)
        assertEquals(0f, deqInf, 0.1f) // Defaults to 0 clamped

        val qAngleNan = EntityDeltaQuantizer.quantizeAngle(Float.NaN)
        val deqAngleNan = EntityDeltaQuantizer.dequantizeAngle(qAngleNan)
        assertEquals(0f, deqAngleNan, 0.01f)
    }

    @Test
    fun `test max capacity stress`() {
        val config = EntityDeltaQuantizerConfig(maxEntities = 300)
        val quantizer = EntityDeltaQuantizer(config)

        for (i in 0 until 400) {
            quantizer.addEntity(i, i.toFloat(), i.toFloat(), 0f)
        }

        assertEquals(300, quantizer.getEntityCount())
    }

    @Test
    fun `test deterministic outputs with identical inputs`() {
        val quantizer1 = EntityDeltaQuantizer()
        quantizer1.addEntity(1, 100f, 200f, 1.5f)
        quantizer1.setTick(10)
        val payload1 = quantizer1.generatePayload()

        val quantizer2 = EntityDeltaQuantizer()
        quantizer2.addEntity(1, 100f, 200f, 1.5f)
        quantizer2.setTick(10)
        val payload2 = quantizer2.generatePayload()

        assertEquals(payload1.checksum, payload2.checksum)
        assertEquals(payload1.entities[0].x16, payload2.entities[0].x16)
        assertEquals(payload1.entities[0].y16, payload2.entities[0].y16)
        assertEquals(payload1.entities[0].angle16, payload2.entities[0].angle16)
    }

    @Test
    fun `test checksum detects changes`() {
        val quantizer1 = EntityDeltaQuantizer()
        quantizer1.addEntity(1, 100f, 200f, 1.5f)
        quantizer1.setTick(10)
        val payload1 = quantizer1.generatePayload()

        val quantizer2 = EntityDeltaQuantizer()
        quantizer2.addEntity(1, 100f, 201f, 1.5f) // Changed Y
        quantizer2.setTick(10)
        val payload2 = quantizer2.generatePayload()

        assertNotEquals(payload1.checksum, payload2.checksum)
    }

    @Test
    fun `test tick frame-rate independent clamping`() {
        val quantizer = EntityDeltaQuantizer()
        quantizer.setTick(0)
        
        quantizer.tick(0.016f)
        var payload = quantizer.generatePayload()
        assertEquals(1, payload.tickId)

        // NaN test
        quantizer.tick(Float.NaN)
        payload = quantizer.generatePayload()
        assertEquals(2, payload.tickId)
    }

    @Test
    fun `test serialization roundtrip parity`() {
        val qe1 = QuantizedEntity(42, 1000, 2000, 3000)
        val map = qe1.toMap()
        val qe2 = QuantizedEntity.fromMap(map)
        assertEquals(qe1, qe2)

        val payload1 = EntityDeltaPayload(
            tickId = 15,
            entities = listOf(qe1, QuantizedEntity(43, 10, 20, 30)),
            checksum = 99999
        )
        val payloadMap = payload1.toMap()
        val payload2 = EntityDeltaPayload.fromMap(payloadMap)
        assertEquals(payload1, payload2)
    }

    @Test
    fun `test clear functionality`() {
        val quantizer = EntityDeltaQuantizer()
        quantizer.addEntity(1, 0f, 0f, 0f)
        assertEquals(1, quantizer.getEntityCount())
        
        quantizer.clear()
        assertEquals(0, quantizer.getEntityCount())

        val payload = quantizer.generatePayload()
        assertTrue(payload.entities.isEmpty())
    }

    @Test
    fun `test zero and negative inputs boundary`() {
        val quantizer = EntityDeltaQuantizer()
        quantizer.addEntity(0, 0f, 0f, 0f)
        quantizer.addEntity(-1, -10f, -20f, -3f)
        
        val payload = quantizer.generatePayload()
        assertEquals(2, payload.entities.size)
        
        val e0 = payload.entities[0]
        val e1 = payload.entities[1]
        
        assertEquals(0, e0.entityId)
        assertEquals(-1, e1.entityId)
        
        // Dequantize and verify it's close to 0
        val min = -2400f
        val max = 2400f
        val dx0 = EntityDeltaQuantizer.dequantizePosition(e0.x16, min, max)
        assertEquals(0f, dx0, 0.1f)
    }
}
