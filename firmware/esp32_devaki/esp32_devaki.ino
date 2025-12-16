/*
 * FILE: firmware/esp32/DEV_Robot/DEV_Robot.ino
 * ESP32 firmware for DEV 4WD robot with L298N motor driver
 * Supports Bluetooth SPP and WiFi TCP commands with safety features
 * 
 * WIRELESS MODE: Set ONE of these to 1, others to 0
 * If partition scheme allows, set both to 1
 */
#define USE_WIFI 1        // WiFi TCP control
#define USE_BLUETOOTH 0   // Bluetooth SPP control

#if USE_BLUETOOTH
  #include "BluetoothSerial.h"
  #if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
    #error Bluetooth is not enabled! Please run `make menuconfig` to enable it
  #endif
  BluetoothSerial SerialBT;
#endif

#if USE_WIFI
  #include <WiFi.h>
#endif

// ============= WIFI CONFIGURATION =============
#if USE_WIFI
  const char* WIFI_SSID = "Nonchalant";
  const char* WIFI_PASSWORD = "12345678";
  const int WIFI_PORT = 8888;
  
  WiFiServer wifiServer(WIFI_PORT);
  WiFiClient wifiClient;
#endif

// ============= PIN DEFINITIONS =============
// L298N Motor Driver Pins
// Left Motor (Motor A)
const int ENA = 25;
const int IN1 = 26;
const int IN2 = 27;

// Right Motor (Motor B)
const int IN3 = 12;
const int IN4 = 13;
const int ENB = 14;

// Sensors (Optional - stubs for now)
// GY-53 VL53L0X (I2C): SDA=21, SCL=22 (default I2C)

// ============= ULTRASONIC SENSORS (3 Front + 1 Back) =============
// Front Left
const int TRIG_FRONT_LEFT = 4;
const int ECHO_FRONT_LEFT = 5;

// Front Center
const int TRIG_FRONT = 18;
const int ECHO_FRONT = 19;

// Front Right
const int TRIG_FRONT_RIGHT = 32;
const int ECHO_FRONT_RIGHT = 33;

// Back
const int TRIG_BACK = 16;
const int ECHO_BACK = 17;

// Distance readings (cm)
int distFrontLeft = 999;
int distFrontCenter = 999;
int distFrontRight = 999;
int distBack = 999;

// ============= CONFIGURATION =============
const int PWM_FREQ = 1000;      // 1 kHz PWM frequency
const int PWM_RESOLUTION = 8;   // 8-bit resolution (0-255)
const int PWM_CHANNEL_L = 0;
const int PWM_CHANNEL_R = 1;

const int MAX_PWM_DELTA = 10;   // Max PWM change per loop for smooth ramping
const int LOOP_DELAY_MS = 20;   // Main loop delay

const int SAFE_DISTANCE_CM = 15; // Stop if obstacle closer than this

// ============= GLOBAL STATE =============
int currentLeftSpeed = 0;
int currentRightSpeed = 0;
int targetLeftSpeed = 0;
int targetRightSpeed = 0;

String inputBuffer = "";
#if USE_WIFI
  String wifiInputBuffer = "";
#endif

