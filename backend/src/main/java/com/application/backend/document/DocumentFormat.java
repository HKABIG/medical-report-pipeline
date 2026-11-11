package com.application.backend.document;

/** How a document reached the pipeline. Only TEXT has offsets a reviewer can check. */
public enum DocumentFormat {
    /** A .txt file exists; offsets refer to it. */
    TEXT,
    /** Only a PDF exists and it has no text layer, for example a scanned image. */
    IMAGE_ONLY_PDF
}
