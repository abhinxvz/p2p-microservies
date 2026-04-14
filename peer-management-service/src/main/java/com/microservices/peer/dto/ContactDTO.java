package com.microservices.peer.dto;

import java.util.List;

/**
 * DTO returned by the /network/contacts endpoint.
 * Represents a contact with their online/offline status.
 * If online, includes their active sessionIds.
 */
public class ContactDTO {
    private String username;
    private boolean online;
    private List<String> sessionIds;

    public ContactDTO() {}

    public ContactDTO(String username, boolean online, List<String> sessionIds) {
        this.username = username;
        this.online = online;
        this.sessionIds = sessionIds;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }

    public List<String> getSessionIds() { return sessionIds; }
    public void setSessionIds(List<String> sessionIds) { this.sessionIds = sessionIds; }
}
