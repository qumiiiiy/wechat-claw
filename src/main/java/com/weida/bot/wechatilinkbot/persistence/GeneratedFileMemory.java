package com.weida.bot.wechatilinkbot.persistence;

import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Bounded, expiring in-process store for the latest generated file. */
@Component
public class GeneratedFileMemory {

    private final GeneratedFileMemoryProperties properties;
    private final LinkedHashMap<String, RememberedFile> fileMap =
            new LinkedHashMap<>(16, 0.75F, true);
    private long totalBytes;

    public GeneratedFileMemory(GeneratedFileMemoryProperties properties) {
        this.properties = properties;
    }

    public synchronized void remember(String conversationId, List<GeneratedMedia> mediaList) {
        if (!properties.isEnabled()
                || conversationId == null
                || conversationId.isBlank()
                || mediaList == null) {
            return;
        }
        GeneratedMedia file = latestFile(mediaList);
        if (file == null || file.bytes().length > properties.getMaxFileBytes()) {
            return;
        }
        removeExpired();
        remove(conversationId);
        makeCapacityFor(file.bytes().length);
        if (file.bytes().length > properties.getMaxTotalBytes()) {
            return;
        }
        RememberedFile remembered = new RememberedFile(
                file,
                Instant.now());
        fileMap.put(conversationId.strip(), remembered);
        totalBytes += file.bytes().length;
    }

    public synchronized Optional<GeneratedMedia> getLatest(String conversationId) {
        if (!properties.isEnabled()
                || conversationId == null
                || conversationId.isBlank()) {
            return Optional.empty();
        }
        removeExpired();
        RememberedFile file = fileMap.get(conversationId.strip());
        return file == null ? Optional.empty() : Optional.of(file.media());
    }

    public synchronized void clear(String conversationId) {
        if (conversationId != null) {
            remove(conversationId.strip());
        }
    }

    private GeneratedMedia latestFile(List<GeneratedMedia> mediaList) {
        for (int index = mediaList.size() - 1; index >= 0; index--) {
            GeneratedMedia media = mediaList.get(index);
            if (media != null && media.kind() == GeneratedMediaKind.FILE) {
                return media;
            }
        }
        return null;
    }

    private void makeCapacityFor(int fileBytes) {
        while (!fileMap.isEmpty()
                && (fileMap.size() >= properties.getMaxConversations()
                        || totalBytes + fileBytes > properties.getMaxTotalBytes())) {
            Iterator<Map.Entry<String, RememberedFile>> iterator = fileMap.entrySet().iterator();
            Map.Entry<String, RememberedFile> eldest = iterator.next();
            totalBytes -= eldest.getValue().media().bytes().length;
            iterator.remove();
        }
    }

    private void removeExpired() {
        Instant cutoff = Instant.now().minus(properties.getExpireAfter());
        Iterator<Map.Entry<String, RememberedFile>> iterator = fileMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, RememberedFile> entry = iterator.next();
            if (entry.getValue().storedAt().isBefore(cutoff)) {
                totalBytes -= entry.getValue().media().bytes().length;
                iterator.remove();
            }
        }
    }

    private void remove(String conversationId) {
        RememberedFile removed = fileMap.remove(conversationId);
        if (removed != null) {
            totalBytes -= removed.media().bytes().length;
        }
    }

    private record RememberedFile(GeneratedMedia media, Instant storedAt) {
    }
}
