package com.filee.core;

import java.util.*;

public class FormatRegistry {

    public enum Category {
        IMAGE,
        DOCUMENT,
        AUDIO,
        VIDEO,
        UNKNOWN
    }

    // Documents that only convert to limited targets (not full cross-category)
    private static final Map<String, Set<String>> CUSTOM_TARGET_MAP = new LinkedHashMap<>();

    private final Map<String, Category> extensionCategoryMap = new HashMap<>();
    private final Map<Category, Set<String>> categoryFormatsMap = new EnumMap<>(Category.class);

    public FormatRegistry() {
        initializeRegistry();
        initializeCustomTargets();
    }

    private void initializeRegistry() {
        for (Category cat : Category.values()) {
            categoryFormatsMap.put(cat, new LinkedHashSet<>());
        }
        // Images
        register(Category.IMAGE, "png", "jpg", "jpeg", "webp", "gif", "bmp", "tiff", "ico");
        // Documents
        register(Category.DOCUMENT, "pdf", "txt", "md", "html", "docx", "csv");
        // Audio
        register(Category.AUDIO, "mp3", "wav", "aac", "flac", "ogg", "m4a");
        // Video
        register(Category.VIDEO, "mp4", "mkv", "webm", "avi", "mov", "gif");
    }

    private void initializeCustomTargets() {
        // Documents don't all convert to every other document format
        CUSTOM_TARGET_MAP.put("md",   new LinkedHashSet<>(List.of("html", "txt", "pdf")));
        CUSTOM_TARGET_MAP.put("html", new LinkedHashSet<>(List.of("txt", "pdf")));
        CUSTOM_TARGET_MAP.put("txt",  new LinkedHashSet<>(List.of("pdf", "md", "html")));
        CUSTOM_TARGET_MAP.put("docx", new LinkedHashSet<>(List.of("txt", "pdf")));
        CUSTOM_TARGET_MAP.put("pdf",  new LinkedHashSet<>(List.of("png", "jpg", "jpeg", "webp", "bmp", "tiff", "txt")));
        CUSTOM_TARGET_MAP.put("csv",  new LinkedHashSet<>(List.of("txt")));
    }

    private void register(Category category, String... extensions) {
        for (String ext : extensions) {
            String lower = ext.toLowerCase(Locale.ROOT);
            extensionCategoryMap.put(lower, category);
            categoryFormatsMap.get(category).add(lower);
        }
    }

    public Category getCategory(String extension) {
        if (extension == null) return Category.UNKNOWN;
        String clean = extension.startsWith(".") ? extension.substring(1) : extension;
        return extensionCategoryMap.getOrDefault(clean.toLowerCase(Locale.ROOT), Category.UNKNOWN);
    }

    public Set<String> getAvailableTargetFormats(String sourceExtension) {
        String clean = (sourceExtension.startsWith(".") ? sourceExtension.substring(1) : sourceExtension).toLowerCase(Locale.ROOT);
        // Use custom map if defined (documents)
        if (CUSTOM_TARGET_MAP.containsKey(clean)) {
            return Collections.unmodifiableSet(CUSTOM_TARGET_MAP.get(clean));
        }
        Category cat = getCategory(clean);
        if (cat == Category.UNKNOWN) {
            return Collections.emptySet();
        }
        Set<String> allInCat = new LinkedHashSet<>(categoryFormatsMap.get(cat));
        allInCat.remove(clean);
        if (cat == Category.IMAGE) {
            allInCat.add("pdf");
        }
        return Collections.unmodifiableSet(allInCat);
    }
}
