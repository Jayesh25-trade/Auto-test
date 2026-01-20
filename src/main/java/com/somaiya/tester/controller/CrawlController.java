package com.somaiya.tester.controller;

import com.somaiya.tester.service.CrawlService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
public class CrawlController {

    private final CrawlService service;

    public CrawlController(CrawlService service) {
        this.service = service;
    }

    @PostMapping("/crawl")
    public String crawl(@RequestBody CrawlRequest req) {
        return service.run(req.url, req.name);
    }

    @GetMapping("/status")
    public CrawlService.Status status() {
        return service.status();
    }

    @GetMapping("/download/{file}")
    public ResponseEntity<Resource> download(@PathVariable String file) throws Exception {
        Path path = Paths.get(file);
        Resource res = new UrlResource(path.toUri());

        if (!res.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + path.getFileName().toString() + "\"")
                .body(res);
    }

    static class CrawlRequest {
        public String url;
        public String name;
    }
}
