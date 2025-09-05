package top.c0r3.talk.model;

public class User {
    private Long dbId;          // 数据库自增ID，用于内部查询
    private String id;          // UUID，用于业务逻辑
    private String nickname;
    private String passwdhash;
    private long lastLogin;

    public User() {}

    public User(String id, String nickname, String passwdhash) {
        this.id = id;
        this.nickname = nickname;
        this.passwdhash = passwdhash;
        this.lastLogin = System.currentTimeMillis();
    }

    // Getters and Setters
    public Long getDbId() { return dbId; }
    public void setDbId(Long dbId) { this.dbId = dbId; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getPasswdhash() { return passwdhash; }
    public void setPasswdhash(String passwdhash) { this.passwdhash = passwdhash; }

    public long getLastLogin() { return lastLogin; }
    public void setLastLogin(long lastLogin) { this.lastLogin = lastLogin; }

    @Override
    public String toString() {
        return "User{id='" + id + "', nickname='" + nickname + "'}";
    }
}
