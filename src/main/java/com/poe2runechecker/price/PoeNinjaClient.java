package com.poe2runechecker.price;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Тянет цены с poe.ninja PoE2 и кэширует их.
 *
 * Реальный API (подсмотрен через DevTools на poe.ninja/poe2):
 *   GET https://poe.ninja/poe2/api/economy/exchange/current/overview?league=<L>&type=Currency
 *
 * Формат ответа:
 *   {
 *     "core":  { "primary": "divine", "secondary": "chaos",
 *                "rates": { "exalted": 967.3, "chaos": 2.96 } },   // единиц в 1 primary
 *     "lines": [ { "id": "gemcutters-prism", "primaryValue": 0.2112, ... }, ... ],
 *     "items": [ { "id": "...", "name": "Gemcutter's Prism", ... }, ... ]
 *   }
 *
 * primaryValue — цена в "primary"-валюте (divine). Мы конвертируем в выбранную DisplayUnit.
 */
public class PoeNinjaClient {

    /** В какой валюте показывать итог. */
    public enum DisplayUnit { DIVINE, EXALTED, CHAOS }

    private static final String BASE =
            "https://poe.ninja/poe2/api/economy/exchange/current/overview";

    // Категории результатов комбинаций.
    // Все категории poe.ninja PoE2, которые могут быть результатом рунных комбинаций.
    // (type-имена подсмотрены через DevTools; "Fragments"/"Runes" и т.п. — множественное!)
    private static final String[] TYPES = {
            "Currency",            // орбы, осколки
            "Fragments",           // фрагменты
            "Runes",               // руны (Masterwork, Adept, Ancient...)
            "SoulCores",           // soul cores
            "Essences",            // эссенции
            "Idols",               // идолы
            "LineageSupportGems",  // lineage support gems
            "Verisium",            // сплавы (Mystic Alloy, Adaptive Alloy...)
            "Abyss",               // abyssal bones
            "Breach",              // breach catalysts/splinters
            "Expedition",          // expedition флюксы/саги
            "Delirium",            // liquid emotions
            "Ritual"               // omens
    };

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    // normalize(name) -> цена в primary (divine)
    private final Map<String, Double> priceInPrimary = new HashMap<>();
    // normalize(name) -> исходное "красивое" имя
    private final Map<String, String> normToName = new HashMap<>();
    private double exaltedPerPrimary = 0;
    private double chaosPerPrimary = 0;

    private Instant lastFetch = Instant.EPOCH;
    private final Duration cacheTtl = Duration.ofMinutes(15); // обновляем не чаще раза в 15 мин
    private final Path cacheDir = Path.of(System.getProperty("cacheDir", "cache"));

    private volatile String league;
    private final DisplayUnit displayUnit;

    public PoeNinjaClient(String league, DisplayUnit unit) {
        this.league = (league == null || league.isBlank()) ? "Standard" : league;
        this.displayUnit = unit == null ? DisplayUnit.EXALTED : unit;
    }

    public String league() { return league; }

    /** Сменить лигу на лету — сбрасывает кэш, цены перетянутся при следующем запросе. */
    public synchronized void setLeague(String newLeague) {
        if (newLeague == null || newLeague.isBlank() || newLeague.equals(league)) return;
        this.league = newLeague;
        priceInPrimary.clear();
        normToName.clear();
        lastFetch = Instant.EPOCH;
    }

    /** Цена за 1 шт. в выбранной DisplayUnit, 0 если не нашли. */
    public synchronized double priceOf(String itemName) {
        ensureFresh();
        double primary = priceInPrimary.getOrDefault(normalize(itemName), 0.0);
        return switch (displayUnit) {
            case DIVINE  -> primary;
            case EXALTED -> primary * exaltedPerPrimary;
            case CHAOS   -> primary * chaosPerPrimary;
        };
    }

    public DisplayUnit unit() { return displayUnit; }

    /** Результат fuzzy-сопоставления распознанного OCR-имени с реальным предметом. */
    public record Match(String name, double price, double score) {}

    /**
     * Находит ближайший по похожести реальный предмет к (часто кривому) OCR-имени.
     * Возвращает null, если ничего достаточно похожего нет (порог score).
     */
    public synchronized Match bestMatch(String ocrName) {
        ensureFresh();
        String q = normalize(ocrName);
        if (q.isBlank() || priceInPrimary.isEmpty()) return null;

        String bestKey = null;
        double best = 0;
        for (String key : priceInPrimary.keySet()) {
            double s = similarity(q, key);
            if (s > best) { best = s; bestKey = key; }
        }
        if (bestKey == null || best < 0.55) return null; // слишком непохоже — пропускаем
        String name = normToName.getOrDefault(bestKey, bestKey);
        return new Match(name, priceOf(name), best);
    }

