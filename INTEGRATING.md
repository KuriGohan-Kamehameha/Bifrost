# Integrating with Bifrost

Bifrost exposes a broadcast-based IPC API that lets other apps on the device drive its LEDs — flashing police lights during a car chase, matching the LED colour to a character's health bar, setting a preset for your launcher's wallpaper, whatever you need. It is entirely opt-in: the user must enable **"Allow third-party LED control"** in Bifrost's settings before any command is accepted.

---

## Quick orientation

| Concept | What it means for you |
|---|---|
| **Commands are fire-and-forget broadcasts** | Send an ordered broadcast, optionally read the result code back. |
| **Your override is a layer above app-profile switching** | Bifrost resumes its normal behaviour when your override ends. |
| **Bifrost must already be running** | You cannot start the service from the background. If it's not running, commands are silently dropped — this is intentional. |
| **Rate limit: 8 burst / 4 per second** | Per calling UID. Burst budget refills at 4 tokens/s. Exceeding it returns `RESULT_REJECTED_RATE_LIMITED`. |

---

## 1 — Declare the permission

In your `AndroidManifest.xml`, declare that you want to use the permission. Android will show this to the user during install.

```xml
<uses-permission android:name="com.moonbench.bifrost.permission.CONTROL_LEDS" />
```

You do **not** need to be granted the permission at runtime — it is declared at `normal` protection level, so Android grants it automatically at install time. The user still has to flip the toggle inside Bifrost.

---

## 2 — Copy the constants

You only need the string values; there is no library to depend on. Copy these into your own project or hardcode the strings directly.

```kotlin
object BifrostApi {

    const val PERMISSION         = "com.moonbench.bifrost.permission.CONTROL_LEDS"
    const val RECEIVER_PACKAGE   = "com.moonbench.bifrost"
    const val RECEIVER_CLASS     = "com.moonbench.bifrost.external.ExternalApiReceiver"

    const val ACTION_DISPLAY          = "com.moonbench.bifrost.api.ACTION_DISPLAY"
    const val ACTION_CLEAR            = "com.moonbench.bifrost.api.ACTION_CLEAR"
    const val ACTION_INSTALL_PROFILE  = "com.moonbench.bifrost.api.ACTION_INSTALL_PROFILE"
    const val ACTION_UNINSTALL_PROFILE = "com.moonbench.bifrost.api.ACTION_UNINSTALL_PROFILE"

    const val API_VERSION = 1

    // Common extras
    const val EXTRA_API_VERSION  = "apiVersion"   // Int — always include, set to API_VERSION
    const val EXTRA_REQUEST_ID   = "requestId"    // String? — optional, echoed in result data

    // DISPLAY extras
    const val EXTRA_EFFECT       = "effect"        // String — animation name, see §4
    const val EXTRA_COLOR        = "color"         // Int (ARGB) — left LED
    const val EXTRA_COLOR_RIGHT  = "colorRight"    // Int (ARGB) — right LED; defaults to color
    const val EXTRA_INTENSITY    = "intensity"     // Int 0–255 (or 0–intensityScale if scale set)
    const val EXTRA_INTENSITY_SCALE = "intensityScale" // Int 1–255; omit to use 0–255 range
    const val EXTRA_SPEED        = "speed"         // Float 0.0–1.0
    const val EXTRA_SMOOTHNESS   = "smoothness"    // Float 0.0–1.0
    const val EXTRA_SENSITIVITY  = "sensitivity"   // Float 0.0–1.0
    const val EXTRA_PRIORITY     = "priority"      // Int 0–100; default 50
    const val EXTRA_DURATION_MS  = "durationMs"    // Long 1–600_000; use for timed override
    const val EXTRA_UNTIL        = "until"         // String "NEXT_COMMAND" | "EXPLICIT_CLEAR"
    const val EXTRA_INDEFINITE   = "indefinite"    // Boolean — same as UNTIL_EXPLICIT_CLEAR

    const val UNTIL_NEXT_COMMAND   = "NEXT_COMMAND"
    const val UNTIL_EXPLICIT_CLEAR = "EXPLICIT_CLEAR"

    // INSTALL_PROFILE extras (all DISPLAY extras apply, plus:)
    const val EXTRA_PROFILE_NAME            = "profileName"
    const val EXTRA_PROFILE_REPLACE_IF_EXISTS = "replaceIfExists"  // Boolean
    const val EXTRA_SATURATION_BOOST        = "saturationBoost"    // Float 0.0–1.0
    const val EXTRA_USE_CUSTOM_SAMPLING     = "useCustomSampling"  // Boolean
    const val EXTRA_USE_SINGLE_COLOR        = "useSingleColor"     // Boolean
    const val EXTRA_BREATHE_WHEN_CHARGING   = "breatheWhenCharging"
    const val EXTRA_INDICATE_CHARGING_SPEED = "indicateChargingSpeed"
    const val EXTRA_FLASH_WHEN_READY        = "flashWhenReady"
    const val EXTRA_BATTERY_LOW_COLOR       = "batteryLowColor"    // Int (ARGB) optional
    const val EXTRA_BATTERY_MID_COLOR       = "batteryMidColor"
    const val EXTRA_BATTERY_HIGH_COLOR      = "batteryHighColor"
    const val EXTRA_CPU_COOL_COLOR          = "cpuCoolColor"
    const val EXTRA_CPU_WARM_COLOR          = "cpuWarmColor"
    const val EXTRA_CPU_HOT_COLOR           = "cpuHotColor"

    // Result codes (only meaningful with ordered broadcasts)
    const val RESULT_ACCEPTED           =  0
    const val RESULT_REJECTED_DISABLED  = -1   // user hasn't enabled the toggle
    const val RESULT_REJECTED_VERSION   = -2   // apiVersion mismatch
    const val RESULT_REJECTED_VALIDATION = -3  // bad extras
    const val RESULT_REJECTED_UNAUTHORIZED = -4 // caller UID could not be resolved
    const val RESULT_REJECTED_RATE_LIMITED = -5
    const val RESULT_REJECTED_UNKNOWN_ACTION = -6
}
```

