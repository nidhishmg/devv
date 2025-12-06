# DEV 🤖

**Voice-Driven Android Robot Controller with Natural Interaction**

DEV is a complete Android application that turns a Samsung tablet into an intelligent robot controller. It combines offline voice recognition, natural text-to-speech, computer vision face tracking, and wireless communication to create a responsive, voice-controlled mobile robot platform.

## ✨ Features

### 🎤 Voice Control
- **Offline Speech Recognition** using Vosk (no internet required)
- **Wake Word Detection**: "hey dev" and tolerant variants
- **Natural TTS** with SSML, pauses, and humanization
- **Intent Routing** for robot commands
- **LLM Fallback** for unmatched queries (optional Ollama integration)

### 📷 Computer Vision
- **Face Tracking** with ML Kit Face Detection
- **Follow-Me Mode**: Autonomous person tracking
- **Differential Drive Control** using proportional controller
- **Real-Time Processing** at 15-20 FPS

### 🔌 Connectivity
- **TCP over Wi-Fi** for high-bandwidth control
- **Bluetooth SPP** support (planned)
- **Mock Server** for testing without hardware

### 😊 Robot Personality
- **Animated Face UI** with expressive states
- **Conversational Responses** with natural phrasing
- **Thinking Cues** before LLM responses
- **Barge-In Prevention** (mic mutes during speech)

## 📋 Requirements

### Android App
- **Android**: API 24+ (Android 7.0+)
- **Hardware**: Samsung tablet (or any Android device with camera)
- **Permissions**: Camera, Microphone, Internet, Wi-Fi

### Robot Hardware
- **MCU**: ESP32 (Wi-Fi capable)
- **Motor Driver**: TB6612FNG or L298N
- **Motors**: 2x DC motors with wheels
- **Power**: 7.4V LiPo or similar

### Development
- **Android Studio**: Arctic Fox or later
- **Gradle**: 8.0+
- **Kotlin**: 1.9.20+
- **JDK**: 17+

## 🚀 Quick Start

**One-Command Setup:**
```bash
git clone https://github.com/nidhishmg/devv.git
cd devv
# Download Vosk model (130MB - required for voice recognition)
curl -L https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip -o vosk.zip
unzip vosk.zip -d app/src/main/assets/
rm vosk.zip
```

**Then open in Android Studio:**
1. File → Open → Select the `devv` folder
2. Wait for Gradle sync to complete
3. Connect your Android device (enable USB debugging)
4. Click Run ▶️ button
5. Grant Camera & Microphone permissions when prompted
6. Say **"Hey Dev"** to wake it up!

**Test without hardware:**
```bash
python tools/mock_robot_noplot.py
```

In the app:
- Check "Wi-Fi Simulator"
- Set Host: `10.0.2.2` (emulator) or PC's IP (device)
- Set Port: `9000`
- Tap "Connect"

### 5. Upload ESP32 Firmware
- Open `firmware/esp32_devaki/esp32_devaki.ino` in Arduino IDE
- Update Wi-Fi credentials
- Adjust motor pins for your hardware
- Upload to ESP32

## 📖 Documentation

- **[Architecture Guide](docs/ARCHITECTURE.md)** - System design and component overview
- **[API Reference](docs/API.md)** - Command protocol and code APIs

## 🎮 Usage

### Voice Commands

**Wake the robot:**
```
"Hey dev"
→ Robot: "Yes?"
```

**Movement:**
```
"Forward"        → Robot moves forward
"Turn left"      → Robot turns left
"Stop"           → Robot stops (works without wake word!)
```

**Follow mode:**
```
"Follow me"      → Enables face tracking
"Stop following" → Disables follow mode
```

**Speed control:**
```
"Faster"         → Increases base speed
"Slower"         → Decreases base speed
```

**Robot state:**
```
"Sleep"          → Face closes eyes
"Wake up"        → Face opens eyes
```

**Ask anything:**
```
"What time is it?"       → Queries local LLM
"What's today's news?"   → Uses Gemini (if enabled) for real-time info
"Tell me about the weather" → Gemini searches web and responds
```

### LLM Configuration

**Option 1: Local Ollama (offline, no news/weather)**
- Install Ollama on your PC
- Pull a model: `ollama pull phi3:mini`
- Set LLM URL in app to your PC's IP: `http://192.168.1.5:11434`

**Option 2: Gemini API (online, with real-time web data)** ⭐ Recommended
1. Get free API key: https://aistudio.google.com/app/apikey
2. In app, long-press the robot face to open settings
3. Enable "Use Gemini" and paste your API key
4. Now queries like "today's news" will search the web automatically!

