package worker;

import org.apache.ratis.RaftConfigKeys;
import org.apache.ratis.conf.ConfUtils;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.datastream.SupportedDataStreamType;
import org.apache.ratis.grpc.GrpcConfigKeys;
import org.apache.ratis.grpc.GrpcFactory;
import org.apache.ratis.netty.NettyConfigKeys;
import org.apache.ratis.protocol.*;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.RaftServerConfigKeys;
import org.apache.ratis.statemachine.StateMachine;
import org.apache.ratis.statemachine.TransactionContext;
import org.apache.ratis.statemachine.impl.BaseStateMachine;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import org.apache.ratis.util.LifeCycle;
import org.apache.ratis.util.NetUtils;
import org.apache.ratis.util.SizeInBytes;
import org.apache.ratis.util.TimeDuration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class RaftWorkerServer {
    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.out.println("Usage: java RaftWorkerServer <current-address> <peer-addresses> <storage-dir>");
            System.exit(1);
        }

        String currentAddress = args[0];
        String[] peerAddresses = args[1].split(",");
        String storageDirPath = args[2];

        List<File> storageDir = new ArrayList<>();
        storageDir.add(new File(storageDirPath));

        RaftProperties properties = new RaftProperties();

        List<RaftPeer> peers = Arrays.stream(peerAddresses)
                .map(addr -> RaftPeer.newBuilder().setId(RaftPeerId.valueOf("n" + addr)).setAddress(addr).build())
                .collect(Collectors.toList());

        RaftGroupId raftGroupId = RaftGroupId.valueOf(UUID.fromString("12345678-1234-1234-1234-123456789012"));
        RaftGroup raftGroup = RaftGroup.valueOf(raftGroupId, peers);

        String id = "n" + currentAddress;;
        RaftPeerId peerId = RaftPeerId.valueOf(id);
        RaftPeer peer = raftGroup.getPeer(peerId);

        System.out.println("Starting server with ID: " + peerId);
        System.out.println("Raft Group ID: " + raftGroupId);
        System.out.println("Peers: " + peers);

        RaftServerConfigKeys.setStorageDir(properties, storageDir);
        RaftServerConfigKeys.Rpc.setTimeoutMin(properties, TimeDuration.valueOf(2, TimeUnit.SECONDS));
        RaftServerConfigKeys.Rpc.setTimeoutMax(properties, TimeDuration.valueOf(3, TimeUnit.SECONDS));

        final int port = NetUtils.createSocketAddr(peer.getAddress()).getPort();
        GrpcConfigKeys.Server.setPort(properties, port);

        Optional.ofNullable(peer.getClientAddress()).ifPresent(address ->
                GrpcConfigKeys.Client.setPort(properties, NetUtils.createSocketAddr(address).getPort()));
        Optional.ofNullable(peer.getAdminAddress()).ifPresent(address ->
                GrpcConfigKeys.Admin.setPort(properties, NetUtils.createSocketAddr(address).getPort()));

        String dataStreamAddress = peer.getDataStreamAddress();
        if (dataStreamAddress != null) {
            final int dataStreamport = NetUtils.createSocketAddr(dataStreamAddress).getPort();
            NettyConfigKeys.DataStream.setPort(properties, dataStreamport);
            RaftConfigKeys.DataStream.setType(properties, SupportedDataStreamType.NETTY);
        }
        RaftServerConfigKeys.setStorageDir(properties, storageDir);
        RaftServerConfigKeys.Write.setElementLimit(properties, 40960);
        RaftServerConfigKeys.Write.setByteLimit(properties, SizeInBytes.valueOf("1000MB"));

        RaftServer raftServer = RaftServer.newBuilder()
                .setServerId(peerId)
                .setGroup(raftGroup)
                .setProperties(properties)
                .setStateMachine(new SimpleStateMachine())
                .build();

        raftServer.start();
    }

    static class SimpleStateMachine extends BaseStateMachine {
        private static final String STORAGE_DIR = "storage";

        public SimpleStateMachine() {
            // Ensure the storage directory exists
            File storageDir = new File(STORAGE_DIR);
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }
        }

        @Override
        public CompletableFuture<Message> applyTransaction(TransactionContext trx) {
            ByteString logData = trx.getLogEntry().getStateMachineLogEntry().getLogData();
            String[] parts = logData.toStringUtf8().split(",", 4);
            System.out.println(parts[0] + " " + parts[1] + " " + parts[2]);
            String cmd = parts[0];
            String fileId = parts[1];
            int size = Integer.parseInt(parts[2]);
            byte[] data = parts[3].getBytes();

            if (cmd.equals("store")) {
                File file = new File(STORAGE_DIR, fileId);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(data);
                } catch (IOException e) {
                    e.printStackTrace();
                    return CompletableFuture.completedFuture(Message.valueOf("ERROR"));
                }
                return CompletableFuture.completedFuture(Message.valueOf("SUCCESS"));
            } else if (cmd.equals("fetch")) {
                File file = new File(STORAGE_DIR, fileId);
                if (file.exists()) {
                    try {
                        byte[] fileData = Files.readAllBytes(file.toPath());
                        return CompletableFuture.completedFuture(Message.valueOf(new String(fileData)));
                    } catch (IOException e) {
                        e.printStackTrace();
                        return CompletableFuture.completedFuture(Message.valueOf("ERROR"));
                    }
                } else {
                    return CompletableFuture.completedFuture(Message.valueOf("ERROR"));
                }
            } else {
                return CompletableFuture.completedFuture(Message.valueOf("INVALID"));
            }
        }
    }
}