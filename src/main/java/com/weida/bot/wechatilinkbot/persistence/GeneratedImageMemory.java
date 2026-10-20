package com.weida.bot.wechatilinkbot.persistence;

import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import com.weida.bot.wechatilinkbot.model.MediaKind;
import com.weida.bot.wechatilinkbot.model.MediaPart;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Bounded, expiring in-process store for the latest generated conversation image.
 */
@Component
public class GeneratedImageMemory {

    private final GeneratedImageMemoryProperties properties;
    private final LinkedHashMap<String, RememberedImage> imageMap =
            new LinkedHashMap<>(16, 0.75F, true);
    private long totalBytes;

    public GeneratedImageMemory(GeneratedImageMemoryProperties properties) {
        this.properties = properties;
    }

    public synchronized void remember(
            String conversationId,
            List<GeneratedMedia> generatedMediaList) {
        if (!properties.isEnabled()
                || conversationId == null
                || conversationId.isBlank()
                || generatedMediaList == null) {
            return;
        }
        GeneratedMedia image = latestImage(generatedMediaList);
        if (image == null || image.bytes().length > properties.getMaxImageBytes()) {
            return;
        }
        removeExpired();
        remove(conversationId);
        makeCapacityFor(image.bytes().length);
        if (image.bytes().length > properties.getMaxTotalBytes()) {
            return;
        }
        RememberedImage rememberedImage = new RememberedImage(
                image.bytes(),
                mimeType(image.fileName()),
                currentTime());
        imageMap.put(conversationId, rememberedImage);
        totalBytes += rememberedImage.bytes().length;
    }

    public synchronized Optional<MediaPart> getLatest(String conversationId) {
        if (!properties.isEnabled()
                || conversationId == null
                || conversationId.isBlank()) {
            return Optional.empty();
        }
        removeExpired();
        RememberedImage image = imageMap.get(conversationId);
        if (image == null) {
            return Optional.empty();
        }
        return Optional.of(new MediaPart(
                MediaKind.IMAGE,
                image.mimeType(),
                image.bytes()));
    }

    public synchronized void clear(String conversationId) {
        if (conversationId != null) {
            remove(conversationId);
        }
    }

    Instant currentTime() {
        return Instant.now();
    }

    private GeneratedMedia latestImage(List<GeneratedMedia> generatedMediaList) {
        for (int index = generatedMediaList.size() - 1; index >= 0; index--) {
            GeneratedMedia media = generatedMediaList.get(index);
            if (media != null && media.kind() == GeneratedMediaKind.IMAGE) {
                return media;
            }
        }
        return null;
    }

    private void makeCapacityFor(int imageBytes) {
        while (!imageMap.isEmpty()
                && (imageMap.size() >= properties.getMaxConversations()
                        || totalBytes + imageBytes > properties.getMaxTotalBytes())) {
            Iterator<Map.Entry<String, RememberedImage>> iterator =
                    imageMap.entrySet().iterator();
            Map.Entry<String, RememberedImage> eldest = iterator.next();
            totalBytes -= eldest.getValue().bytes().length;
            iterator.remove();
        }
    }

    private void removeExpired() {
        Instant cutoffTime = currentTime().minus(properties.getExpireAfter());
        Iterator<Map.Entry<String, RememberedImage>> iterator =
                imageMap.entrySet().iterator();
        while (iterator.hasNext()) {
            RememberedImage image = iterator.next().getValue();
            if (image.storedAt().isBefore(cutoffTime)) {
                totalBytes -= image.bytes().length;
                iterator.remove();
            }
        }
    }

    private void remove(String conversationId) {
        RememberedImage removed = imageMap.remove(conversationId);
        if (removed != null) {
            totalBytes -= removed.bytes().length;
        }
    }

    private static String mimeType(String fileName) {
        String lowerCaseName = fileName.toLowerCase(Locale.ROOT);
        if (lowerCaseName.endsWith(".jpg") || lowerCaseName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerCaseName.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }

    private record RememberedImage(byte[] bytes, String mimeType, Instant storedAt) {

        private RememberedImage {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
