package com.recipe.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.Base64;

/**
 * Parses and sanitizes Gemini API responses, extracting image data and metadata.
 */
@Component
public class GeminiResponseParser {

    private static final Logger log = LoggerFactory.getLogger(GeminiResponseParser.class);

    /**
     * Extract image data URL or external image URL from a parsed Gemini response map.
     */
    public String parseImageResponseMap(Map<String, Object> resp) {
        if (resp == null) return null;
        try {
            List<?> candidates = (List<?>) resp.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Object cand0 = candidates.get(0);
                if (cand0 instanceof Map) {
                    Map<?,?> candMap = (Map<?,?>) cand0;
                    Map<?,?> content = (Map<?,?>) candMap.get("content");
                    if (content != null) {
                        List<?> parts = (List<?>) content.get("parts");
                        if (parts != null && !parts.isEmpty()) {
                            // Prefer the originally expected path when there are multiple parts
                            List<Integer> indicesToTry = new ArrayList<>();
                            if (parts.size() > 1) indicesToTry.add(1);
                            // Always try part 0 as a sensible fallback (many responses put inline data there)
                            indicesToTry.add(0);
                            // Also try any other parts if present
                            for (int i = 0; i < parts.size(); i++) {
                                if (!indicesToTry.contains(i)) indicesToTry.add(i);
                            }

                            for (int idx : indicesToTry) {
                                try {
                                    Object partObj = parts.get(idx);
                                    if (!(partObj instanceof Map)) continue;
                                    Map<?,?> p = (Map<?,?>) partObj;
                                    Object inline = p.get("inlineData");
                                    if (inline == null) inline = p.get("inline_data");
                                    if (inline instanceof Map) {
                                        Object data = ((Map<?,?>) inline).get("data");
                                        if (data instanceof String) {
                                            String base64 = (String) data;
                                            Object mm = ((Map<?,?>) inline).get("mimeType");
                                            if (mm == null) mm = ((Map<?,?>) inline).get("mime_type");
                                            String mime = mm instanceof String ? (String) mm : "image/png";
                                            log.info("Parsed inline image at part[{}] mime={} size={}", idx, mime, base64.length());
                                            return "data:" + mime + ";base64," + base64;
                                        }
                                    }
                                    Object imageUrl = p.get("imageUrl");
                                    if (imageUrl instanceof String) {
                                        log.info("Found external image URL at part[{}]: {}", idx, imageUrl);
                                        return (String) imageUrl;
                                    }
                                } catch (IndexOutOfBoundsException ioob) {
                                    // Index out of bounds while trying part indices — log at debug and continue
                                    log.debug("IndexOutOfBounds while scanning parts: {}", ioob.getMessage());
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            log.debug("Ignored exception in parseImageResponseMap: {}", ignored.getMessage());
        }
        return null;
    }

    /**
     * Helper that accepts various inline data shapes and returns a base64 string if possible.
     */
    public String tryExtractBase64FromInline(Object inline) {
        if (inline == null) return null;
        try {
            // If it's a Map with a 'data' field
            if (inline instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) inline;
                Object data = m.get("data");
                if (data instanceof String) {
                    // assume already base64
                    return (String) data;
                } else if (data instanceof List) {
                    // list of numeric bytes
                    List<?> arr = (List<?>) data;
                    byte[] bytes = new byte[arr.size()];
                    for (int i = 0; i < arr.size(); i++) {
                        Object n = arr.get(i);
                        int val = 0;
                        if (n instanceof Number) val = ((Number) n).intValue();
                        else if (n instanceof String) val = Integer.parseInt((String) n);
                        bytes[i] = (byte) val;
                    }
                    return Base64.getEncoder().encodeToString(bytes);
                }
            }
        } catch (Exception ex) {
            // Log exception for diagnostics and return null (best-effort extraction)
            log.debug("tryExtractBase64FromInline failed: {}", ex.getMessage(), ex);
        }
        return null;
    }

    /**
     * Recursively scan an object tree (maps/lists) for a large base64-like string and optional mimeType.
     * Returns a map with keys 'mime' and 'base64' when found, otherwise null.
     */
    public Map<String, String> recursiveFindBase64AndMime(Object node) {
        if (node == null) return null;
        try {
            if (node instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) node;
                // Common inline fields
                if (m.containsKey("data") && m.get("data") instanceof String) {
                    String s = ((String) m.get("data")).trim();
                    if (looksLikeBase64Image(s)) {
                        String mime = null;
                        Object mt = m.get("mimeType");
                        if (mt == null) mt = m.get("mime_type");
                        if (mt instanceof String) mime = (String) mt;
                        Map<String, String> out = new HashMap<>();
                        out.put("base64", s);
                        out.put("mime", mime == null ? "image/png" : mime);
                        return out;
                    }
                }
                // Check string fields that may *be* the base64 payload
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    Object v = e.getValue();
                    if (v instanceof String) {
                        String s = ((String) v).trim();
                        if (looksLikeBase64Image(s)) {
                            Map<String, String> out = new HashMap<>();
                            out.put("base64", s);
                            out.put("mime", "image/png");
                            return out;
                        }
                    } else {
                        Map<String, String> found = recursiveFindBase64AndMime(v);
                        if (found != null) return found;
                    }
                }
            } else if (node instanceof List) {
                for (Object item : (List<?>) node) {
                    Map<String, String> found = recursiveFindBase64AndMime(item);
                    if (found != null) return found;
                }
            } else if (node instanceof String) {
                String s = ((String) node).trim();
                if (looksLikeBase64Image(s)) {
                    Map<String, String> out = new HashMap<>();
                    out.put("base64", s);
                    out.put("mime", "image/png");
                    return out;
                }
            }
        } catch (Exception ignored) {
            log.debug("recursiveFindBase64AndMime exception: {}", ignored.getMessage(), ignored);
        }
        return null;
    }

    private boolean looksLikeBase64Image(String s) {
        if (s == null) return false;
        // Quick acceptance for known image signatures (PNG/JPEG) which can be short in fixtures
        if (s.startsWith("iVBOR") || s.startsWith("/9j/")) return true; // PNG or JPEG signatures in base64

        // Heuristic for longer payloads: must be reasonably long and only contain base64 chars
        if (s.length() < 200) return false;
        // only base64 chars + possible newlines/equals
        if (!s.matches("^[A-Za-z0-9+/=\\n\\r]+$")) return false;
        // fallback: if it's long and looks base64-ish, accept it
        return s.length() > 500;
    }

    /**
     * Sanitize a Gemini image response for debug logging.
     * This will redact large/binary fields (inline data) and truncate very long strings to avoid
     * logging secrets or heavy payloads.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> sanitizeResponseForDebug(Map<String, Object> resp) {
        if (resp == null) return Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        // copy top-level keys except we will specially handle candidates
        for (Map.Entry<String, Object> e : resp.entrySet()) {
            String k = e.getKey();
            if ("candidates".equals(k) && e.getValue() instanceof List) {
                List<?> candidates = (List<?>) e.getValue();
                List<Object> sanCandidates = new ArrayList<>();
                for (Object cobj : candidates) {
                    if (!(cobj instanceof Map)) {
                        sanCandidates.add(cobj);
                        continue;
                    }
                    Map<String, Object> c = (Map<String, Object>) cobj;
                    Map<String, Object> sanC = new LinkedHashMap<>();
                    // copy non-content keys as-is (truncate strings)
                    for (Map.Entry<String, Object> ce : c.entrySet()) {
                        String ck = ce.getKey();
                        Object cv = ce.getValue();
                        if ("content".equals(ck) && cv instanceof Map) {
                            Map<String, Object> content = (Map<String, Object>) cv;
                            Map<String, Object> sanContent = new LinkedHashMap<>();
                            for (Map.Entry<String, Object> contEntry : content.entrySet()) {
                                String contK = contEntry.getKey();
                                Object contV = contEntry.getValue();
                                if ("parts".equals(contK) && contV instanceof List) {
                                    List<?> parts = (List<?>) contV;
                                    List<Object> sanParts = new ArrayList<>();
                                    for (Object partObj : parts) {
                                        if (!(partObj instanceof Map)) {
                                            sanParts.add(partObj);
                                            continue;
                                        }
                                        Map<String, Object> part = (Map<String, Object>) partObj;
                                        Map<String, Object> sanPart = new LinkedHashMap<>();
                                        for (Map.Entry<String, Object> p : part.entrySet()) {
                                            String pk = p.getKey();
                                            Object pv = p.getValue();
                                            // redact inline binary fields
                                            if ("inlineData".equals(pk) || "inline_data".equals(pk)) {
                                                if (pv instanceof Map) {
                                                    Object data = ((Map<?, ?>) pv).get("data");
                                                    if (data instanceof List) {
                                                        sanPart.put(pk, Map.of("data", String.format("[bytes:%d]", ((List<?>) data).size())));
                                                    } else if (data instanceof String) {
                                                        sanPart.put(pk, Map.of("data", String.format("[base64:%d]", ((String) data).length())));
                                                    } else {
                                                        sanPart.put(pk, "[REDACTED_BINARY]");
                                                    }
                                                } else if (pv instanceof List) {
                                                    sanPart.put(pk, String.format("[bytes:%d]", ((List<?>) pv).size()));
                                                } else if (pv instanceof String) {
                                                    sanPart.put(pk, String.format("[base64:%d]", ((String) pv).length()));
                                                } else {
                                                    sanPart.put(pk, "[REDACTED_BINARY]");
                                                }
                                            } else if ("data".equals(pk) && pv instanceof List) {
                                                sanPart.put(pk, String.format("[bytes:%d]", ((List<?>) pv).size()));
                                            } else if (pv instanceof String) {
                                                String s = (String) pv;
                                                sanPart.put(pk, s.length() > 200 ? s.substring(0, 200) + "..." : s);
                                            } else {
                                                sanPart.put(pk, pv);
                                            }
                                        }
                                        sanParts.add(sanPart);
                                    }
                                    sanContent.put(contK, sanParts);
                                } else if (contV instanceof String) {
                                    String s = (String) contV;
                                    sanContent.put(contK, s.length() > 200 ? s.substring(0, 200) + "..." : s);
                                } else {
                                    sanContent.put(contK, contV);
                                }
                            }
                            sanC.put(ck, sanContent);
                        } else if (cv instanceof String) {
                            String s = (String) cv;
                            sanC.put(ck, s.length() > 200 ? s.substring(0, 200) + "..." : s);
                        } else {
                            sanC.put(ck, cv);
                        }
                    }
                    sanCandidates.add(sanC);
                }
                out.put("candidates", sanCandidates);
            } else if (e.getValue() instanceof String) {
                String s = (String) e.getValue();
                out.put(k, s.length() > 200 ? s.substring(0, 200) + "..." : s);
            } else {
                out.put(k, e.getValue());
            }
        }
        return out;
    }
}
