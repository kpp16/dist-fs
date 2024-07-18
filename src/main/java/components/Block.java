package components;

import interfaces.FileType;

import java.security.SecureRandom;

public class Block {
    public static int BLOCK_SIZE = 1000;

    private final FileType blockFileType;
    private final long blockID;

    public static long generateRandomBlockID() {
        SecureRandom random = new SecureRandom();
        return random.nextLong();
    }

    public Block(FileType blockFileType) {
        this.blockFileType = blockFileType;
        this.blockID = generateRandomBlockID();
    }

    public long getBlockID() {
        return blockID;
    }

    public FileType getBlockFileType() {
        return blockFileType;
    }
}

