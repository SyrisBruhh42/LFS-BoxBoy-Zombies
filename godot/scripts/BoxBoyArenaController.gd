extends Node2D
class_name BoxBoyArenaController

## Godot 4.7.2 2.5D 3/4 Orthographic Combat & Bridge Controller for BoxBoy Zombies.
## Synchronizes wave state, Pixel 8 Pro LRA haptics, Tensor G3 thermal FPS targets,
## squad voice PTT frames, and desktop mouse/keyboard + twin-stick input.

const ORTHO_Y_SCALE: float = 0.80
const WALL_Z_HEIGHT: float = 40.0
const PILLAR_Z_HEIGHT: float = 48.0
const PLAYER_BASE_SPEED: float = 245.0
const DASH_IMPULSE_SPEED: float = 640.0

const TARGET_FPS_TURBO: int = 120
const TARGET_FPS_BALANCED: int = 60
const TARGET_FPS_RESCUE: int = 30

const WEAPON_SLOTS: Array[String] = [
	"pistol",
	"uzi",
	"shotgun",
	"assault_rifle",
	"grenade_launcher",
	"railgun"
]

var aegis_bridge: Object = null
var current_wave: int = 1
var player_hp: int = 100
var player_max_hp: int = 100
var player_shield: int = 60
var player_points: int = 600
var total_kills: int = 0
var active_weapon_index: int = 0
var active_zombies_count: int = 8
var aim_angle_rad: float = 0.0
var thermal_target_fps: int = TARGET_FPS_TURBO
var shader_complexity_scale: float = 1.0
var is_firing_primary: bool = false
var dash_cooldown_sec: float = 0.0
var telemetry_accum_sec: float = 0.0
var coop_peers_by_id: Dictionary = {}

@onready var player_node: CharacterBody2D = $ArenaNavigationRegion/YSortWorldStage/BoxBoyPlayer
@onready var aim_raycast: RayCast2D = $ArenaNavigationRegion/YSortWorldStage/BoxBoyPlayer/AimRayCast2D
@onready var arena_camera: Camera2D = $TacticalArenaCamera
@onready var hud_status_label: Label = $TacticalHudCanvas/HudStatusLabel

func _ready() -> void:
	Engine.max_fps = TARGET_FPS_TURBO
	_bind_native_aegis_bridge()
	_update_hud_banner()

func _bind_native_aegis_bridge() -> void:
	if Engine.has_singleton("BoxBoyAegisBridge"):
		aegis_bridge = Engine.get_singleton("BoxBoyAegisBridge")
		if aegis_bridge.has_signal("on_thermal_target_fps"):
			aegis_bridge.connect("on_thermal_target_fps", Callable(self, "_on_thermal_target_fps"))
		if aegis_bridge.has_signal("on_coop_peer_snapshot"):
			aegis_bridge.connect("on_coop_peer_snapshot", Callable(self, "_on_coop_peer_snapshot"))
		if aegis_bridge.has_signal("on_load_checkpoint_json"):
			aegis_bridge.connect("on_load_checkpoint_json", Callable(self, "_on_load_checkpoint_json"))
		if aegis_bridge.has_signal("on_comfort_config_updated"):
			aegis_bridge.connect("on_comfort_config_updated", Callable(self, "_on_comfort_config_updated"))

func _notification(what: int) -> void:
	if what == NOTIFICATION_APPLICATION_PAUSED or what == NOTIFICATION_APPLICATION_FOCUS_OUT:
		_sync_wave_state_to_bridge()

