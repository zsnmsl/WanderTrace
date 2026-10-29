package com.wandertrace;

import static org.junit.Assert.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class EnvironmentStatusTest {
    private JSONObject live() throws Exception {
        return new JSONObject().put("system",true).put("pid",3311).put("bootId","boot-A")
                .put("processStartTicks","1234").put("elapsedMs",99000).put("moduleVersionCode",BuildConfig.VERSION_CODE)
                .put("frameworkApi",93).put("locationHook",true).put("sensorHook",true).put("providers",3);
    }
    private String output(JSONObject status, String boot, int pid, String ticks, boolean present, boolean running, boolean disabled) {
        return "WT_UID=0\nWT_BOOT_ID="+boot+"\nWT_SYSTEM_PID="+pid+"\nWT_PROCESS_STAT="+pid+" (system_server) S "+"0 ".repeat(18)+ticks
                +" 0 0\nWT_FRAMEWORK_PRESENT="+(present ? 1 : 0)+"\nWT_FRAMEWORK_RUNNING="+(running ? 1 : 0)
                +"\nWT_RECOVERY_DISABLED="+(disabled ? 1 : 0)+"\nWT_STATUS_BEGIN\n"+(status == null ? "" : status)+"\nWT_STATUS_END\n";
    }
    private EnvironmentStatus inspect(JSONObject status) {
        return EnvironmentStatus.parse(0,output(status,"boot-A",3311,"1234",false,false,false),100000,BuildConfig.VERSION_CODE);
    }
    @Test public void actualUidAndExitStatusAreRequiredEvenWithFreshModuleEvidence() throws Exception {
        String out=output(live(),"boot-A",3311,"1234",true,true,false);
        assertEquals(EnvironmentStatus.Root.UNAVAILABLE,EnvironmentStatus.parse(0,out.replace("WT_UID=0","WT_UID=2000"),100000,BuildConfig.VERSION_CODE).root);
        assertEquals(EnvironmentStatus.Root.UNAVAILABLE,EnvironmentStatus.parse(1,out,100000,BuildConfig.VERSION_CODE).root);
    }
    @Test public void installedFilesAloneNeverConfirmRunningFrameworkOrReadyModule() {
        EnvironmentStatus s=EnvironmentStatus.parse(0,output(null,"boot-A",3311,"1234",true,false,false),100000,BuildConfig.VERSION_CODE);
        assertTrue(s.frameworkPresent); assertFalse(s.frameworkRunning); assertFalse(s.ready(false));
    }
    @Test public void runningFrameworkWithoutModuleHeartbeatRequiresScopeAndReboot() {
        EnvironmentStatus s=EnvironmentStatus.parse(0,output(null,"boot-A",3311,"1234",true,true,false),100000,BuildConfig.VERSION_CODE);
        assertTrue(s.frameworkRunning); assertFalse(s.moduleLive); assertFalse(s.ready(false));
        assertTrue(s.detail.contains("系统框架")); assertFalse(s.ready(false));
    }
    @Test public void liveHeartbeatConfirmsModuleWhenFrameworkMapsCannotBeRead() throws Exception {
        EnvironmentStatus s=inspect(live()); assertTrue(s.frameworkRunning); assertTrue(s.currentModule); assertTrue(s.ready(true));
    }
    @Test public void rejectsPreviousBootEvenIfPidAndStartTicksHappenToBeReused() throws Exception {
        EnvironmentStatus s=EnvironmentStatus.parse(0,output(live(),"boot-B",3311,"1234",true,true,false),100000,BuildConfig.VERSION_CODE);
        assertFalse(s.moduleLive); assertFalse(s.ready(false));
    }
    @Test public void rejectsDifferentOrReusedSystemProcess() throws Exception {
        assertFalse(EnvironmentStatus.parse(0,output(live(),"boot-A",4422,"1234",true,true,false),100000,BuildConfig.VERSION_CODE).moduleLive);
        assertFalse(EnvironmentStatus.parse(0,output(live(),"boot-A",3311,"4567",true,true,false),100000,BuildConfig.VERSION_CODE).moduleLive);
    }
    @Test public void usesProcessIdentityIfRomDoesNotExposeBootId() throws Exception {
        JSONObject s=live().put("bootId",""); assertTrue(inspect(s).ready(true));
        s.put("processStartTicks",""); assertFalse(inspect(s).moduleLive);
    }
    @Test public void staleFutureAndPartialStatusNeverBecomeReady() throws Exception {
        assertFalse(inspect(live().put("elapsedMs",89999)).moduleLive);
        assertFalse(inspect(live().put("elapsedMs",100001)).moduleLive);
        String broken=output(null,"boot-A",3311,"1234",true,true,false).replace("WT_STATUS_BEGIN\n","WT_STATUS_BEGIN\n{partial");
        assertFalse(EnvironmentStatus.parse(0,broken,100000,BuildConfig.VERSION_CODE).moduleLive);
    }
    @Test public void oldLegacyModuleVersionCannotPretendHotReloadIsAvailable() throws Exception {
        EnvironmentStatus s=inspect(live().put("moduleVersionCode",BuildConfig.VERSION_CODE-1));
        assertTrue(s.moduleLive); assertTrue(s.frameworkRunning); assertTrue(s.ready(true));
        assertFalse(s.currentModule); assertFalse(s.hotReloadCapable); assertTrue(s.detail.contains("配置协议兼容"));
    }
    private JSONObject legacy(long elapsed) throws Exception {
        return live().put("elapsedMs",elapsed).put("bootId",(Object)null).put("processStartTicks",(Object)null).put("moduleVersionCode",(Object)null);
    }
    private EnvironmentStatus legacyInspection(JSONObject before,JSONObject after,String owner,int pid) {
        String out="WT_STATUS_OWNER="+owner+"\nWT_PREVIOUS_STATUS_BEGIN\n"+before+"\nWT_PREVIOUS_STATUS_END\n"
                +output(after,"boot-A",pid,"1234",false,false,false);
        return EnvironmentStatus.parse(0,out,100000,BuildConfig.VERSION_CODE);
    }
    @Test public void legacyBackendRequiresLiveSystemOwnedAdvancingHeartbeat() throws Exception {
        var s=legacyInspection(legacy(98000),legacy(99000),"1000",3311);
        assertTrue(s.legacyHeartbeat); assertTrue(s.ready(true)); assertFalse(s.currentModule); assertFalse(s.hotReloadCapable);
        assertFalse(legacyInspection(legacy(99000),legacy(99000),"1000",3311).ready(false));
        assertFalse(legacyInspection(legacy(98000),legacy(99000),"0",3311).ready(false));
        assertFalse(legacyInspection(legacy(98000),legacy(99000),"1000",4422).ready(false));
        assertFalse(legacyInspection(legacy(88000),legacy(89000),"1000",3311).ready(false));
        assertFalse(legacyInspection(legacy(99000),legacy(100001),"1000",3311).ready(false));
        assertFalse(legacyInspection(legacy(98000),legacy(99000).put("bootId","boot-B"),"1000",3311).ready(false));
    }
    @Test public void incompatibleConfigurationSchemaCannotStartEvenWithLiveHooks() throws Exception {
        var s=inspect(live().put("configSchema",com.wandertrace.Config.SYSTEM_CONFIG_SCHEMA+1));
        assertTrue(s.moduleLive); assertFalse(s.backendCompatible); assertFalse(s.ready(false));
    }
    @Test public void recoveryModeAndUnavailableHooksPreventStarting() throws Exception {
        EnvironmentStatus s=EnvironmentStatus.parse(0,output(live(),"boot-A",3311,"1234",true,true,true),100000,BuildConfig.VERSION_CODE);
        assertTrue(s.recoveryDisabled); assertFalse(s.ready(false));
        assertFalse(inspect(live().put("locationHook",false)).ready(false));
        assertFalse(inspect(live().put("providers",0)).ready(false));
        s=inspect(live().put("sensorHook",false)); assertTrue(s.ready(false)); assertFalse(s.ready(true));
    }
    @Test public void rootTimeoutAndMissingSuLeaveFrameworkUnverified() {
        for (EnvironmentStatus.Root root : new EnvironmentStatus.Root[]{EnvironmentStatus.Root.TIMEOUT,EnvironmentStatus.Root.UNAVAILABLE,EnvironmentStatus.Root.ERROR}) {
            EnvironmentStatus s=EnvironmentStatus.rootFailure(root); assertFalse(s.frameworkRunning); assertFalse(s.ready(false));
        }
    }
    @Test public void procStatCommandNamesWithSpacesAndParenthesesAreHandled() {
        assertEquals("1234",SystemIdentity.processStartTicks("3311 (name with (parentheses)) S "+"0 ".repeat(18)+"1234 0 0"));
        assertEquals("",SystemIdentity.processStartTicks("truncated"));
    }
}
