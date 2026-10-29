package com.wandertrace;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import org.json.JSONArray;
import org.json.JSONObject;

/** One selected Web-service key. All returned coordinates use the editor's GCJ-02 format. */
final class MapSearchClient {
    private MapSearchClient() {}
    static double[] search(String provider,String key,String address) throws IOException {
        HttpURLConnection connection=null;String json;
        try {
            connection=(HttpURLConnection)new URL(requestUrl(provider,key,address)).openConnection();
            connection.setConnectTimeout(10000);connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent","WanderTrace/2.4 (Android map editor)");
            int code=connection.getResponseCode();if(code<200 || code>=300) throw new IOException("地图服务 HTTP "+code);
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096];int count;
                while((count=in.read(buffer))!=-1) {if(out.size()+count>1048576) throw new IOException("地图服务响应过大");out.write(buffer,0,count);}
                json=new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch(IOException failure) {
            // Do not surface request URLs, which contain the user's credential.
            throw new IOException("地点查询未完成，请检查网络、Key 类型与服务授权",failure);
        } finally {if(connection!=null) connection.disconnect();}
        return parse(provider,json);
    }
    static String requestUrl(String provider,String key,String address) {
        String encoded=enc(address),credential=enc(key);
        return switch(provider) {
            case "tencent" -> "https://apis.map.qq.com/ws/geocoder/v1/?address="+encoded+"&key="+credential;
            case "baidu" -> "https://api.map.baidu.com/geocoding/v3/?address="+encoded+"&output=json&ret_coordtype=gcj02ll&ak="+credential;
            case "tianditu" -> "https://api.tianditu.gov.cn/geocoder?ds="+enc("{\"keyWord\":"+JSONObject.quote(address)+"}")+"&tk="+credential;
            default -> "https://restapi.amap.com/v3/geocode/geo?address="+encoded+"&key="+credential;
        };
    }
    static double[] parse(String provider,String json) throws IOException {
        try {
            JSONObject result=new JSONObject(json);double lat,lon;
            switch(provider) {
                case "baidu","tencent" -> {
                    if(result.optInt("status",-1)!=0) throw serviceError(result);
                    JSONObject point=result.getJSONObject("result").getJSONObject("location");lat=point.getDouble("lat");lon=point.getDouble("lng");
                }
                case "tianditu" -> {
                    if(!"0".equals(result.optString("status"))) throw serviceError(result);
                    JSONObject point=result.getJSONObject("location");lat=point.getDouble("lat");lon=point.getDouble("lon");
                    validate(lat,lon);return CoordTransform.wgs2gcj(lat,lon);
                }
                default -> {
                    if(!"1".equals(result.optString("status"))) throw serviceError(result);
                    JSONArray found=result.optJSONArray("geocodes");if(found==null || found.length()==0) throw new IOException("未找到地点");
                    String[] point=found.getJSONObject(0).getString("location").split(",");
                    if(point.length!=2) throw new IOException("地点坐标无效");lon=Double.parseDouble(point[0]);lat=Double.parseDouble(point[1]);
                }
            }
            validate(lat,lon);return new double[]{lat,lon};
        } catch(org.json.JSONException | NumberFormatException failure) {throw new IOException("地图服务返回的地点数据无效",failure);}
    }
    private static IOException serviceError(JSONObject result) {
        return new IOException("地图服务未授权或未找到地点（状态 "+result.optString("status","unknown")+"），请检查 Web 服务 Key 与接口权限");
    }
    private static void validate(double lat,double lon) throws IOException {
        if(!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat)>90 || Math.abs(lon)>180) throw new IOException("地点坐标无效");
    }
    private static String enc(String value) {
        try {return URLEncoder.encode(value==null ? "" : value,"UTF-8");}
        catch(java.io.UnsupportedEncodingException impossible) {throw new AssertionError(impossible);}
    }
}
