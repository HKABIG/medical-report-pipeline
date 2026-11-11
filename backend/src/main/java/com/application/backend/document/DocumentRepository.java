package com.application.backend.document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.application.backend.config.AppProperties;

/**
 * The source documents, one per id across the configured folders. A .txt file is the document; a PDF on its own
 * is accepted only to detect that it has no text layer, so the pipeline can flag it rather than guess.
 * A PDF with a text layer but no .txt is rejected, because offsets could not be checked against a file.
 */
@Repository
public class DocumentRepository {

    private static final String TXT = ".txt";
    private static final String PDF = ".pdf";

    private final List<Path> folders;

    @Autowired
    public DocumentRepository(AppProperties properties) {
        this(properties.documentPaths());
    }

    public DocumentRepository(List<Path> folders) {
        this.folders = List.copyOf(folders);
    }

    public List<String> ids() {
        return List.copyOf(index().keySet());
    }

    public Optional<SourceDocument> find(String id) {
        return Optional.ofNullable(index().get(id)).map(files -> load(id, files));
    }

    public List<SourceDocument> all() {
        return index().entrySet().stream().map(entry -> load(entry.getKey(), entry.getValue())).toList();
    }

    private SourceDocument load(String id, DocumentFiles files) {
        if (files.txt() != null) {
            return SourceDocument.ofText(id, readUtf8(files.txt()));
        }
        String pdfText = extractPdfText(files.pdf());
        if (!pdfText.isBlank()) {
            throw new IllegalStateException("Document " + id + " has a text layer but no .txt file; export it to "
                    + id + TXT + " so provenance offsets refer to a reviewable file");
        }
        return new SourceDocument(id, DocumentFormat.IMAGE_ONLY_PDF, Optional.empty());
    }

    private Map<String, DocumentFiles> index() {
        Map<String, DocumentFiles> byId = new TreeMap<>();
        for (Path folder : folders) {
            if (!Files.isDirectory(folder)) {
                throw new UncheckedIOException(new IOException("Document folder not found: " + folder));
            }
            try (Stream<Path> entries = Files.list(folder)) {
                entries.filter(Files::isRegularFile).forEach(path -> {
                    String name = path.getFileName().toString();
                    if (name.endsWith(TXT)) {
                        String id = name.substring(0, name.length() - TXT.length());
                        byId.merge(id, new DocumentFiles(path, null), (a, b) -> a.withTxt(path));
                    } else if (name.endsWith(PDF)) {
                        String id = name.substring(0, name.length() - PDF.length());
                        byId.merge(id, new DocumentFiles(null, path), (a, b) -> a.withPdf(path));
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot list " + folder, e);
            }
        }
        return byId;
    }

    private static String readUtf8(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }

    private static String extractPdfText(Path pdf) {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + pdf, e);
        }
    }

    private record DocumentFiles(Path txt, Path pdf) {
        DocumentFiles withTxt(Path path) {
            return new DocumentFiles(path, pdf);
        }

        DocumentFiles withPdf(Path path) {
            return new DocumentFiles(txt, path);
        }
    }
}
