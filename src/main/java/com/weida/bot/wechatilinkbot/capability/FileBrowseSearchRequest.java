package com.weida.bot.wechatilinkbot.capability;

import java.util.List;

/**
 * Model-produced, structured file search conditions before server-side validation.
 *
 * <p>Natural-language understanding happens in the model; this record only carries the
 * bounded fields that the capability can validate and execute.</p>
 */
public record FileBrowseSearchRequest(
        List<String> nameFragments,
        List<String> fileTypes,
        String directoryHint,
        String sortCriteria,
        Boolean directoriesOnly,
        Boolean recentFileReference) {

    public FileBrowseSearchRequest {
        nameFragments = nameFragments == null ? List.of() : List.copyOf(nameFragments);
        fileTypes = fileTypes == null ? List.of() : List.copyOf(fileTypes);
    }
}
