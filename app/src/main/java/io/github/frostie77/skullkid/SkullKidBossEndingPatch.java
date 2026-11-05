package io.github.frostie77.skullkid;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Targeted compatibility candidate for Skull Kid's final boss.
 * The original Flash 6 movie resumes the root timeline by calling
 * _root.Play() at the final frame of the boss's blood animation
 * (sprite 459, frame 60). The Odin recording shows that this transition
 * does not complete. Replace that one call with the equivalent AVM1
 * SetTarget('_root'); Play; SetTarget(''); Stop; End instructions.
 *
 * The source SWF is never changed on disk or included in the APK.
 * The candidate needs on-device verification before calling it a fix.
 */
final class SkullKidBossEndingPatch {
    private static final int MAX_SWF = 40_000_000;
    private static final byte[] BEFORE = fromHex(
        "961000060000000000000000005f726f6f74001c96060000506c61790052170700");
    private static final byte[] AFTER = fromHex("8b06005f726f6f7400068b0100000700");

    private SkullKidBossEndingPatch() {}

    private static byte[] fromHex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++)
            out[i] = (byte) ((Character.digit(s.charAt(2 * i), 16) << 4) |
                Character.digit(s.charAt(2 * i + 1), 16));
        return out;
    }

    private static int u16(byte[] b, int at) {
        return (b[at] & 255) | ((b[at + 1] & 255) << 8);
    }
    private static int u32(byte[] b, int at) {
        return u16(b, at) | (u16(b, at + 2) << 16);
    }
    private static void put32(byte[] b, int at, int value) {
        for (int i = 0; i < 4; i++) b[at + i] = (byte) (value >>> (8 * i));
    }

    static byte[] patch(byte[] source) throws IOException {
        if (source.length < 21 || source.length > MAX_SWF ||
            source[1] != 'W' || source[2] != 'S' ||
            (source[0] != 'C' && source[0] != 'F') || source[3] != 6)
            return source;

        byte[] raw;
        if (source[0] == 'F') {
            raw = source.clone();
        } else {
            ByteArrayOutputStream expanded = new ByteArrayOutputStream(source.length * 2);
            expanded.write(source, 0, 8);
            try (InflaterInputStream input = new InflaterInputStream(
                    new ByteArrayInputStream(source, 8, source.length - 8))) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = input.read(buffer)) >= 0) {
                    if (expanded.size() + n > MAX_SWF)
                        throw new IOException("SWF decompression limit exceeded");
                    expanded.write(buffer, 0, n);
                }
            }
            raw = expanded.toByteArray();
            raw[0] = 'F';
        }
        if (u32(raw, 4) != raw.length)
            throw new IOException("Invalid SWF declared size");

        int rectBits = (raw[8] & 255) >>> 3;
        int at = 8 + (5 + 4 * rectBits + 7) / 8 + 4;
        int actionAt = -1, actionEnd = -1, spriteHeader = -1, spriteSize = -1;

        while (at + 2 <= raw.length) {
            int head = u16(raw, at), kind = head >>> 6;
            int len = head & 63, dataAt = at + 2;
            if (len == 63) {
                if (dataAt + 4 > raw.length) break;
                len = u32(raw, dataAt);
                dataAt += 4;
            }
            if (len < 0 || len > raw.length - dataAt) break;
            if (kind == 39 && len >= 4 && u16(raw, dataAt) == 459) {
                if ((head & 63) != 63) return source;
                int frame = 1, child = dataAt + 4, end = dataAt + len;
                while (child + 2 <= end) {
                    int ch = u16(raw, child), ct = ch >>> 6;
                    int size = ch & 63, payload = child + 2;
                    if (size == 63) {
                        if (payload + 4 > end) break;
                        size = u32(raw, payload);
                        payload += 4;
                    }
                    if (size < 0 || size > end - payload) break;
                    if (ct == 12 && frame == 60 && (ch & 63) == 63 &&
                        size == BEFORE.length &&
                        Arrays.equals(Arrays.copyOfRange(raw, payload, payload + size), BEFORE)) {
                        actionAt = payload;
                        actionEnd = payload + size;
                        spriteHeader = at;
                        spriteSize = len;
                        break;
                    }
                    if (ct == 1) frame++;
                    if (ct == 0) break;
                    child = payload + size;
                }
                break;
            }
            if (kind == 0) break;
            at = dataAt + len;
        }

        if (actionAt < 0) return source; // Other SWF versions play unchanged.
        int delta = AFTER.length - BEFORE.length;
        byte[] edited = new byte[raw.length + delta];
        System.arraycopy(raw, 0, edited, 0, actionAt);
        System.arraycopy(AFTER, 0, edited, actionAt, AFTER.length);
        System.arraycopy(raw, actionEnd, edited, actionAt + AFTER.length,
            raw.length - actionEnd);
        put32(edited, actionAt - 4, AFTER.length); // Extended DoAction tag length.
        put32(edited, spriteHeader + 2, spriteSize + delta);
        put32(edited, 4, edited.length);

        if (source[0] == 'F') return edited;
        ByteArrayOutputStream compressed = new ByteArrayOutputStream(source.length + 1024);
        compressed.write(edited, 0, 8);
        try (DeflaterOutputStream out = new DeflaterOutputStream(compressed,
                new Deflater(Deflater.BEST_COMPRESSION))) {
            out.write(edited, 8, edited.length - 8);
        }
        byte[] packed = compressed.toByteArray();
        packed[0] = 'C';
        return packed;
    }
}
