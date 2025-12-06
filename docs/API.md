# DEV Robot Controller - API Reference

## Robot Command Protocol

All commands are text lines ending with `\n`. Commands are case-insensitive.

### Movement Commands

#### FWD / FORWARD
Move robot forward at base speed.
```
FWD\n
```

#### BACK / BACKWARD
Move robot backward at base speed.
```
BACK\n
```

#### LEFT
Turn robot left (left wheel backward, right wheel forward).
```
LEFT\n
```

#### RIGHT
Turn robot right (left wheel forward, right wheel backward).
```
RIGHT\n
```

#### STOP
Stop all motors immediately (brake).
```
STOP\n
```

### Speed Control

#### SPEED <L> <R>
Set individual wheel speeds (differential drive).
- `L`: Left wheel speed (0-255)
- `R`: Right wheel speed (0-255)
- Rate limited to max 60ms between commands

```
SPEED 180 180\n    # Both wheels forward at 180
SPEED 200 100\n    # Turn right while moving forward
SPEED 0 0\n        # Stop (prefer STOP command)
```

#### SET SPD <n>
Set the default base speed used by FWD/BACK/LEFT/RIGHT commands.
- `n`: Speed value (0-255)

```
SET SPD 200\n      # Set base speed to 200
```

### Utility Commands

#### PING
Test connection. Robot responds with PONG.
```
PING\n
→ PONG\n
```

## Voice Commands

Voice commands are processed by `IntentRouter` and mapped to robot commands.

### Wake Phrases
- "hey dev"
- "hey dave" (variant)
- "hey deb" (variant)
- "hey dove" (variant)

**Note**: "stop" command works immediately without wake phrase.

### Movement Intents
| Voice Command | Robot Action | Synonyms |
|---------------|--------------|----------|
| "forward" | `FWD` | "go forward", "move forward", "ahead" |
| "back" | `BACK` | "backward", "go back", "reverse" |
| "left" | `LEFT` | "turn left", "go left" |
| "right" | `RIGHT` | "turn right", "go right" |
| "stop" | `STOP` | "halt", "freeze", "wait" |

### Follow Mode
| Voice Command | Action |
|---------------|--------|
| "follow me" | Enable follow mode | 
| "stop following" | Disable follow mode |

**Synonyms**: "follow", "track me", "chase me", "don't follow", "unfollow"

### Speed Control
| Voice Command | Action | Change |
|---------------|--------|--------|
| "faster" | Increase base speed | +20 |
| "slower" | Decrease base speed | -20 |

**Synonyms**: "speed up", "go faster", "hurry", "slow down", "go slower"

### Robot State
| Voice Command | Face State |
|---------------|------------|
| "sleep" | SLEEPING (eyes closed) |
| "wake up" | IDLE (eyes open) |

**Synonyms**: "go to sleep", "rest", "wake", "awaken"

## LLM Integration (Optional)

When a voice command doesn't match any robot intent, it's sent to a local LLM endpoint.

### Endpoint
```
POST {llmUrl}/api/generate
Content-Type: application/json
```

### Request Body
```json
{
  "model": "phi3:mini",
  "prompt": "<user query>",
  "system": "You are DEV, a brief helpful robot. Today is <yyyy-mm-dd>. Answer in one short sentence.",
  "stream": false
}
```

### Response
```json
{
  "response": "short answer string",
  "done": true
}
```

### Configuration
Default LLM URL: `http://localhost:11434` (Ollama default)

Can be changed in app settings. If empty, LLM queries return "I'm offline right now."

## Android App API

### VoskEngine

```kotlin
class VoskEngine(context: Context) {
    fun initialize(onReady: () -> Unit)
    fun startListening()
    fun stopListening()
    fun setSpeaking(speaking: Boolean)
    fun release()
    
    var onWake: (() -> Unit)?
    var onPartial: ((String) -> Unit)?
    var onFinal: ((String) -> Unit)?
}
```

### TtsEngine

```kotlin
class TtsEngine(
    context: Context,
    onStartSpeaking: () -> Unit = {},
    onDoneSpeaking: () -> Unit = {}
) {
    fun init(onReady: () -> Unit = {})
    fun speak(text: String)
    fun speakSsml(ssml: String)
    fun shutdown()
}
```

### TcpClient

```kotlin
class TcpClient {
    suspend fun connect(host: String, port: Int, timeoutMs: Int = 3000)
    fun isConnected(): Boolean
    suspend fun sendLine(line: String)
    fun close()
    fun release()
    
    var onConnectionChange: ((Boolean, String) -> Unit)?
}
```

### FaceTracker

```kotlin
class FaceTracker(context: Context, previewView: PreviewView) {
    fun startCamera()
    fun release()
    
    var onFaceDetected: ((Face?, Int, Int) -> Unit)?
}
```

### FollowController

```kotlin
class FollowController {
    fun computeSpeed(
        faceX: Float, 
        centerX: Float, 
        imgWidth: Int, 
        baseSpeed: Int
    ): Pair<Int, Int>
}
```

### IntentRouter

```kotlin
class IntentRouter {
    data class Intent(
        val command: String?,
        val followMode: Boolean?,
        val faceState: FaceView.State?,
        val speedChange: Int,
        val confirmation: String
    )
    
    fun route(text: String): Intent?
}
```

### FaceView

```kotlin
class FaceView {
    enum class State {
        SLEEPING, LISTENING, TALKING, MOVING, IDLE
    }
    
    fun setState(state: State)
}
```

### RateLimiter

```kotlin
class RateLimiter(minIntervalMs: Long) {
    fun shouldSend(key: String = "default"): Boolean
    fun reset(key: String = "default")
    fun resetAll()
}
```

## ESP32 Firmware API

### Pin Configuration
```cpp
// Left Motor (TB6612FNG)
const int MOTOR_L_PWM = 25;
const int MOTOR_L_IN1 = 26;
const int MOTOR_L_IN2 = 27;

// Right Motor
const int MOTOR_R_PWM = 32;
const int MOTOR_R_IN1 = 33;
const int MOTOR_R_IN2 = 14;
```

### Motor Control Functions
```cpp
void motorForward(int speed);      // 0-255
void motorBackward(int speed);     // 0-255
void motorLeft(int speed);         // 0-255
void motorRight(int speed);        // 0-255
void motorSpeedLR(int left, int right);  // 0-255 each
void motorStop();                  // Brake
```

## Python Mock Server API

### Running
```bash
# With matplotlib visualization
python mock_robot.py

# Console only
python mock_robot_noplot.py
```

### Supported Commands
All robot commands listed above are supported and logged to console.

### Output Format
```
[RX] <command>       # Received command
[TX] <response>      # Sent response (PONG only)
    → <action>       # Action description
```

## Rate Limits

- **Follow Mode Speed Commands**: Max 1 per 60ms (~16 Hz)
- **Manual Commands**: No limit (user interaction is naturally rate-limited)
- **Voice Commands**: Limited by wake detection + TTS playback time
- **TCP Connection Timeout**: 3 seconds

## Error Codes

The app uses Android standard error handling with Log messages tagged "DEVAKI".

Common error scenarios:
- **Permission Denied**: Mic/Camera permissions not granted
- **Connection Failed**: Robot unreachable or wrong IP/port
- **TTS Init Failed**: Text-to-speech engine unavailable
- **Vosk Model Missing**: Model not found in assets (auto-copied on first run)
- **LLM Query Failed**: Endpoint unreachable or timeout