// ============= SETUP =============
void setup() {
  Serial.begin(115200);
  
  #if USE_BLUETOOTH
    // Initialize Bluetooth with device name
    SerialBT.begin("DEV_Robot");
    Serial.println("DEV_Robot Bluetooth Started");
    Serial.println("Waiting for connection...");
  #endif
  
  #if USE_WIFI
    // Initialize WiFi
    Serial.print("Connecting to WiFi: ");
    Serial.println(WIFI_SSID);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    
    int wifiAttempts = 0;
    while (WiFi.status() != WL_CONNECTED && wifiAttempts < 20) {
      delay(500);
      Serial.print(".");
      wifiAttempts++;
    }
    
    if (WiFi.status() == WL_CONNECTED) {
      Serial.println("\nWiFi Connected!");
      Serial.print("IP Address: ");
      Serial.println(WiFi.localIP());
      Serial.print("Control Port: ");
      Serial.println(WIFI_PORT);
      
      wifiServer.begin();
      Serial.println("WiFi server started");
    } else {
      Serial.println("\nWiFi connection failed");
    }
  #endif
  
  // Setup motor pins
  pinMode(IN1, OUTPUT);
  pinMode(IN2, OUTPUT);
  pinMode(IN3, OUTPUT);
  pinMode(IN4, OUTPUT);
  
  // Setup PWM channels (compatible with ESP32 Arduino Core 3.0+)
  #if ESP_ARDUINO_VERSION_MAJOR >= 3
    ledcAttach(ENA, PWM_FREQ, PWM_RESOLUTION);
    ledcAttach(ENB, PWM_FREQ, PWM_RESOLUTION);
  #else
    ledcSetup(PWM_CHANNEL_L, PWM_FREQ, PWM_RESOLUTION);
    ledcSetup(PWM_CHANNEL_R, PWM_FREQ, PWM_RESOLUTION);
    ledcAttachPin(ENA, PWM_CHANNEL_L);
    ledcAttachPin(ENB, PWM_CHANNEL_R);
  #endif
  
  // Setup ultrasonic sensor pins (3 front + 1 back)
  pinMode(TRIG_FRONT_LEFT, OUTPUT);
  pinMode(ECHO_FRONT_LEFT, INPUT);
  pinMode(TRIG_FRONT, OUTPUT);
  pinMode(ECHO_FRONT, INPUT);
  pinMode(TRIG_FRONT_RIGHT, OUTPUT);
  pinMode(ECHO_FRONT_RIGHT, INPUT);
  pinMode(TRIG_BACK, OUTPUT);
  pinMode(ECHO_BACK, INPUT);
  
  // Initialize motors to stop
  stopMotors();
  
  Serial.println("Setup complete!");
}

// ============= MAIN LOOP =============
void loop() {
  // Read USB Serial commands (for testing)
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n' || c == '\r') {
      if (inputBuffer.length() > 0) {
        processCommand(inputBuffer);
        inputBuffer = "";
      }
    } else {
      inputBuffer += c;
    }
  }
  
  #if USE_BLUETOOTH
    // Read Bluetooth commands
    while (SerialBT.available()) {
      char c = SerialBT.read();
      if (c == '\n') {
        processCommand(inputBuffer);
        inputBuffer = "";
      } else {
        inputBuffer += c;
      }
    }
  #endif
  
  #if USE_WIFI
    // Handle WiFi client connections
    if (WiFi.status() == WL_CONNECTED) {
      // Check for new client
      if (!wifiClient.connected()) {
        wifiClient = wifiServer.available();
        if (wifiClient) {
          Serial.println("WiFi client connected!");
          wifiClient.println("DEV_Robot Ready");
          wifiInputBuffer = "";
        }
      }
      
      // Read WiFi commands from connected client
      if (wifiClient.connected()) {
        while (wifiClient.available()) {
          char c = wifiClient.read();
          if (c == '\n' || c == '\r') {
            if (wifiInputBuffer.length() > 0) {
              processCommand(wifiInputBuffer);
              wifiClient.println("OK");
              wifiInputBuffer = "";
            }
          } else {
            wifiInputBuffer += c;
          }
        }
      }
    }
  #endif
  
  // Safety check - stop if obstacle detected
  checkSafety();
  
  // Smooth PWM ramping
  smoothRamp();
  
  // Apply motor speeds
  applyMotorSpeeds();
  
  delay(LOOP_DELAY_MS);
}

// ============= COMMAND PROCESSING =============
void processCommand(String cmd) {
  cmd.trim();
  Serial.println("CMD: " + cmd);
  
  if (cmd == "FWD") {
    targetLeftSpeed = 200;
    targetRightSpeed = 200;
  }
  else if (cmd == "BACK") {
    targetLeftSpeed = -200;
    targetRightSpeed = -200;
  }
  else if (cmd == "LEFT") {
    targetLeftSpeed = -150;
    targetRightSpeed = 150;
  }
  else if (cmd == "RIGHT") {
    targetLeftSpeed = 150;
    targetRightSpeed = -150;
  }
  else if (cmd == "STOP") {
    targetLeftSpeed = 0;
    targetRightSpeed = 0;
  }
  else if (cmd.startsWith("SPEED ")) {
    // Parse "SPEED L R"
    int firstSpace = cmd.indexOf(' ');
    int secondSpace = cmd.indexOf(' ', firstSpace + 1);
    
    if (secondSpace > 0) {
      String leftStr = cmd.substring(firstSpace + 1, secondSpace);
      String rightStr = cmd.substring(secondSpace + 1);
      
      targetLeftSpeed = constrain(leftStr.toInt(), -255, 255);
      targetRightSpeed = constrain(rightStr.toInt(), -255, 255);
    }
  }
  else if (cmd == "DIST") {
    // Send all distance readings as JSON
    sendDistanceData();
  }
  else {
    Serial.println("Unknown command");
  }
}

