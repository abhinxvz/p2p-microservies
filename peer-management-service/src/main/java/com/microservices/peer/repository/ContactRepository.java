package com.microservices.peer.repository;

import com.microservices.peer.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {
    List<Contact> findByUserId(String userId);
    List<Contact> findByUserIdAndStatus(String userId, String status);
    List<Contact> findByContactUserIdAndStatus(String contactUserId, String status);
    boolean existsByUserIdAndContactUserId(String userId, String contactUserId);
    Optional<Contact> findByUserIdAndContactUserId(String userId, String contactUserId);
}
