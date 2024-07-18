import exceptions.InvalidFileTypeException;
import exceptions.LocationDoesNotExistException;
import filesystem.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileSystemServer {
    private static final int PORT = 8080;
    private final FileSystem fileSystem;

    private static final Logger logger = Logger.getLogger(FileSystemServer.class.getName());

    private static final int THREAD_POOL_SIZE = 10;

    static {
        try {
            // Configure the logger with a file handler and a simple formatter
            FileHandler fileHandler = new FileHandler("server.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setLevel(Level.ALL);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public FileSystemServer(String userName, String group) {
        this.fileSystem = new FileSystem(userName, group);
    }

    public void start() {
        ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("File system server is running...");
            logger.log(Level.INFO, "File system server is running on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Server exception: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }

    public void handleClient(Socket clientSocket) {
        String clientInfo = clientSocket.getRemoteSocketAddress().toString();
        logger.log(Level.INFO, "Client connected: " + clientInfo);

        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String command;
            while ((command = in.readLine()) != null) {
                logger.log(Level.INFO, command);
                String response;
                try {
                    response = processCommand(command);
                } catch (LocationDoesNotExistException | InvalidFileTypeException e) {
                    response = "Error: " + e.getMessage();
                    logger.log(Level.WARNING, response, e);
                }
                out.println(response);
            }
        } catch (IOException e) {
            if (e.getMessage().contains("Connection reset")) {
                logger.log(Level.INFO, "Client disconnected: " + clientInfo);
            } else {
                logger.log(Level.SEVERE, "IOException occurred: " + e.getMessage(), e);
            }
        } finally {
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to close client socket: " + e.getMessage(), e);
            }
        }
    }

    private String processCommand(String command) throws LocationDoesNotExistException, InvalidFileTypeException {
        String[] parts = command.split(" ");
        String cmd = parts[0];

        switch (cmd) {
            case "cd":
                fileSystem.changeDir(parts[1]);
                return "Changed directory to " + parts[1];
            case "mkdir":
                fileSystem.createDir(parts[1]);
                return "Directory " + parts[1] + " created.";
            case "write":
                String fileName = parts[1];
                StringBuilder dataBuilder = new StringBuilder();
                for (int i = 2; i < parts.length; i++) {
                    dataBuilder.append(parts[i]).append(" ");
                }
                byte[] data = dataBuilder.toString().getBytes();
                fileSystem.createFile(fileName, data);
                return "File " + fileName + " created.";
            case "pwd":
                return fileSystem.getCurDir().getAddress();
            case "tree":
                return fileSystem.tree();
            case "read":
                byte[] fileData = fileSystem.readFile(parts[1]);
                if (fileData != null) {
                    // Convert byte array to string (assuming UTF-8 encoding)
                    return new String(fileData, StandardCharsets.UTF_8);
                } else {
                    return "File " + parts[1] + " not found or could not be read.";
                }
            case "rm":
                fileSystem.deleteFile(parts[1]);
                return "Deleted file " + parts[1] + ".";
            default:
                return "Unknown command: " + cmd;
        }
    }

    public static void main(String[] args) {
        FileSystemServer server = new FileSystemServer("user", "group");
        server.start();
    }
}