---

## 3 — Send a command

All commands go to the same receiver component via explicit broadcast. An **explicit** intent (package + class) is required on Android 8+.

```kotlin
fun sendToBifrost(context: Context, action: String, fill: Intent.() -> Unit = {}) {
    val intent = Intent(action).apply {
        setClassName(BifrostApi.RECEIVER_PACKAGE, BifrostApi.RECEIVER_CLASS)
        putExtra(BifrostApi.EXTRA_API_VERSION, BifrostApi.API_VERSION)
        fill()
    }
    // sendBroadcast = fire and forget
    context.sendBroadcast(intent, BifrostApi.PERMISSION)
}
```

To read the result code back, use `sendOrderedBroadcast`:

```kotlin
context.sendOrderedBroadcast(
    intent,
    BifrostApi.PERMISSION,
    object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            val code = resultCode   // one of RESULT_* constants
            val reqId = resultData  // your requestId, if you set one
            Log.d("MyApp", "Bifrost replied: $code / $reqId")
        }
    },
    null, Activity.RESULT_OK, null, null
)
```

You only need the ordered variant if you care about the reply. For most fire-and-forget cases (`DISPLAY` during a cutscene, `CLEAR` on game over) a plain `sendBroadcast` is fine.

---

## 4 — Available effects

These effect names go in `EXTRA_EFFECT`. Effects that need screen capture (`AMBILIGHT`, `AUDIO_REACTIVE`, `AMBIAURORA`) are **not available** via the external API — Bifrost rejects them with `RESULT_REJECTED_VALIDATION` because they require a consent flow only Bifrost itself can trigger.

| Name | Supports color | Supports speed | Supports smoothness |
|---|---|---|---|
| `STATIC` | ✓ | — | — |
| `BREATH` | ✓ | ✓ | — |
| `PULSE` | ✓ | ✓ | — |
| `STROBE` | ✓ | ✓ | — |
| `SPARKLE` | ✓ | ✓ | — |
| `FADE_TRANSITION` | ✓ | ✓ | — |
| `CHASE` | ✓ | ✓ | — |
| `RAINBOW` | — | ✓ | — |
| `RAVE` | — | ✓ | — |
| `BATTERY_INDICATOR` | — | — | — |
| `CPU_TEMPERATURE` | — | — | — |

