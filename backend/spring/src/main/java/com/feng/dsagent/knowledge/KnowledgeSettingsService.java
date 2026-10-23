package com.feng.dsagent.knowledge;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The retrieval tuning that is actually in effect: the admin-saved row when one exists, the
 * {@code app.knowledge.*} configuration properties otherwise.
 *
 * <p>The search service reads through {@link #current()} on every query, so a saved change takes effect on the
 * next request without a restart and without touching the chunk snapshot. Reads are cheap (a cached reference);
 * the cache is reloaded after each save and is safe to reload again if the row changes behind our back.
 */
@Service
public class KnowledgeSettingsService {

    /**
     * The knobs of the text matcher. {@code titleWeightLong}/{@code titleWeightShort} are the per-occurrence
     * title bonuses for tokens of length >= 3 and below; the body score stays as computed by
     * {@link KnowledgeText#bodyScore}.
     */
    public record MatchingSettings(
        double minimumScore,
        int searchLimit,
        double titleWeightLong,
        double titleWeightShort
    ) {}

    /** Hard bounds for what an administrator may set; the service refuses anything outside them. */
    public static final double MIN_SCORE_FLOOR = 0.0;
    public static final double MIN_SCORE_CEILING = 100.0;
    public static final int SEARCH_LIMIT_FLOOR = 1;
    public static final int SEARCH_LIMIT_CEILING = 12;
    public static final double TITLE_WEIGHT_FLOOR = 0.5;
    public static final double TITLE_WEIGHT_CEILING = 20.0;

    private final JdbcTemplate jdbc;
    private final KnowledgeProperties defaults;
    private final AtomicReference<MatchingSettings> cache = new AtomicReference<>();

    public KnowledgeSettingsService(JdbcTemplate jdbc, KnowledgeProperties defaults) {
        this.jdbc = jdbc;
        this.defaults = defaults;
    }

    /** The settings in effect right now: the saved row, or the configuration defaults when nobody saved yet. */
    public MatchingSettings current() {
        MatchingSettings cached = cache.get();
        if (cached != null) {
            return cached;
        }
        MatchingSettings loaded = load();
        cache.set(loaded);
        return loaded;
    }

    /** True when an administrator has saved a row at all; the console shows whether defaults are in effect. */
    public boolean customized() {
        return !jdbc.query("SELECT id FROM knowledge_settings WHERE id = 1", (row, index) -> 1).isEmpty();
    }

    /** Persists a new row (there is only ever id = 1) and makes it visible to the next search. */
    public void save(MatchingSettings settings, long editorUserId) {
        validate(settings);
        jdbc.update("DELETE FROM knowledge_settings WHERE id = 1");
        jdbc.update(
            "INSERT INTO knowledge_settings (id, minimum_score, search_limit, title_weight_long, title_weight_short, updated_by)"
                + " VALUES (1, ?, ?, ?, ?, ?)",
            settings.minimumScore(), settings.searchLimit(), settings.titleWeightLong(), settings.titleWeightShort(),
            editorUserId
        );
        cache.set(settings);
    }

    /** Drops the saved row; the next search falls back to the configuration defaults. */
    public boolean resetToDefaults() {
        int removed = jdbc.update("DELETE FROM knowledge_settings WHERE id = 1");
        cache.set(null);
        return removed > 0;
    }

    /** The configuration defaults, shown in the console so an admin sees what a reset would restore. */
    public MatchingSettings defaults() {
        return new MatchingSettings(
            defaults.minimumScore(),
            defaults.searchLimit() > 0 ? defaults.searchLimit() : 6,
            5.0,
            3.0
        );
    }

    private MatchingSettings load() {
        List<MatchingSettings> rows = jdbc.query(
            "SELECT minimum_score, search_limit, title_weight_long, title_weight_short FROM knowledge_settings WHERE id = 1",
            (row, index) -> new MatchingSettings(
                row.getDouble("minimum_score"),
                row.getInt("search_limit"),
                row.getDouble("title_weight_long"),
                row.getDouble("title_weight_short")
            )
        );
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        // No saved row: the configuration properties are the whole story, including the historical title weights.
        return defaults();
    }

    /** Refuses out-of-range tuning; shared by saves and by the console's test-search overrides. */
    public static void validate(MatchingSettings settings) {
        if (settings.minimumScore() < MIN_SCORE_FLOOR || settings.minimumScore() > MIN_SCORE_CEILING) {
            throw new IllegalArgumentException("minimumScore out of range");
        }
        if (settings.searchLimit() < SEARCH_LIMIT_FLOOR || settings.searchLimit() > SEARCH_LIMIT_CEILING) {
            throw new IllegalArgumentException("searchLimit out of range");
        }
        if (settings.titleWeightLong() < TITLE_WEIGHT_FLOOR || settings.titleWeightLong() > TITLE_WEIGHT_CEILING
            || settings.titleWeightShort() < TITLE_WEIGHT_FLOOR || settings.titleWeightShort() > TITLE_WEIGHT_CEILING) {
            throw new IllegalArgumentException("title weight out of range");
        }
    }
}
