package com.wandertrace.xposed;

import com.wandertrace.Config;
import com.wandertrace.RootController;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Immutable snapshots loaded on the module's worker, never via Binder to the UI process. */
final class SystemState {
    static volatile Config config = new Config();
    static boolean reload() {
        try {
            File file = new File(RootController.DIR, "config.json");
            if (new File(RootController.DIR, "disable").exists() || !file.isFile()
                    || file.length() > 2 * 1024 * 1024) {
                config = new Config();
                return false;
            }
            config = Config.fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            return true;
        } catch (Throwable ignored) {
            config = new Config();
            return false;
        }
    }
}
