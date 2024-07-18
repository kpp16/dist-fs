import socket

SERVER_ADDRESS = 'localhost'
SERVER_PORT = 8080
TIMEOUT = 5  # Timeout in seconds

def main():
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(TIMEOUT)  # Set timeout for socket operations
            s.connect((SERVER_ADDRESS, SERVER_PORT))
            print(f"Connected to server at {SERVER_ADDRESS}:{SERVER_PORT}")
            
            while True:
                s.send("pwd\n".encode())
                data = s.recv(1024).decode().strip()
                command = input(f"{data}@{SERVER_ADDRESS}:{SERVER_PORT} > ")
                if command.lower() == "exit":
                    print("Exiting...")
                    break
                
                s.sendall((command + "\n").encode())  # Send command to server with newline character

                try:
                    data = s.recv(1024)  # Receive response from server
                    if not data:
                        print("Server closed the connection.")
                        break
                    
                    print(data.decode().strip())
                except socket.timeout:
                    print("Socket timed out waiting for a response.")
                except KeyboardInterrupt:
                    print("\nInterrupted by user. Exiting...")
                    break
    except Exception as e:
        print('An error occurred:', e)

if __name__ == "__main__":
    main()
