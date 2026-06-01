package dev.salocin.simpleflipper.api;

import com.google.gson.Gson;
import dev.salocin.simpleflipper.SimpleFlipper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Poller for the public, keyless Hypixel Bazaar endpoint — enough live market data to
 * tell whether one of our placed buy orders has been out-bid.
 *
 * <p><b>Snapshot-aligned polling</b> (the technique from BazaarUtils): the bazaar API only
 * refreshes its data about every 20s, stamping each response with {@code lastUpdated}. A blind
 * fixed-interval poll can land just before a refresh and be ~20s stale — stale enough that an
 * outbid order still looks like the top of book (exactly the bug we hit). Instead, after each
 * fetch we read {@code lastUpdated} and schedule the NEXT fetch for {@code lastUpdated + 20s +
 * 0.5s} — i.e. right after the server is expected to publish the next snapshot. That keeps our
 * data within ~1s of fresh, matching BazaarUtils. If the snapshot hasn't advanced yet we back
 * off briefly rather than hammer the endpoint.
 *
 * <p>Read-only, on a daemon background thread; the whole map is swapped atomically.
 */
public final class BazaarApi {
    private static final String ENDPOINT = "https://api.hypixel.net/v2/skyblock/bazaar";
    private static final Gson GSON = new Gson();

    /** Bazaar snapshots refresh ~every 20s; aim to fetch just after the next one. */
    private static final long BASE_INTERVAL_MS = 20_000;
    private static final long POST_OFFSET_MS = 500;     // land just after the expected new snapshot
    private static final long STALE_BACKOFF_MS = 750;   // snapshot hasn't advanced yet → retry soon
    private static final long FAILURE_RETRY_MS = 1_000; // network/parse error → retry

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private volatile Map<String, Product> byId = Map.of();
    private volatile long lastFetchMillis = 0;
    private volatile long lastSnapshotTs = -1;   // server's lastUpdated from the most recent reply
    private volatile boolean logged = false;
    private ScheduledExecutorService scheduler;

    /** Begin snapshot-aligned background polling. (intervalSeconds kept for API compatibility; ignored.) */
    public synchronized void start(int intervalSeconds) {
        if (scheduler != null) { SimpleFlipper.LOGGER.info("[api] poller already running"); return; }
        SimpleFlipper.LOGGER.info("[api] starting snapshot-aligned poller -> {}", ENDPOINT);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SimpleFlipper-Poller");
            t.setDaemon(true);
            return t;
        });
        scheduleFetch(0);
    }

    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void scheduleFetch(long delayMs) {
        ScheduledExecutorService s = scheduler;
        if (s == null) return;
        s.schedule(this::fetchSafely, Math.max(0, delayMs), TimeUnit.MILLISECONDS);
    }

    private void fetchSafely() {
        // Catch Throwable: a scheduled task that throws an Error is silently dropped and never
        // reschedules — that's the "no data, no logs" failure mode. Catching it keeps us alive.
        long nextDelay = FAILURE_RETRY_MS;
        try {
            nextDelay = fetch();
        } catch (Throwable e) {
            SimpleFlipper.LOGGER.warn("[api] bazaar fetch failed: {}: {}",
                    e.getClass().getSimpleName(), e.getMessage());
        } finally {
            scheduleFetch(nextDelay);   // self-reschedule (snapshot-aligned), so the poll never stops
        }
    }

    /** Fetch once; returns the delay (ms) until the next fetch should run. */
    private long fetch() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .header("User-Agent", "SimpleFlipper/" + SimpleFlipper.VERSION)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            SimpleFlipper.LOGGER.warn("[api] bazaar API returned HTTP {}", resp.statusCode());
            return FAILURE_RETRY_MS;
        }
        BazaarData data = GSON.fromJson(resp.body(), BazaarData.class);
        if (data == null || !data.success || data.products == null) {
            SimpleFlipper.LOGGER.warn("[api] bad payload: data={} success={}",
                    data != null, data != null && data.success);
            return FAILURE_RETRY_MS;
        }
        byId = new java.util.HashMap<>(data.products);   // tolerates null values (Map.copyOf throws)
        lastFetchMillis = System.currentTimeMillis();
        if (!logged) { logged = true; SimpleFlipper.LOGGER.info("[api] first snapshot OK — {} products", byId.size()); }

        // Snapshot-aligned scheduling: aim for just after the next server snapshot.
        long snapshotTs = data.lastUpdated;
        if (snapshotTs <= 0) return BASE_INTERVAL_MS;   // no timestamp → plain interval
        if (snapshotTs == lastSnapshotTs) return STALE_BACKOFF_MS;  // server hasn't advanced yet
        lastSnapshotTs = snapshotTs;

        long now = System.currentTimeMillis();
        long target = snapshotTs + BASE_INTERVAL_MS + POST_OFFSET_MS;
        return now >= target ? STALE_BACKOFF_MS : Math.max(target - now, STALE_BACKOFF_MS);
    }

    public boolean hasData() {
        return !byId.isEmpty();
    }

    public long lastFetchMillis() {
        return lastFetchMillis;
    }

    /** Lookup by product id or display name ("Enchanted Hard Stone" -> ENCHANTED_HARD_STONE). */
    public Product get(String idOrName) {
        if (idOrName == null) return null;
        return byId.get(idOrName.toUpperCase(Locale.ROOT).trim().replace(' ', '_'));
    }
}