// ============= MOTOR CONTROL =============
void smoothRamp() {
  // Gradually change current speeds toward target speeds
  if (currentLeftSpeed < targetLeftSpeed) {
    currentLeftSpeed = min(currentLeftSpeed + MAX_PWM_DELTA, targetLeftSpeed);
  } else if (currentLeftSpeed > targetLeftSpeed) {
    currentLeftSpeed = max(currentLeftSpeed - MAX_PWM_DELTA, targetLeftSpeed);
  }
  
  if (currentRightSpeed < targetRightSpeed) {
    currentRightSpeed = min(currentRightSpeed + MAX_PWM_DELTA, targetRightSpeed);
  } else if (currentRightSpeed > targetRightSpeed) {
    currentRightSpeed = max(currentRightSpeed - MAX_PWM_DELTA, targetRightSpeed);
  }
}

void applyMotorSpeeds() {
  // Left motor
  if (currentLeftSpeed > 0) {
    digitalWrite(IN1, HIGH);
    digitalWrite(IN2, LOW);
    #if ESP_ARDUINO_VERSION_MAJOR >= 3
      ledcWrite(ENA, abs(currentLeftSpeed));
    #else
      ledcWrite(PWM_CHANNEL_L, abs(currentLeftSpeed));
    #endif
  } else if (currentLeftSpeed < 0) {
    digitalWrite(IN1, LOW);
    digitalWrite(IN2, HIGH);
    #if ESP_ARDUINO_VERSION_MAJOR >= 3
      ledcWrite(ENA, abs(currentLeftSpeed));
    #else
      ledcWrite(PWM_CHANNEL_L, abs(currentLeftSpeed));
    #endif
  } else {
    digitalWrite(IN1, LOW);
    digitalWrite(IN2, LOW);
    #if ESP_ARDUINO_VERSION_MAJOR >= 3
      ledcWrite(ENA, 0);
    #else
      ledcWrite(PWM_CHANNEL_L, 0);
    #endif
  }
  
  // Right motor
  if (currentRightSpeed > 0) {
    digitalWrite(IN3, HIGH);
    digitalWrite(IN4, LOW);
    #if ESP_ARDUINO_VERSION_MAJOR >= 3
      ledcWrite(ENB, abs(currentRightSpeed));
    #else
      ledcWrite(PWM_CHANNEL_R, abs(currentRightSpeed));
    #endif
  } else if (currentRightSpeed < 0) {
    digitalWrite(IN3, LOW);
    digitalWrite(IN4, HIGH);
    #if ESP_ARDUINO_VERSION_MAJOR >= 3
      ledcWrite(ENB, abs(currentRightSpeed));
    #else
      ledcWrite(PWM_CHANNEL_R, abs(currentRightSpeed));
    #endif
  } else {
    digitalWrite(IN3, LOW);
    digitalWrite(IN4, LOW);
    #if ESP_ARDUINO_VERSION_MAJOR >= 3
      ledcWrite(ENB, 0);
    #else
      ledcWrite(PWM_CHANNEL_R, 0);
    #endif
  }
}

void stopMotors() {
  targetLeftSpeed = 0;
  targetRightSpeed = 0;
  currentLeftSpeed = 0;
  currentRightSpeed = 0;
  
  digitalWrite(IN1, LOW);
  digitalWrite(IN2, LOW);
  digitalWrite(IN3, LOW);
  digitalWrite(IN4, LOW);
  #if ESP_ARDUINO_VERSION_MAJOR >= 3
    ledcWrite(ENA, 0);
    ledcWrite(ENB, 0);
  #else
    ledcWrite(PWM_CHANNEL_L, 0);
    ledcWrite(PWM_CHANNEL_R, 0);
  #endif
}

// ============= SAFETY STUBS =============
// NOTE: Implement actual sensor reading for production use

int readUltrasonicCm(int trigPin, int echoPin) {
  // Trigger pulse
  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);
  
  // Read echo
  long duration = pulseIn(echoPin, HIGH, 30000); // Timeout 30ms
  if (duration == 0) {
    return 999; // No echo = far away
  }
  
  int distance = duration * 0.034 / 2;
  return distance;
}

