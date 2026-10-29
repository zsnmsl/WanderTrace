package com.wandertrace;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Stable boot/process identities keep an old status file from looking like a live module. */
public final class SystemIdentity {
    private SystemIdentity() {}
    public static String read(String path) {
        try { return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8).trim(); }
        catch (Exception ignored) { return ""; }
    }
    public static String processStartTicks(String stat) {
        int close = stat.lastIndexOf(')');
        if (close < 0) return "";
        String[] fields = stat.substring(close + 1).trim().split("\\s+");
        // Fields after the command begin with field 3 (state); starttime is field 22.
        return fields.length > 19 && fields[19].matches("[0-9]+") ? fields[19] : "";
    }
}
