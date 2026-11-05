package io.github.frostie77.skullkid;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Adds a Key listener to the imported Flash movie IN MEMORY ONLY. The original
 * user-provided SWF remains intact in app-private storage and is never bundled.
 *
 * Injected AVM1 logic (for Flash 6):
 * if (!_root.__skullQualityListener) {
 *   _root.__skullQualityListener = {};
 *   _root.__skullQualityListener.onKeyDown = function() {
 *     if (Key.getCode() == 89) {
 *       if (_root._quality == "LOW") _root._quality = "MEDIUM";
 *       else if (_root._quality == "MEDIUM") _root._quality = "HIGH";
 *       else _root._quality = "LOW";
 *     }
 *   };
 *   Key.addListener(_root.__skullQualityListener);
 * }
 *
 * The tag is appended just before the main timeline's first ShowFrame.
 * Original art, scripts, scene timing and content are otherwise unchanged.
 */
final class SkullKidQualityPatch {
    private static final int MAX_UNCOMPRESSED = 40_000_000;
    private static final byte[] QUALITY_ACTION = Base64.getDecoder().decode(
        "lgcAAF9yb290AByWGAAAX19za3VsbFF1YWxpdHlMaXN0ZW5lcgBOEp0CAAUAmQIAnwGWBwAAX3Jvb3QAHJYYAABfX3NrdWxsUXVhbGl0eUxpc3RlbmVyAJYFAAcAAAAAQ0+WBwAAX3Jvb3QAHJYYAABfX3NrdWxsUXVhbGl0eUxpc3RlbmVyAE6WCwAAb25LZXlEb3duAJvsAAAAAOcAlgUABwAAAACWBQAAS2V5AByWCQAAZ2V0Q29kZQBSlgUAB1kAAABJEp0CALkAlgcAAF9yb290AByWCgAAX3F1YWxpdHkATpYFAABMT1cASZ0CAE0AlgcAAF9yb290AByWCgAAX3F1YWxpdHkATpYIAABNRURJVU0ASZ0CAEkAlgcAAF9yb290AByWDwAAX3F1YWxpdHkAAExPVwBPmQIARQCWBwAAX3Jvb3QAHJYSAABfcXVhbGl0eQAATUVESVVNAE+ZAgAfAJYHAABfcm9vdAAclhAAAF9xdWFsaXR5AABISUdIAE8AT5YHAABfcm9vdAAclhgAAF9fc2t1bGxRdWFsaXR5TGlzdGVuZXIATpYFAAcBAAAAlgUAAEtleQAclg0AAGFkZExpc3RlbmVyAFIXAA==");

    private SkullKidQualityPatch() {}

    private static int u32(byte[] b, int p) {
        return (b[p] & 255) | ((b[p+1] & 255) << 8) |
            ((b[p+2] & 255) << 16) | ((b[p+3] & 255) << 24);
    }

    private static void putU32(byte[] b, int p, int value) {
        b[p] = (byte) value;
        b[p+1] = (byte) (value >> 8);
        b[p+2] = (byte) (value >> 16);
        b[p+3] = (byte) (value >> 24);
    }

    private static byte[] expand(byte[] b) throws IOException {
        if (b.length < 21 || b[1] != 'W' || b[2] != 'S')
            throw new IOException("Invalid Flash SWF signature");
        if (b[0] == 'F') return b;
        if (b[0] != 'C') throw new IOException("Unsupported SWF compression");
        ByteArrayOutputStream result = new ByteArrayOutputStream(b.length * 2);
        result.write(b, 0, 8);
        try (InflaterInputStream input = new InflaterInputStream(
                new ByteArrayInputStream(b, 8, b.length - 8))) {
            byte[] chunk = new byte[8192];
            int len;
            while ((len = input.read(chunk)) >= 0) {
                if (result.size() + len > MAX_UNCOMPRESSED)
                    throw new IOException("SWF decompression size limit exceeded");
                result.write(chunk, 0, len);
            }
        }
        byte[] expanded = result.toByteArray();
        expanded[0] = 'F';
        if (u32(expanded, 4) != expanded.length)
            throw new IOException("SWF decompressed length mismatch");
        return expanded;
    }

    static byte[] inject(byte[] source) throws IOException {
        byte[] raw = expand(source);
        if (raw.length > MAX_UNCOMPRESSED || raw[3] < 6)
            throw new IOException("Unsupported Flash movie");
        int bitsPerCoord = (raw[8] & 255) >>> 3;
        int rectSize = (5 + 4 * bitsPerCoord + 7) / 8;
        int p = 8 + rectSize + 4; // FrameSize, FrameRate, FrameCount
        if (p >= raw.length) throw new IOException("Invalid SWF stage header");
        int showFrame = -1;
        while (p + 2 <= raw.length) {
            int start = p;
            int tag = (raw[p] & 255) | ((raw[p+1] & 255) << 8);
            int kind = tag >> 6;
            int length = tag & 63;
            p += 2;
            if (length == 63) {
                if (p + 4 > raw.length) throw new IOException("Truncated long SWF tag");
                length = u32(raw, p);
                p += 4;
            }
            if (length < 0 || length > raw.length - p)
                throw new IOException("Corrupt SWF tag length");
            if (kind == 1) { showFrame = start; break; }
            if (kind == 0) break;
            p += length;
        }
        if (showFrame < 0) throw new IOException("No main Flash frame");
        int insertion = 6 + QUALITY_ACTION.length;
        if ((long) raw.length + insertion > MAX_UNCOMPRESSED)
            throw new IOException("SWF size limit exceeded");
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length + insertion);
        out.write(raw, 0, showFrame);
        int tagHeader = (12 << 6) | 63;
        out.write(tagHeader & 255);
        out.write(tagHeader >> 8);
        byte[] len = new byte[4];
        putU32(len, 0, QUALITY_ACTION.length);
        out.write(len);
        out.write(QUALITY_ACTION);
        out.write(raw, showFrame, raw.length - showFrame);
        byte[] result = out.toByteArray();
        putU32(result, 4, result.length);
        if (source[0] != 'C') return result;
        ByteArrayOutputStream compressed = new ByteArrayOutputStream(source.length + insertion);
        compressed.write(result, 0, 8);
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed,
                new Deflater(Deflater.BEST_COMPRESSION))) {
            deflater.write(result, 8, result.length - 8);
        }
        byte[] packed = compressed.toByteArray();
        packed[0] = 'C';
        return packed;
    }
}
