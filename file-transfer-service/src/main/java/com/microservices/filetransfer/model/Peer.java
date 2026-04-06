package com.microservices.filetransfer.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "peers")
public class Peer {
    @Id
    private String peerId;
    
    private String username;
    private String ipAddress;
    private Integer port;
    
    @Enumerated(EnumType.STRING)
    private PeerStatus status;
    
    private LocalDateTime lastSeen;
    private LocalDateTime connectedAt;
    
    public enum PeerStatus {
        ONLINE, OFFLINE, BUSY
    }
    
    public Peer() {}
    
    public Peer(String peerId, String username, String ipAddress, Integer port, 
                PeerStatus status, LocalDateTime lastSeen, LocalDateTime connectedAt) {
        this.peerId = peerId;
        this.username = username;
        this.ipAddress = ipAddress;
        this.port = port;
        this.status = status;
        this.lastSeen = lastSeen;
        this.connectedAt = connectedAt;
    }
    
    public String getPeerId() { return peerId; }
    public void setPeerId(String peerId) { this.peerId = peerId; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    
    public PeerStatus getStatus() { return status; }
    public void setStatus(PeerStatus status) { this.status = status; }
    
    public LocalDateTime getLastSeen() { return lastSeen; }
    public void setLastSeen(LocalDateTime lastSeen) { this.lastSeen = lastSeen; }
    
    public LocalDateTime getConnectedAt() { return connectedAt; }
    public void setConnectedAt(LocalDateTime connectedAt) { this.connectedAt = connectedAt; }
}
