package com.dora.jagent.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

public interface DocumentStorageService {

    String saveFile(String kbId, String documentId, MultipartFile file) throws IOException;

    Path getFilePath(String filePath);
}
