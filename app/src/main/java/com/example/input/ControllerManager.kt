package com.example.input

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Identifiers for all customizable on-screen MyBoy!-style touch controls.
 */
enum class TouchButtonId(val displayName: String, val shortLabel: String) {
    BTN_A("A Button (Right Primary)", "A"),
    BTN_B("B Button (Bottom Action)", "B"),
    BTN_X("X Button (Left Tactical)", "X"),
    BTN_Y("Y Button (Top Utility)", "Y"),
    BTN_L1("L1 Shoulder Bumper", "L"),
    BTN_R1("R1 Shoulder Bumper", "R")
}

/**
 * High-level gameplay actions that can be bound to any touch button or hardware keycode.
 */
enum class GamepadAction(
    val displayName: String,
    val hudBadge: String,
    val isContinuousHold: Boolean
) {
    FIRE_WEAPON("Fire Equipped Weapon", "FIRE", true),
    INTERACT_BUY("Interact / Unlock Door / Buy", "ACT", false),
    DROP_BARREL("Drop Explosive Red Barrel", "BRL", false),
    TACTICAL_RELOAD("Tactical Magazine Reload", "RLD", false),
    PREV_WEAPON("Switch Previous Weapon", "PREV", false),
    NEXT_WEAPON("Switch Next Weapon", "NEXT", false),
    TOGGLE_SPEED("Cycle Emulator Clock Speed", "SPD+", false),
    TOGGLE_AI_BOT("Toggle Autonomous AI Bot", "BOT", false)
}

/**
 * Immutable data class defining custom button-to-action mappings for both
 * on-screen touch controls and physical gamepad/keyboard keycodes, plus D-Pad deadzone & sensitivity.
 */
data class GamepadMapping(
    val mappingName: String = "MyBoy! Standard",
    val touchBindings: Map<TouchButtonId, GamepadAction> = DEFAULT_TOUCH_BINDINGS,
    val hardwareKeyBindings: Map<Int, GamepadAction> = DEFAULT_HARDWARE_BINDINGS,
    val dpadDeadzone: Float = 0.12f,
    val touchHapticsEnabled: Boolean = true
) {
    fun actionForTouchButton(buttonId: TouchButtonId): GamepadAction {
        return touchBindings[buttonId] ?: DEFAULT_TOUCH_BINDINGS.getValue(buttonId)
    }

    fun actionForHardwareKey(keyCode: Int): GamepadAction? {
        return hardwareKeyBindings[keyCode]
    }

    fun labelForTouchButton(buttonId: TouchButtonId, isReloading: Boolean = false, hasInteractTarget: Boolean = false): String {
        val action = actionForTouchButton(buttonId)
        val suffix = when {
            action == GamepadAction.TACTICAL_RELOAD && isReloading -> "RLD.."
            action == GamepadAction.INTERACT_BUY && hasInteractTarget -> "BUY!"
            else -> action.hudBadge
        }
        return "${buttonId.shortLabel}|$suffix"
    }

    fun toSerializedString(): String {
        val touchPart = touchBindings.entries.joinToString(",") { "${it.key.name}=${it.value.name}" }
        val hwPart = hardwareKeyBindings.entries.joinToString(",") { "${it.key}=${it.value.name}" }
        return "NAME:$mappingName|DZ:$dpadDeadzone|TOUCH:$touchPart|HW:$hwPart"
    }

    companion object {
        val DEFAULT_TOUCH_BINDINGS: Map<TouchButtonId, GamepadAction> = mapOf(
            TouchButtonId.BTN_A to GamepadAction.FIRE_WEAPON,
            TouchButtonId.BTN_B to GamepadAction.INTERACT_BUY,
            TouchButtonId.BTN_X to GamepadAction.DROP_BARREL,
            TouchButtonId.BTN_Y to GamepadAction.TACTICAL_RELOAD,
            TouchButtonId.BTN_L1 to GamepadAction.PREV_WEAPON,
            TouchButtonId.BTN_R1 to GamepadAction.NEXT_WEAPON
        )

        val DEFAULT_HARDWARE_BINDINGS: Map<Int, GamepadAction> = mapOf(
            KeyEvent.KEYCODE_BUTTON_A to GamepadAction.FIRE_WEAPON,
            KeyEvent.KEYCODE_SPACE to GamepadAction.FIRE_WEAPON,
            KeyEvent.KEYCODE_BUTTON_B to GamepadAction.INTERACT_BUY,
            KeyEvent.KEYCODE_E to GamepadAction.INTERACT_BUY,
            KeyEvent.KEYCODE_BUTTON_X to GamepadAction.DROP_BARREL,
            KeyEvent.KEYCODE_Q to GamepadAction.DROP_BARREL,
            KeyEvent.KEYCODE_BUTTON_Y to GamepadAction.TACTICAL_RELOAD,
            KeyEvent.KEYCODE_R to GamepadAction.TACTICAL_RELOAD,
            KeyEvent.KEYCODE_BUTTON_L1 to GamepadAction.PREV_WEAPON,
            KeyEvent.KEYCODE_BUTTON_R1 to GamepadAction.NEXT_WEAPON
        )

        fun fromSerializedString(raw: String): GamepadMapping {
            if (raw.isBlank() || !raw.contains("TOUCH:")) return GamepadMapping()
            return try {
                val sections = raw.split("|").associate {
                    val k = it.substringBefore(":")
                    val v = it.substringAfter(":", "")
                    k to v
                }
                val name = sections["NAME"]?.ifBlank { "Custom Mapping" } ?: "Custom Mapping"
                val dz = sections["DZ"]?.toFloatOrNull()?.coerceIn(0.02f, 0.45f) ?: 0.12f

                val parsedTouch = DEFAULT_TOUCH_BINDINGS.toMutableMap()
                sections["TOUCH"]?.split(",")?.forEach { pair ->
                    val btnName = pair.substringBefore("=")
                    val actName = pair.substringAfter("=", "")
                    val btn = TouchButtonId.entries.find { it.name == btnName }
                    val act = GamepadAction.entries.find { it.name == actName }
                    if (btn != null && act != null) {
                        parsedTouch[btn] = act
                    }
                }

                val parsedHw = DEFAULT_HARDWARE_BINDINGS.toMutableMap()
                sections["HW"]?.split(",")?.forEach { pair ->
                    val code = pair.substringBefore("=").toIntOrNull()
                    val actName = pair.substringAfter("=", "")
                    val act = GamepadAction.entries.find { it.name == actName }
                    if (code != null && act != null) {
                        parsedHw[code] = act
                    }
                }

                GamepadMapping(
                    mappingName = name,
                    touchBindings = parsedTouch,
                    hardwareKeyBindings = parsedHw,
                    dpadDeadzone = dz
                )
            } catch (_: Exception) {
                GamepadMapping()
            }
        }
    }
}

