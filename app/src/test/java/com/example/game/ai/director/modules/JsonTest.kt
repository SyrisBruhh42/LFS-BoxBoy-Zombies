package com.example.game.ai.director.modules

import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

class JsonTest {
    @Test
    fun testJson() {
        val obj = JSONObject("{\"a\": 1}")
        assertEquals(1, obj.getInt("a"))
    }
}