void readAllSensors() {
  // Read all 4 ultrasonic sensors
  distFrontLeft = readUltrasonicCm(TRIG_FRONT_LEFT, ECHO_FRONT_LEFT);
  distFrontCenter = readUltrasonicCm(TRIG_FRONT, ECHO_FRONT);
  distFrontRight = readUltrasonicCm(TRIG_FRONT_RIGHT, ECHO_FRONT_RIGHT);
  distBack = readUltrasonicCm(TRIG_BACK, ECHO_BACK);
}

void sendDistanceData() {
  // Read all sensors and send as JSON
  readAllSensors();
  
  String json = "{";
  json += "\"front_left\":" + String(distFrontLeft) + ",";
  json += "\"front_center\":" + String(distFrontCenter) + ",";
  json += "\"front_right\":" + String(distFrontRight) + ",";
  json += "\"back\":" + String(distBack);
  json += "}";
  
  Serial.println(json);
  
  #if USE_BLUETOOTH
    SerialBT.println(json);
  #endif
  
  #if USE_WIFI
    if (wifiClient.connected()) {
      wifiClient.println(json);
    }
  #endif
}

void checkSafety() {
  // Read all front sensors if moving forward
  if (targetLeftSpeed > 0 || targetRightSpeed > 0) {
    distFrontLeft = readUltrasonicCm(TRIG_FRONT_LEFT, ECHO_FRONT_LEFT);
    distFrontCenter = readUltrasonicCm(TRIG_FRONT, ECHO_FRONT);
    distFrontRight = readUltrasonicCm(TRIG_FRONT_RIGHT, ECHO_FRONT_RIGHT);
    
    // Check all 3 front sensors
    if (distFrontLeft < SAFE_DISTANCE_CM) {
      Serial.println("SAFETY: Front-left obstacle at " + String(distFrontLeft) + "cm!");
      stopMotors();
      return;
    }
    if (distFrontCenter < SAFE_DISTANCE_CM) {
      Serial.println("SAFETY: Front-center obstacle at " + String(distFrontCenter) + "cm!");
      stopMotors();
      return;
    }
    if (distFrontRight < SAFE_DISTANCE_CM) {
      Serial.println("SAFETY: Front-right obstacle at " + String(distFrontRight) + "cm!");
      stopMotors();
      return;
    }
  }
  
  // Read back sensor if moving backward
  if (targetLeftSpeed < 0 || targetRightSpeed < 0) {
    distBack = readUltrasonicCm(TRIG_BACK, ECHO_BACK);
    if (distBack < SAFE_DISTANCE_CM) {
      Serial.println("SAFETY: Back obstacle at " + String(distBack) + "cm!");
      stopMotors();
      return;
    }
  }
}

/*
 * WIRING NOTES:
 * 
 * L298N Module:
 * - ENA -> ESP32 GPIO 25 (PWM - Left motor)
 * - IN1 -> ESP32 GPIO 26
 * - IN2 -> ESP32 GPIO 27
 * - IN3 -> ESP32 GPIO 12
 * - IN4 -> ESP32 GPIO 13
 * - ENB -> ESP32 GPIO 14 (PWM - Right motor)
 * - VCC -> External battery voltage (motors)
 * - GND -> Common ground with ESP32 and battery
 * - 5V output -> Can power ESP32 if using external battery
 * 
 * IMPORTANT: Ensure common ground between ESP32, L298N, and battery!
 * 
 * HC-SR04 Ultrasonic Sensors (4 sensors):
 * - Front Left:   TRIG=GPIO 4,  ECHO=GPIO 5,  VCC=5V, GND=GND
 * - Front Center: TRIG=GPIO 18, ECHO=GPIO 19, VCC=5V, GND=GND
 * - Front Right:  TRIG=GPIO 32, ECHO=GPIO 33, VCC=5V, GND=GND
 * - Back:         TRIG=GPIO 16, ECHO=GPIO 17, VCC=5V, GND=GND
 * 
 * GY-53 VL53L0X (Optional):
 * - SDA -> GPIO 21 (default I2C)
 * - SCL -> GPIO 22 (default I2C)
 * - VCC -> 3.3V
 * - GND -> GND
 * 
 * For GY-53 support, add VL53L0X library and initialize in setup().
 */
