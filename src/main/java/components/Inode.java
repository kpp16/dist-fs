package components;

import interfaces.FileType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Inode implements Serializable {
    private static final long serialVersionUID = 1L;

    private String owner;
    private String group;
    private String address;
    private long size;
    private FileType fileType;

    private Date create;
    private Date access;
    private Date modify;

    private long inodeNumber;
    private List<Long> blockIds;

    public Inode(String owner, String group, String address, long size, FileType fileType, Date create, Date access, Date modify, long inodeNumber, List<Long> blockIds) {
        this.owner = owner;
        this.group = group;
        this.address = address;
        this.size = size;
        this.fileType = fileType;
        this.create = create;
        this.access = access;
        this.modify = modify;
        this.inodeNumber = inodeNumber;
        this.blockIds = new ArrayList<>(blockIds);
    }

    public List<Long> getBlockIds() {
        return blockIds;
    }

    public void setBlockIds(List<Long> blockIds) {
        this.blockIds = blockIds;
    }

    public FileType getFileType() {
        return fileType;
    }

    public void setFileType(FileType fileType) {
        this.fileType = fileType;
    }

    public long getInodeNumber() {
        return inodeNumber;
    }

    public void setInodeNumber(long inodeNumber) {
        this.inodeNumber = inodeNumber;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public Date getCreate() {
        return create;
    }

    public void setCreate(Date create) {
        this.create = create;
    }

    public Date getAccess() {
        return access;
    }

    public void setAccess(Date access) {
        this.access = access;
    }

    public Date getModify() {
        return modify;
    }

    public void setModify(Date modify) {
        this.modify = modify;
    }
}
