package com.microservices.filetransfer.repository;

import com.microservices.filetransfer.model.Peer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PeerRepository extends JpaRepository<Peer, String> {
    List<Peer> findByStatus(Peer.PeerStatus status);
}