/**
 * Callback interface for executing mapped [GamepadAction] events triggered via touch or hardware input.
 */
interface GamepadActionCallbacks {
    fun onFireHoldChanged(isFiring: Boolean)
    fun onInteractTriggered()
    fun onDropBarrelTriggered()
    fun onReloadTriggered()
    fun onCycleWeapon(delta: Int)
    fun onCycleSpeed()
    fun onToggleAiBot()
}

/**
 * Singleton manager that maintains active [GamepadMapping] state, built-in tactical controller presets,
 * touch button remapping, hardware keycode remapping, and deadzone-filtered D-Pad vector calculation.
 */
object ControllerManager {

    val PRESET_STANDARD = GamepadMapping(
        mappingName = "MyBoy! Classic (A=Fire, Y=Reload)"
    )

    val PRESET_BUMPER_SHOOTER = GamepadMapping(
        mappingName = "Bumper Shooter (R1=Fire, A=Reload, L1=Barrel)",
        touchBindings = mapOf(
            TouchButtonId.BTN_R1 to GamepadAction.FIRE_WEAPON,
            TouchButtonId.BTN_L1 to GamepadAction.DROP_BARREL,
            TouchButtonId.BTN_A to GamepadAction.TACTICAL_RELOAD,
            TouchButtonId.BTN_B to GamepadAction.INTERACT_BUY,
            TouchButtonId.BTN_X to GamepadAction.PREV_WEAPON,
            TouchButtonId.BTN_Y to GamepadAction.NEXT_WEAPON
        )
    )

    val PRESET_SOUTHPAW_ARCADE = GamepadMapping(
        mappingName = "Arcade Blitz (X=Fire, A=Barrel, B=Reload)",
        touchBindings = mapOf(
            TouchButtonId.BTN_X to GamepadAction.FIRE_WEAPON,
            TouchButtonId.BTN_A to GamepadAction.DROP_BARREL,
            TouchButtonId.BTN_B to GamepadAction.TACTICAL_RELOAD,
            TouchButtonId.BTN_Y to GamepadAction.INTERACT_BUY,
            TouchButtonId.BTN_L1 to GamepadAction.PREV_WEAPON,
            TouchButtonId.BTN_R1 to GamepadAction.NEXT_WEAPON
        )
    )

