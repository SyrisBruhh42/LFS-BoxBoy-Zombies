package com.example.game.combat.modules

import com.example.data.DEFAULT_WEAPONS
import com.example.game.ElementalAffinity
import com.example.game.LootRarity
import com.example.game.ModularWeaponInstance
import com.example.game.WeaponBarrelPart
import com.example.game.WeaponMagazinePart
import com.example.game.WeaponManufacturer
import com.example.game.WeaponOpticPart
import com.example.game.WeaponReceiverPart
import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class KnockbackAndPoiseStaggerTest {

    @Test
    fun `Shotgun produces heavy knockback and stagger frames`() {
        val shotgun = DEFAULT_WEAPONS.find { it.shortCode == "12G-SG" }!!
        val zombie = ZombieEntity(
            id = 1,
            archetype = ZombieArchetype.SHAMBLER,
            x = 0f,
            y = 0f,
            hp = 100,
            maxHp = 100
        )
        val angleRad = 0f // Straight right

        val impact = KnockbackAndPoiseStagger.computeImpact(shotgun, null, zombie, angleRad)

        // Shotgun should have high base magnitude and stagger frames
        assertTrue("Shotgun should produce heavy knockback (>= 150f)", hypot(impact.vx, impact.vy) >= 150f)
        assertTrue("Shotgun should produce significant stagger frames (>= 10)", impact.staggerRecoveryFrames >= 10)
    }

    @Test
    fun `Korsakov manufacturer amplifies knockback and stagger`() {
        val uzi = DEFAULT_WEAPONS.find { it.shortCode == "UZI" }!!
        val zombie = ZombieEntity(
            id = 2,
            archetype = ZombieArchetype.SHAMBLER,
            x = 0f,
            y = 0f,
            hp = 100,
            maxHp = 100
        )
        val angleRad = 0f

        val baseImpact = KnockbackAndPoiseStagger.computeImpact(uzi, null, zombie, angleRad)
        val baseMagnitude = hypot(baseImpact.vx, baseImpact.vy)

        val korsakovInstance = ModularWeaponInstance(
            instanceId = "test-korsakov",
            baseWeaponId = uzi.id,
            customTitle = "Korsakov UZI",
            rarity = LootRarity.UNCOMMON,
            itemLevel = 1,
            receiver = WeaponReceiverPart("Korsakov Rec", WeaponManufacturer.KORSAKOV_ARMORIES, 1f, 1f),
            barrel = WeaponBarrelPart("Standard", 1f, 0, 0, 0f),
            magazine = WeaponMagazinePart("Standard", 0, 1f, ElementalAffinity.KINETIC),
            optic = WeaponOpticPart("Iron Sights", 1f, 0f, 0f)
        )

        val korsakovImpact = KnockbackAndPoiseStagger.computeImpact(uzi, korsakovInstance, zombie, angleRad)
        val korsakovMagnitude = hypot(korsakovImpact.vx, korsakovImpact.vy)

        assertTrue("Korsakov manufacturer should increase knockback magnitude", korsakovMagnitude > baseMagnitude)
        assertTrue("Korsakov manufacturer should increase stagger frames", korsakovImpact.staggerRecoveryFrames > baseImpact.staggerRecoveryFrames)
    }

    @Test
    fun `Devil Boss heavily resists knockback and stagger`() {
        val shotgun = DEFAULT_WEAPONS.find { it.shortCode == "12G-SG" }!!
        val boss = ZombieEntity(
            id = 3,
            archetype = ZombieArchetype.DEVIL_BOSS,
            x = 0f,
            y = 0f,
            hp = 5000,
            maxHp = 5000
        )
        val shambler = ZombieEntity(
            id = 4,
            archetype = ZombieArchetype.SHAMBLER,
            x = 0f,
            y = 0f,
            hp = 100,
            maxHp = 100
        )
        val angleRad = 0f

        val bossImpact = KnockbackAndPoiseStagger.computeImpact(shotgun, null, boss, angleRad)
        val shamblerImpact = KnockbackAndPoiseStagger.computeImpact(shotgun, null, shambler, angleRad)

        assertTrue("Boss should take significantly less knockback than Shambler", hypot(bossImpact.vx, bossImpact.vy) < hypot(shamblerImpact.vx, shamblerImpact.vy))
        assertTrue("Boss should resist stagger completely", bossImpact.staggerRecoveryFrames == 0)
    }
}
