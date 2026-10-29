package com.wandertrace;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TileImageLoaderTest {
    @Test public void http200TextIsNotAUsableMapImage() throws Exception {
        try {TileImageLoader.decode(new TileSource("amap",""),18,false,"<html>error</html>".getBytes(StandardCharsets.UTF_8));fail("text accepted as map");}
        catch(TileImageLoader.Failure expected) {assertTrue(expected.getMessage().contains("非图像"));}
        Bitmap image=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);image.eraseColor(0xFF336699);
        ByteArrayOutputStream out=new ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.PNG,100,out);
        assertEquals(256,TileImageLoader.decode(new TileSource("amap",""),18,false,out.toByteArray()).getWidth());image.recycle();
    }
    @Test public void solidZoom19PlaceholderIsRejectedEvenThoughItIsAValidPng() throws Exception {
        // Generate a compact indexed PNG; no captured service image is stored in the repository.
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeLong(0x89504E470D0A1A0AL);
        ByteArrayOutputStream header=new ByteArrayOutputStream();DataOutputStream h=new DataOutputStream(header);
        h.writeInt(256);h.writeInt(256);h.write(new byte[]{1,3,0,0,0});
        chunk(out,"IHDR",header.toByteArray());chunk(out,"PLTE",new byte[]{(byte)0xFC,(byte)0xF9,(byte)0xF2});
        ByteArrayOutputStream compressed=new ByteArrayOutputStream();
        try(DeflaterOutputStream z=new DeflaterOutputStream(compressed)) {z.write(new byte[33*256]);}
        chunk(out,"IDAT",compressed.toByteArray());chunk(out,"IEND",new byte[0]);
        try {TileImageLoader.decode(new TileSource("amap",""),19,false,bytes.toByteArray());fail("blank placeholder accepted");}
        catch(TileImageLoader.Failure expected) {assertTrue(expected.noDetail);}
    }
    private static void chunk(DataOutputStream out,String type,byte[] data) throws IOException {
        byte[] name=type.getBytes(StandardCharsets.US_ASCII);CRC32 crc=new CRC32();crc.update(name);crc.update(data);
        out.writeInt(data.length);out.write(name);out.write(data);out.writeInt((int)crc.getValue());
    }
}
