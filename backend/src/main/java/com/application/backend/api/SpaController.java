package com.application.backend.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Lets the bundled UI handle its own routes on a page reload. */
@Controller
public class SpaController {

    @GetMapping({"/documents/**", "/duplicates/**"})
    public String index() {
        return "forward:/index.html";
    }
}
