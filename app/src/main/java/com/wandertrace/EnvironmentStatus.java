package com.wandertrace;

import org.json.JSONObject;

/** Result of a ROOT check plus independently validated framework/module evidence. */
public final class EnvironmentStatus {
    public enum Root { GRANTED, UNAVAILABLE, TIMEOUT, ERROR }
    public final Root root;
    public final boolean frameworkPresent, frameworkRunning, moduleLive, currentModule;
    public final boolean locationHook, sensorHook, recoveryDisabled;
    public final int providers;
    public final String summary, detail;
    public final boolean hotReloadCapable;
    public final int systemPid;
    public final String processIdentity;
    public final String generationId;
    public final boolean backendCompatible,legacyHeartbeat;

    private EnvironmentStatus(Root root, boolean present, boolean running, boolean live, boolean current,
                              boolean location, boolean sensor, boolean disabled, int providers,
                              String summary, String detail,boolean hotReload,int pid,String identity,String generation,boolean compatible,boolean legacy) {
        this.root=root; frameworkPresent=present; frameworkRunning=running; moduleLive=live; currentModule=current;
        locationHook=location; sensorHook=sensor; recoveryDisabled=disabled; this.providers=providers;
        this.summary=summary; this.detail=detail;
        hotReloadCapable=hotReload;systemPid=pid;processIdentity=identity;
        generationId=generation;
        backendCompatible=compatible;legacyHeartbeat=legacy;
    }
    public boolean ready(boolean needsSensors) {
        return root == Root.GRANTED && moduleLive && backendCompatible && !recoveryDisabled
                && locationHook && providers > 0 && (!needsSensors || sensorHook);
    }
    public static EnvironmentStatus rootFailure(Root root) {
        String message = root == Root.TIMEOUT ? "ROOT 请求超时" : root == Root.ERROR ? "ROOT 检查未完成" : "未获得 ROOT 权限";
        return new EnvironmentStatus(root, false, false, false, false, false, false, false, 0,
                message + " · LSPosed 待验证", message + "。请在 Magisk / KernelSU 等管理器中允许“游迹”获取 ROOT。"
                + "\n获得 ROOT 后才能验证系统框架内的模块状态，请授权后点击重新检测。",false,-1,"","",false,false);
    }
    private static String field(String output, String key) {
        String prefix="WT_"+key+"=";
        for (String line : output.split("\\r?\\n")) if (line.startsWith(prefix)) return line.substring(prefix.length()).trim();
        return "";
    }
    static String sensorStreamDetail(String raw) {
        try {
            JSONObject stream=new JSONObject(raw);
            if (!stream.has("channels")) return "";
            long channels=Math.max(0,stream.optLong("channels"));
            String text="\n计步订阅通道："+channels;
            if (channels==0) text+="（尚未捕获计步订阅；Hook 已加载不代表目标应用已收到事件）";
            text+=stream.optInt("stepPipelineRevision",1)>=2 ? "\n原生计步补丁：已加载" : "\n原生计步补丁：未加载，当前仍使用旧原生桥";
            if (stream.has("sentEvents")) {
                text+="\n计步事件：已发送 "+Math.max(0,stream.optLong("sentEvents"))+"，待发送 "+Math.max(0,stream.optLong("pendingEvents"))
                        +"，重试 "+Math.max(0,stream.optLong("retryWrites"))+"，丢弃 "+Math.max(0,stream.optLong("droppedEvents"));
                if (stream.optLong("droppedEvents")>0 || stream.optLong("writeErrors")>0)
                    text+="\n计步事件存在积压丢弃或写入错误，不能据 Hook 状态判断计步已正常送达。";
            }
            if ("non-wakeup-standard-step".equals(stream.optString("coverage")))
                text+="\n覆盖范围：非唤醒型标准计步传感器；未覆盖唤醒型和厂商私有健康接口。";
            return text;
        } catch (Exception ignored) {return "";}
    }
    static EnvironmentStatus parse(int exitCode, String output, long now, int version) {
        if (exitCode != 0 || !"0".equals(field(output,"UID"))) return rootFailure(Root.UNAVAILABLE);
        boolean present="1".equals(field(output,"FRAMEWORK_PRESENT"));
        boolean running="1".equals(field(output,"FRAMEWORK_RUNNING"));
        boolean disabled="1".equals(field(output,"RECOVERY_DISABLED"));
        boolean live=false, current=false, location=false, sensor=false, stale=false;
        int providers=0;
        boolean hotReload=false;int livePid=-1;
        String generation="",sensorStream="";
        boolean compatible=false,legacy=false;
        try {livePid=Integer.parseInt(field(output,"SYSTEM_PID"));}catch (NumberFormatException ignored) {}
        String bootIdentity=field(output,"BOOT_ID"),ticksIdentity=SystemIdentity.processStartTicks(field(output,"PROCESS_STAT"));
        String identity=bootIdentity.isEmpty() && ticksIdentity.isEmpty() ? "" : bootIdentity+":"+ticksIdentity;
        String startToken="WT_STATUS_BEGIN\n", endToken="\nWT_STATUS_END";
        String normalized=output.replace("\r\n","\n");
        int start=normalized.indexOf(startToken), end=start < 0 ? -1 : normalized.indexOf(endToken,start+startToken.length());
        if (start >= 0 && end > start) {
            String raw=normalized.substring(start+startToken.length(),end).trim();
            if (!raw.isEmpty()) try {
                JSONObject status=new JSONObject(raw);
                String boot=field(output,"BOOT_ID"), pid=field(output,"SYSTEM_PID");
                String ticks=SystemIdentity.processStartTicks(field(output,"PROCESS_STAT"));
                String reportedBoot=status.optString("bootId"), reportedTicks=status.optString("processStartTicks");
                boolean bootMatches=!boot.isEmpty() && boot.equals(reportedBoot);
                boolean processMatches=!ticks.isEmpty() && ticks.equals(reportedTicks);
                boolean identityMatches=(bootMatches || processMatches)
                        && (boot.isEmpty() || reportedBoot.isEmpty() || bootMatches)
                        && (ticks.isEmpty() || reportedTicks.isEmpty() || processMatches);
                long age=now-status.optLong("elapsedMs",-1);
                boolean advancingLegacy=false;
                int priorBegin=normalized.indexOf("WT_PREVIOUS_STATUS_BEGIN\n"),priorEnd=normalized.indexOf("\nWT_PREVIOUS_STATUS_END");
                if (reportedBoot.isEmpty() && reportedTicks.isEmpty() && "1000".equals(field(output,"STATUS_OWNER")) && priorBegin>=0 && priorEnd>priorBegin) {
                    try {
                        JSONObject prior=new JSONObject(normalized.substring(priorBegin+"WT_PREVIOUS_STATUS_BEGIN\n".length(),priorEnd).trim());
                        long advance=status.optLong("elapsedMs",-1)-prior.optLong("elapsedMs",-1);
                        advancingLegacy=prior.optBoolean("system") && prior.optInt("pid",-1)==status.optInt("pid",-2)
                                && advance>0 && advance<=5000;
                    } catch (Exception ignored) {}
                }
                live=status.optBoolean("system") && pid.equals(String.valueOf(status.optInt("pid",-1)))
                        && (identityMatches || advancingLegacy) && status.has("elapsedMs") && age >= 0 && age <= 10000;
                legacy=live && advancingLegacy && !identityMatches;
                stale=!live;
                if (live) {
                    running=true; present=true; current=status.optInt("moduleVersionCode",-1)==version;
                    location=status.optBoolean("locationHook"); sensor=status.optBoolean("sensorHook");
                    sensorStream=sensorStreamDetail(status.optString("sensor"));
                    providers=Math.max(0,status.optInt("providers"));
                    hotReload=status.optBoolean("hotReload") && status.optInt("frameworkApi")>=102;
                    livePid=status.optInt("pid",-1);identity=boot+":"+ticks;
                    generation=status.optString("generationId");
                    compatible=status.optInt("configSchema",1)==Config.SYSTEM_CONFIG_SCHEMA;
                }
            } catch (Exception ignored) { stale=true; }
        }
        String summary, detail;
        if (disabled) {
            summary="ROOT 已授权 · 模块处于恢复停用状态";
            detail="检测到 /data/misc/wandertrace/disable。移除恢复停用标记后再检测；未加载的入口无法凭参数热更新接管。";
        } else if (live && !compatible) {
            summary="ROOT 已授权 · 系统配置协议不兼容";
            detail="系统模块已运行，但其配置协议与应用不兼容，正在尝试框架进程内更新。";
        } else if (live && location && providers > 0) {
            summary=current ? "ROOT 已授权 · 定位与系统模块已就绪" : "ROOT 已授权 · 兼容后端已就绪";
            detail="ROOT：已授权\nLSPosed / 兼容框架：已确认系统模块实时运行\n推荐作用域：仅系统框架"
                    + "\n定位 Hook：已加载（"+providers+" 个提供者）\n原生计步 Hook："+(sensor ? "已加载" : "未就绪；开启步频前请检查兼容性")
                    + "\n进程内热重载："+(hotReload ? "支持（Java 模块，保留原生桥）" : "当前驻留模块不支持");
            detail+=sensorStream;
            if (!current) detail+="\n运行中的后端配置协议兼容，可以正常开始 / 停止模拟；代码版本更新在后台单独处理。";
            if (legacy) detail+="\n旧格式心跳已通过系统进程 PID、system 文件所有者和连续两次递增采样验证。";
        } else if (live) {
            summary="ROOT 已授权 · 系统定位 Hook 未就绪";
            detail="已确认框架和游迹系统模块运行，但定位 Hook 或提供者尚未就绪。请稍后重新检测，仍失败时查看 LSPosed 模块日志。";
        } else if (running) {
            summary="ROOT 已授权 · LSPosed 已运行，模块待加载";
            detail="检测到系统进程中的 LSPosed 运行迹象。请启用游迹并使用推荐的“系统框架”作用域。若当前进程尚未加载支持热重载的入口，框架可能无法无中断接管。";
        } else if (present) {
            summary="ROOT 已授权 · LSPosed 运行状态未确认";
            detail="发现 LSPosed 安装文件，但尚未确认框架运行。请确认框架支持 API 102 并已启用，在 LSPosed 中启用游迹和推荐的“系统框架”作用域。";
        } else {
            summary="ROOT 已授权 · 未确认 LSPosed 框架";
            detail="未确认运行中的 LSPosed。请检查框架是否已安装并启用 API 102，在 LSPosed 启用游迹和推荐的“系统框架”作用域。受系统访问限制时也可能无法读取框架迹象。";
        }
        if (stale && !live && !disabled) detail += "\n已有状态文件已过期、进程身份不匹配或无法读取，不作为模块生效依据。";
        return new EnvironmentStatus(Root.GRANTED,present,running,live,current,location,sensor,disabled,providers,summary,detail,hotReload,livePid,identity,generation,compatible,legacy);
    }
}
