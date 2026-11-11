package com.application.backend;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Serves the review API by default. With {@code --app.pipeline.command=extract|replay} it runs the extraction
 * pipeline instead, without a web server, and exits with the pipeline's status.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BackendApplication {

    private static final String PIPELINE_ARGUMENT = "--app.pipeline.command=";

    public static void main(String[] args) {
        boolean pipeline = Arrays.stream(args).anyMatch(arg -> arg.startsWith(PIPELINE_ARGUMENT));
        SpringApplicationBuilder builder = new SpringApplicationBuilder(BackendApplication.class)
                .web(pipeline ? WebApplicationType.NONE : WebApplicationType.SERVLET);
        ConfigurableApplicationContext context = builder.run(args);
        if (pipeline) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