func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventMouseMotion:
		if player_node != null:
			var world_mouse: Vector2 = get_global_mouse_position()
			aim_angle_rad = (world_mouse - player_node.global_position).angle()
			if aim_raycast != null:
				aim_raycast.rotation = aim_angle_rad
	elif event is InputEventMouseButton:
		var mb: InputEventMouseButton = event as InputEventMouseButton
		if mb.button_index == MOUSE_BUTTON_LEFT:
			is_firing_primary = mb.pressed
			if mb.pressed:
				_fire_equipped_weapon()
		elif mb.button_index == MOUSE_BUTTON_RIGHT and mb.pressed:
			_trigger_tactical_dash()
		elif mb.button_index == MOUSE_BUTTON_WHEEL_UP and mb.pressed:
			cycle_weapon(-1)
		elif mb.button_index == MOUSE_BUTTON_WHEEL_DOWN and mb.pressed:
			cycle_weapon(1)
	elif event is InputEventKey and event.pressed and not event.echo:
		var key_event: InputEventKey = event as InputEventKey
		match key_event.keycode:
			KEY_1:
				select_weapon_slot(0)
			KEY_2:
				select_weapon_slot(1)
			KEY_3:
				select_weapon_slot(2)
			KEY_4:
				select_weapon_slot(3)
			KEY_5:
				select_weapon_slot(4)
			KEY_6:
				select_weapon_slot(5)
			KEY_Q:
				cycle_weapon(-1)
			KEY_E:
				_trigger_station_interaction()
			KEY_V:
				dispatch_squad_voice_ptt("#party:lfs-boxboy-zombies:default", "PCM16_OPUS_FRAME", 60)

func _physics_process(delta: float) -> void:
	var clamped_dt: float = clampf(delta, 0.002, 0.050)
	if dash_cooldown_sec > 0.0:
		dash_cooldown_sec = maxf(0.0, dash_cooldown_sec - clamped_dt)

	var move_vec: Vector2 = _sample_wasd_movement_vector()
	var arrow_aim: Vector2 = _sample_arrow_aim_vector()
	if arrow_aim.length_squared() > 0.04:
		aim_angle_rad = arrow_aim.angle()
		if aim_raycast != null:
			aim_raycast.rotation = aim_angle_rad

	if player_node != null:
		var effective_speed: float = PLAYER_BASE_SPEED
		player_node.velocity = Vector2(move_vec.x * effective_speed, move_vec.y * effective_speed * ORTHO_Y_SCALE)
		player_node.move_and_slide()
		if arena_camera != null:
			var lead_offset: Vector2 = Vector2(cos(aim_angle_rad), sin(aim_angle_rad) * ORTHO_Y_SCALE) * 56.0
			arena_camera.global_position = player_node.global_position + lead_offset

	telemetry_accum_sec += clamped_dt
	if telemetry_accum_sec >= 0.50:
		telemetry_accum_sec = 0.0
		var measured_fps: float = float(Engine.get_frames_per_second())
		var frame_ms: float = clamped_dt * 1000.0
		_report_frame_timing_to_bridge(measured_fps, frame_ms)
		_sync_wave_state_to_bridge()

func _sample_wasd_movement_vector() -> Vector2:
	var vx: float = 0.0
	var vy: float = 0.0
	if Input.is_physical_key_pressed(KEY_A):
		vx -= 1.0
	if Input.is_physical_key_pressed(KEY_D):
		vx += 1.0
	if Input.is_physical_key_pressed(KEY_W):
		vy -= 1.0
	if Input.is_physical_key_pressed(KEY_S):
		vy += 1.0
	var vec := Vector2(vx, vy)
	return vec.normalized() if vec.length_squared() > 1.0 else vec

func _sample_arrow_aim_vector() -> Vector2:
	var ax: float = 0.0
	var ay: float = 0.0
	if Input.is_physical_key_pressed(KEY_LEFT):
		ax -= 1.0
	if Input.is_physical_key_pressed(KEY_RIGHT):
		ax += 1.0
	if Input.is_physical_key_pressed(KEY_UP):
		ay -= 1.0
	if Input.is_physical_key_pressed(KEY_DOWN):
		ay += 1.0
	return Vector2(ax, ay)

func select_weapon_slot(slot_index: int) -> void:
	active_weapon_index = clampi(slot_index, 0, WEAPON_SLOTS.size() - 1)
	_trigger_pixel_haptic("WEAPON_SWAP", 0.35)
	_update_hud_banner()

func cycle_weapon(delta_slot: int) -> void:
	var count: int = WEAPON_SLOTS.size()
	active_weapon_index = (active_weapon_index + delta_slot + count) % count
	_trigger_pixel_haptic("WEAPON_SWAP", 0.35)
	_update_hud_banner()

func _fire_equipped_weapon() -> void:
	var weapon_id: String = WEAPON_SLOTS[active_weapon_index]
	var intensity: float = 0.85 if (weapon_id == "shotgun" or weapon_id == "railgun") else 0.45
	_trigger_pixel_haptic("WEAPON_FIRE_" + weapon_id.to_upper(), intensity)

