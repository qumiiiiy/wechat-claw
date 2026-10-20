package com.weida.bot.wechatilinkbot.model;

import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Outcome of {@code WordDocumentClient.edit(Path, DocumentEditRequest)}.
 *
 * <p>The original file is never mutated; callers can compare
 * {@link #sourceSha256Hex()} against a digest they took before invocation
 * to prove byte-equality of the source.</p>
 *
 * @param filePath             atomic-moved target path inside the output directory
 * @param bytes                bytes read from the final target path
 * @param sha256               SHA-256 digest of the edited bytes
 * @param replacementsApplied  total number of run-level text replacements
 *                             applied across the document (excluding the
 *                             optional title rewrite)
 * @param sourceSha256         SHA-256 digest of the source bytes as they
 *                             were when the copy started
 */
public record DocumentEditResult(
        Path filePath,
        byte[] bytes,
        byte[] sha256,
        int replacementsApplied,
        byte[] sourceSha256) {

    public DocumentEditResult {
        Objects.requireNonNull(filePath, "filePath 不能为空");
        Objects.requireNonNull(bytes, "bytes 不能为空");
        Objects.requireNonNull(sha256, "sha256 不能为空");
        Objects.requireNonNull(sourceSha256, "sourceSha256 不能为空");
        if (replacementsApplied < 0) {
            throw new IllegalArgumentException(
                    "replacementsApplied 不能为负数：" + replacementsApplied);
        }
        bytes = bytes.clone();
        sha256 = sha256.clone();
        sourceSha256 = sourceSha256.clone();
    }

    public String sha256Hex() {
        return HexFormat.of().formatHex(sha256);
    }

    public String sourceSha256Hex() {
        return HexFormat.of().formatHex(sourceSha256);
    }

    public String fileName() {
        return filePath.getFileName().toString();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public byte[] sha256() {
        return sha256.clone();
    }

    @Override
    public byte[] sourceSha256() {
        return sourceSha256.clone();
    }
}
