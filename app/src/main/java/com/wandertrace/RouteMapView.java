package com.wandertrace;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Map-only adapter. Existing Activity, route data and single-Web-key contracts stay unchanged. */
public class RouteMapView extends FrameLayout {
    public interface OnPointsChanged {void onChanged(double[][] points);}
    private final Object cameraLock=new Object();
    private final List<double[]> points=new ArrayList<>();
    private TileSource source=new TileSource("osm","");
    private WebView renderer;
    private boolean pageReady,configured,routeMode,fitPending;
    private int revision,pointsRevision;
    private long sequence;
    private double centerLat=39.908823,centerLon=116.397470,zoom=14;
    private OnPointsChanged listener;
    private volatile String renderState="{}";

    public RouteMapView(Context context,AttributeSet attrs) {super(context,attrs);createRenderer();}
    @SuppressLint("SetJavaScriptEnabled")
    private void createRenderer() {
        if(renderer!=null) return;
        WebView web=new WebView(getContext().getApplicationContext());renderer=web;pageReady=false;
        web.setBackgroundColor(0xFFDCE6E4);web.setHorizontalScrollBarEnabled(false);web.setVerticalScrollBarEnabled(false);web.setOverScrollMode(OVER_SCROLL_NEVER);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);settings.setDisplayZoomControls(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        // Only bundled scripts execute. The bridge exposes map camera/status, never ROOT or app actions.
        web.addJavascriptInterface(new Bridge(web),"WanderBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) {return true;}
            @Override public void onPageFinished(WebView view,String url) {
                if(renderer!=view || !url.equals("file:///android_asset/map/index.html")) return;
                pageReady=true;sendScene();
            }
        });
        addView(web,new FrameLayout.LayoutParams(-1,-1));web.loadUrl("file:///android_asset/map/index.html");
    }
    public void setOnPointsChanged(OnPointsChanged callback) {listener=callback;}
    public void configureTiles(String provider,String key) {configureTiles(provider,key,false);}
    public void configureTiles(String provider,String key,boolean satellite) {
        synchronized(cameraLock) {
            double[] gcj=source.toGcj(centerLat,centerLon);source=new TileSource(provider,key,satellite);
            double[] nativeCenter=source.fromGcj(gcj[0],gcj[1]);centerLat=nativeCenter[0];centerLon=nativeCenter[1];
            zoom=Math.max(2,Math.min(source.maxZoom()+2,zoom));revision++;sequence++;configured=true;
        }
        renderState="{}";setContentDescription("地图加载中…");sendScene();
    }
    public void setRouteMode(boolean route) {if(routeMode!=route) {routeMode=route;pointsRevision++;sendScene();}}
    public void setPoints(double[][] values) {
        List<double[]> next=new ArrayList<>();
        if(values!=null) for(double[] p:values) if(p!=null && p.length>=2 && valid(p[0],p[1])) next.add(p.clone());
        if(equalPoints(points,next)) return;
        boolean prefix=prefix(points,next)||prefix(next,points);
        if(routeMode && next.size()>1 && (points.isEmpty() || !prefix)) fitPending=true;
        points.clear();points.addAll(next);pointsRevision++;sendScene();
    }
    private static boolean equalPoints(List<double[]> a,List<double[]> b) {return a.size()==b.size()&&prefix(a,b);}
    private static boolean prefix(List<double[]> a,List<double[]> b) {
        if(a.size()>b.size()) return false;
        for(int i=0;i<a.size();i++) if(a.get(i)[0]!=b.get(i)[0] || a.get(i)[1]!=b.get(i)[1]) return false;
        return true;
    }
    public void centerOn(double lat,double lon) {
        if(!valid(lat,lon)) return;
        synchronized(cameraLock) {double[] p=source.fromGcj(lat,lon);centerLat=p[0];centerLon=p[1];sequence++;}sendScene();
    }
    public double[] centerGcj() {synchronized(cameraLock) {return source.toGcj(centerLat,centerLon);}}
    public int zoomLevel() {return Math.round(cameraZoom());}
    public float cameraZoom() {synchronized(cameraLock) {return (float)zoom;}}
    int dataZoom() {synchronized(cameraLock) {return Math.min(source.maxZoom(),Math.max(2,(int)Math.round(zoom)));}}
    public void setCamera(double lat,double lon,float level) {
        if(!valid(lat,lon) || !Float.isFinite(level)) return;
        synchronized(cameraLock) {double[] p=source.fromGcj(lat,lon);centerLat=p[0];centerLon=p[1];zoom=Math.max(2,Math.min(source.maxZoom()+2,level));sequence++;}sendScene();
    }
    public void zoomBy(int delta) {zoomAt(delta,getWidth()/2f,getHeight()/2f);}
    void zoomAt(double delta,float focusX,float focusY) {
        if(!Double.isFinite(delta)) return;
        synchronized(cameraLock) {
            double next=Math.max(2,Math.min(source.maxZoom()+2,zoom+delta));
            double[] center=MapProjection.zoomCenter(centerLat,centerLon,zoom,next,focusX,focusY,getWidth(),getHeight());
            zoom=next;centerLat=center[0];centerLon=center[1];sequence++;
        }
        sendScene();
    }
    public void undo() {if(!points.isEmpty()) {points.remove(points.size()-1);changed();}}
    public void clearPoints() {points.clear();changed();}
    private void changed() {
        pointsRevision++;double[][] copy=new double[points.size()][];for(int i=0;i<points.size();i++) copy[i]=points.get(i).clone();
        if(listener!=null) listener.onChanged(copy);sendScene();
    }
    JSONObject scene() {
        JSONObject data=new JSONObject();
        synchronized(cameraLock) {
            try {
                data.put("revision",revision).put("sequence",sequence).put("pointsRevision",pointsRevision)
                    .put("provider",source.provider).put("satellite",source.satellite).put("route",routeMode)
                    .put("maxNativeZoom",source.maxZoom()).put("maxZoom",source.maxZoom()+2)
                    .put("base",source.pattern(false)).put("labels",source.hasLabels()?source.pattern(true):JSONObject.NULL)
                    .put("attribution",source.attribution()).put("center",new JSONArray().put(centerLat).put(centerLon))
                    .put("zoom",zoom).put("fit",fitPending);
                JSONArray route=new JSONArray();for(double[] p:points) {double[] n=source.fromGcj(p[0],p[1]);route.put(new JSONArray().put(n[0]).put(n[1]));}
                data.put("points",route);
            } catch(JSONException impossible) {throw new IllegalStateException("Invalid map scene",impossible);}
        }
        return data;
    }
    private void sendScene() {
        if(renderer==null || !pageReady || !configured) return;
        String data=scene().toString();fitPending=false;
        renderer.evaluateJavascript("window.WanderMap&&window.WanderMap.apply("+data+")",null);
    }
    public String renderState() {return renderState;}
    public void retryTiles() {if(renderer!=null&&pageReady) renderer.evaluateJavascript("window.WanderMap&&window.WanderMap.retry()",null);}
    private static boolean valid(double lat,double lon) {return Double.isFinite(lat)&&Double.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180;}
    public final class Bridge {
        private final WebView owner;
        Bridge(WebView owner) {this.owner=owner;}
        @JavascriptInterface public void ready() {post(() -> {if(renderer==owner) {pageReady=true;sendScene();}});}
        @JavascriptInterface public void camera(int current,long currentSequence,double lat,double lon,double level) {
            if(renderer!=owner || !valid(lat,lon)||!Double.isFinite(level)) return;
            synchronized(cameraLock) {
                if(current!=revision || currentSequence!=sequence) return;
                centerLat=lat;centerLon=((lon+180)%360+360)%360-180;zoom=Math.max(2,Math.min(source.maxZoom()+2,level));
            }
        }
        @JavascriptInterface public void status(int current,boolean complete,int loaded,int expected,int labelLoaded,int labelExpected,int failed,int count,boolean allVisible) {
            if(renderer!=owner || current!=revision) return;
            try {
                renderState=new JSONObject().put("complete",complete).put("loaded",loaded).put("expected",expected)
                    .put("labelLoaded",labelLoaded).put("labelExpected",labelExpected).put("failed",failed)
                    .put("points",count).put("allPointsVisible",allVisible).put("satellite",source.satellite).toString();
            } catch(JSONException ignored) {return;}
            String title=source.satellite?"卫星图":"道路图";
            post(() -> {if(current==revision) setContentDescription(title+(complete?"已完整显示":"加载中")+"；图块 "+loaded+"/"+expected+"；路线 "+count+" 点");});
        }
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN && getParent()!=null) getParent().requestDisallowInterceptTouchEvent(true);
        boolean handled=super.dispatchTouchEvent(event);
        if((event.getActionMasked()==MotionEvent.ACTION_UP || event.getActionMasked()==MotionEvent.ACTION_CANCEL)&&getParent()!=null) getParent().requestDisallowInterceptTouchEvent(false);
        return handled;
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        super.onSizeChanged(w,h,oldw,oldh);if(renderer!=null&&pageReady) renderer.evaluateJavascript("window.WanderMap&&window.WanderMap.resize()",null);
    }
    @Override protected void onAttachedToWindow() {super.onAttachedToWindow();createRenderer();renderer.onResume();}
    @Override protected void onDetachedFromWindow() {
        synchronized(cameraLock) {revision++;}pageReady=false;
        WebView old=renderer;renderer=null;
        if(old!=null) {old.removeJavascriptInterface("WanderBridge");old.stopLoading();removeView(old);old.destroy();}
        super.onDetachedFromWindow();
    }
}
