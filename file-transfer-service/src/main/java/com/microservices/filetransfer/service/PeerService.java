package com.microservices.filetransfer.service;

import com.microservices.filetransfer.model.Peer;
import com.microservices.filetransfer.repository.PeerRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PeerService {
    
    private final PeerRepository peerRepository;
    
    public PeerService(PeerRepository peerRepository) {
        this.peerRepository = peerRepository;
    }
    
    public Peer registerPeer(String username, String ipAddress, Integer port) {
        Peer peer = new Peer();
        peer.setPeerId(UUID.randomUUID().toString());
        peer.setUsername(username);
        peer.setIpAddress(ipAddress);
        peer.setPort(port);
        peer.setStatus(Peer.PeerStatus.ONLINE);
        peer.setConnectedAt(LocalDateTime.now());
        peer.setLastSeen(LocalDateTime.now());
        return peerRepository.save(peer);
    }
    
    public void updatePeerStatus(String peerId, Peer.PeerStatus status) {
        peerRepository.findById(peerId).ifPresent(peer -> {
            peer.setStatus(status);
            peer.setLastSeen(LocalDateTime.now());
            peerRepository.save(peer);
        });
    }
    
    public void disconnectPeer(String peerId) {
        peerRepository.findById(peerId).ifPresent(peer -> {
            peer.setStatus(Peer.PeerStatus.OFFLINE);
            peer.setLastSeen(LocalDateTime.now());
            peerRepository.save(peer);
        });
    }
    
    public List<Peer> getOnlinePeers() {
        return peerRepository.findByStatus(Peer.PeerStatus.ONLINE);
    }
    
    public List<Peer> getAllPeers() {
        return peerRepository.findAll();
    }
}
