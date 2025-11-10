package com.recipe.ai.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing time values from recipe data.
 * Handles various time formats (e.g., "45 minutes", "1 hour 30 minutes", numeric values).
 */
public class TimeParsingUtils {

    private static final Logger log = LoggerFactory.getLogger(TimeParsingUtils.class);

    // Private constructor prevents instantiation
    private TimeParsingUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Minimal time-constraint checks. Returns an empty list when no violations.
     * If maxTotalMinutes is null, this is a no-op (empty list returned).
     */
    public static List<String> runTimeConstraintChecks(Map<String, Object> recipeObj, Integer maxTotalMinutes) {
        List<String> violations = new ArrayList<>();
        if (maxTotalMinutes == null) return violations;

        // 1) Prefer an explicit numeric field returned by the model
        Object etm = recipeObj.get("estimatedTimeMinutes");
        if (etm instanceof Number) {
            int est = ((Number) etm).intValue();
            if (est > maxTotalMinutes) {
                violations.add(String.format("Estimated total time %d minutes exceeds maximum allowed %d minutes", est, maxTotalMinutes));
                return violations;
            }
            return violations;
        }

        // 2) Fallback: try to parse prepTime string like '45 minutes' or '1 hour 30 minutes'
        Object pt = recipeObj.get("prepTime");
        if (pt instanceof String) {
            Integer parsed = parseMinutesFromString((String) pt);
            if (parsed != null && parsed > maxTotalMinutes) {
                violations.add(String.format("Parsed prepTime %d minutes exceeds maximum allowed %d minutes", parsed, maxTotalMinutes));
                return violations;
            }
        }

        // No explicit violation found
        return violations;
    }

    /**
     * Extract first reasonable minute estimate from a human string.
     * Handles patterns like '1 hour 30 minutes' or '45 minutes' or 'about 20-30 mins'.
     */
    public static Integer parseMinutesFromString(String s) {
        if (s == null) return null;
        try {
            String low = s.toLowerCase();
            // handle patterns like '1 hour 30 minutes' or '45 minutes' or 'about 20-30 mins'
            int minutes = 0;
            Matcher mHour = Pattern.compile("(\\d+)\\s*hour").matcher(low);
            if (mHour.find()) {
                minutes += Integer.parseInt(mHour.group(1)) * 60;
            }
            Matcher mMin = Pattern.compile("(\\d+)\\s*(?:minute|min)s?").matcher(low);
            if (mMin.find()) {
                minutes += Integer.parseInt(mMin.group(1));
            }
            if (minutes > 0) return minutes;
            // fallback: capture a lone number and assume minutes
            Matcher mNum = Pattern.compile("(\\d+)").matcher(low);
            if (mNum.find()) {
                return Integer.parseInt(mNum.group(1));
            }
        } catch (Exception e) {
            log.debug("parseMinutesFromString failed for '{}': {}", s, e.getMessage());
        }
        return null;
    }

    /**
     * Parse a numeric minute value from a map field which may be a Number or a human string.
     */
    public static Integer parseMinutesFromField(Map<String, Object> m, String key) {
        if (m == null || key == null) return null;
        try {
            Object v = m.get(key);
            if (v == null) return null;
            if (v instanceof Number) return ((Number) v).intValue();
            if (v instanceof String) return parseMinutesFromString((String) v);
        } catch (Exception e) {
            log.debug("parseMinutesFromField failed for key='{}': {}", key, e.getMessage());
        }
        return null;
    }
}
