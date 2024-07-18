package components;

import interfaces.FileType;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class FileBlock extends Block {
    private int size;
    private static final List<String> SERVERS;

    static {
        Dotenv dotenv = Dotenv.load();
        String hosts = dotenv.get("BLOCK_HOSTS");
        SERVERS = Arrays.stream(hosts.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    private String server1;
    private String server2;

    public FileBlock(byte[] data, int size) {
        super(FileType.FILE);
        this.size = size;
        selectServers();
        saveDataToServers(data);
    }

    private void selectServers() {
        Random random = new Random();
        int index1 = random.nextInt(SERVERS.size());
        int index2;
        do {
            index2 = random.nextInt(SERVERS.size());
        } while (index1 == index2);

        server1 = SERVERS.get(index1);
        server2 = SERVERS.get(index2);
    }

    private void saveDataToServers(byte[] data) {
        System.out.println("Storing to server1: " + server1);
        if (!saveDataToServer(data, server1)) {
            throw new RuntimeException("Failed to store data on server1");
        }
        System.out.println("Storing to server2: " + server2);
        if (!saveDataToServer(data, server2)) {
            throw new RuntimeException("Failed to store data on server2");
        }
    }

    private boolean saveDataToServer(byte[] data, String server) {
        String[] serverDetails = server.split(":");
        try (Socket socket = new Socket(serverDetails[0], Integer.parseInt(serverDetails[1]));
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            dos.writeBytes("store");
            dos.writeBytes(String.format("%20s", super.getBlockID()));
            dos.writeBytes(String.format("%10d", data.length));
            dos.write(data);

            // Read the response
            byte[] responseBytes = new byte[7]; // "SUCCESS" or "ERROR"
            dis.readFully(responseBytes);
            String response = new String(responseBytes).trim();
            return "SUCCESS".equals(response);

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public byte[] getData() {
        byte[] data = tryFetchDataFromServer(server1);
        if (data == null) {
            data = tryFetchDataFromServer(server2);
            if (data == null) {
                throw new RuntimeException("Failed to fetch data from both servers");
            }
        }
        return data;
    }

    private byte[] tryFetchDataFromServer(String server) {
        String[] serverDetails = server.split(":");
        try (Socket socket = new Socket(serverDetails[0], Integer.parseInt(serverDetails[1]));
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            dos.writeBytes("fetch");
            dos.writeBytes(String.format("%20s", super.getBlockID()));

            // Read the size of the incoming data
            byte[] sizeBytes = new byte[10];
            dis.readFully(sizeBytes);
            int dataSize = Integer.parseInt(new String(sizeBytes).trim());

            // Read the data
            byte[] data = new byte[dataSize];
            dis.readFully(data);
            return data;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void setData(byte[] data) {
        this.size = data.length;
        saveDataToServers(data);
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