---

## 5 — `ACTION_DISPLAY`

Show an LED effect until the terminator condition fires.

```kotlin
// Police lights for 4 seconds during a chase
sendToBifrost(context, BifrostApi.ACTION_DISPLAY) {
    putExtra(BifrostApi.EXTRA_EFFECT, "STROBE")
    putExtra(BifrostApi.EXTRA_COLOR, Color.RED)
    putExtra(BifrostApi.EXTRA_COLOR_RIGHT, Color.BLUE)
    putExtra(BifrostApi.EXTRA_SPEED, 0.8f)
    putExtra(BifrostApi.EXTRA_INTENSITY, 255)
    putExtra(BifrostApi.EXTRA_DURATION_MS, 4_000L)
    putExtra(BifrostApi.EXTRA_PRIORITY, 70)
    putExtra(BifrostApi.EXTRA_REQUEST_ID, "chase-lights-001")
}
```

### Terminator — how long does the override last?

Exactly **one** of these may be set per DISPLAY command. Combining them is rejected.

| Extra | Effect |
|---|---|
| `EXTRA_DURATION_MS` (Long, 1–600 000) | Override ends after this many milliseconds and Bifrost resumes normal operation. |
| `EXTRA_UNTIL = "NEXT_COMMAND"` | Override ends when your app sends the next DISPLAY or CLEAR. **Default if nothing is specified.** |
| `EXTRA_UNTIL = "EXPLICIT_CLEAR"` | Override persists until you send `ACTION_CLEAR`. |
| `EXTRA_INDEFINITE = true` | Alias for `UNTIL_EXPLICIT_CLEAR`. |

### Priority

`EXTRA_PRIORITY` is an integer from 0 (lowest) to 100 (highest), default 50.

- A new DISPLAY command from your own app **always** replaces your previous one, regardless of priority.
- A command from a **different** app is ignored if the current override has a strictly higher priority.
- Same priority: last one wins.

Use a higher priority if your override must not be interrupted by other apps (e.g. a low-battery alarm). Use a lower priority if your effect is cosmetic and shouldn't stomp on a more urgent notification from another app.

### Intensity scale

If your value range is not 0–255, use `EXTRA_INTENSITY_SCALE` to normalise:

```kotlin
putExtra(BifrostApi.EXTRA_INTENSITY, 75)          // "75% brightness"
putExtra(BifrostApi.EXTRA_INTENSITY_SCALE, 100)   // tells Bifrost: max is 100
```

---

## 6 — `ACTION_CLEAR`

Ends your app's current override immediately. Only your own override can be cleared this way — you can't clear another app's override.

```kotlin
sendToBifrost(context, BifrostApi.ACTION_CLEAR)
```

---

## 7 — `ACTION_INSTALL_PROFILE` / `ACTION_UNINSTALL_PROFILE`

Install a named preset that will appear in Bifrost's preset list. Useful if your app ships with a theme: install it on first launch, uninstall it when uninstalled. Bifrost auto-removes your presets when Android broadcasts your package removal, but calling uninstall yourself is cleaner.

```kotlin
// Install "Emerald Trail" preset
sendToBifrost(context, BifrostApi.ACTION_INSTALL_PROFILE) {
    putExtra(BifrostApi.EXTRA_PROFILE_NAME, "Emerald Trail")
    putExtra(BifrostApi.EXTRA_EFFECT, "CHASE")
    putExtra(BifrostApi.EXTRA_COLOR, Color.GREEN)
    putExtra(BifrostApi.EXTRA_COLOR_RIGHT, Color.rgb(0, 200, 80))
    putExtra(BifrostApi.EXTRA_INTENSITY, 200)
    putExtra(BifrostApi.EXTRA_SPEED, 0.6f)
    putExtra(BifrostApi.EXTRA_PROFILE_REPLACE_IF_EXISTS, true)  // overwrite on update
}

// Remove it
sendToBifrost(context, BifrostApi.ACTION_UNINSTALL_PROFILE) {
    putExtra(BifrostApi.EXTRA_PROFILE_NAME, "Emerald Trail")
}
```

