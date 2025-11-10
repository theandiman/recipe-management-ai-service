package com.recipe.ai.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.*;

/**
 * Validates recipes against dietary preferences and allergen constraints.
 * Performs token-based matching of ingredients and instructions against known allergen keywords.
 */
@Component
public class RecipeSafetyValidator {

    // Expanded allergen and token lists (lowercase canonical forms)
    private static final Map<String, List<String>> ALLERGEN_KEYWORDS = Map.of(
        "tree-nuts", List.of("almond", "walnut", "pecan", "cashew", "hazelnut", "macadamia", "brazil nut", "pistachio"),
        "peanuts", List.of("peanut"),
        "dairy", List.of("milk", "butter", "cheese", "cream", "yogurt", "ghee", "custard", "feta", "cheddar", "parmesan", "mascarpone", "whey", "casein", "buttermilk"),
        "gluten", List.of("wheat", "flour", "barley", "rye", "seitan", "bulgur", "couscous", "malt"),
        "shellfish", List.of("shrimp", "prawn", "crab", "lobster", "mussel", "clam", "oyster", "scallop"),
        "fish", List.of("salmon", "tuna", "trout", "cod", "haddock", "anchovy", "sardine"),
        "soy", List.of("soy", "tofu", "edamame", "miso", "soy sauce", "tempeh")
    );

    private static final Map<String, String> SYNONYMS = Map.ofEntries(
        Map.entry("almond milk", "almond"),
        Map.entry("peanut butter", "peanut"),
        Map.entry("soy milk", "soy"),
        Map.entry("buttermilk", "dairy"),
        Map.entry("parmesan", "cheese"),
        Map.entry("feta", "cheese"),
        Map.entry("cottage cheese", "cheese"),
        Map.entry("mozzarella", "cheese"),
        Map.entry("yoghurt", "yogurt"),
        Map.entry("eggs", "egg"),
        Map.entry("egg yolk", "egg"),
        Map.entry("egg white", "egg"),
        Map.entry("ground beef", "beef")
    );

    private static final List<String> VEGAN_FORBIDDEN = List.of(
        "chicken", "beef", "pork", "bacon", "egg", "fish", "salmon", "tuna", 
        "shrimp", "lamb", "butter", "milk", "cheese", "yogurt", "honey", "gelatin"
    );

    private static final List<String> VEGETARIAN_FORBIDDEN = List.of(
        "chicken", "beef", "pork", "bacon", "lamb", "shrimp", "crab", "lobster"
    );

    /**
     * Basic safety checks for generated recipe JSON.
     * Returns a list of human-readable violation messages; empty list means no violations.
     */
    public List<String> runSafetyChecksOnRecipe(
            Map<String, Object> recipeObj, 
            List<String> dietaryPreferences, 
            List<String> allergies) {
        
        List<String> violations = new ArrayList<>();

        // Build allergy token set from declared allergies
        Set<String> allergyTokens = buildAllergyTokens(allergies);

        // Extract tokens from ingredients and instructions
        Set<String> ingredientTokens = extractTokensFromList(recipeObj.get("ingredients"));
        Set<String> instructionTokens = extractTokensFromList(recipeObj.get("instructions"));

        // Check allergies: intersection of declared allergy tokens vs extracted tokens
        for (String token : allergyTokens) {
            if (ingredientTokens.contains(token) || instructionTokens.contains(token)) {
                violations.add(String.format("Detected allergen token '%s' in recipe", token));
            }
        }

        // Dietary preference checks: vegan/vegetarian/gluten_free
        if (dietaryPreferences != null) {
            for (String dp : dietaryPreferences) {
                if (dp == null) continue;
                String d = dp.trim().toLowerCase();
                
                if (d.equals("vegan")) {
                    for (String t : VEGAN_FORBIDDEN) {
                        if (ingredientTokens.contains(t) || instructionTokens.contains(t)) {
                            violations.add("Contains non-vegan ingredient: " + t);
                        }
                    }
                } else if (d.equals("vegetarian")) {
                    for (String t : VEGETARIAN_FORBIDDEN) {
                        if (ingredientTokens.contains(t) || instructionTokens.contains(t)) {
                            violations.add("Contains non-vegetarian ingredient: " + t);
                        }
                    }
                } else if (d.equals("gluten_free") || d.equals("gluten-free")) {
                    List<String> glutenTokens = ALLERGEN_KEYWORDS.getOrDefault("gluten", List.of());
                    for (String t : glutenTokens) {
                        if (ingredientTokens.contains(t) || instructionTokens.contains(t)) {
                            violations.add("May contain gluten ingredient: " + t);
                        }
                    }
                }
            }
        }

        // Deduplicate and return
        Set<String> dedup = new LinkedHashSet<>(violations);
        return new ArrayList<>(dedup);
    }

