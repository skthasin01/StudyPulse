package studypulse.model;

import java.time.LocalDateTime;

/** A student account (table users). */
public class User {

    private int id;
    private String fullName;
    private String email;
    private String passwordHash;
    private String salt;
    private LocalDateTime createdAt;
    private boolean wellbeingEnabled = true;
    private int restDay = 5;          // 1 = Monday ... 7 = Sunday

    public User() {
    }

    public User(String fullName, String email, String passwordHash, String salt) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.salt = salt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isWellbeingEnabled() {
        return wellbeingEnabled;
    }

    public void setWellbeingEnabled(boolean wellbeingEnabled) {
        this.wellbeingEnabled = wellbeingEnabled;
    }

    public int getRestDay() {
        return restDay;
    }

    public void setRestDay(int restDay) {
        this.restDay = restDay;
    }

    @Override
    public String toString() {
        return fullName;
    }
}
