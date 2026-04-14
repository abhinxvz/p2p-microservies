package com.microservices.filetransfer.service;

import com.microservices.filetransfer.handler.SignalingSocketHandler;
import com.microservices.filetransfer.model.FileChunk;
import com.microservices.filetransfer.model.FileMetadata;
import com.microservices.filetransfer.repository.FileChunkRepository;
import com.microservices.filetransfer.repository.FileMetadataRepository;
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
    private final SignalingSocketHandler signalingSocketHandler;

    public FileTransferService(FileMetadataRepository fileMetadataRepository,
                               FileChunkRepository fileChunkRepository,
                               SignalingSocketHandler signalingSocketHandler) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.fileChunkRepository = fileChunkRepository;
        this.signalingSocketHandler = signalingSocketHandler;
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

        signalingSocketHandler.sendToUser(receiverPeerId,
            Map.of("type", "transfer-request",
                   "fileId", saved.getFileId(),
                   "fileName", saved.getFileName(),
                   "fileSize", saved.getFileSize()));

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
        updateTransferProgress(fileId);
    }

    public byte[] downloadFile(String fileId) throws IOException {
        List<FileChunk> chunks = fileChunkRepository.findByFileIdOrderByChunkNumber(fileId);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (FileChunk chunk : chunks) out.write(chunk.getData());
        return out.toByteArray();
    }

    public FileMetadata acceptTransfer(String fileId) {
        return fileMetadataRepository.findByFileId(fileId)
            .map(metadata -> {
                metadata.setStatus(FileMetadata.TransferStatus.IN_PROGRESS);
                FileMetadata saved = fileMetadataRepository.save(metadata);
                signalingSocketHandler.sendToUser(saved.getSenderPeerId(),
                    Map.of("type", "transfer-accepted", "fileId", fileId));
                return saved;
            })
            .orElseThrow(() -> new RuntimeException("File not found: " + fileId));
    }

    public FileMetadata rejectTransfer(String fileId) {
        return fileMetadataRepository.findByFileId(fileId)
            .map(metadata -> {
                metadata.setStatus(FileMetadata.TransferStatus.CANCELLED);
                FileMetadata saved = fileMetadataRepository.save(metadata);
                signalingSocketHandler.sendToUser(saved.getSenderPeerId(),
                    Map.of("type", "transfer-rejected", "fileId", fileId));
                return saved;
            })
            .orElseThrow(() -> new RuntimeException("File not found: " + fileId));
    }

    private void updateTransferProgress(String fileId) {
        fileMetadataRepository.findByFileId(fileId).ifPresent(metadata -> {
            List<FileChunk> chunks = fileChunkRepository.findByFileIdOrderByChunkNumber(fileId);
            if (chunks.isEmpty()) return;

            int totalChunks = chunks.get(0).getTotalChunks();
            int receivedChunks = chunks.size();
            long bytesTransferred = chunks.stream().mapToLong(c -> c.getData().length).sum();

            metadata.setBytesTransferred(bytesTransferred);
            metadata.setProgress((receivedChunks * 100) / totalChunks);

            if (receivedChunks >= totalChunks) {
                metadata.setStatus(FileMetadata.TransferStatus.COMPLETED);
                metadata.setCompletedAt(LocalDateTime.now());
            } else {
                metadata.setStatus(FileMetadata.TransferStatus.IN_PROGRESS);
            }

            fileMetadataRepository.save(metadata);
        });
    }

    private String calculateChecksum(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return Base64.getEncoder().encodeToString(digest.digest(data));
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
