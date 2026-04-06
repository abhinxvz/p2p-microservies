package com.microservices.filetransfer.controller;

import com.microservices.filetransfer.dto.PeerRegistrationRequest;
import com.microservices.filetransfer.model.Peer;
import com.microservices.filetransfer.service.PeerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/peers")
@CrossOrigin(origins = "*")
public class PeerController {
    
    private final PeerService peerService;
    
    public PeerController(PeerService peerService) {
        this.peerService = peerService;
    }
    
    @PostMapping("/register")
    public ResponseEntity<Peer> registerPeer(@RequestBody PeerRegistrationRequest request) {
        Peer peer = peerService.registerPeer(
            request.getUsername(),
            request.getIpAddress(),
            request.getPort()
        );
        return ResponseEntity.ok(peer);
    }
    
    @PutMapping("/{peerId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable String peerId,
            @RequestParam Peer.PeerStatus status) {
        peerService.updatePeerStatus(peerId, status);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/{peerId}/disconnect")
    public ResponseEntity<Void> disconnectPeer(@PathVariable String peerId) {
        peerService.disconnectPeer(peerId);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/online")
    public ResponseEntity<List<Peer>> getOnlinePeers() {
        return ResponseEntity.ok(peerService.getOnlinePeers());
    }
    
    @GetMapping
    public ResponseEntity<List<Peer>> getAllPeers() {
        return ResponseEntity.ok(peerService.getAllPeers());
    }
}