Installed presets are tagged with your package name internally. You can only uninstall presets that your own package installed.

The new preset appears in Bifrost's UI the next time the user opens the app (it reloads from SharedPreferences on resume).

---

## 8 — Minimal working example

```kotlin
class MyActivity : AppCompatActivity() {

    override fun onResume() {
        super.onResume()
        // Pulse blue while the user is in this activity
        sendBifrostDisplay(
            effect = "BREATH",
            color = Color.BLUE,
            intensity = 180,
            terminator = Pair(BifrostApi.EXTRA_UNTIL, BifrostApi.UNTIL_EXPLICIT_CLEAR)
        )
    }

    override fun onPause() {
        super.onPause()
        val intent = Intent(BifrostApi.ACTION_CLEAR).apply {
            setClassName(BifrostApi.RECEIVER_PACKAGE, BifrostApi.RECEIVER_CLASS)
            putExtra(BifrostApi.EXTRA_API_VERSION, BifrostApi.API_VERSION)
        }
        sendBroadcast(intent, BifrostApi.PERMISSION)
    }

    private fun sendBifrostDisplay(
        effect: String,
        color: Int,
        intensity: Int = 255,
        speed: Float = 0.5f,
        terminator: Pair<String, Any> = Pair(BifrostApi.EXTRA_DURATION_MS, 5_000L)
    ) {
        val intent = Intent(BifrostApi.ACTION_DISPLAY).apply {
            setClassName(BifrostApi.RECEIVER_PACKAGE, BifrostApi.RECEIVER_CLASS)
            putExtra(BifrostApi.EXTRA_API_VERSION, BifrostApi.API_VERSION)
            putExtra(BifrostApi.EXTRA_EFFECT, effect)
            putExtra(BifrostApi.EXTRA_COLOR, color)
            putExtra(BifrostApi.EXTRA_INTENSITY, intensity)
            putExtra(BifrostApi.EXTRA_SPEED, speed)
            when (val v = terminator.second) {
                is Long    -> putExtra(terminator.first, v)
                is Boolean -> putExtra(terminator.first, v)
                is String  -> putExtra(terminator.first, v)
            }
        }
        sendBroadcast(intent, BifrostApi.PERMISSION)
    }
}
```

---

## 9 — Common mistakes

**Command is silently dropped**
Bifrost only processes commands when its foreground service is already running. There is no way to start the service remotely — the user must have opened Bifrost and started the LEDs themselves. Design your app to be graceful when Bifrost is absent.

**`RESULT_REJECTED_DISABLED`**
The user hasn't toggled "Allow third-party LED control" in Bifrost settings. You may want to show a prompt directing them there, but respect their choice — don't nag.

**`RESULT_REJECTED_UNAUTHORIZED`**
Your UID couldn't be resolved. This should not happen in normal operation. Check that you aren't running as a shared UID, and that your `uses-permission` declaration is present in the manifest.

**`RESULT_REJECTED_RATE_LIMITED`**
You're sending more than ~4 commands/second. Avoid tight loops. Debounce game-state events before translating them to LED commands.

**Terminator not set**
If you send `ACTION_DISPLAY` without any terminator, it defaults to `UNTIL_NEXT_COMMAND`, meaning the next `ACTION_DISPLAY` from your app will replace it. This is usually fine, but if you stop sending commands while the override is still active (e.g. app goes to background), the override stays until Bifrost is restarted. Prefer `EXTRA_DURATION_MS` for short effects or `EXTRA_INDEFINITE` + `ACTION_CLEAR` for persistent ones.

---

## 10 — API version

Always include `EXTRA_API_VERSION = 1`. Future breaking changes will increment this number. Bifrost rejects requests with an unknown version rather than silently misbehaving, so version skew is surfaced immediately.
