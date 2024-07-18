package worker;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;

public class Client {
    private static final String STORAGE_DIR = "storage";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java Worker <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);

        // Ensure the storage directory exists
        File storageDir = new File(STORAGE_DIR);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Worker listening on port " + port);

            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    System.out.println("Connected by " + clientSocket.getRemoteSocketAddress());
                    handleClient(clientSocket);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

            while (true) {
                // Read command (store/fetch)
                byte[] cmdBytes = new byte[5];
                in.readFully(cmdBytes);
                String cmd = new String(cmdBytes).trim();

                if (cmd.isEmpty()) {
                    break;
                }

                // Read file ID
                byte[] fileIdBytes = new byte[20];
                in.readFully(fileIdBytes);
                String fileId = new String(fileIdBytes).trim();

                if (cmd.equals("store")) {
                    System.out.println("Store!");

                    // Read the size of the incoming data
                    byte[] sizeBytes = new byte[10];
                    in.readFully(sizeBytes);
                    int size = Integer.parseInt(new String(sizeBytes).trim());

                    // Read data
                    byte[] data = new byte[size];
                    in.readFully(data);

                    // Save data to file
                    File file = new File(STORAGE_DIR, fileId);
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        fos.write(data);
                    }

                    System.out.println(file.toPath().getFileName() + " stored at " + STORAGE_DIR);

                    // Send success response
                    out.write("SUCCESS".getBytes());
                } else if (cmd.equals("fetch")) {
                    File file = new File(STORAGE_DIR, fileId);
                    if (file.exists()) {
                        byte[] data = Files.readAllBytes(file.toPath());
                        String size = String.format("%010d", data.length);

                        // Send size and data
                        out.write(size.getBytes());
                        out.write(data);
                    } else {
                        // Send error response
                        out.write("ERROR".getBytes());
                    }
                } else {
                    out.write("INVALID".getBytes());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
