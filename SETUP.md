# Devaki - Setup Guide

## Complete Setup Instructions

### Prerequisites
- Android Studio (Arctic Fox or later)
- JDK 17+
- Python 3.7+ (for mock server)
- ESP32 board (for actual robot)

## Step 1: Vosk Model Setup

The Vosk model is NOT included in the repository (too large for git). Download it manually:

### Option A: Manual Download
1. Visit https://alphacephei.com/vosk/models
2. Download `vosk-model-small-en-us-0.15.zip` (~40 MB)
3. Extract the zip file
4. Copy the folder to: `app/src/main/assets/vosk-model-small-en-us-0.15/`

### Option B: Command Line (Linux/Mac/WSL)
```bash
cd app/src/main/assets/
curl -O https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
unzip vosk-model-small-en-us-0.15.zip
rm vosk-model-small-en-us-0.15.zip
```

### Verify Structure
Your assets folder should look like:
```
app/src/main/assets/
└── vosk-model-small-en-us-0.15/
    ├── README
    ├── am/
    ├── conf/
    ├── graph/
    └── ivector/
```

## Step 2: Build Android App

1. **Open Project**
   - Launch Android Studio
   - File → Open → Select `devaki` folder
   - Wait for Gradle sync to complete

2. **Connect Device**
   - Enable Developer Options on Android device
   - Enable USB Debugging
   - Connect via USB
   - Accept debugging authorization

3. **Build & Run**
   - Click Run ▶ button
   - Select your device
   - Wait for installation

4. **Grant Permissions**
   - On first run, grant Camera and Microphone permissions

## Step 3: Test with Mock Server

### Run Mock Server
```bash
cd tools/
python mock_robot_noplot.py
```

You should see:
```
[*] Mock robot server listening on 0.0.0.0:9000
[*] Waiting for connections...
```

### Configure App

**If using Android Emulator:**
- Check ✓ "Wi-Fi Simulator"
- Host: `10.0.2.2`
- Port: `9000`

**If using Real Device:**
- Uncheck "Wi-Fi Simulator"
- Host: `<your-computer-ip>` (find with `ipconfig` or `ifconfig`)
- Port: `9000`
- Ensure device and PC on same Wi-Fi network

### Test Connection
1. Tap "Connect" button
2. Status should change to "Connected to..."
3. Tap "FWD" button
4. Mock server console should show: `[RX] FWD`

## Step 4: Test Voice Control

1. **Wake Robot**
   - Say "hey dev"
   - Face should show LISTENING (eyes open)
   - Robot should say "Yes?"

2. **Give Command**
   - Say "forward"
   - Robot should move
   - TTS should confirm "moving forward"

3. **Test Immediate Stop**
   - Say "stop" (without wake word)
   - Robot should stop immediately

## Step 5: Test Face Tracking

1. Enable "Follow Mode" toggle
2. Show your face to the front camera
3. Move left → robot should turn left
4. Move right → robot should turn right
5. Move closer/farther → speed should adjust

## Step 6: Upload ESP32 Firmware (Optional)

### Hardware Setup
```
ESP32          TB6612FNG/L298N       Motors
-----          ---------------       ------
GPIO 25  -->   PWM (Left)      -->   Left Motor
GPIO 26  -->   IN1 (Left)
GPIO 27  -->   IN2 (Left)
GPIO 32  -->   PWM (Right)     -->   Right Motor
GPIO 33  -->   IN1 (Right)
GPIO 14  -->   IN2 (Right)
GND      -->   GND
```

### Configure Firmware
1. Open `firmware/esp32_devaki/esp32_devaki.ino` in Arduino IDE
2. Update Wi-Fi credentials:
   ```cpp
   const char* ssid = "YOUR_WIFI_SSID";
   const char* password = "YOUR_WIFI_PASSWORD";
   ```
3. Verify pin assignments match your hardware
4. Select Board: ESP32 Dev Module
5. Upload sketch

### Find ESP32 IP
After upload, open Serial Monitor (115200 baud):
```
WiFi connected!
IP address: 192.168.1.xxx
TCP server started on port 9000
```

### Connect App to Robot
- Uncheck "Wi-Fi Simulator"
- Host: `192.168.1.xxx` (IP from Serial Monitor)
- Port: `9000`
- Tap "Connect"

## Step 7: Optional LLM Setup

### Install Ollama
```bash
# Linux/Mac
curl https://ollama.ai/install.sh | sh

# Windows
# Download from https://ollama.ai/download
```

### Pull Model
```bash
ollama pull phi3:mini
```

### Configure App
The app defaults to `http://localhost:11434` which works if:
- Running on emulator (Ollama on host PC)
- Running on PC (Android-x86)

For real device on same network:
- Use PC's IP: `http://192.168.1.xxx:11434`

### Test LLM
1. Say "hey dev"
2. Say "what's the date?"
3. Robot should query LLM and speak the answer

## Troubleshooting

### "Vosk model not found"
- Download and extract model as described in Step 1
- Check folder name exactly matches: `vosk-model-small-en-us-0.15`
- Rebuild project

### "Permission denied"
- Go to Settings → Apps → Devaki → Permissions
- Enable Camera and Microphone
- Restart app

### "Connection failed"
- Verify mock server is running
- Check firewall allows port 9000
- Ping robot IP from device
- Ensure same Wi-Fi network

### "No speech recognition"
- Check microphone permission
- Speak clearly, 1-2 feet from device
- Reduce background noise
- Check Vosk model is loaded (see Logcat)

### "TTS not working"
- Go to Settings → Accessibility → Text-to-Speech
- Ensure Google TTS or Samsung TTS is installed
- Test TTS with sample text

## Next Steps

- Tune follow mode Kp gain for your robot
- Adjust rate limiter for smoother motion
- Add more voice commands in IntentRouter
- Integrate sensors (ultrasonic, IMU)
- Add telemetry from robot to app

## Getting Help

- Check docs/ARCHITECTURE.md for system design
- Check docs/API.md for command reference
- Review Logcat for detailed error messages (tag: DEVAKI)
- Open an issue on GitHub

Happy building! 🤖
