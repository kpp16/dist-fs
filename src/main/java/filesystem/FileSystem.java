package filesystem;

import components.*;
import exceptions.InvalidFileTypeException;
import exceptions.LocationDoesNotExistException;
import interfaces.FileType;

import java.util.*;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static components.Block.BLOCK_SIZE;

public class FileSystem {
    private Map<Long, Block> blockStore;
    private Map<Long, Inode> inodeStore;
    private Map<String, Inode> inodeNameStore;

    private String userName;
    private String group;

    private final ThreadLocal<Inode> curDir;
    private final TransactionManager transactionManager;

    private final Lock lock = new ReentrantLock();

    private final Lock blockStoreLock = new ReentrantLock();
    private final Lock inodeStoreLock = new ReentrantLock();
    private final Lock inodeNameStoreLock = new ReentrantLock();
    private final Lock curDirLock = new ReentrantLock();

    public FileSystem(String userName, String group) {
        this.userName = userName;
        this.group = group;

        this.blockStore = new HashMap<>();
        this.inodeStore = new HashMap<>();
        this.inodeNameStore = new HashMap<>();
        this.transactionManager = new TransactionManager();

        List<Long> startBlockIds = new ArrayList<>();

        DirBlock block = new DirBlock();
        this.blockStore.put(block.getBlockID(), block);

        startBlockIds.add(block.getBlockID());

        Date now = new Date();
        Inode rootNode = new Inode(userName, group, "/", 0, FileType.DIRECTORY, now, now, now, 0, startBlockIds);

        this.inodeStore.put(rootNode.getInodeNumber(), rootNode);
        this.inodeNameStore.put("/", rootNode);
        this.curDir = ThreadLocal.withInitial(() -> rootNode);
    }

