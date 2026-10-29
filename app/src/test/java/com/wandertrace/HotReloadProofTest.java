package com.wandertrace;

import static org.junit.Assert.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class HotReloadProofTest {
    private EnvironmentStatus status(String generation,int pid,String ticks,int version,boolean hot) throws Exception {
        JSONObject s=new JSONObject().put("system",true).put("pid",pid).put("bootId","boot-A").put("processStartTicks",ticks)
                .put("elapsedMs",99000).put("moduleVersionCode",version).put("frameworkApi",102).put("hotReload",hot)
                .put("generationId",generation).put("locationHook",true).put("sensorHook",true).put("providers",3);
        String out="WT_UID=0\nWT_BOOT_ID=boot-A\nWT_SYSTEM_PID="+pid+"\nWT_PROCESS_STAT="+pid+" (system_server) S "+"0 ".repeat(18)+ticks+" 0 0\nWT_STATUS_BEGIN\n"+s+"\nWT_STATUS_END\n";
        return EnvironmentStatus.parse(0,out,100000,BuildConfig.VERSION_CODE);
    }
    @Test public void requiresANewGenerationInTheSameSurvivingProcess() throws Exception {
        EnvironmentStatus old=status("old",3311,"1234",BuildConfig.VERSION_CODE-1,true);
        assertTrue(HotReloadController.confirmed(old,status("new",3311,"1234",BuildConfig.VERSION_CODE,true)));
        assertFalse(HotReloadController.confirmed(old,status("old",3311,"1234",BuildConfig.VERSION_CODE,true)));
        assertFalse(HotReloadController.confirmed(old,status("",3311,"1234",BuildConfig.VERSION_CODE,true)));
    }
    @Test public void processRestartOrPidReuseCannotBeCalledAnUninterruptedReload() throws Exception {
        EnvironmentStatus old=status("old",3311,"1234",BuildConfig.VERSION_CODE-1,true);
        assertFalse(HotReloadController.confirmed(old,status("new",4422,"1234",BuildConfig.VERSION_CODE,true)));
        assertFalse(HotReloadController.confirmed(old,status("new",3311,"9999",BuildConfig.VERSION_CODE,true)));
    }
    @Test public void legacyStateAndOldVersionNeverCountAsSuccessfulReload() throws Exception {
        EnvironmentStatus modern=status("old",3311,"1234",BuildConfig.VERSION_CODE-1,true);
        assertFalse(HotReloadController.confirmed(status("old",3311,"1234",BuildConfig.VERSION_CODE-1,false),status("new",3311,"1234",BuildConfig.VERSION_CODE,true)));
        assertFalse(HotReloadController.confirmed(modern,status("new",3311,"1234",BuildConfig.VERSION_CODE-1,true)));
        assertFalse(HotReloadController.confirmed(modern,EnvironmentStatus.rootFailure(EnvironmentStatus.Root.UNAVAILABLE)));
    }
}
