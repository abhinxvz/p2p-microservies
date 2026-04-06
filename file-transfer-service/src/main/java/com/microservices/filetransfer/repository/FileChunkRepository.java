package com.microservices.filetransfer.repository;

import com.microservices.filetransfer.model.FileChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileChunkRepository extends JpaRepository<FileChunk, Long> {
    List<FileChunk> findByFileIdOrderByChunkNumber(String fileId);
    Optional<FileChunk> findByFileIdAndChunkNumber(String fileId, Integer chunkNumber);
    void deleteByFileId(String fileId);
}
