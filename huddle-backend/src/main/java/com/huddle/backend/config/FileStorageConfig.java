package com.huddle.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class FileStorageConfig {

    @Value("${app.file.storage-path:./uploads}")
    private String storagePath;

    public Path getStorageRoot() {
        return Paths.get(storagePath).toAbsolutePath().normalize();
    }
}