    private Set<String> buildAllergyTokens(List<String> allergies) {
        Set<String> allergyTokens = new HashSet<>();
        if (allergies != null) {
            for (String a : allergies) {
                if (a == null) continue;
                String an = a.trim().toLowerCase();
                if (an.contains("nut")) {
                    allergyTokens.addAll(ALLERGEN_KEYWORDS.getOrDefault("tree-nuts", List.of()));
                    allergyTokens.addAll(ALLERGEN_KEYWORDS.getOrDefault("peanuts", List.of()));
                } else if (an.equals("dairy") || an.equals("milk")) {
                    allergyTokens.addAll(ALLERGEN_KEYWORDS.getOrDefault("dairy", List.of()));
                } else if (an.equals("gluten") || an.equals("wheat")) {
                    allergyTokens.addAll(ALLERGEN_KEYWORDS.getOrDefault("gluten", List.of()));
                } else if (ALLERGEN_KEYWORDS.containsKey(an)) {
                    allergyTokens.addAll(ALLERGEN_KEYWORDS.getOrDefault(an, List.of()));
                } else {
                    allergyTokens.add(an);
                }
            }
        }
        return allergyTokens;
    }

    private Set<String> extractTokensFromList(Object obj) {
        Set<String> tokens = new HashSet<>();
        if (obj instanceof List) {
            for (Object item : (List<?>) obj) {
                tokens.addAll(extractTokens(String.valueOf(item)));
            }
        }
        return tokens;
    }

    /**
     * Helper: normalize + tokenize text into canonical tokens
     */
    private Set<String> extractTokens(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) return out;
        
        try {
            // normalize diacritics
            String norm = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase();
            
            // remove punctuation except internal hyphens (keep letters, numbers, hyphens and whitespace)
            norm = norm.replaceAll("[^\\p{Alnum}\\-\\s]+", " ");
            
            // collapse whitespace
            norm = norm.replaceAll("\\s+", " ").trim();
            if (norm.isEmpty()) return out;

            // tokenization: split by spaces
            String[] parts = norm.split(" ");
            for (int i = 0; i < parts.length; ) {
                String token = parts[i];
                if (token.length() <= 2) {
                    i++;
                    continue; // skip short words
                }
                
                // join two-word phrases where applicable (e.g., 'almond milk')
                if (i + 1 < parts.length) {
                    String two = token + " " + parts[i + 1];
                    if (SYNONYMS.containsKey(two)) {
                        out.add(SYNONYMS.get(two));
                        i += 2; // skip both tokens
                        continue;
                    }
                }
                
                // map single-word synonyms
                if (SYNONYMS.containsKey(token)) {
                    out.add(SYNONYMS.get(token));
                } else {
                    // naive singularization: drop trailing s for plurals (eggs -> egg)
                    if (token.endsWith("s") && token.length() > 3) {
                        token = token.substring(0, token.length() - 1);
                    }
                    out.add(token);
                }
                i++;
            }
        } catch (Exception e) {
            // fallback: add raw lowercased input
            if (!s.isBlank()) {
                out.add(s.toLowerCase());
            }
        }
        return out;
    }
}
