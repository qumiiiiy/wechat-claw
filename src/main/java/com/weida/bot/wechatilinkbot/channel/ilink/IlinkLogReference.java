package com.weida.bot.wechatilinkbot.channel.ilink;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Produces bounded pseudonymous correlation references for channel logs. */
final class IlinkLogReference {

    private static final int REFERENCE_BYTES = 6;

    private IlinkLogReference() {
    }

    static String of(Object value) {
        if (value == null) {
            return "none";
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return "none";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, REFERENCE_BYTES);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
