package com.example.game.loot.borderlands.catalog

import com.example.game.ZombieEntity
import com.example.game.BoxBoyGameEngine // Note: Assume ZombieArchetype is accessible where ZombieEntity is
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class SingularityPullGrenadeModTest {

    // Dummy enum just in case we need it to instantiate ZombieEntity, usually it's already there
    // If ZombieArchetype is missing during compilation, we'll see it, but based on grep results, 
    // com.example.game.BoxBoyGameEngine contains ZombieEntity and ZombieArchetype.SHAMBLER is used.

    private fun createZombie(id: Int, x: Float, y: Float): ZombieEntity {
        // Need to import or use correct constructor based on ZombieEntity grep
        // data class ZombieEntity(val id: Int, val archetype: ZombieArchetype, var x: Float, var y: Float, var hp: Int, val maxHp: Int, var attackCooldownMs: Long = 0L)
        // Note: For test purposes, we might need a reflection based approach or assume a standard constructor.
        // Let's create an anonymous class or just use the constructor we grepped earlier:
        // ZombieEntity(1, ZombieArchetype.SHAMBLER, 0f, 0f, 100, 100)
        
        // Since we don't have access to ZombieArchetype here directly without correct imports, let's assume it's in com.example.game
        
        // But since we are writing tests, let's look at the constructor exactly
        // BoxBoyGameEngine.kt:75:data class ZombieEntity(val id: Int, val archetype: ZombieArchetype, var x: Float, var y: Float, var hp: Int, val maxHp: Int, var attackCooldownMs: Long = 0L)
        
        // Let's try to load class by name to bypass compiler error if ZombieArchetype is not imported, or just import it.
        // Actually, we can just compile and see if the import com.example.game.ZombieArchetype works.
        return ZombieEntity(id, com.example.game.ZombieArchetype.SHAMBLER, x, y, 100, 100)
    }

    @Test
    fun testSerializationRoundtrip() {
        val config = SingularityGrenadeConfig(200f, 500f, 3.5f, 10f)
        val state = SingularityGrenadeState(100f, -50f, 1.2f, true)

        val configMap = config.toMap()
        val stateMap = state.toMap()

        val newConfig = SingularityGrenadeConfig.fromMap(configMap)
        val newState = SingularityGrenadeState.fromMap(stateMap)

        assertEquals(config, newConfig)
        assertEquals(state.x, newState.x)
        assertEquals(state.y, newState.y)
        assertEquals(state.elapsedSeconds, newState.elapsedSeconds)
        assertEquals(state.isActive, newState.isActive)
        assertEquals(state.computeChecksum(), newState.computeChecksum())
    }

    @Test
    fun testNominalPull() {
        val config = SingularityGrenadeConfig(100f, 100f, 5f, 5f)
        val state = SingularityGrenadeState(0f, 0f, 0f, true)
        
        // Zombie inside radius, 50 units away
        val z1 = createZombie(1, 50f, 0f) 
        val zombies = listOf(z1)

        SingularityPullGrenadeEngine.tick(config, state, zombies, 0.100f)

        // It should have moved closer to (0,0)
        assertTrue(z1.x < 50f)
        assertTrue(z1.x > 0f)
        assertEquals(0f, z1.y)
    }

    @Test
    fun testOutsideRadiusIgnored() {
        val config = SingularityGrenadeConfig(100f, 100f, 5f, 5f)
        val state = SingularityGrenadeState(0f, 0f, 0f, true)
        
        // Zombie outside radius, 150 units away
        val z1 = createZombie(1, 150f, 0f) 
        val zombies = listOf(z1)

        SingularityPullGrenadeEngine.tick(config, state, zombies, 0.100f)

        // It should not move
        assertEquals(150f, z1.x)
        assertEquals(0f, z1.y)
    }

    @Test
    fun testMaxDurationExpiration() {
        val config = SingularityGrenadeConfig(100f, 100f, 2f, 5f)
        val state = SingularityGrenadeState(0f, 0f, 1.95f, true)
        
        val z1 = createZombie(1, 50f, 0f)
        val zombies = listOf(z1)

        SingularityPullGrenadeEngine.tick(config, state, zombies, 0.100f)
        
        // Now elapsed is 2.05f >= 2f, so it becomes inactive
        assertFalse(state.isActive)
        
        // Since it expires during this tick, no pull should happen AFTER expiration, 
        // wait, the code adds dtSeconds then checks for expiration, so it might return without pulling
        assertEquals(50f, z1.x)
    }

    @Test
    fun testDtSecondsClamping_TooSmall() {
        val config = SingularityGrenadeConfig(100f, 100f, 5f, 5f)
        val state = SingularityGrenadeState(0f, 0f, 0f, true)
        val zombies = listOf(createZombie(1, 50f, 0f))
        
        // Pass a tiny dt, it should clamp to 0.001f
        SingularityPullGrenadeEngine.tick(config, state, zombies, 0.0001f)
        
        assertEquals(0.001f, state.elapsedSeconds, 0.00001f)
    }

    @Test
    fun testDtSecondsClamping_TooLarge() {
        val config = SingularityGrenadeConfig(100f, 100f, 5f, 5f)
        val state = SingularityGrenadeState(0f, 0f, 0f, true)
        val zombies = listOf(createZombie(1, 50f, 0f))
        
        // Pass a large dt, it should clamp to 0.100f
        SingularityPullGrenadeEngine.tick(config, state, zombies, 1.0f)
        
        assertEquals(0.100f, state.elapsedSeconds, 0.00001f)
    }

    @Test
    fun testNanRecovery_State() {
        val config = SingularityGrenadeConfig(100f, 100f, 5f, 5f)
        val state = SingularityGrenadeState(Float.NaN, Float.NaN, 0f, true)
        val z1 = createZombie(1, 50f, 0f)
        val zombies = listOf(z1)
        
        SingularityPullGrenadeEngine.tick(config, state, zombies, 0.100f)
        
        assertEquals(0f, state.x)
        assertEquals(0f, state.y)
    }

    @Test
    fun testNanRecovery_Zombie() {
        val config = SingularityGrenadeConfig(100f, 100f, 5f, 5f)
        val state = SingularityGrenadeState(0f, 0f, 0f, true)
        val z1 = createZombie(1, Float.NaN, Float.NaN)
        val zombies = listOf(z1)
        
        SingularityPullGrenadeEngine.tick(config, state, zombies, 0.100f)
        
        assertEquals(0f, z1.x)
        assertEquals(0f, z1.y)
    }

    @Test
    fun testAntiJitterBoundary() {
        val config = SingularityGrenadeConfig(100f, 500f, 5f, 5f) // High force
        val state = SingularityGrenadeState(0f, 0f, 0f, true)
        val z1 = createZombie(1, 6f, 0f) // Just outside jitter bound
        val zombies = listOf(z1)
        
        SingularityPullGrenadeEngine.tick(config, state, zombies, 0.100f)
        
        // Even with high force, it shouldn't overshoot the center and jitter
        // The pull distance is bounded by (dist - minJitterDistance) -> (6 - 5) = 1
        assertEquals(5f, z1.x, 0.0001f)
    }

    @Test
    fun testAlreadyInsideJitterRadius() {
        val config = SingularityGrenadeConfig(100f, 500f, 5f, 5f)
        val state = SingularityGrenadeState(0f, 0f, 0f, true)
        val z1 = createZombie(1, 3f, 0f) // Inside jitter bound
        val zombies = listOf(z1)
        
        SingularityPullGrenadeEngine.tick(config, state, zombies, 0.100f)
        
        // Should not be pulled at all
        assertEquals(3f, z1.x, 0f)
    }

    @Test
    fun testHighStressMaxCapacity() {
        val config = SingularityGrenadeConfig(100f, 100f, 5f, 5f)
        val state = SingularityGrenadeState(0f, 0f, 0f, true)
        
        // 300 zombies
        val zombies = (1..300).map { i ->
            // Distribute them in a line
            createZombie(i, i.toFloat(), 0f)
        }
        
        // Run tick
        SingularityPullGrenadeEngine.tick(config, state, zombies, 0.1f)
        
        // Check a random one that was within range
        val z50 = zombies[49] // initially at x=50
        assertTrue(z50.x < 50f)
        
        // Check one outside range
        val z150 = zombies[149] // initially at x=150
        assertEquals(150f, z150.x, 0f)
    }

    @Test
    fun testDeterminism() {
        val config = SingularityGrenadeConfig(100f, 100f, 5f, 5f)
        
        val state1 = SingularityGrenadeState(0f, 0f, 0f, true)
        val zombies1 = listOf(createZombie(1, 50f, 0f))
        
        val state2 = SingularityGrenadeState(0f, 0f, 0f, true)
        val zombies2 = listOf(createZombie(1, 50f, 0f))
        
        SingularityPullGrenadeEngine.tick(config, state1, zombies1, 0.1f)
        SingularityPullGrenadeEngine.tick(config, state2, zombies2, 0.1f)
        
        assertEquals(zombies1[0].x, zombies2[0].x)
        assertEquals(state1.computeChecksum(), state2.computeChecksum())
    }

    @Test
    fun testNegativeOrZeroDt() {
        val config = SingularityGrenadeConfig(100f, 100f, 5f, 5f)
        val state = SingularityGrenadeState(0f, 0f, 0f, true)
        val z1 = createZombie(1, 50f, 0f)
        val zombies = listOf(z1)
        
        SingularityPullGrenadeEngine.tick(config, state, zombies, -0.5f)
        
        // Should clamp to 0.001f
        assertEquals(0.001f, state.elapsedSeconds, 0.0001f)
        assertTrue(z1.x < 50f) // Some pull should still happen
    }
}
