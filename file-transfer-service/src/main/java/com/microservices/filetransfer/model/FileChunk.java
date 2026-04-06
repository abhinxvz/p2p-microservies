package com.microservices.filetransfer.model;

import jakarta.persistence.*;

@Entity
@Table(name = "file_chunks")
public class FileChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String fileId;
    private Integer chunkNumber;
    private Integer totalChunks;
    
    @Lob
    @Column(length = 1048576)
    private byte[] data;
    
    private String checksum;
    private Boolean received;
    
    public FileChunk() {}
    
    public FileChunk(Long id, String fileId, Integer chunkNumber, Integer totalChunks, 
                     byte[] data, String checksum, Boolean received) {
        this.id = id;
        this.fileId = fileId;
        this.chunkNumber = chunkNumber;
        this.totalChunks = totalChunks;
        this.data = data;
        this.checksum = checksum;
        this.received = received;
    }
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    
    public Integer getChunkNumber() { return chunkNumber; }
    public void setChunkNumber(Integer chunkNumber) { this.chunkNumber = chunkNumber; }
    
    public Integer getTotalChunks() { return totalChunks; }
    public void setTotalChunks(Integer totalChunks) { this.totalChunks = totalChunks; }
    
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
    
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    
    public Boolean getReceived() { return received; }
    public void setReceived(Boolean received) { this.received = received; }
}
