/*
 * DEV ESP32 Robot Controller
 * 
 * Features:
 * - Wi-Fi TCP server on port 9000
 * - Accepts text commands: FWD, BACK, LEFT, RIGHT, STOP, SPEED L R, SET SPD n, PING
 * - Controls motors via TB6612FNG or L298N motor driver
 * - 8-bit PWM at 1.5 kHz
 */

#include <WiFi.h>

// Wi-Fi credentials (CHANGE THESE!)
const char* ssid = "YOUR_WIFI_SSID";
const char* password = "YOUR_WIFI_PASSWORD";

// TCP Server
WiFiServer server(9000);
WiFiClient client;

// Motor Driver Pins (TB6612FNG example - adjust for your hardware)
// Left Motor
const int MOTOR_L_PWM = 25;  // PWM speed control
const int MOTOR_L_IN1 = 26;  // Direction 1
const int MOTOR_L_IN2 = 27;  // Direction 2

// Right Motor
const int MOTOR_R_PWM = 32;  // PWM speed control
const int MOTOR_R_IN1 = 33;  // Direction 1
const int MOTOR_R_IN2 = 14;  // Direction 2

// PWM Settings
const int PWM_FREQ = 1500;   // 1.5 kHz
const int PWM_RES = 8;       // 8-bit (0-255)
const int PWM_CHANNEL_L = 0;
const int PWM_CHANNEL_R = 1;

// Default speed
int baseSpeed = 180;

void setup() {
  Serial.begin(115200);
  
  // Configure motor pins
  pinMode(MOTOR_L_IN1, OUTPUT);
  pinMode(MOTOR_L_IN2, OUTPUT);
  pinMode(MOTOR_R_IN1, OUTPUT);
  pinMode(MOTOR_R_IN2, OUTPUT);
  
  // Setup PWM channels
  ledcSetup(PWM_CHANNEL_L, PWM_FREQ, PWM_RES);
  ledcSetup(PWM_CHANNEL_R, PWM_FREQ, PWM_RES);
  ledcAttachPin(MOTOR_L_PWM, PWM_CHANNEL_L);
  ledcAttachPin(MOTOR_R_PWM, PWM_CHANNEL_R);
  
  // Start with motors stopped
  motorStop();
  
  // Connect to Wi-Fi
  Serial.print("Connecting to ");
  Serial.println(ssid);
  WiFi.begin(ssid, password);
  
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  
  Serial.println("");
  Serial.println("WiFi connected!");
  Serial.print("IP address: ");
  Serial.println(WiFi.localIP());
  
  // Start TCP server
  server.begin();
  Serial.println("TCP server started on port 9000");
}

void loop() {
  // Accept new client connection
  if (!client || !client.connected()) {
    client = server.available();
    if (client) {
      Serial.println("New client connected");
    }
  }
  
  // Process commands from client
  if (client && client.connected() && client.available()) {
    String command = client.readStringUntil('\n');
    command.trim();
    
    if (command.length() > 0) {
      Serial.print("RX: ");
      Serial.println(command);
      processCommand(command);
    }
  }
}

void processCommand(String cmd) {
  cmd.toUpperCase();
  
  if (cmd == "FWD" || cmd == "FORWARD") {
    motorForward(baseSpeed);
  }
  else if (cmd == "BACK" || cmd == "BACKWARD") {
    motorBackward(baseSpeed);
  }
  else if (cmd == "LEFT") {
    motorLeft(baseSpeed);
  }
  else if (cmd == "RIGHT") {
    motorRight(baseSpeed);
  }
  else if (cmd == "STOP") {
    motorStop();
  }
  else if (cmd.startsWith("SPEED ")) {
    // SPEED <L> <R>
    int spaceIdx = cmd.indexOf(' ', 6);
    if (spaceIdx > 0) {
      int leftSpeed = cmd.substring(6, spaceIdx).toInt();
      int rightSpeed = cmd.substring(spaceIdx + 1).toInt();
      motorSpeedLR(leftSpeed, rightSpeed);
    }
  }
  else if (cmd.startsWith("SET SPD ")) {
    // SET SPD <n>
    baseSpeed = cmd.substring(8).toInt();
    baseSpeed = constrain(baseSpeed, 0, 255);
    Serial.print("Base speed set to: ");
    Serial.println(baseSpeed);
  }
  else if (cmd == "PING") {
    if (client && client.connected()) {
      client.println("PONG");
    }
  }
  else {
    Serial.print("Unknown command: ");
    Serial.println(cmd);
  }
}

