#!/usr/bin/env python3
"""
Devaki Mock Robot Server with Matplotlib Visualization
TCP server that accepts robot commands and displays live wheel speed chart.
"""

import socket
import threading
import time
import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation
from collections import deque

# Server config
HOST = '0.0.0.0'
PORT = 9000

# Speed tracking
left_speeds = deque(maxlen=100)
right_speeds = deque(maxlen=100)
timestamps = deque(maxlen=100)
start_time = time.time()

# Current speeds
current_left = 0
current_right = 0

def handle_client(conn, addr):
    """Handle client connection and process commands."""
    global current_left, current_right
    
    print(f"[+] Client connected: {addr}")
    
    try:
        buffer = ""
        while True:
            data = conn.recv(1024)
            if not data:
                break
            
            buffer += data.decode('utf-8')
            
            # Process complete lines
            while '\n' in buffer:
                line, buffer = buffer.split('\n', 1)
                command = line.strip()
                
                if not command:
                    continue
                
                print(f"[RX] {command}")
                
                # Process commands
                if command.upper() == "PING":
                    conn.sendall(b"PONG\n")
                    print("[TX] PONG")
                
                elif command.upper() == "STOP":
                    current_left = 0
                    current_right = 0
                    record_speed(0, 0)
                
                elif command.upper().startswith("SPEED "):
                    parts = command.split()
                    if len(parts) == 3:
                        try:
                            left = int(parts[1])
                            right = int(parts[2])
                            current_left = max(0, min(255, left))
                            current_right = max(0, min(255, right))
                            record_speed(current_left, current_right)
                        except ValueError:
                            print(f"[!] Invalid SPEED values: {command}")
                
                elif command.upper() in ["FWD", "FORWARD"]:
                    current_left = current_right = 180
                    record_speed(180, 180)
                
                elif command.upper() in ["BACK", "BACKWARD"]:
                    current_left = current_right = -180
                    record_speed(-180, -180)
                
                elif command.upper() == "LEFT":
                    current_left = -150
                    current_right = 150
                    record_speed(-150, 150)
                
                elif command.upper() == "RIGHT":
                    current_left = 150
                    current_right = -150
                    record_speed(150, -150)
    
    except Exception as e:
        print(f"[!] Error: {e}")
    
    finally:
        conn.close()
        print(f"[-] Client disconnected: {addr}")

def record_speed(left, right):
    """Record speed values for plotting."""
    timestamps.append(time.time() - start_time)
    left_speeds.append(left)
    right_speeds.append(right)

def run_server():
    """Run TCP server."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind((HOST, PORT))
        s.listen()
        print(f"[*] Mock robot server listening on {HOST}:{PORT}")
        print("[*] Waiting for connections...")
        
        while True:
            conn, addr = s.accept()
            client_thread = threading.Thread(target=handle_client, args=(conn, addr))
            client_thread.daemon = True
            client_thread.start()

def update_plot(frame):
    """Update matplotlib plot."""
    plt.cla()
    
    if len(timestamps) > 0:
        plt.plot(list(timestamps), list(left_speeds), 'b-', label='Left', linewidth=2)
        plt.plot(list(timestamps), list(right_speeds), 'r-', label='Right', linewidth=2)
    
    plt.axhline(y=0, color='gray', linestyle='--', alpha=0.5)
    plt.xlabel('Time (s)')
    plt.ylabel('Speed')
    plt.title('Devaki Robot Wheel Speeds')
    plt.legend(loc='upper right')
    plt.ylim(-255, 255)
    plt.grid(True, alpha=0.3)
    
    # Show current values
    plt.text(0.02, 0.98, f'L: {current_left:3d}  R: {current_right:3d}',
             transform=plt.gca().transAxes,
             verticalalignment='top',
             bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.5))

if __name__ == '__main__':
    # Start server in background thread
    server_thread = threading.Thread(target=run_server)
    server_thread.daemon = True
    server_thread.start()
    
    # Setup matplotlib
    plt.style.use('dark_background')
    fig = plt.figure(figsize=(10, 6))
    
    # Animate plot
    ani = FuncAnimation(fig, update_plot, interval=100, cache_frame_data=False)
    
    try:
        plt.show()
    except KeyboardInterrupt:
        print("\n[*] Shutting down...")
