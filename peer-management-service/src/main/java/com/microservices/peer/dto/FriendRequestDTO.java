package com.microservices.peer.dto;

/**
 * DTO for incoming friend requests shown to the recipient.
 */
public class FriendRequestDTO {
    private Long id;
    private String fromUsername;

    public FriendRequestDTO() {}

    public FriendRequestDTO(Long id, String fromUsername) {
        this.id = id;
        this.fromUsername = fromUsername;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFromUsername() { return fromUsername; }
    public void setFromUsername(String fromUsername) { this.fromUsername = fromUsername; }
}