> **Tip:** Gemini is free (1500 requests/day), fast (~2-3s), and mobile-friendly. It automatically searches the web when needed.

### Manual Control

Use the on-screen buttons:
- **FWD** / **BACK** / **LEFT** / **RIGHT** / **STOP**
- **Follow Mode** toggle
- **Base Speed** slider (0-255)

### Settings

- **Host**: Robot IP address (default: 192.168.1.100)
- **Port**: TCP port (default: 9000)
- **Wi-Fi Simulator**: Use for testing with mock server
- **Base Speed**: Default motor speed (default: 180)

## 🏗️ Project Structure

```
devaki/
├── app/                          # Android application
│   ├── src/main/
│   │   ├── java/com/devaki/app/
│   │   │   ├── brain/           # Intent routing
│   │   │   ├── control/         # Follow controller
│   │   │   ├── data/            # Settings persistence
│   │   │   ├── net/             # TCP client, rate limiter
│   │   │   ├── ui/              # Face view, bindings
│   │   │   ├── vision/          # Face tracking
│   │   │   ├── voice/           # Vosk, TTS, humanizer
│   │   │   └── MainActivity.kt
│   │   ├── res/                 # Layouts, strings, colors
│   │   └── assets/              # Vosk model (download separately)
│   └── build.gradle
├── firmware/
│   └── esp32_devaki/
│       └── esp32_devaki.ino     # ESP32 robot controller
├── tools/
│   ├── mock_robot.py            # Mock server with plot
│   └── mock_robot_noplot.py     # Mock server console only
├── docs/
│   ├── ARCHITECTURE.md
│   └── API.md
├── build.gradle
├── settings.gradle
└── README.md
```

## 🔧 Configuration

### Tuning Follow Mode

Edit `FollowController.kt`:
```kotlin
private const val KP = 1.8f  // Proportional gain (1.0 - 3.0)
```
- Higher Kp = More aggressive turning
- Lower Kp = Smoother but slower response

### Adjusting Rate Limiting

Edit `MainActivity.kt`:
```kotlin
private val speedRateLimiter = RateLimiter(60)  // milliseconds
```
- Lower value = More frequent updates (max load on robot)
- Higher value = Less frequent updates (smoother motion)

### Voice Wake Variants

Edit `VoskEngine.kt`:
```kotlin
private val WAKE_ALIASES = listOf("hey dev", "hey dave", "hey deb", "hey dove")
```

## 🧪 Testing

### Test Voice Recognition
1. Grant microphone permission
2. Say "hey dev"
3. Face should show LISTENING state
4. Robot should say "Yes?"
5. Say "forward"
6. Robot should move and confirm

### Test Face Tracking
1. Grant camera permission
2. Enable "Follow Mode"
3. Show your face to camera
4. Move left/right
5. Robot should track your movement

### Test TCP Connection
1. Run mock server: `python tools/mock_robot_noplot.py`
2. In app, set Host/Port
3. Tap "Connect"
4. Status should show "Connected"
5. Press FWD button
6. Mock server console shows "RX: FWD"

## 🐛 Troubleshooting

### Vosk Not Working
- **Issue**: "Vosk model not found"
- **Fix**: Download and extract model to `app/src/main/assets/vosk-model-small-en-us-0.15/`

### TTS Silent
- **Issue**: No speech output
- **Fix**: Check Android TTS engine is installed (Settings → Accessibility → Text-to-Speech)

### Camera Not Starting
- **Issue**: Black screen in preview
- **Fix**: Grant camera permission, restart app

### Connection Failed
- **Issue**: "Connection failed" toast
- **Fix**: 
  - Verify robot/mock server is running
  - Check IP address (use `ipconfig` or `ifconfig`)
  - Ensure devices on same network
  - For emulator, use `10.0.2.2` as host

### Face Tracking Jumpy
- **Issue**: Robot jerks while following
- **Fix**: 
  - Increase rate limiter interval (default 60ms → 100ms)
  - Decrease Kp gain (default 1.8 → 1.2)
  - Improve lighting conditions

## 🤝 Contributing

Contributions welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License.

## 🙏 Acknowledgments

- **[Vosk](https://alphacephei.com/vosk/)** - Offline speech recognition
- **[ML Kit](https://developers.google.com/ml-kit)** - Face detection
- **[Ollama](https://ollama.ai/)** - Local LLM inference
- **[CameraX](https://developer.android.com/camerax)** - Camera API

## 📞 Support

For issues and questions:
- Open an issue on GitHub
- Check the [documentation](docs/)
- Review the [API reference](docs/API.md)

---

**Made with ❤️ for robotics enthusiasts**
