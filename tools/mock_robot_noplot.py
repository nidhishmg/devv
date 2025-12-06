#!/usr/bin/env python3
"""
DEV Mock Robot Server (Console Only - No Matplotlib)
Simple TCP server that accepts robot commands and prints to console.
"""

import socket
import threading

# Server config
HOST = '0.0.0.0'
PORT = 9000

def handle_client(conn, addr):
    """Handle client connection and process commands."""
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
                    print("    → Motors STOPPED")
                
                elif command.upper().startswith("SPEED "):
                    parts = command.split()
                    if len(parts) == 3:
                        try:
                            left = int(parts[1])
                            right = int(parts[2])
                            print(f"    → L={left:3d}, R={right:3d}")
                        except ValueError:
                            print(f"[!] Invalid SPEED values: {command}")
                
                elif command.upper() in ["FWD", "FORWARD"]:
                    print("    → Moving FORWARD")
                
                elif command.upper() in ["BACK", "BACKWARD"]:
                    print("    → Moving BACKWARD")
                
                elif command.upper() == "LEFT":
                    print("    → Turning LEFT")
                
                elif command.upper() == "RIGHT":
                    print("    → Turning RIGHT")
                
                elif command.upper().startswith("SET SPD "):
                    speed = command.split()[-1]
                    print(f"    → Base speed set to {speed}")
                
                else:
                    print(f"[!] Unknown command: {command}")
    
    except Exception as e:
        print(f"[!] Error: {e}")
    
    finally:
        conn.close()
        print(f"[-] Client disconnected: {addr}")

def run_server():
    """Run TCP server."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind((HOST, PORT))
        s.listen()
        print(f"[*] Mock robot server listening on {HOST}:{PORT}")
        print("[*] Waiting for connections...")
        print("[*] Press Ctrl+C to stop")
        print()
        
        try:
            while True:
                conn, addr = s.accept()
                client_thread = threading.Thread(target=handle_client, args=(conn, addr))
                client_thread.daemon = True
                client_thread.start()
        except KeyboardInterrupt:
            print("\n[*] Shutting down...")

if __name__ == '__main__':
    run_server()
