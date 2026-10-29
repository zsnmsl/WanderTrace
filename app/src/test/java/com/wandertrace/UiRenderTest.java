package com.wandertrace;

import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.Spinner;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UiRenderTest {
    private static void render(View root, int width, int height, String name) throws Exception {
        root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        root.layout(0,0,width,height);
        Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888); root.draw(new Canvas(image));
        File folder=new File(RuntimeEnvironment.getApplication().getCacheDir(),"ui-preview"); assertTrue(folder.isDirectory() || folder.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))) { image.compress(Bitmap.CompressFormat.PNG,100,out); }
        image.recycle();
    }
    @Test public void renderNarrowAndRegularLayoutsWithThreeReachableTabs() throws Exception {
        RuntimeEnvironment.getApplication().getSharedPreferences(com.wandertrace.Config.PREFS,0).edit().clear().commit();
        var controller=Robolectric.buildActivity(MainActivity.class).setup(); MainActivity activity=controller.get();
        org.robolectric.shadow.api.Shadow.<org.robolectric.shadows.ShadowLooper>extract(Looper.getMainLooper()).idle();
        AlertDialog setup=ShadowAlertDialog.getLatestAlertDialog();
        render(setup.getWindow().getDecorView(),360,700,"setup-360");
        ((Spinner)setup.findViewById(R.id.setupProvider)).setSelection(2); org.robolectric.shadow.api.Shadow.<org.robolectric.shadows.ShadowLooper>extract(Looper.getMainLooper()).idle();
        ((CheckBox)setup.findViewById(R.id.setupConsent)).setChecked(true); setup.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        View root=activity.findViewById(android.R.id.content);
        for (int width:new int[]{360,411}) {
            render(root,width,800,"single-"+width);
            RadioGroup tabs=activity.findViewById(R.id.rgMode); assertEquals(3,tabs.getChildCount());
            for(int i=0;i<3;i++) {
                View tab=tabs.getChildAt(i); assertTrue(tab.getWidth() >= 96); assertTrue(tab.getHeight() >= 48);
                assertTrue(tab.getRight() <= tabs.getWidth());
            }
            activity.findViewById(R.id.rbRoute).performClick(); render(root,width,800,"route-"+width);
            RouteMapView map=activity.findViewById(R.id.routeMap);
            map.centerOn(39.91,116.4);activity.findViewById(R.id.btnAddPoint).performClick();
            map.centerOn(39.92,116.41);activity.findViewById(R.id.btnAddPoint).performClick();
            activity.findViewById(R.id.btnStart).performClick();
            org.robolectric.shadow.api.Shadow.<org.robolectric.shadows.ShadowLooper>extract(Looper.getMainLooper()).idle();
            AlertDialog options=ShadowAlertDialog.getLatestAlertDialog();
            render(options.getWindow().getDecorView(),width,700,"route-options-"+width);options.dismiss();
            activity.findViewById(R.id.rbSettings).performClick(); render(root,width,800,"settings-"+width);
            assertEquals(View.GONE,activity.findViewById(R.id.pageEditor).getVisibility());
            activity.findViewById(R.id.rbSingle).performClick();
        }
        controller.pause().stop().destroy();
    }
}
