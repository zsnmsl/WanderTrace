package com.wandertrace;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class MapSearchClientTest {
    @Test public void eachProviderUsesItsSingleWebCredentialAndCorrectCoordinateContract() {
        String amap=MapSearchClient.requestUrl("amap","key&value","天安门");assertTrue(amap.contains("restapi.amap.com"));assertTrue(amap.endsWith("key=key%26value"));
        String tencent=MapSearchClient.requestUrl("tencent","one-key","北京");assertTrue(tencent.contains("apis.map.qq.com/ws/geocoder/v1/"));assertTrue(tencent.endsWith("key=one-key"));
        String baidu=MapSearchClient.requestUrl("baidu","one-ak","北京");assertTrue(baidu.contains("ret_coordtype=gcj02ll"));assertTrue(baidu.endsWith("ak=one-ak"));
        String tdt=MapSearchClient.requestUrl("tianditu","one-tk","北京");assertTrue(tdt.contains("api.tianditu.gov.cn/geocoder?ds="));assertTrue(tdt.endsWith("tk=one-tk"));
    }
    @Test public void allSearchResultsReachEditorAsGcjAndInvalidResultsAreRejected() throws Exception {
        assertArrayEquals(new double[]{39.9,116.4},MapSearchClient.parse("amap","{\"status\":\"1\",\"geocodes\":[{\"location\":\"116.4,39.9\"}]}"),0);
        String json="{\"status\":0,\"result\":{\"location\":{\"lat\":39.9,\"lng\":116.4}}}";
        for(String provider:new String[]{"tencent","baidu"}) assertArrayEquals(new double[]{39.9,116.4},MapSearchClient.parse(provider,json),0);
        assertArrayEquals(CoordTransform.wgs2gcj(39.9,116.4),MapSearchClient.parse("tianditu","{\"status\":\"0\",\"location\":{\"lat\":39.9,\"lon\":116.4}}"),0);
        for(String[] pair:new String[][]{{"amap","{\"status\":0}"},{"tencent","{\"status\":199}"},{"baidu","{\"status\":0,\"result\":{\"location\":{\"lat\":999,\"lng\":116}}}"},{"tianditu","broken"}}) {
            try {MapSearchClient.parse(pair[0],pair[1]);fail("invalid result accepted");}catch(java.io.IOException expected) {assertFalse(expected.getMessage().isEmpty());}
        }
    }
}