    val ALL_PRESETS: List<GamepadMapping> = listOf(
        PRESET_STANDARD,
        PRESET_BUMPER_SHOOTER,
        PRESET_SOUTHPAW_ARCADE
    )

    private val _activeMapping = MutableStateFlow(PRESET_STANDARD)
    val activeMapping: StateFlow<GamepadMapping> = _activeMapping.asStateFlow()

    fun applyMapping(mapping: GamepadMapping) {
        _activeMapping.value = mapping
    }

    fun bindTouchButton(buttonId: TouchButtonId, action: GamepadAction): GamepadMapping {
        val current = _activeMapping.value
        val updatedMap = current.touchBindings.toMutableMap().apply {
            this[buttonId] = action
        }
        val next = current.copy(
            mappingName = "Custom Touch Map",
            touchBindings = updatedMap
        )
        _activeMapping.value = next
        return next
    }

    fun bindHardwareKey(keyCode: Int, action: GamepadAction): GamepadMapping {
        val current = _activeMapping.value
        val updatedHw = current.hardwareKeyBindings.toMutableMap().apply {
            this[keyCode] = action
        }
        val next = current.copy(
            mappingName = "Custom Hardware Map",
            hardwareKeyBindings = updatedHw
        )
        _activeMapping.value = next
        return next
    }

    fun updateDeadzone(deadzone: Float): GamepadMapping {
        val next = _activeMapping.value.copy(dpadDeadzone = deadzone.coerceIn(0.02f, 0.45f))
        _activeMapping.value = next
        return next
    }

    fun resetToDefault(): GamepadMapping {
        _activeMapping.value = PRESET_STANDARD
        return PRESET_STANDARD
    }

    /**
     * Applies smooth 360-degree analog radial deadzone filtering to raw touch D-Pad input in [-1f, 1f]
     * without 8-way angle snapping or deadzone step-jumps.
     */
    fun filterDpadVector(rawX: Float, rawY: Float): Pair<Float, Float> {
        val dz = _activeMapping.value.dpadDeadzone.coerceIn(0.02f, 0.35f)
        val mag = kotlin.math.hypot(rawX, rawY)
        if (mag < dz) return 0f to 0f
        val linear = ((mag - dz) / (1f - dz)).coerceIn(0f, 1f)
        // Smooth blend (75% linear + 25% cubic) for fine micro-control near center and full speed at rim
        val curvedMag = linear * (0.75f + 0.25f * linear * linear)
        val scale = curvedMag / mag
        return (rawX * scale).coerceIn(-1f, 1f) to (rawY * scale).coerceIn(-1f, 1f)
    }

    // =========================================================================
    // Unified WASD-to-Joystick Bridge & Browser Key-Repeat Anti-Stutter Engine
    // =========================================================================
    // In browser-streamed Android emulators, holding WASD often emits rapid KEY_DOWN/KEY_UP
    // repeat pulses separated by ~80-140ms gaps, or loses Compose focus when clicking UI.
    // We intercept keys at Activity.dispatchKeyEvent and apply a 115ms key-release grace
    // window (cancelled immediately on opposite-axis key press) plus smooth analog joystick
    // interpolation so WASD drives the virtual joystick at a silky-smooth 60-120Hz.
    private const val KEY_REPEAT_SUSTAIN_MS = 115L

    @Volatile var touchJoyX: Float = 0f
    @Volatile var touchJoyY: Float = 0f
    @Volatile var isTouchJoyActive: Boolean = false

    @Volatile private var keyWDown: Boolean = false
    @Volatile private var keySDown: Boolean = false
    @Volatile private var keyADown: Boolean = false
    @Volatile private var keyDDown: Boolean = false

    @Volatile private var keyWReleaseGraceUntilMs: Long = 0L
    @Volatile private var keySReleaseGraceUntilMs: Long = 0L
    @Volatile private var keyAReleaseGraceUntilMs: Long = 0L
    @Volatile private var keyDReleaseGraceUntilMs: Long = 0L

    @Volatile private var keyAimUpDown: Boolean = false
    @Volatile private var keyAimDownDown: Boolean = false
    @Volatile private var keyAimLeftDown: Boolean = false
    @Volatile private var keyAimRightDown: Boolean = false

    @Volatile private var keyAimUpGraceUntilMs: Long = 0L
    @Volatile private var keyAimDownGraceUntilMs: Long = 0L
    @Volatile private var keyAimLeftGraceUntilMs: Long = 0L
    @Volatile private var keyAimRightGraceUntilMs: Long = 0L

    @Volatile var hwFireDown: Boolean = false
    @Volatile private var hwFireGraceUntilMs: Long = 0L

