package com.microservices.filetransfer.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "file_metadata")
public class FileMetadata {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String fileId;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String checksum;
    
    private String senderPeerId;
    private String receiverPeerId;
    
    @Enumerated(EnumType.STRING)
    private TransferStatus status;
    
    private Long bytesTransferred;
    private Integer progress;
    
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    
    public enum TransferStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
    }
    
    public FileMetadata() {}
    
    public FileMetadata(Long id, String fileId, String fileName, Long fileSize, String fileType,
                       String checksum, String senderPeerId, String receiverPeerId, 
                       TransferStatus status, Long bytesTransferred, Integer progress,
                       LocalDateTime createdAt, LocalDateTime completedAt) {
        this.id = id;
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileType = fileType;
        this.checksum = checksum;
        this.senderPeerId = senderPeerId;
        this.receiverPeerId = receiverPeerId;
        this.status = status;
        this.bytesTransferred = bytesTransferred;
        this.progress = progress;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    
    public String getSenderPeerId() { return senderPeerId; }
    public void setSenderPeerId(String senderPeerId) { this.senderPeerId = senderPeerId; }
    
    public String getReceiverPeerId() { return receiverPeerId; }
    public void setReceiverPeerId(String receiverPeerId) { this.receiverPeerId = receiverPeerId; }
    
    public TransferStatus getStatus() { return status; }
    public void setStatus(TransferStatus status) { this.status = status; }
    
    public Long getBytesTransferred() { return bytesTransferred; }
    public void setBytesTransferred(Long bytesTransferred) { this.bytesTransferred = bytesTransferred; }
    
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
