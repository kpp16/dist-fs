package components;

import org.apache.ratis.client.RaftClient;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.protocol.*;

import interfaces.FileType;
import io.github.cdimascio.dotenv.Dotenv;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.exceptions.RaftException;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class FileBlock extends Block {
    private int size;
    private static final List<String> SERVERS;

    public static final List<String> PEER_ADDR = Arrays.asList(
            "localhost:8081",
            "localhost:8082",
            "localhost:8083"
    );

    private static RaftGroup raftGroup;

    static {
        Dotenv dotenv = Dotenv.load();
        String hosts = dotenv.get("BLOCK_HOSTS");
        SERVERS = Arrays.stream(hosts.split(","))
                .map(String::trim)
                .collect(Collectors.toList());

        RaftGroupId raftGroupId = RaftGroupId.valueOf(UUID.fromString("12345678-1234-1234-1234-123456789012"));
        List<RaftPeer> peers = PEER_ADDR.stream()
                .map(addr -> RaftPeer.newBuilder().setId(RaftPeerId.valueOf("n" + addr)).setAddress(addr).build())
                .collect(Collectors.toList());
        raftGroup = RaftGroup.valueOf(raftGroupId, peers);

    }

    public FileBlock(byte[] data, int size) {
        super(FileType.FILE);
        this.size = size;
        saveDataToServers(data);
    }

    private void saveDataToServers(byte[] data) {
        System.out.println("Storing data");
        boolean resp = saveDataToServer(data);
        if (resp) {
            System.out.println("Successfully stored data");
        } else {
            System.out.println("Failed to store data");
        }
    }

    private boolean saveDataToServer(byte[] data) {
        RaftProperties raftProperties = new RaftProperties();
        try (RaftClient client = RaftClient.newBuilder()
                .setProperties(raftProperties)
                .setRaftGroup(raftGroup)
                .build()) {

            String command = "store," + super.getBlockID() + "," + data.length + "," + new String(data);
            RaftClientReply reply = client.io().send(Message.valueOf(command));

            return "SUCCESS".equals(reply.getMessage().getContent().toStringUtf8());

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public byte[] getData() {
        RaftProperties raftProperties = new RaftProperties();
        try (RaftClient client = RaftClient.newBuilder()
                .setProperties(raftProperties)
                .setRaftGroup(raftGroup)
                .build()) {

            String command = "fetch," + super.getBlockID() + ",0,0";
            RaftClientReply reply = client.io().send(Message.valueOf(command));

            return reply.getMessage().getContent().toByteArray();

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