    @Volatile var pendingInteractTrigger: Boolean = false
    @Volatile var pendingBarrelDropTrigger: Boolean = false
    @Volatile var pendingReloadTrigger: Boolean = false
    @Volatile var pendingWeaponCycleDelta: Int = 0

    @Volatile var smoothedJoyX: Float = 0f
        private set
    @Volatile var smoothedJoyY: Float = 0f
        private set

    fun updateTouchJoystick(rawX: Float, rawY: Float) {
        val (fx, fy) = filterDpadVector(rawX, rawY)
        touchJoyX = fx
        touchJoyY = fy
        isTouchJoyActive = kotlin.math.hypot(fx, fy) > 0.005f
    }

    /**
     * Window-level hardware key handler invoked from MainActivity.dispatchKeyEvent and Compose onKeyEvent.
     * Ensures WASD, Arrow keys, Space, E, Q, R, 1, 2 never drop when clicking the canvas or HUD.
     */
    fun onHardwareKeyEvent(keyCode: Int, isDown: Boolean, eventTimeMs: Long): Boolean {
        val graceUntil = eventTimeMs + KEY_REPEAT_SUSTAIN_MS
        return when (keyCode) {
            KeyEvent.KEYCODE_W -> {
                keyWDown = isDown
                if (isDown) {
                    keyWReleaseGraceUntilMs = graceUntil
                    // Cancel opposite axis grace immediately for crisp counter-strafing
                    keySReleaseGraceUntilMs = 0L
                } else {
                    keyWReleaseGraceUntilMs = graceUntil
                }
                true
            }
            KeyEvent.KEYCODE_S -> {
                keySDown = isDown
                if (isDown) {
                    keySReleaseGraceUntilMs = graceUntil
                    keyWReleaseGraceUntilMs = 0L
                } else {
                    keySReleaseGraceUntilMs = graceUntil
                }
                true
            }
            KeyEvent.KEYCODE_A -> {
                keyADown = isDown
                if (isDown) {
                    keyAReleaseGraceUntilMs = graceUntil
                    keyDReleaseGraceUntilMs = 0L
                } else {
                    keyAReleaseGraceUntilMs = graceUntil
                }
                true
            }
            KeyEvent.KEYCODE_D -> {
                keyDDown = isDown
                if (isDown) {
                    keyDReleaseGraceUntilMs = graceUntil
                    keyAReleaseGraceUntilMs = 0L
                } else {
                    keyDReleaseGraceUntilMs = graceUntil
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                keyAimUpDown = isDown
                keyAimUpGraceUntilMs = graceUntil
                if (isDown) keyAimDownGraceUntilMs = 0L
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                keyAimDownDown = isDown
                keyAimDownGraceUntilMs = graceUntil
                if (isDown) keyAimUpGraceUntilMs = 0L
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                keyAimLeftDown = isDown
                keyAimLeftGraceUntilMs = graceUntil
                if (isDown) keyAimRightGraceUntilMs = 0L
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                keyAimRightDown = isDown
                keyAimRightGraceUntilMs = graceUntil
                if (isDown) keyAimLeftGraceUntilMs = 0L
                true
            }
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_R1 -> {
                hwFireDown = isDown
                hwFireGraceUntilMs = graceUntil
                true
            }
            KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_BUTTON_B -> {
                if (isDown) pendingInteractTrigger = true
                true
            }
            KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_BUTTON_X -> {
                if (isDown) pendingBarrelDropTrigger = true
                true
            }
            KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_BUTTON_Y -> {
                if (isDown) pendingReloadTrigger = true
                true
            }
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_BUTTON_L1 -> {
                if (isDown) pendingWeaponCycleDelta = -1
                true
            }
            KeyEvent.KEYCODE_2 -> {
                if (isDown) pendingWeaponCycleDelta = 1
                true
            }
            else -> false
        }
    }

    /**
     * Computes the unified WASD + Touch Virtual Joystick vector and smoothly interpolates
     * [smoothedJoyX] and [smoothedJoyY] so WASD feels like a responsive 360 deg analog stick
     * without key-repeat micro-stutters.
     */
    fun stepAndSampleJoystickVector(nowUptimeMs: Long, dtSec: Float): Pair<Float, Float> {
        val wActive = keyWDown || nowUptimeMs < keyWReleaseGraceUntilMs
        val sActive = keySDown || nowUptimeMs < keySReleaseGraceUntilMs
        val aActive = keyADown || nowUptimeMs < keyAReleaseGraceUntilMs
        val dActive = keyDDown || nowUptimeMs < keyDReleaseGraceUntilMs

        val kx = (if (dActive) 1f else 0f) - (if (aActive) 1f else 0f)
        val ky = (if (sActive) 1f else 0f) - (if (wActive) 1f else 0f)

        val targetX: Float
        val targetY: Float
        if (kx != 0f || ky != 0f) {
            val len = kotlin.math.hypot(kx, ky)
            targetX = kx / len
            targetY = ky / len
        } else if (isTouchJoyActive || kotlin.math.hypot(touchJoyX, touchJoyY) > 0.005f) {
            targetX = touchJoyX
            targetY = touchJoyY
        } else {
            targetX = 0f
            targetY = 0f
        }

        // Fast analog response (accelerates in ~28ms, glides to rest in ~40ms)
        val isAccelerating = kotlin.math.hypot(targetX, targetY) > 0.05f
        val rate = if (isAccelerating) 34f else 26f
        val alpha = (dtSec * rate).coerceIn(0.32f, 1f)
        var nx = smoothedJoyX + (targetX - smoothedJoyX) * alpha
        var ny = smoothedJoyY + (targetY - smoothedJoyY) * alpha
        val mag = kotlin.math.hypot(nx, ny)
        if (mag < 0.015f && !isAccelerating) {
            nx = 0f
            ny = 0f
        } else if (mag > 1f) {
            nx /= mag
            ny /= mag
        }
        smoothedJoyX = nx
        smoothedJoyY = ny
        return nx to ny
    }

    fun sampleArrowAimAngleRad(nowUptimeMs: Long): Float? {
        val up = keyAimUpDown || nowUptimeMs < keyAimUpGraceUntilMs
        val down = keyAimDownDown || nowUptimeMs < keyAimDownGraceUntilMs
        val left = keyAimLeftDown || nowUptimeMs < keyAimLeftGraceUntilMs
        val right = keyAimRightDown || nowUptimeMs < keyAimRightGraceUntilMs
        val ax = (if (right) 1f else 0f) - (if (left) 1f else 0f)
        val ay = (if (down) 1f else 0f) - (if (up) 1f else 0f)
        if (ax != 0f || ay != 0f) {
            return kotlin.math.atan2(ay, ax)
        }
        return null
    }

    fun isArrowOrHwFiring(nowUptimeMs: Long): Boolean {
        val arrowActive = keyAimUpDown || keyAimDownDown || keyAimLeftDown || keyAimRightDown ||
            nowUptimeMs < keyAimUpGraceUntilMs || nowUptimeMs < keyAimDownGraceUntilMs ||
            nowUptimeMs < keyAimLeftGraceUntilMs || nowUptimeMs < keyAimRightGraceUntilMs
        val spaceActive = hwFireDown || nowUptimeMs < hwFireGraceUntilMs
        return arrowActive || spaceActive
    }

    /**
     * Dispatches a touch button press or release according to the active [GamepadMapping].
     */
    fun dispatchTouchButtonEvent(
        buttonId: TouchButtonId,
        isPressed: Boolean,
        callbacks: GamepadActionCallbacks
    ) {
        val action = _activeMapping.value.actionForTouchButton(buttonId)
        dispatchAction(action, isPressed, callbacks)
    }

    /**
     * Dispatches a hardware keycode press or release according to the active [GamepadMapping].
     * Returns true if the keycode was mapped to an action.
     */
    fun dispatchHardwareKeyEvent(
        keyCode: Int,
        isPressed: Boolean,
        callbacks: GamepadActionCallbacks
    ): Boolean {
        val action = _activeMapping.value.actionForHardwareKey(keyCode) ?: return false
        dispatchAction(action, isPressed, callbacks)
        return true
    }

    fun dispatchAction(
        action: GamepadAction,
        isPressed: Boolean,
        callbacks: GamepadActionCallbacks
    ) {
        when (action) {
            GamepadAction.FIRE_WEAPON -> callbacks.onFireHoldChanged(isPressed)
            GamepadAction.INTERACT_BUY -> if (isPressed) callbacks.onInteractTriggered()
            GamepadAction.DROP_BARREL -> if (isPressed) callbacks.onDropBarrelTriggered()
            GamepadAction.TACTICAL_RELOAD -> if (isPressed) callbacks.onReloadTriggered()
            GamepadAction.PREV_WEAPON -> if (isPressed) callbacks.onCycleWeapon(-1)
            GamepadAction.NEXT_WEAPON -> if (isPressed) callbacks.onCycleWeapon(1)
            GamepadAction.TOGGLE_SPEED -> if (isPressed) callbacks.onCycleSpeed()
            GamepadAction.TOGGLE_AI_BOT -> if (isPressed) callbacks.onToggleAiBot()
        }
    }
}