    public void changeDir(String newDirPath) throws LocationDoesNotExistException, InvalidFileTypeException {
        Inode currentDir = curDir.get();
        if (newDirPath.equals(".")) {
            return;
        } else if (newDirPath.equals("..")) {
            if (currentDir.getInodeNumber() == 0) {
                return;
            } else {
                String curPath = currentDir.getAddress();
                String[] ogparts = curPath.split("/");
                String[] parts = new String[ogparts.length - 1];
                System.arraycopy(ogparts, 1, parts, 0, parts.length);

                if (parts.length == 1) {
                    String newPath = "/";
                    lock.lock();
                    try {
                        if (inodeNameStore.containsKey(newPath)) {
                            curDir.set(inodeNameStore.get(newPath));
                        } else {
                            throw new LocationDoesNotExistException("Path does not exist");
                        }
                    } finally {
                        lock.unlock();
                    }
                    return;
                }
                StringBuilder newPathBuilder = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    if (parts[i].isEmpty()) {
                        continue;
                    }
                    newPathBuilder.append("/").append(parts[i]);
                }
                String newPath = newPathBuilder.toString();
                lock.lock();
                try {
                    if (inodeNameStore.containsKey(newPath)) {
                        curDir.set(inodeNameStore.get(newPath));
                    } else {
                        throw new LocationDoesNotExistException("Path does not exist");
                    }
                } finally {
                    lock.unlock();
                }
            }
        } else {
            List<Long> curBlocks = currentDir.getBlockIds();
            if (!curBlocks.isEmpty()) {
                Inode curInode = null;
                StringBuilder startPath;
                if (currentDir.getInodeNumber() == 0) {
                    startPath = new StringBuilder();
                } else {
                    startPath = new StringBuilder(currentDir.getAddress());
                }
                String[] parts = newDirPath.split("/");
                for (String part : parts) {
                    startPath.append("/").append(part);
                    lock.lock();
                    try {
                        if (inodeNameStore.containsKey(startPath.toString())) {
                            curInode = inodeNameStore.get(startPath.toString());
                            if (curInode.getFileType() != FileType.DIRECTORY) {
                                throw new InvalidFileTypeException("The path is not a directory");
                            }
                        } else {
                            throw new LocationDoesNotExistException("Path does not exist");
                        }
                    } finally {
                        lock.unlock();
                    }
                }
                curDir.set(curInode);
            } else {
                throw new LocationDoesNotExistException("Path does not exist");
            }
        }
    }

    public static List<Block> splitBinaryData(byte[] data) {
        List<Block> blocks = new ArrayList<>();
        int dataLength = data.length;
        int start = 0;

        while (start < dataLength) {
            int end = Math.min(start + BLOCK_SIZE, dataLength);
            int size = end - start;
            byte[] blockData = new byte[size];
            System.arraycopy(data, start, blockData, 0, size);

            blocks.add(new FileBlock(blockData, size));
            start += BLOCK_SIZE;
        }

        return blocks;
    }

    // TODO: basic filename checks
    public void createFile(String fileName, byte[] data) {
        Transaction transaction = new Transaction();

        List<Block> blocks = new ArrayList<>(splitBinaryData(data));
        List<Long> blockIds = new ArrayList<>();

        for (Block block: blocks) {
            long blockID = block.getBlockID();
            Runnable addBlock = () -> {
                blockStoreLock.lock();
                try {
                    blockStore.put(blockID, block);
                } finally {
                    blockStoreLock.unlock();
                }
            };

            Runnable removeBlock = () -> {
                blockStoreLock.lock();
                try {
                    blockStore.remove(blockID);
                } finally {
                    blockStoreLock.unlock();
                }
            };

            transaction.addOperation(addBlock, removeBlock);
            blockIds.add(blockID);
        }

        String newAddr;
        if (this.curDir.get().getInodeNumber() == 0) {
            newAddr = "/" + fileName;
        } else {
            newAddr = this.curDir.get().getAddress() + "/" + fileName;
        }
        Date now = new Date();
        Inode newInode;

        List<Block> oldBlocks = new ArrayList<>();

        try {
            inodeNameStoreLock.lock();
            if (inodeNameStore.containsKey(newAddr)) {
                newInode = inodeNameStore.get(newAddr);
                List<Long> oldBlockIds = new ArrayList<>(newInode.getBlockIds());
                for (Long blockId: oldBlockIds) {
                    
                }
            } else {
                newInode = new Inode(userName, group, newAddr, blocks.size() * 1000L, FileType.FILE, now, now, now, Block.generateRandomBlockID(), blockIds);
            }
        } finally {
            inodeNameStoreLock.unlock();
        }

        long inodeNumber = newInode.getInodeNumber();

        Runnable addInode = () -> {
            inodeStoreLock.lock();
            inodeNameStoreLock.lock();
            curDirLock.lock();

            try {
                inodeStore.put(inodeNumber, newInode);
                inodeNameStore.put(newAddr, newInode);

                Inode curDirInode = curDir.get();
                curDirInode.setSize(curDirInode.getSize() + newInode.getSize());
                DirBlock curDirBlock = (DirBlock) blockStore.get(curDirInode.getBlockIds().getFirst());
                curDirBlock.addInode(newInode);
            } finally {
                inodeStoreLock.unlock();
                inodeNameStoreLock.unlock();
                curDirLock.unlock();
            }
        };

        Runnable removeInode = () -> {
            inodeStoreLock.lock();
            inodeNameStoreLock.lock();
            curDirLock.lock();

            try {
                inodeStore.remove(inodeNumber);
                inodeNameStore.remove(newAddr);

                Inode curDirInode = curDir.get();
                curDirInode.setSize(curDirInode.getSize() - newInode.getSize());
                DirBlock curDirBlock = (DirBlock) blockStore.get(curDirInode.getBlockIds().getFirst());
                curDirBlock.removeInode(newInode.getInodeNumber());
            } finally {
                inodeStoreLock.unlock();
                inodeNameStoreLock.unlock();
                curDirLock.unlock();
            }
        };

        transaction.addOperation(addInode, removeInode);

        transactionManager.executeTransaction(transaction);
    }

    public void deleteFile(String fileName) throws LocationDoesNotExistException, InvalidFileTypeException {
        try {
            blockStoreLock.lock();
            inodeStoreLock.lock();
            curDirLock.lock();
            inodeNameStoreLock.lock();

            String newAddr;
            if (this.curDir.get().getInodeNumber() == 0) {
                newAddr = "/" + fileName;
            } else {
                newAddr = this.curDir.get().getAddress() + "/" + fileName;
            }
            if (inodeNameStore.containsKey(newAddr)) {
                Inode inode = inodeNameStore.get(newAddr);
                if (inode.getFileType() == FileType.DIRECTORY) {
                    throw new InvalidFileTypeException("Not a file");
                }
                List<Long> blockIds = inode.getBlockIds();
                for (Long blockId: blockIds) {
                    blockStore.remove(blockId);
                }
                inodeNameStore.remove(newAddr);
                inodeStore.remove(inode.getInodeNumber());

                DirBlock curDirBlock = (DirBlock) this.blockStore.get(this.curDir.get().getBlockIds().getFirst());
                curDirBlock.removeInode(inode.getInodeNumber());
                this.curDir.get().setSize(this.curDir.get().getSize() - 1);

            } else {
                throw new LocationDoesNotExistException("File does not exist");
            }

        } finally {
            blockStoreLock.unlock();
            inodeStoreLock.unlock();
            curDirLock.unlock();
            inodeNameStoreLock.unlock();
        }
    }

    public void createDir(String dirName) {
        Transaction transaction = new Transaction();

        List<Long> newBlocks = new ArrayList<>();
        String curPath = curDir.get().getAddress();
        String newPath;
        if (curDir.get().getInodeNumber() == 0) {
            newPath = curPath + dirName;
        } else {
            newPath = curPath + "/" + dirName;
        }
        Date now = new Date();

        DirBlock block = new DirBlock();
        long blockID = block.getBlockID();
        Runnable addBlock = () -> {
            blockStoreLock.lock();
            try {
                blockStore.put(blockID, block);
            } finally {
                blockStoreLock.unlock();
            }
        };

        Runnable removeBlock = () -> {
            blockStoreLock.lock();
            try {
                blockStore.remove(blockID);
            } finally {
                blockStoreLock.unlock();
            }
        };

        transaction.addOperation(addBlock, removeBlock);
        newBlocks.add(block.getBlockID());

        Inode newInode = new Inode(userName, group, newPath, 0, FileType.DIRECTORY, now, now, now, Block.generateRandomBlockID(), newBlocks);
        long inodeNumber = newInode.getInodeNumber();

        Runnable addInode = () -> {
            inodeStoreLock.lock();
            inodeNameStoreLock.lock();
            curDirLock.lock();

            try {
                inodeStore.put(inodeNumber, newInode);
                inodeNameStore.put(newPath, newInode);

                Inode curDirInode = curDir.get();
                curDirInode.setSize(curDirInode.getSize() + newInode.getSize());
                DirBlock dirBlock = (DirBlock) blockStore.get(curDirInode.getBlockIds().getFirst());
                dirBlock.addInode(newInode);
            } finally {
                inodeStoreLock.unlock();
                inodeNameStoreLock.unlock();
                curDirLock.unlock();
            }
        };

        Runnable removeInode = () -> {
            inodeStoreLock.lock();
            inodeNameStoreLock.lock();
            curDirLock.lock();

            try {
                inodeStore.remove(inodeNumber);
                inodeNameStore.remove(newPath);

                Inode curDirInode = curDir.get();
                curDirInode.setSize(curDirInode.getSize() - newInode.getSize());
                DirBlock dirBlock = (DirBlock) blockStore.get(curDirInode.getBlockIds().getFirst());
                dirBlock.removeInode(newInode.getInodeNumber());
            } finally {
                inodeStoreLock.unlock();
                inodeNameStoreLock.unlock();
                curDirLock.unlock();
            }
        };

        transaction.addOperation(addInode, removeInode);

        transactionManager.executeTransaction(transaction);
    }

    private void dfs(Inode inpInode, int tabs) {
        for (int i = 0; i < tabs; i++) {
            System.out.print("\t");
        }
        String addr = inpInode.getAddress();
        String[] parts = addr.split("/");
        if (parts.length > 0) {
            if (inpInode.getFileType() == FileType.DIRECTORY) {
                addr = "/" + parts[parts.length - 1];
            } else {
                addr = parts[parts.length - 1];
            }
        } else {
            addr = "/";
        }
        System.out.println(addr);
        if (inpInode.getFileType() == FileType.FILE) {
            return;
        } else {
            lock.lock();
            try {
                if (blockStore.containsKey(inpInode.getBlockIds().getFirst())) {
                    DirBlock block = (DirBlock) blockStore.get(inpInode.getBlockIds().getFirst());
                    Map<Long, Inode> inodeMap = new HashMap<>(block.getInodeMap());
                    Set<Long> keySet = inodeMap.keySet();
                    for (Long key : keySet) {
                        Inode inode = inodeMap.get(key);
                        dfs(inode, tabs + 1);
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    public String tree() {
        dfs(inodeStore.get(0L), 0);
        return "blah";
    }

    public void printCurrentDir() {
        System.out.println(this.curDir.get().getAddress());
    }

    public byte[] readFile(String fileName) throws LocationDoesNotExistException {
        byte[] data = null;
        List<Byte> bytes = new ArrayList<>();
        try {
            blockStoreLock.lock();
            inodeStoreLock.lock();
            inodeNameStoreLock.lock();
            curDirLock.lock();

            String newAddr;
            if (this.curDir.get().getInodeNumber() == 0) {
                newAddr = "/" + fileName;
            } else {
                newAddr = this.curDir.get().getAddress() + "/" + fileName;
            }

            if (inodeNameStore.containsKey(newAddr)) {
                Inode fileInode = inodeStore.get(inodeNameStore.get(newAddr).getInodeNumber());
                List<Long> blockIds = new ArrayList<>(fileInode.getBlockIds());
                for (Long blockId : blockIds) {
                    FileBlock block = (FileBlock) blockStore.get(blockId);
                    byte[] curData = block.getData();
                    for (byte b : curData) {
                        bytes.add(b);
                    }
                }
                data = new byte[bytes.size()];
                for (int i = 0; i < bytes.size(); i++) {
                    data[i] = bytes.get(i);
                }
            } else {
                throw new LocationDoesNotExistException("File does not exist");
            }

        } finally {
            blockStoreLock.unlock();
            inodeStoreLock.unlock();
            inodeNameStoreLock.unlock();
            curDirLock.unlock();
        }

        return data;
    }

    public Inode getCurDir() {
        return curDir.get();
    }
}