func _trigger_tactical_dash() -> void:
	if dash_cooldown_sec > 0.0 or player_node == null:
		return
	dash_cooldown_sec = 1.15
	var dash_dir := Vector2(cos(aim_angle_rad), sin(aim_angle_rad) * ORTHO_Y_SCALE)
	player_node.global_position += dash_dir * 84.0
	_trigger_pixel_haptic("TACTICAL_DASH", 0.65)

func _trigger_station_interaction() -> void:
	_trigger_pixel_haptic("STATION_INTERACT", 0.50)
	_sync_wave_state_to_bridge()

func build_wave_state_json() -> String:
	var px: float = player_node.global_position.x if player_node != null else 800.0
	var py: float = player_node.global_position.y if player_node != null else 450.0
	var payload: Dictionary = {
		"wave": current_wave,
		"hp": player_hp,
		"maxHp": player_max_hp,
		"shield": player_shield,
		"points": player_points,
		"totalKills": total_kills,
		"weapon": WEAPON_SLOTS[active_weapon_index],
		"activeZombies": active_zombies_count,
		"playerX": roundi(px),
		"playerY": roundi(py),
		"targetFps": thermal_target_fps
	}
	return JSON.stringify(payload)

func _sync_wave_state_to_bridge() -> void:
	var json_str: String = build_wave_state_json()
	if aegis_bridge != null and aegis_bridge.has_method("syncWaveStateJson"):
		aegis_bridge.call("syncWaveStateJson", json_str)

func _trigger_pixel_haptic(effect_id: String, intensity: float) -> void:
	if aegis_bridge != null and aegis_bridge.has_method("triggerPixelHaptic"):
		aegis_bridge.call("triggerPixelHaptic", effect_id, clampf(intensity, 0.0, 1.0))

func _report_frame_timing_to_bridge(fps: float, frame_time_ms: float) -> void:
	if aegis_bridge != null and aegis_bridge.has_method("reportFrameTiming"):
		aegis_bridge.call("reportFrameTiming", fps, frame_time_ms)

func dispatch_squad_voice_ptt(channel_id: String, pcm_base64: String, duration_ms: int) -> void:
	if aegis_bridge != null and aegis_bridge.has_method("dispatchSquadVoicePttFrame"):
		aegis_bridge.call("dispatchSquadVoicePttFrame", channel_id, pcm_base64, duration_ms)

func _on_thermal_target_fps(target_fps: int) -> void:
	thermal_target_fps = clampi(target_fps, TARGET_FPS_RESCUE, TARGET_FPS_TURBO)
	Engine.max_fps = thermal_target_fps
	if thermal_target_fps >= TARGET_FPS_TURBO:
		shader_complexity_scale = 1.0
	elif thermal_target_fps >= TARGET_FPS_BALANCED:
		shader_complexity_scale = 0.75
	else:
		shader_complexity_scale = 0.50
	_update_hud_banner()

func _on_coop_peer_snapshot(peer_json: String) -> void:
	var parsed: Variant = JSON.parse_string(peer_json)
	if typeof(parsed) == TYPE_DICTIONARY:
		var dict: Dictionary = parsed
		var peer_id: String = str(dict.get("peerId", "peer-unknown"))
		coop_peers_by_id[peer_id] = dict
		_update_hud_banner()

func _on_load_checkpoint_json(checkpoint_json: String) -> void:
	var parsed: Variant = JSON.parse_string(checkpoint_json)
	if typeof(parsed) == TYPE_DICTIONARY:
		var dict: Dictionary = parsed
		current_wave = int(dict.get("wave", current_wave))
		player_hp = int(dict.get("hp", player_hp))
		player_points = int(dict.get("points", player_points))
		total_kills = int(dict.get("totalKills", total_kills))
		_update_hud_banner()

func _on_comfort_config_updated(_config_json: String) -> void:
	_update_hud_banner()

func _update_hud_banner() -> void:
	if hud_status_label != null:
		var wpn: String = WEAPON_SLOTS[active_weapon_index].to_upper()
		hud_status_label.text = "WAVE %d | HP %d/%d | PTS %d | WPN %s | %d FPS TARGET | PEERS %d" % [
			current_wave,
			player_hp,
			player_max_hp,
			player_points,
			wpn,
			thermal_target_fps,
			coop_peers_by_id.size()
		]
