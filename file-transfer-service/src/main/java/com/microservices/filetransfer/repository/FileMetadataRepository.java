package com.microservices.filetransfer.repository;

import com.microservices.filetransfer.model.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {
    Optional<FileMetadata> findByFileId(String fileId);
    List<FileMetadata> findBySenderPeerId(String peerId);
    List<FileMetadata> findByReceiverPeerId(String peerId);
    List<FileMetadata> findByStatus(FileMetadata.TransferStatus status);
}
