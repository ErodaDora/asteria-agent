package com.dora.jagent.service.impl;

import com.dora.jagent.service.DocumentStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class DocumentStorageServiceImpl implements DocumentStorageService {

    @Value("${knowledge.storage.base-path:./data/knowledge-documents}")
    private String baseStoragePath;

    @Override
    public String saveFile(String kbId, String documentId, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("uploaded file is empty");
        }

        Path kbDir = Paths.get(baseStoragePath, kbId);
        Path documentDir = kbDir.resolve(documentId);
        Files.createDirectories(documentDir);

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String uniqueFilename = UUID.randomUUID() + extension;
        Path targetPath = documentDir.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        String relativePath = Paths.get(kbId, documentId, uniqueFilename).toString().replace("\\", "/");
        log.info("knowledge document saved: kbId={}, documentId={}, path={}", kbId, documentId, relativePath);
        return relativePath;
    }

    @Override
    public Path getFilePath(String filePath) {
        return Paths.get(baseStoragePath, filePath);
    }
}
