package com.microservices.filetransfer.dto;

public class PeerRegistrationRequest {
    private String username;
    private String ipAddress;
    private Integer port;
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
}
