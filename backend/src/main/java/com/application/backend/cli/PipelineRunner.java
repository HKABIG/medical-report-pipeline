package com.application.backend.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.application.backend.catalog.Catalog;
import com.application.backend.catalog.CatalogLoader;
import com.application.backend.config.AppProperties;
import com.application.backend.extraction.OutputStore;

/** The command-line entry point: {@code --app.pipeline.command=extract|replay}. Exits non-zero on any failure. */
@Component
@ConditionalOnProperty(name = "app.pipeline.command")
public class PipelineRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(PipelineRunner.class);

    private final AppProperties properties;
    private final CatalogLoader catalogLoader;
    private final PipelineService pipeline;
    private final OutputStore outputs;
    private int exitCode;

    public PipelineRunner(AppProperties properties, CatalogLoader catalogLoader, PipelineService pipeline,
            OutputStore outputs) {
        this.properties = properties;
        this.catalogLoader = catalogLoader;
        this.pipeline = pipeline;
        this.outputs = outputs;
    }

    @Override
    public void run(ApplicationArguments args) {
        Catalog catalog = catalogLoader.load(properties.catalogPath());
        log.info("{}: catalog {} ({} fields), output to {}", properties.pipeline().command(),
                properties.catalogPath(), catalog.fields().size(), outputs.folder());
        exitCode = pipeline.run(properties.pipeline().command(), catalog, outputs).succeeded() ? 0 : 1;
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