// Motor control functions
void motorForward(int speed) {
  speed = constrain(speed, 0, 255);
  digitalWrite(MOTOR_L_IN1, HIGH);
  digitalWrite(MOTOR_L_IN2, LOW);
  digitalWrite(MOTOR_R_IN1, HIGH);
  digitalWrite(MOTOR_R_IN2, LOW);
  ledcWrite(PWM_CHANNEL_L, speed);
  ledcWrite(PWM_CHANNEL_R, speed);
}

void motorBackward(int speed) {
  speed = constrain(speed, 0, 255);
  digitalWrite(MOTOR_L_IN1, LOW);
  digitalWrite(MOTOR_L_IN2, HIGH);
  digitalWrite(MOTOR_R_IN1, LOW);
  digitalWrite(MOTOR_R_IN2, HIGH);
  ledcWrite(PWM_CHANNEL_L, speed);
  ledcWrite(PWM_CHANNEL_R, speed);
}

void motorLeft(int speed) {
  speed = constrain(speed, 0, 255);
  // Left motor backward, right forward
  digitalWrite(MOTOR_L_IN1, LOW);
  digitalWrite(MOTOR_L_IN2, HIGH);
  digitalWrite(MOTOR_R_IN1, HIGH);
  digitalWrite(MOTOR_R_IN2, LOW);
  ledcWrite(PWM_CHANNEL_L, speed);
  ledcWrite(PWM_CHANNEL_R, speed);
}

void motorRight(int speed) {
  speed = constrain(speed, 0, 255);
  // Left motor forward, right backward
  digitalWrite(MOTOR_L_IN1, HIGH);
  digitalWrite(MOTOR_L_IN2, LOW);
  digitalWrite(MOTOR_R_IN1, LOW);
  digitalWrite(MOTOR_R_IN2, HIGH);
  ledcWrite(PWM_CHANNEL_L, speed);
  ledcWrite(PWM_CHANNEL_R, speed);
}

void motorSpeedLR(int leftSpeed, int rightSpeed) {
  leftSpeed = constrain(leftSpeed, 0, 255);
  rightSpeed = constrain(rightSpeed, 0, 255);
  
  // Set left motor direction and speed
  if (leftSpeed > 0) {
    digitalWrite(MOTOR_L_IN1, HIGH);
    digitalWrite(MOTOR_L_IN2, LOW);
    ledcWrite(PWM_CHANNEL_L, leftSpeed);
  } else {
    digitalWrite(MOTOR_L_IN1, LOW);
    digitalWrite(MOTOR_L_IN2, LOW);
    ledcWrite(PWM_CHANNEL_L, 0);
  }
  
  // Set right motor direction and speed
  if (rightSpeed > 0) {
    digitalWrite(MOTOR_R_IN1, HIGH);
    digitalWrite(MOTOR_R_IN2, LOW);
    ledcWrite(PWM_CHANNEL_R, rightSpeed);
  } else {
    digitalWrite(MOTOR_R_IN1, LOW);
    digitalWrite(MOTOR_R_IN2, LOW);
    ledcWrite(PWM_CHANNEL_R, 0);
  }
}

void motorStop() {
  // Brake motors
  digitalWrite(MOTOR_L_IN1, LOW);
  digitalWrite(MOTOR_L_IN2, LOW);
  digitalWrite(MOTOR_R_IN1, LOW);
  digitalWrite(MOTOR_R_IN2, LOW);
  ledcWrite(PWM_CHANNEL_L, 0);
  ledcWrite(PWM_CHANNEL_R, 0);
}
