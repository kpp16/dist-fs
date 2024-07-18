package components;

import interfaces.FileType;

import java.util.HashMap;
import java.util.Map;

public class DirBlock extends Block {
    private Map<Long, Inode> blockMap;

    public DirBlock(Map<Long, Inode> inodes) {
        super(FileType.DIRECTORY);
        this.blockMap = new HashMap<Long, Inode>(inodes.size());
    }

    public DirBlock() {
        super(FileType.DIRECTORY);
        this.blockMap = new HashMap<Long, Inode>();
    }

    public Map<Long, Inode> getInodeMap() {
        return blockMap;
    }

    public void setInodeMap(Map<Long, Inode> blockMap) {
        this.blockMap = blockMap;
    }

    public void addInode(Inode inode) {
        blockMap.put(inode.getInodeNumber(), inode);
    }

    public void removeInode(long blockNumber) {
        blockMap.remove(blockNumber);
    }

    public void updateBlock(Block block, Inode inode) {
        this.removeInode(inode.getInodeNumber());
        this.addInode(inode);
    }
}
