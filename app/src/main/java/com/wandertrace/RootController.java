package com.wandertrace;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeoutException;
import android.os.SystemClock;

/** Root is requested by the application itself, so the root manager records its UID. */
public final class RootController {
    public static final String DIR = "/data/misc/wandertrace";
    private RootController() {}

    public static String apply(Context context, Config config) throws Exception {
        return apply(context, config, false);
    }
    public static String apply(Context context, Config config, boolean refreshLocation) throws Exception {
        File source = new File(context.getFilesDir(), "system-config.json");
        try (FileOutputStream out = new FileOutputStream(source)) {
            out.write(config.toJson().getBytes(StandardCharsets.UTF_8));
        }
        return run("id -u; mkdir -p " + DIR + "; chown 1000:1000 " + DIR
                + "; chmod 0750 " + DIR + "; cat '" + source.getAbsolutePath()
                + "' > " + DIR + "/config.json.tmp && chown 1000:1000 " + DIR
                + "/config.json.tmp && chmod 0640 " + DIR + "/config.json.tmp"
                + " && chcon u:object_r:system_data_file:s0 " + DIR + " " + DIR
                + "/config.json.tmp && mv " + DIR + "/config.json.tmp " + DIR
                + "/config.json && echo APPLIED; "
                + (refreshLocation ? "sleep 1; if [ \"$(cmd location is-location-enabled --user current)\" = true ]; then "
                + "cmd location set-location-enabled false --user current; cmd location set-location-enabled true --user current; fi; " : "")
                + "cat " + DIR + "/status.json 2>/dev/null || true");
    }

    public static String run(String command) throws Exception {
        Result result;
        try { result=execute(command); }
        catch (TimeoutException timeout) { throw new IllegalStateException("ROOT 请求超时，请在 ROOT 管理器中允许游迹"); }
        String output=result.output;
        if (result.exitCode != 0 || !output.startsWith("0\n") || !output.contains("APPLIED")) {
            throw new IllegalStateException("ROOT 写入失败：" + output.trim());
        }
        return output;
    }

