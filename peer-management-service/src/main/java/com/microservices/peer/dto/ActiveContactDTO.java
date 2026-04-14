package com.microservices.peer.dto;

import java.util.List;

/**
 * DTO returned by the /network/active endpoint.
 * Groups all active sessionIds under a single username,
 * so the frontend sees "alice has 2 tabs open" instead of two raw Peer rows.
 */
public class ActiveContactDTO {
    private String username;
    private List<String> sessionIds;

    public ActiveContactDTO() {}

    public ActiveContactDTO(String username, List<String> sessionIds) {
        this.username = username;
        this.sessionIds = sessionIds;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public List<String> getSessionIds() { return sessionIds; }
    public void setSessionIds(List<String> sessionIds) { this.sessionIds = sessionIds; }
}
