package top.c0r3.talk.model;

import java.util.ArrayList;
import java.util.List;

public class Group {
    private Long dbId;          // 数据库自增ID
    private String id;          // UUID，业务ID
    private String name;
    private String passwdhash;
    private String ownerId;     // 群主的UUID
    private Long ownerDbId;     // 群主的数据库ID
    private long createTime;
    private List<String> memberIds;    // 成员UUID列表
    private List<Long> memberDbIds;    // 成员数据库ID列表

    public Group() {
        this.memberIds = new ArrayList<>();
        this.memberDbIds = new ArrayList<>();
    }

    public Group(String id, String name, String passwdhash, String ownerId) {
        this.id = id;
        this.name = name;
        this.passwdhash = passwdhash;
        this.ownerId = ownerId;
        this.createTime = System.currentTimeMillis();
        this.memberIds = new ArrayList<>();
        this.memberDbIds = new ArrayList<>();
        this.memberIds.add(ownerId); // 群主自动加入
    }

    // Getters and Setters
    public Long getDbId() { return dbId; }
    public void setDbId(Long dbId) { this.dbId = dbId; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPasswdhash() { return passwdhash; }
    public void setPasswdhash(String passwdhash) { this.passwdhash = passwdhash; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public Long getOwnerDbId() { return ownerDbId; }
    public void setOwnerDbId(Long ownerDbId) { this.ownerDbId = ownerDbId; }

    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }

    public List<String> getMemberIds() { return memberIds; }
    public void setMemberIds(List<String> memberIds) { this.memberIds = memberIds; }

    public List<Long> getMemberDbIds() { return memberDbIds; }
    public void setMemberDbIds(List<Long> memberDbIds) { this.memberDbIds = memberDbIds; }

    public void addMember(String userId, Long userDbId) {
        if (!memberIds.contains(userId)) {
            memberIds.add(userId);
            memberDbIds.add(userDbId);
        }
    }

    public void removeMember(String userId) {
        int index = memberIds.indexOf(userId);
        if (index >= 0) {
            memberIds.remove(index);
            if (index < memberDbIds.size()) {
                memberDbIds.remove(index);
            }
        }
    }

    @Override
    public String toString() {
        return "Group{id='" + id + "', name='" + name + "', members=" + memberIds.size() + "}";
    }
}
