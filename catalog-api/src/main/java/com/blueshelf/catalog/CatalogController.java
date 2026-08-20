package com.blueshelf.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private static final Logger LOG = LoggerFactory.getLogger(CatalogController.class);
    private final CatalogRepository repo;
    /** Chaos knob: fail every Nth request (0 = never). Lets us demo resilience on the AEM side. */
    private final AtomicInteger failEvery = new AtomicInteger(0);
    private final AtomicInteger counter = new AtomicInteger();
    private volatile long delayMs = 0;

    public CatalogController(CatalogRepository repo) { this.repo = repo; }

    public record Page<T>(List<T> items, int total, int page, int size) {}

    @GetMapping("/products")
    public Page<Product> products(@RequestParam(required = false) String category,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "12") int size,
                                  @RequestParam(defaultValue = "relevance") String sort) throws InterruptedException {
        chaos();
        var stream = repo.products().stream()
                .filter(p -> category == null || category.isBlank() || p.category().equalsIgnoreCase(category))
                .filter(p -> q == null || q.isBlank() || (p.name() + " " + p.brand() + " " + p.shortDescription()).toLowerCase().contains(q.toLowerCase()));
        Comparator<Product> cmp = switch (sort) {
            case "price-asc" -> Comparator.comparingDouble(p -> p.salePrice() != null ? p.salePrice() : p.price());
            case "price-desc" -> Comparator.comparingDouble((Product p) -> p.salePrice() != null ? p.salePrice() : p.price()).reversed();
            case "rating" -> Comparator.comparingDouble(Product::rating).reversed();
            default -> Comparator.comparing(Product::sku);
        };
        List<Product> all = stream.sorted(cmp).toList();
        int from = Math.min(page * size, all.size());
        return new Page<>(all.subList(from, Math.min(from + size, all.size())), all.size(), page, size);
    }

    @GetMapping("/products/{sku}")
    public ResponseEntity<Product> product(@PathVariable String sku) throws InterruptedException {
        chaos();
        return repo.products().stream().filter(p -> p.sku().equalsIgnoreCase(sku)).findFirst()
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/categories")
    public List<Map<String, Object>> categories() {
        return repo.products().stream().map(Product::category).distinct().sorted()
                .map(c -> Map.<String, Object>of("id", c, "name", c.substring(0, 1).toUpperCase() + c.substring(1),
                        "count", repo.products().stream().filter(p -> p.category().equals(c)).count()))
                .toList();
    }

    @GetMapping("/stores")
    public List<Store> stores(@RequestParam(required = false) String zip) throws InterruptedException {
        chaos(); // fault injection must cover EVERY endpoint consumers depend on, or resilience tests lie
        // toy "nearby": same 3-digit prefix first, then the rest. Note: sorts, never filters —
        // an unknown zip still returns 5 stores; "no results" only happens when this service is down.
        return repo.stores().stream()
                .sorted(Comparator.comparing((Store s) -> zip == null || !s.zip().startsWith(zip.substring(0, Math.min(3, zip.length()))) ? 1 : 0))
                .limit(5).toList();
    }

    /** Ops/chaos endpoints: POST /api/_chaos?failEvery=3&delayMs=2000 */
    @PostMapping("/_chaos")
    public Map<String, Object> chaos(@RequestParam(defaultValue = "0") int failEvery, @RequestParam(defaultValue = "0") long delayMs) {
        this.failEvery.set(failEvery); this.delayMs = delayMs;
        LOG.warn("Chaos set: failEvery={} delayMs={}", failEvery, delayMs);
        return Map.of("failEvery", failEvery, "delayMs", delayMs);
    }

    private void chaos() throws InterruptedException {
        if (delayMs > 0) Thread.sleep(delayMs);
        int n = failEvery.get();
        if (n > 0 && counter.incrementAndGet() % n == 0) throw new IllegalStateException("chaos monkey: simulated upstream failure");
    }
}
