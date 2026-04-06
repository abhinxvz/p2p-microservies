package com.microservices.filetransfer.dto;

public class TransferInitRequest {
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String senderPeerId;
    private String receiverPeerId;
    
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public String getSenderPeerId() { return senderPeerId; }
    public void setSenderPeerId(String senderPeerId) { this.senderPeerId = senderPeerId; }
    public String getReceiverPeerId() { return receiverPeerId; }
    public void setReceiverPeerId(String receiverPeerId) { this.receiverPeerId = receiverPeerId; }
}
