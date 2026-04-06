package com.microservices.filetransfer.dto;

public class SignalMessage {
    private String sourcePeerId;
    private String targetPeerId;
    private String type; // offer, answer, ice-candidate
    private Object payload;
    
    public String getSourcePeerId() { return sourcePeerId; }
    public void setSourcePeerId(String sourcePeerId) { this.sourcePeerId = sourcePeerId; }
    public String getTargetPeerId() { return targetPeerId; }
    public void setTargetPeerId(String targetPeerId) { this.targetPeerId = targetPeerId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }
}
