package com.microservices.filetransfer.service;

import com.microservices.filetransfer.model.FileChunk;
import com.microservices.filetransfer.model.FileMetadata;
import com.microservices.filetransfer.repository.FileChunkRepository;
import com.microservices.filetransfer.repository.FileMetadataRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class FileTransferService {
    
    private final FileMetadataRepository fileMetadataRepository;
    private final FileChunkRepository fileChunkRepository;
    private final SimpMessagingTemplate messagingTemplate;
    
    private static final int CHUNK_SIZE = 1048576; // 1MB
    
    public FileTransferService(FileMetadataRepository fileMetadataRepository,
                              FileChunkRepository fileChunkRepository,
                              SimpMessagingTemplate messagingTemplate) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.fileChunkRepository = fileChunkRepository;
        this.messagingTemplate = messagingTemplate;
    }
    
    public FileMetadata initiateTransfer(String fileName, Long fileSize, String fileType,
                                        String senderPeerId, String receiverPeerId) {
        FileMetadata metadata = new FileMetadata();
        metadata.setFileId(UUID.randomUUID().toString());
        metadata.setFileName(fileName);
        metadata.setFileSize(fileSize);
        metadata.setFileType(fileType);
        metadata.setSenderPeerId(senderPeerId);
        metadata.setReceiverPeerId(receiverPeerId);
        metadata.setStatus(FileMetadata.TransferStatus.PENDING);
        metadata.setBytesTransferred(0L);
        metadata.setProgress(0);
        metadata.setCreatedAt(LocalDateTime.now());
        
        FileMetadata saved = fileMetadataRepository.save(metadata);
        
        // Notify receiver about incoming transfer
        messagingTemplate.convertAndSend(
            "/queue/transfer-request-" + receiverPeerId, 
            saved
        );
        
        return saved;
    }
    
    @Transactional
    public void uploadChunk(String fileId, Integer chunkNumber, Integer totalChunks, 
                           byte[] data) throws NoSuchAlgorithmException {
        FileChunk chunk = new FileChunk();
        chunk.setFileId(fileId);
        chunk.setChunkNumber(chunkNumber);
        chunk.setTotalChunks(totalChunks);
        chunk.setData(data);
        chunk.setChecksum(calculateChecksum(data));
        chunk.setReceived(true);
        
        fileChunkRepository.save(chunk);
        
        // Update transfer progress
        updateTransferProgress(fileId);
        
        // Notify about chunk received
        messagingTemplate.convertAndSend(
            "/topic/chunk-received-" + fileId,
            Map.of("chunkNumber", chunkNumber, "totalChunks", totalChunks)
        );
    }
    
    public byte[] downloadFile(String fileId) throws IOException {
        List<FileChunk> chunks = fileChunkRepository.findByFileIdOrderByChunkNumber(fileId);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (FileChunk chunk : chunks) {
            outputStream.write(chunk.getData());
        }
        
        return outputStream.toByteArray();
    }
    
    public FileMetadata acceptTransfer(String fileId) {
        return fileMetadataRepository.findByFileId(fileId)
            .map(metadata -> {
                metadata.setStatus(FileMetadata.TransferStatus.IN_PROGRESS);
                FileMetadata saved = fileMetadataRepository.save(metadata);
                
                // Notify sender that transfer was accepted
                messagingTemplate.convertAndSend(
                    "/queue/transfer-accepted-" + metadata.getSenderPeerId(),
                    saved
                );
                
                return saved;
            })
            .orElseThrow(() -> new RuntimeException("File not found"));
    }
    
    public FileMetadata rejectTransfer(String fileId) {
        return fileMetadataRepository.findByFileId(fileId)
            .map(metadata -> {
                metadata.setStatus(FileMetadata.TransferStatus.CANCELLED);
                FileMetadata saved = fileMetadataRepository.save(metadata);
                
                // Notify sender that transfer was rejected
                messagingTemplate.convertAndSend(
                    "/queue/transfer-rejected-" + metadata.getSenderPeerId(),
                    saved
                );
                
                return saved;
            })
            .orElseThrow(() -> new RuntimeException("File not found"));
    }
    
    private void updateTransferProgress(String fileId) {
        fileMetadataRepository.findByFileId(fileId).ifPresent(metadata -> {
            List<FileChunk> chunks = fileChunkRepository.findByFileIdOrderByChunkNumber(fileId);
            
            if (!chunks.isEmpty()) {
                int totalChunks = chunks.get(0).getTotalChunks();
                int receivedChunks = chunks.size();
                int progress = (receivedChunks * 100) / totalChunks;
                
                long bytesTransferred = chunks.stream()
                    .mapToLong(chunk -> chunk.getData().length)
                    .sum();
                
                metadata.setBytesTransferred(bytesTransferred);
                metadata.setProgress(progress);
                
                if (receivedChunks == totalChunks) {
                    metadata.setStatus(FileMetadata.TransferStatus.COMPLETED);
                    metadata.setCompletedAt(LocalDateTime.now());
                } else {
                    metadata.setStatus(FileMetadata.TransferStatus.IN_PROGRESS);
                }
                
                fileMetadataRepository.save(metadata);
                
                // Notify about progress update
                messagingTemplate.convertAndSend(
                    "/topic/transfer-progress-" + fileId,
                    metadata
                );
            }
        });
    }
    
    private String calculateChecksum(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        return Base64.getEncoder().encodeToString(hash);
    }
    
    public List<FileMetadata> getTransfersBySender(String peerId) {
        return fileMetadataRepository.findBySenderPeerId(peerId);
    }
    
    public List<FileMetadata> getTransfersByReceiver(String peerId) {
        return fileMetadataRepository.findByReceiverPeerId(peerId);
    }
    
    public Optional<FileMetadata> getTransferStatus(String fileId) {
        return fileMetadataRepository.findByFileId(fileId);
    }
}
