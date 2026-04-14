package com.microservices.peer.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "contacts")
public class Contact {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String userId;
    private String contactUserId;
    private String status; // PENDING, ACCEPTED, REJECTED

    public Contact() {}

    public Contact(String userId, String contactUserId, String status) {
        this.userId = userId;
        this.contactUserId = contactUserId;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getContactUserId() { return contactUserId; }
    public void setContactUserId(String contactUserId) { this.contactUserId = contactUserId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
