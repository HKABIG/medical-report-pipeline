package com.application.backend.catalog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.springframework.stereotype.Service;

import com.application.backend.config.AppProperties;

/** The configured catalog, re-read when the file changes so a catalog edit shows up without a restart. */
@Service
public class CatalogService {

    private final CatalogLoader loader;
    private final Path path;
    private Catalog cached;
    private FileTime cachedAt;

    public CatalogService(CatalogLoader loader, AppProperties properties) {
        this.loader = loader;
        this.path = properties.catalogPath();
    }

    public synchronized Catalog current() {
        FileTime modified = lastModified();
        if (cached == null || !modified.equals(cachedAt)) {
            cached = loader.load(path);
            cachedAt = modified;
        }
        return cached;
    }

    private FileTime lastModified() {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read catalog " + path, e);
        }
    }
}