    /** 1.0 = идентичны, 0 = совсем разные. На основе расстояния Левенштейна. */
    private static double similarity(String a, String b) {
        int max = Math.max(a.length(), b.length());
        if (max == 0) return 1;
        return 1.0 - (double) levenshtein(a, b) / max;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    private boolean isFresh() {
        return !priceInPrimary.isEmpty()
                && Duration.between(lastFetch, Instant.now()).compareTo(cacheTtl) < 0;
    }

    private void ensureFresh() {
        if (isFresh()) return;

        // 1) пробуем диск (сохраняется между запусками программы)
        if (priceInPrimary.isEmpty()) loadFromDisk();
        if (isFresh()) {
            System.out.println("[cache] using disk cache, age="
                    + Duration.between(lastFetch, Instant.now()).toMinutes() + " min");
            return;
        }

        // 2) кэш устарел/пуст — тянем с poe.ninja и сохраняем
        try {
            Map<String, Double> newPrices = new HashMap<>();
            Map<String, String> newNames = new HashMap<>();
            for (String type : TYPES) fetchOverview(type, newPrices, newNames);
            if (!newPrices.isEmpty()) {
                priceInPrimary.clear(); priceInPrimary.putAll(newPrices);
                normToName.clear();     normToName.putAll(newNames);
                lastFetch = Instant.now();
                saveToDisk();
                System.out.println("[cache] fetched " + newPrices.size() + " prices from poe.ninja");
            }
        } catch (Exception e) {
            System.err.println("[poe.ninja] fetch failed: " + e.getMessage()
                    + " (using cached: " + priceInPrimary.size() + ")");
        }
    }

    // ---- персистентный кэш на диск ----

    /** Сериализуемый снимок кэша одной лиги. */
    public static class CacheFile {
        public long fetchedAtEpochMs;
        public String league;
        public double exaltedPerPrimary;
        public double chaosPerPrimary;
        public Map<String, Double> priceInPrimary;
        public Map<String, String> normToName;
    }

    private Path cachePath() {
        String slug = league.toLowerCase().replaceAll("[^a-z0-9]+", "_");
        return cacheDir.resolve("prices_v4_" + slug + ".json");
    }

    private void loadFromDisk() {
        Path p = cachePath();
        if (!Files.exists(p)) return;
        try {
            CacheFile c = mapper.readValue(p.toFile(), CacheFile.class);
            if (c.priceInPrimary == null || c.priceInPrimary.isEmpty()) return;
            priceInPrimary.clear(); priceInPrimary.putAll(c.priceInPrimary);
            normToName.clear();     normToName.putAll(c.normToName);
            exaltedPerPrimary = c.exaltedPerPrimary;
            chaosPerPrimary   = c.chaosPerPrimary;
            lastFetch = Instant.ofEpochMilli(c.fetchedAtEpochMs);
        } catch (Exception e) {
            System.err.println("[cache] load failed: " + e.getMessage());
        }
    }

    private void saveToDisk() {
        try {
            Files.createDirectories(cacheDir);
            CacheFile c = new CacheFile();
            c.fetchedAtEpochMs = lastFetch.toEpochMilli();
            c.league = league;
            c.exaltedPerPrimary = exaltedPerPrimary;
            c.chaosPerPrimary = chaosPerPrimary;
            c.priceInPrimary = priceInPrimary;
            c.normToName = normToName;
            mapper.writeValue(cachePath().toFile(), c);
        } catch (Exception e) {
            System.err.println("[cache] save failed: " + e.getMessage());
        }
    }

    private void fetchOverview(String type,
                               Map<String, Double> outPrices,
                               Map<String, String> outNames) throws Exception {
        String url = BASE + "?league=" + enc(league) + "&type=" + enc(type);

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "poe2-rune-checker/0.1 (personal use)")
                .header("Accept", "application/json")
                .GET().build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            System.err.println("[poe.ninja] " + type + " HTTP " + resp.statusCode());
            return;
        }

        JsonNode root = mapper.readTree(resp.body());

        // курсы конвертации (берём из любой категории — они одинаковы)
        JsonNode rates = root.path("core").path("rates");
        if (rates.has("exalted")) exaltedPerPrimary = rates.path("exalted").asDouble();
        if (rates.has("chaos"))   chaosPerPrimary   = rates.path("chaos").asDouble();

        // id -> name
        Map<String, String> idToName = new HashMap<>();
        for (JsonNode it : root.path("items")) {
            idToName.put(it.path("id").asText(), it.path("name").asText());
        }

        // id -> primaryValue, кладём по имени
        for (JsonNode line : root.path("lines")) {
            String id = line.path("id").asText();
            String name = idToName.get(id);
            if (name == null || name.isBlank()) continue;
            String norm = normalize(name);
            outPrices.put(norm, line.path("primaryValue").asDouble(0));
            outNames.put(norm, name);
        }
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase().replaceAll("[^a-z0-9 ]", "");
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
