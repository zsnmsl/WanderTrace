package com.wandertrace;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class SensorStreamStatusTest {
    @Test public void loadedHookWithNoSubscribersDoesNotClaimDelivery() {
        String detail=EnvironmentStatus.sensorStreamDetail("{\"channels\":0,\"coverage\":\"non-wakeup-standard-step\"}");
        assertTrue(detail.contains("不代表目标应用已收到事件"));
        assertTrue(detail.contains("未覆盖唤醒型"));
    }
    @Test public void transportProblemsAreVisibleWithoutChangingReadinessRules() {
        String detail=EnvironmentStatus.sensorStreamDetail("{\"stepPipelineRevision\":2,\"channels\":2,\"sentEvents\":100,\"pendingEvents\":3,\"retryWrites\":4,\"droppedEvents\":5}");
        assertTrue(detail.contains("补丁：已加载"));
        assertTrue(detail.contains("已发送 100"));assertTrue(detail.contains("待发送 3"));
        assertTrue(detail.contains("重试 4"));assertTrue(detail.contains("丢弃 5"));assertTrue(detail.contains("不能据 Hook 状态判断"));
    }
    @Test public void legacyAndFailedBackendsRemainReadable() {
        assertEquals("",EnvironmentStatus.sensorStreamDetail("unsupported native ABI"));
        assertEquals("",EnvironmentStatus.sensorStreamDetail("{}"));
        assertTrue(EnvironmentStatus.sensorStreamDetail("{\"channels\":1,\"packets\":9}").contains("通道：1"));
        assertTrue(EnvironmentStatus.sensorStreamDetail("{\"channels\":1,\"packets\":9}").contains("仍使用旧原生桥"));
    }
}
