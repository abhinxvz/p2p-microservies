package com.microservices.peer.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "peers")
public class Peer {
    @Id
    private String sessionId;
    private String username;
    private String status; // e.g., ACTIVE, INACTIVE
    private LocalDateTime lastSeen;

    public Peer() {}

    public Peer(String sessionId, String username, String status) {
        this.sessionId = sessionId;
        this.username = username;
        this.status = status;
        this.lastSeen = LocalDateTime.now();
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getLastSeen() { return lastSeen; }
    public void setLastSeen(LocalDateTime lastSeen) { this.lastSeen = lastSeen; }
}
