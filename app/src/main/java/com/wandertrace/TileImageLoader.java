package com.wandertrace;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;

/** Decode actual tile content; HTTP 200 alone does not mean the map contains usable imagery. */
final class TileImageLoader {
    static final class Failure extends IOException {
        final boolean noDetail;
        Failure(String message,boolean noDetail) {super(message);this.noDetail=noDetail;}
    }
    private TileImageLoader() {}
    static Bitmap load(TileSource source,int z,int x,int y,boolean labels) throws IOException {
        HttpURLConnection connection=null;
        try {
            connection=(HttpURLConnection)new URL(source.url(z,x,y,labels)).openConnection();
            connection.setRequestProperty("User-Agent","WanderTrace/2.4 (Android map editor)");
            connection.setConnectTimeout(3500);connection.setReadTimeout(5000);
            int code=connection.getResponseCode();
            if(code!=200) throw new Failure("地图服务返回 HTTP "+code,false);
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192];int count;
                while((count=in.read(buffer))!=-1) {
                    if(out.size()+count>2097152) throw new Failure("地图图块响应过大",false);
                    out.write(buffer,0,count);
                }
                return decode(source,z,labels,out.toByteArray());
            }
        } catch(Failure controlled) {throw controlled;}
        catch(SocketTimeoutException timeout) {throw new Failure("地图连接超时，点击地图可重试",false);}
        catch(UnknownHostException dns) {throw new Failure("无法解析地图服务地址，点击地图可重试",false);}
        catch(SSLException ssl) {throw new Failure("地图安全连接失败，点击地图可重试",false);}
        catch(IOException network) {throw new Failure("地图网络连接失败，点击地图可重试",false);}
        finally {if(connection!=null) connection.disconnect();}
    }
    static Bitmap decode(TileSource source,int zoom,boolean labels,byte[] bytes) throws IOException {
        BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;
        Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(image==null) throw new Failure("地图服务返回了非图像内容",false);
        if(image.getWidth()!=256 || image.getHeight()!=256) {image.recycle();throw new Failure("地图图块尺寸不正确",false);}
        // AMap returns a valid 179-byte, solid cream PNG for unavailable high zoom data.
        // Do not mistake it for a successfully rendered road map; use cached ancestors instead.
        if(source.isGcj() && !"tencent".equals(source.provider) && !source.satellite && !labels && zoom>=18 && bytes.length<512) {
            int color=image.getPixel(0,0);boolean uniform=color==0xFFFCF9F2;
            for(int y=0;uniform && y<256;y+=16) for(int x=0;uniform && x<256;x+=16) uniform=image.getPixel(x,y)==color;
            if(uniform) {image.recycle();throw new Failure("该区域暂无当前精度，正在使用已有底图",true);}
        }
        return image;
    }
}
