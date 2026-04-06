package com.microservices.filetransfer.controller;

import com.microservices.filetransfer.dto.TransferInitRequest;
import com.microservices.filetransfer.model.FileMetadata;
import com.microservices.filetransfer.service.FileTransferService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@RestController
@RequestMapping("/api/transfers")
@CrossOrigin(origins = "*")
public class FileTransferController {
    
    private final FileTransferService fileTransferService;
    
    public FileTransferController(FileTransferService fileTransferService) {
        this.fileTransferService = fileTransferService;
    }
    
    @PostMapping("/initiate")
    public ResponseEntity<FileMetadata> initiateTransfer(@RequestBody TransferInitRequest request) {
        FileMetadata metadata = fileTransferService.initiateTransfer(
            request.getFileName(),
            request.getFileSize(),
            request.getFileType(),
            request.getSenderPeerId(),
            request.getReceiverPeerId()
        );
        return ResponseEntity.ok(metadata);
    }
    
    @PostMapping("/upload-chunk")
    public ResponseEntity<String> uploadChunk(
            @RequestParam String fileId,
            @RequestParam Integer chunkNumber,
            @RequestParam Integer totalChunks,
            @RequestParam("file") MultipartFile file) {
        try {
            fileTransferService.uploadChunk(fileId, chunkNumber, totalChunks, file.getBytes());
            return ResponseEntity.ok("Chunk uploaded successfully");
        } catch (IOException | NoSuchAlgorithmException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to upload chunk: " + e.getMessage());
        }
    }
    
    @GetMapping("/download/{fileId}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String fileId) {
        try {
            byte[] fileData = fileTransferService.downloadFile(fileId);
            
            return fileTransferService.getTransferStatus(fileId)
                .map(metadata -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                    headers.setContentDispositionFormData("attachment", metadata.getFileName());
                    return new ResponseEntity<>(fileData, headers, HttpStatus.OK);
                })
                .orElse(ResponseEntity.notFound().build());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/{fileId}/accept")
    public ResponseEntity<FileMetadata> acceptTransfer(@PathVariable String fileId) {
        FileMetadata metadata = fileTransferService.acceptTransfer(fileId);
        return ResponseEntity.ok(metadata);
    }
    
    @PostMapping("/{fileId}/reject")
    public ResponseEntity<FileMetadata> rejectTransfer(@PathVariable String fileId) {
        FileMetadata metadata = fileTransferService.rejectTransfer(fileId);
        return ResponseEntity.ok(metadata);
    }
    
    @GetMapping("/sender/{peerId}")
    public ResponseEntity<List<FileMetadata>> getTransfersBySender(@PathVariable String peerId) {
        return ResponseEntity.ok(fileTransferService.getTransfersBySender(peerId));
    }
    
    @GetMapping("/receiver/{peerId}")
    public ResponseEntity<List<FileMetadata>> getTransfersByReceiver(@PathVariable String peerId) {
        return ResponseEntity.ok(fileTransferService.getTransfersByReceiver(peerId));
    }
    
    @GetMapping("/status/{fileId}")
    public ResponseEntity<FileMetadata> getTransferStatus(@PathVariable String fileId) {
        return fileTransferService.getTransferStatus(fileId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
