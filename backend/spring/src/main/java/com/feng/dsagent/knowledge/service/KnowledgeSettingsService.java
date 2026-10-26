package com.feng.dsagent.knowledge.service;


import org.springframework.stereotype.Service;
import com.feng.dsagent.knowledge.config.KnowledgeProperties;
import com.feng.dsagent.knowledge.support.KnowledgeText;
import com.feng.dsagent.knowledge.mapper.KnowledgeSettingsMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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

    private final KnowledgeSettingsMapper settings;
    private final KnowledgeProperties defaults;
    private final AtomicReference<MatchingSettings> cache = new AtomicReference<>();

    public KnowledgeSettingsService(KnowledgeSettingsMapper settings, KnowledgeProperties defaults) {
        this.settings = settings;
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
        return !settings.selectSettingsId().isEmpty();
    }

    /** Persists a new row (there is only ever id = 1) and makes it visible to the next search. */
    public void save(MatchingSettings matching, long editorUserId) {
        validate(matching);
        settings.deleteSettings();
        settings.insertSettings(
            matching.minimumScore(),
            matching.searchLimit(),
            matching.titleWeightLong(),
            matching.titleWeightShort(),
            editorUserId
        );
        cache.set(matching);
    }

    /** Drops the saved row; the next search falls back to the configuration defaults. */
    public boolean resetToDefaults() {
        int removed = settings.deleteSettings();
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
        List<MatchingSettings> rows = settings.selectMatchingSettings();
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        // No saved row: the configuration properties are the whole story, including the historical title weights.
        return defaults();
    }

    /** Refuses out-of-range tuning; shared by saves and by the console's test-search overrides. */
    public static void validate(MatchingSettings matching) {
        if (matching.minimumScore() < MIN_SCORE_FLOOR || matching.minimumScore() > MIN_SCORE_CEILING) {
            throw new IllegalArgumentException("minimumScore out of range");
        }
        if (matching.searchLimit() < SEARCH_LIMIT_FLOOR || matching.searchLimit() > SEARCH_LIMIT_CEILING) {
            throw new IllegalArgumentException("searchLimit out of range");
        }
        if (matching.titleWeightLong() < TITLE_WEIGHT_FLOOR || matching.titleWeightLong() > TITLE_WEIGHT_CEILING
            || matching.titleWeightShort() < TITLE_WEIGHT_FLOOR || matching.titleWeightShort() > TITLE_WEIGHT_CEILING) {
            throw new IllegalArgumentException("title weight out of range");
        }
    }
}
