package de.weinert.lumixhdr;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.widget.LinearLayout;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,31,35})
public class CameraPermissionsTest {
    private Activity activity;private ModernCameraPanel panel;
    @Before public void setUp(){
        activity=Robolectric.buildActivity(Activity.class).setup().get();
        panel=new ModernCameraPanel(activity,new LinearLayout(activity),activity.getSharedPreferences("permissions",Context.MODE_PRIVATE),task->{},images->{});
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(required());
    }
    @After public void tearDown(){panel.close();activity.finish();}
    private String[] required(){return ReflectionHelpers.callInstanceMethod(panel,"cameraPermissions");}
    private void request(Runnable action){ReflectionHelpers.callInstanceMethod(panel,"withCameraPermissions",ReflectionHelpers.ClassParameter.from(Runnable.class,action));}
    @Test public void requestsPermissionsForCurrentAndroidVersion(){
        List<String> permissions=Arrays.asList(required());
        assertEquals(Build.VERSION.SDK_INT>=31,permissions.contains(Manifest.permission.BLUETOOTH_SCAN));
        assertEquals(Build.VERSION.SDK_INT>=31,permissions.contains(Manifest.permission.BLUETOOTH_CONNECT));
        assertEquals(Build.VERSION.SDK_INT<=32,permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION));
        assertEquals(Build.VERSION.SDK_INT>=33,permissions.contains(Manifest.permission.NEARBY_WIFI_DEVICES));
    }
    @Test public void permissionGrantResumesOriginalActionOnlyOnce(){
        AtomicInteger actions=new AtomicInteger();request(actions::incrementAndGet);assertEquals(0,actions.get());
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(required());panel.onPermissionsResult(42);panel.onPermissionsResult(42);
        assertEquals(1,actions.get());
    }
    @Test public void denialDoesNotStartBluetoothAndUnrelatedResultDoesNotResume(){
        AtomicInteger actions=new AtomicInteger();request(actions::incrementAndGet);panel.onPermissionsResult(42);assertEquals(0,actions.get());
        request(actions::incrementAndGet);shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(required());panel.onPermissionsResult(99);assertEquals(0,actions.get());panel.onPermissionsResult(42);assertEquals(1,actions.get());
    }
    @Test public void activityCloseSuppressesPendingPermissionAction(){
        AtomicInteger actions=new AtomicInteger();request(actions::incrementAndGet);panel.close();
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(required());panel.onPermissionsResult(42);assertEquals(0,actions.get());
    }
}
