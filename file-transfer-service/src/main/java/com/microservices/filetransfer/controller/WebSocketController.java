package com.microservices.filetransfer.controller;

import com.microservices.filetransfer.dto.SignalMessage;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketController {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    public WebSocketController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
    
    @MessageMapping("/signal")
    public void handleSignal(@Payload SignalMessage message) {
        // Forward WebRTC signaling messages between peers
        messagingTemplate.convertAndSend(
            "/queue/signal-" + message.getTargetPeerId(),
            message
        );
    }
    
    @MessageMapping("/heartbeat")
    public void handleHeartbeat(@Payload String peerId) {
        // Handle peer heartbeat to maintain connection status
        messagingTemplate.convertAndSend("/topic/heartbeat", peerId);
    }
}