    /** Verifies this app's actual su grant; never applies or changes simulation configuration. */
    public static EnvironmentStatus inspect() {
        return inspectCommand(inspectionCommand());
    }
    public static EnvironmentStatus inspectAfterReload(String previousGeneration) {
        if (previousGeneration==null || !previousGeneration.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) return inspect();
        String wait="wt_uid=$(id -u); [ \"$wt_uid\" = 0 ] || exit 1; for wt_attempt in 1 2 3 4 5 6 7 8 9 10; do "
                + "if grep -Eq '\"moduleVersionCode\":"+BuildConfig.VERSION_CODE+"[,}]' "+DIR+"/status.json 2>/dev/null"
                + " && ! grep -Fq '\"generationId\":\""+previousGeneration+"\"' "+DIR+"/status.json; then break; fi; sleep 0.3; done; ";
        return inspectCommand(wait+inspectionCommand());
    }
    private static EnvironmentStatus inspectCommand(String command) {
        try {
            Result result=execute(command);
            return EnvironmentStatus.parse(result.exitCode,result.output,SystemClock.elapsedRealtime(),BuildConfig.VERSION_CODE);
        } catch (TimeoutException timeout) { return EnvironmentStatus.rootFailure(EnvironmentStatus.Root.TIMEOUT); }
        catch (java.io.IOException unavailable) { return EnvironmentStatus.rootFailure(EnvironmentStatus.Root.UNAVAILABLE); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); return EnvironmentStatus.rootFailure(EnvironmentStatus.Root.ERROR);
        } catch (Exception failure) { return EnvironmentStatus.rootFailure(EnvironmentStatus.Root.ERROR); }
    }

    static String inspectionCommand() {
        return "wt_uid=$(id -u); printf 'WT_UID=%s\\n' \"$wt_uid\"; [ \"$wt_uid\" = 0 ] || exit 1; "
                // Bootstrap only the status directory on first use; leave existing config and recovery flags alone.
                + "if [ ! -d " + DIR + " ]; then mkdir -p " + DIR + " && chown 1000:1000 " + DIR
                + " && chmod 0750 " + DIR + " && chcon u:object_r:system_data_file:s0 " + DIR + "; sleep 1; fi; "
                + "wt_pid=$(pidof system_server); wt_pid=${wt_pid%% *}; printf 'WT_SYSTEM_PID=%s\\n' \"$wt_pid\"; "
                + "printf 'WT_BOOT_ID='; cat /proc/sys/kernel/random/boot_id 2>/dev/null; printf '\\nWT_PROCESS_STAT='; "
                + "if [ -n \"$wt_pid\" ]; then cat /proc/\"$wt_pid\"/stat 2>/dev/null; fi; "
                + "printf '\\nWT_FRAMEWORK_RUNNING='; "
                + "if [ -n \"$wt_pid\" ] && grep -Eiq 'lsposed|liblspd|/lspd/|lspd[.](dex|jar)' /proc/\"$wt_pid\"/maps 2>/dev/null; then echo 1; else echo 0; fi; "
                + "wt_present=0; for wt_module in /data/adb/modules/*; do "
                + "if [ -f \"$wt_module/module.prop\" ] && grep -Eiq '^(id|name)=.*(lsposed|lspd)' \"$wt_module/module.prop\"; then wt_present=1; break; fi; done; "
                + "printf 'WT_FRAMEWORK_PRESENT=%s\\n' \"$wt_present\"; "
                + "printf 'WT_RECOVERY_DISABLED='; if [ -f " + DIR + "/disable ]; then echo 1; else echo 0; fi; "
                + "printf 'WT_STATUS_OWNER='; stat -c %u " + DIR + "/status.json 2>/dev/null; printf '\\n'; "
                + "wt_before=$(head -c 32768 " + DIR + "/status.json 2>/dev/null); "
                + "if [ -n \"$wt_before\" ] && ! printf '%s' \"$wt_before\" | grep -Eq '\"(bootId|processStartTicks)\":\"[^\"]+\"'; then "
                + "for wt_sample in 1 2 3 4 5 6 7 8; do sleep 0.2; wt_after=$(head -c 32768 " + DIR + "/status.json 2>/dev/null); "
                + "[ \"$wt_after\" != \"$wt_before\" ] && break; done; fi; "
                + "printf 'WT_PREVIOUS_STATUS_BEGIN\\n%s\\nWT_PREVIOUS_STATUS_END\\n' \"$wt_before\"; "
                + "printf 'WT_STATUS_BEGIN\\n'; head -c 32768 " + DIR + "/status.json 2>/dev/null; printf '\\nWT_STATUS_END\\n'; true";
    }

    private static final class Result {
        final int exitCode; final String output;
        Result(int code,String output) { exitCode=code; this.output=output; }
    }
    private static Result execute(String command) throws Exception {
        Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
        FutureTask<String> capture=new FutureTask<>(() -> {
            ByteArrayOutputStream buffer=new ByteArrayOutputStream(); byte[] chunk=new byte[4096]; int read;
            while ((read=process.getInputStream().read(chunk)) != -1) {
                int remaining=65536-buffer.size();
                if (remaining > 0) buffer.write(chunk,0,Math.min(remaining,read));
            }
            return new String(buffer.toByteArray(),StandardCharsets.UTF_8);
        });
        Thread reader=new Thread(capture,"wander-root-output"); reader.setDaemon(true); reader.start();
        try {
            if (!process.waitFor(45,TimeUnit.SECONDS)) throw new TimeoutException("ROOT authorization timed out");
            return new Result(process.exitValue(),capture.get(1,TimeUnit.SECONDS));
        } finally {
            process.destroy(); if (process.isAlive()) process.destroyForcibly();
            try { process.getInputStream().close(); } catch (Exception ignored) {}
            capture.cancel(true);
        }
    }
}
