package com.somaiya.tester.service;

import com.somaiya.tester.crawler.SomaiyaSmartCrawler;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class CrawlService {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private volatile boolean running = false;
    private final StringBuilder logBuffer = new StringBuilder();

    private volatile int pages = 0;
    private volatile int links = 0;
    private volatile int ok = 0;
    private volatile int broken = 0;

    public synchronized String run(String url, String name) {
        if (running) {
            return "A crawl is already running. Please wait.\n";
        }

        running = true;
        logBuffer.setLength(0);
        pages = links = ok = broken = 0;

        executor.submit(() -> {
            try {
                SomaiyaSmartCrawler.run(url, name, this::onLog);
                log("=== COMPLETED ===");
            } catch (Exception e) {
                log("ERROR: " + e.getMessage());
            } finally {
                running = false;
            }
        });

        return "Crawl started...\n";
    }

    public Status status() {
        return new Status(running, logBuffer.toString(), pages, links, ok, broken);
    }

    private void onLog(String line) {
        log(line);

        if (line.startsWith("[PAGE]")) pages++;
        if (line.contains("→")) links++;
        if (line.endsWith("[OK]")) ok++;
        if (line.endsWith("[BROKEN]")) broken++;
    }

    private synchronized void log(String s) {
        logBuffer.append(s).append("\n");
    }

    public record Status(boolean running, String log, int pages, int links, int ok, int broken) {}
}
