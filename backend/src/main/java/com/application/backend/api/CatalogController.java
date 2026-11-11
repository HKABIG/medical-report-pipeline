package com.application.backend.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.application.backend.catalog.CatalogService;
import com.fasterxml.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService catalogs;

    public CatalogController(CatalogService catalogs) {
        this.catalogs = catalogs;
    }

    @GetMapping
    public JsonNode catalog() {
        return catalogs.current().raw();
    }
}
