package com.somaiya.tester.crawler;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

public class SomaiyaSmartCrawler {

    static class Row {
        String from, to, type, status;
        int depth;
    }

    static Set<String> visited = new HashSet<>();
    static List<Row> report = new ArrayList<>();
    static String baseHost;
    static String baseFileName;
    static Consumer<String> logger = System.out::println; // default = console

    // ---------- WEB ENTRY ----------
    public static void run(String startUrl, String name, Consumer<String> log) throws Exception {
        logger = log; // IMPORTANT: connect UI logger

        baseFileName = name;
        visited.clear();
        report.clear();

        File shotDir = new File("screenshots");
        if (!shotDir.exists()) shotDir.mkdirs();

        URI base = new URI(startUrl);
        baseHost = base.getHost();

        ChromeOptions opt = new ChromeOptions();
        opt.addArguments("--start-maximized");

        WebDriver driver = new ChromeDriver(opt);
        driver.get(startUrl);
        waitForLoad(driver);

        logger.accept("=== SMART FULL CRAWL STARTED ===");

        crawlPage(driver, 0);

        writeCSVs();
        driver.quit();

        logger.accept("DONE.");
        logger.accept("Valid Links  : " + baseFileName + "_valid.csv");
        logger.accept("Broken Links : " + baseFileName + "_broken.csv");
    }

    // ---------- CLI ENTRY ----------
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        System.out.print("Enter Website URL: ");
        String startUrl = sc.nextLine().trim();

        System.out.print("Enter report base name (e.g. somaiya-run1): ");
        baseFileName = sc.nextLine().trim();

        run(startUrl, baseFileName, System.out::println);
    }

    static void crawlPage(WebDriver driver, int depth) {

        String current = normalize(driver.getCurrentUrl());
        if (visited.contains(current)) return;

        visited.add(current);
        logger.accept("\n[PAGE][" + depth + "] " + current);

        openTopMenus(driver);

        List<WebElement> elements = driver.findElements(
                By.xpath("//a[@href] | //button[@onclick] | //*[@role='button' and @onclick]")
        );

        for (WebElement el : elements) {
            try {
                String href = getTarget(el);
                if (href == null) continue;

                href = href.trim();
                if (href.isEmpty()) continue;

                if (href.startsWith("javascript")
                        || href.startsWith("mailto:")
                        || href.startsWith("tel:")
                        || href.contains("(")
                        || href.startsWith("#")) {
                    continue;
                }

                Row r = new Row();
                r.from = current;
                r.to = href;
                r.depth = depth;

                scrollTo(driver, el);

                if (isPdf(href)) {
                    r.type = "PDF";
                    r.status = isAlive(href) ? "OK" : "BROKEN";
                    highlight(driver, el, "yellow");
                    log(r);
                    continue;
                }

                boolean same = isSameDomain(href);

                if (!same) {
                    r.type = "EXTERNAL";
                    r.status = isAlive(href) ? "OK" : "BROKEN";
                    highlight(driver, el, "yellow");
                    log(r);
                    continue;
                }

                r.type = "INTERNAL";
                String norm = normalize(href);

                if (visited.contains(norm)) {
                    r.status = "SKIPPED-VISITED";
                    highlight(driver, el, "yellow");
                    log(r);
                    continue;
                }

                highlight(driver, el, "red");

                ((JavascriptExecutor) driver)
                        .executeScript("window.open(arguments[0],'_blank');", href);

                List<String> tabs = new ArrayList<>(driver.getWindowHandles());
                if (tabs.size() < 2) continue;

                driver.switchTo().window(tabs.get(tabs.size() - 1));
                waitForLoad(driver);

                r.status = isBroken(driver) ? "BROKEN" : "OK";
                log(r);

                crawlPage(driver, depth + 1);

                driver.close();
                driver.switchTo().window(tabs.get(0));
                waitForLoad(driver);

            } catch (Exception ignored) {}
        }
    }

    static void openTopMenus(WebDriver driver) {
        try {
            Actions act = new Actions(driver);
            List<WebElement> menus = driver.findElements(
                    By.xpath("//header//nav//li[contains(@class,'menu') or contains(@class,'has-children')]")
            );
            for (WebElement m : menus) {
                act.moveToElement(m).perform();
                Thread.sleep(120);
            }
        } catch (Exception ignored) {}
    }

    static void scrollTo(WebDriver d, WebElement e) {
        try {
            ((JavascriptExecutor) d).executeScript(
                    "arguments[0].scrollIntoView({behavior:'smooth', block:'center'});", e);
            Thread.sleep(120);
        } catch (Exception ignored) {}
    }

    static String getTarget(WebElement el) {
        try {
            String h = el.getAttribute("href");
            if (h != null) return h;
            return el.getAttribute("onclick");
        } catch (Exception e) {
            return null;
        }
    }

    static boolean isSameDomain(String u) {
        try {
            URI uri = new URI(u);
            if (uri.getHost() == null) return false;
            return uri.getHost().equals(baseHost) ||
                    uri.getHost().endsWith("." + baseHost);
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isPdf(String u) {
        return u.toLowerCase().contains(".pdf");
    }

    static boolean isAlive(String u) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setRequestMethod("HEAD");
            c.setConnectTimeout(6000);
            return c.getResponseCode() < 400;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isBroken(WebDriver d) {
        try {
            String t = d.getTitle().toLowerCase();
            String b = d.findElement(By.tagName("body")).getText().toLowerCase();
            return t.contains("404") || b.contains("page not found") || b.length() < 80;
        } catch (Exception e) {
            return true;
        }
    }

    static void waitForLoad(WebDriver d) {
        new WebDriverWait(d, Duration.ofSeconds(15)).until(
                x -> ((JavascriptExecutor) x)
                        .executeScript("return document.readyState").equals("complete")
        );
    }

    static void highlight(WebDriver d, WebElement e, String color) {
        try {
            String c = color.equals("red") ? "red" : "gold";
            ((JavascriptExecutor) d).executeScript(
                    "arguments[0].style.outline='3px solid " + c + "';" +
                            "arguments[0].style.backgroundColor='" +
                            (color.equals("red") ? "#ffd6d6" : "#fff6b3") + "';", e);
            Thread.sleep(90);
        } catch (Exception ignored) {}
    }

    static String normalize(String u) {
        if (u == null) return "";
        return u.split("#")[0].replaceAll("/+$", "");
    }

    static void log(Row r) {
        report.add(r);
        logger.accept(" → [" + r.type + "] " + r.to + " [" + r.status + "]");
    }

    static void writeCSVs() throws Exception {
        Set<String> validSet = new HashSet<>();
        Set<String> brokenSet = new HashSet<>();

        PrintWriter ok = new PrintWriter(baseFileName + "_valid.csv");
        PrintWriter bad = new PrintWriter(baseFileName + "_broken.csv");

        ok.println("FromPage,Link,Type,Status,Depth");
        bad.println("FromPage,Link,Type,Status,Depth");

        for (Row r : report) {
            if ("OK".equals(r.status) && validSet.add(r.to)) {
                ok.println("\"" + r.from + "\",\"" + r.to + "\"," +
                        r.type + "," + r.status + "," + r.depth);
            }

            if ("BROKEN".equals(r.status) && brokenSet.add(r.to)) {
                bad.println("\"" + r.from + "\",\"" + r.to + "\"," +
                        r.type + "," + r.status + "," + r.depth);
            }
        }

        ok.close();
        bad.close();
    }
}
