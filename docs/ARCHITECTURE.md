# DEV Robot Controller - Architecture

## System Overview

DEV is a voice-driven Android robot controller that combines offline speech recognition, natural text-to-speech, computer vision face tracking, and TCP/Bluetooth communication to control a mobile robot platform.

```
 ┌──────────────┐        TCP (Wi-Fi) / BT SPP        ┌──────────────┐
 │  Android App │ ─────────────────────────────────▶ │  Robot MCU   │
 │ (Samsung Tab)│                                     │ (ESP32)     │
 │              │ ◀── Telemetry (JSON optional) ───── │  Motors     │
 │  Wakeword    │                                     │  Sensors    │
 │  STT (Vosk)  │                                     └──────────────┘
 │  Intent/LLM  │
 │  TTS + Face  │         (Dev tools only)
 │  UI + Follow │ ───────▶ Python Mock Robot (PC)
 └──────────────┘
```

## Architecture Components

### 1. Voice System (`voice/`)

#### VoskEngine
- **Purpose**: Offline speech-to-text using Vosk library
- **Features**:
  - Wake word detection ("hey dev" and variants)
  - Continuous listening at 16 kHz mono PCM
  - Barge-in prevention (ignores audio while TTS is speaking)
  - Immediate command detection (e.g., "stop" works without wake word)
- **Flow**: Audio → Vosk Model → Partial/Final Results → Callbacks

#### TtsEngine
- **Purpose**: Natural text-to-speech with humanization
- **Features**:
  - Selects best offline English voice available
  - Applies SSML for pauses and prosody
  - Random rate/pitch variation per utterance (92-102% rate, ±1 semitone)
  - Callbacks for speak start/end to control barge-in
- **Integration**: Uses Android TextToSpeech API

#### Humanizer
- **Purpose**: Make robot speech feel more natural
- **Features**:
  - Adds conversational openers ("okay", "sure", "got it")
  - Applies contractions ("I'm", "don't", "can't")
  - Generates SSML with natural breaks (200-300ms)
  - Thinking cues for LLM queries ("hmm...", "let me think...")

### 2. Brain System (`brain/`)

#### IntentRouter
- **Purpose**: Parse voice commands into actionable intents
- **Capabilities**:
  - Movement commands (forward, back, left, right, stop)
  - Follow mode toggle
  - Speed adjustment (faster/slower)
  - Face state control (sleep/wake)
- **Pattern Matching**: Tolerant synonym matching
- **Fallback**: Returns null if no match (triggers LLM query)

### 3. Vision System (`vision/`)

#### FaceTracker
- **Purpose**: Real-time face detection for follow-me mode
- **Technology**: CameraX + ML Kit Face Detection
- **Performance**: ~15-20 Hz detection rate
- **Output**: Face bounding box, center coordinates

### 4. Control System (`control/`)

#### FollowController
- **Purpose**: Convert face position to differential wheel speeds
- **Algorithm**: Proportional controller (P-only)
  - Error = (faceCenterX - imageCenterX) / halfWidth
  - Turn = Kp × error × baseSpeed (Kp = 1.8)
  - LeftSpeed = baseSpeed - turn
  - RightSpeed = baseSpeed + turn
- **Rate Limiting**: 60ms minimum between speed commands

### 5. Network System (`net/`)

#### TcpClient
- **Purpose**: TCP socket communication with robot
- **Features**:
  - 3-second connection timeout
  - Automatic reconnection
  - Line-based protocol (commands end with `\n`)
  - Thread-safe synchronized writes
  
#### RateLimiter
- **Purpose**: Prevent command flooding
- **Mechanism**: Tracks last send time per key
- **Use Cases**: Follow mode speed updates, manual controls

### 6. UI System (`ui/`)

#### FaceView
- **Purpose**: Visual robot face display
- **States**:
  - SLEEPING: Closed eyes (thin lines)
  - LISTENING: Open eyes, idle mouth
  - TALKING: Open eyes, animated mouth (green, pulsing)
  - MOVING: Open eyes
  - IDLE: Neutral state
- **Animation**: 20 FPS mouth movement during TALKING

### 7. Data System (`data/`)

#### SettingsStore
- **Purpose**: Persistent settings using DataStore
- **Stored Values**:
  - Host, Port (TCP connection)
  - Base Speed (0-255)
  - Follow Mode enabled
  - Wi-Fi Simulator mode
  - LLM endpoint URL
- **Default Values**:
  - Host: 192.168.1.100
  - Port: 9000
  - Base Speed: 180
  - LLM URL: http://localhost:11434

## Data Flow

### Voice Command Flow
```
User speaks → VoskEngine (wake detection) → 
VoskEngine (final result) → IntentRouter →
├─ Matched: Execute command + TTS confirmation
└─ No match: Query LLM → TTS response
```

### Follow Mode Flow
```
Camera → FaceTracker (ML Kit) → 
Face coordinates → FollowController (P-control) →
Differential speeds → RateLimiter → 
TcpClient → Robot
```

### Robot Communication Flow
```
MainActivity → TcpClient.sendLine(command) →
TCP Socket → ESP32 →
Motor Driver → Wheels
```

## Threading Model

- **Main Thread**: UI updates, view bindings
- **IO Thread**: Network operations (TCP), LLM queries
- **Camera Executor**: Face detection processing
- **Audio Thread**: Vosk speech recognition
- **TTS Callbacks**: Run on main thread

## Key Design Decisions

1. **Offline First**: Vosk for STT, Android TTS - works without internet
2. **Optional LLM**: Fallback to local Ollama for unmatched commands
3. **Rate Limiting**: Prevents overwhelming robot with commands
4. **Barge-in Prevention**: Mutes mic during TTS to avoid feedback loops
5. **State Management**: Face UI reflects robot state (listening/talking/moving)
6. **Tolerant Wake Word**: Multiple aliases prevent missed activations
7. **P-Controller**: Simple but effective for face tracking
8. **Line Protocol**: Text commands are human-readable for debugging

## Performance Characteristics

- **Face Detection**: 15-20 FPS
- **Speech Recognition**: Real-time streaming
- **Command Rate**: Max ~16 Hz (60ms limit)
- **TTS Latency**: ~200-500ms start delay
- **TCP Connect**: 3s timeout
- **LLM Query**: 5-10s (depends on model)

## Error Handling

- Permissions checked at runtime
- Network failures show toast, keep UI responsive
- Missing Vosk model auto-copies from assets
- TTS failures logged but don't crash app
- Invalid commands logged and ignored

## Security Considerations

- No authentication on TCP (local network only)
- Commands are plain text (no encryption)
- LLM queries could expose user data (use local models only)
- Camera/mic permissions required (user granted)
