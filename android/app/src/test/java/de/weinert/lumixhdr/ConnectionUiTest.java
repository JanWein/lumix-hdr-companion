package de.weinert.lumixhdr;

import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.os.Looper;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.io.IOException;
import java.util.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

/** User-visible failure, progress, cancellation and clipboard behavior. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,35})
public class ConnectionUiTest {
    private Activity activity;private ModernCameraPanel panel;
    private final Queue<ModernCameraPanel.Task> tasks=new ArrayDeque<>();
    @Before public void setUp(){
        activity=Robolectric.buildActivity(Activity.class).setup().get();
        panel=new ModernCameraPanel(activity,new LinearLayout(activity),activity.getSharedPreferences("connection-ui",Context.MODE_PRIVATE),tasks::add,images->{});
    }
    @After public void tearDown(){panel.close();activity.finish();}
    private <T>T field(String name){return ReflectionHelpers.getField(panel,name);}
    private void begin(){ReflectionHelpers.callInstanceMethod(panel,"beginConnection");shadowOf(Looper.getMainLooper()).idle();}
    private void fail(String text){ReflectionHelpers.callInstanceMethod(panel,"connectionFailure",from(String.class,text));}
    @Test public void progressAndCopyStayVisibleWhileConnecting(){
        begin();AlertDialog dialog=field("connectionDialog");assertTrue(dialog.isShowing());
        ReflectionHelpers.callInstanceMethod(panel,"connectionProgress",from(String.class,"App-ID anmelden"));
        assertEquals("App-ID anmelden",((TextView)field("connectionProgress")).getText().toString());
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();shadowOf(Looper.getMainLooper()).idle();assertTrue(dialog.isShowing());
        ClipboardManager clipboard=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
        assertTrue(clipboard.getPrimaryClip().getItemAt(0).getText().toString().contains("ui.start"));
    }
    @Test public void failureDismissesProgressAndShowsPersistentCopyableError(){
        begin();AlertDialog progress=field("connectionDialog");fail("Bluetooth-Verbindung fehlgeschlagen (133).");
        assertFalse(progress.isShowing());assertFalse((Boolean)field("connecting"));assertNull(field("connectionDialog"));
        AlertDialog error=field("errorDialog");assertTrue(error.isShowing());
        assertEquals("Bluetooth-Verbindung fehlgeschlagen (133).",shadowOf(error).getMessage().toString());
        error.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();shadowOf(Looper.getMainLooper()).idle();
        ClipboardManager clipboard=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
        String report=clipboard.getPrimaryClip().getItemAt(0).getText().toString();assertTrue(report.contains("ui.error"));assertFalse(report.contains("fehlgeschlagen"));
    }
    @Test public void selectingCameraThenGattFailureShowsErrorWithoutAnotherTap(){
        BluetoothDevice device=BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55");
        ReflectionHelpers.callInstanceMethod(panel,"cameraSelected",from(BluetoothDevice.class,device));
        AlertDialog progress=field("connectionDialog");assertTrue(progress.isShowing());
        BluetoothGatt gatt=shadowOf(device).getBluetoothGatts().get(0);
        shadowOf(gatt).getGattCallback().onConnectionStateChange(gatt,133,BluetoothProfile.STATE_DISCONNECTED);
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(progress.isShowing());AlertDialog error=field("errorDialog");assertTrue(error.isShowing());
        assertTrue(shadowOf(error).getMessage().toString().contains("133"));
        assertTrue(((ConnectionTrace)field("trace")).report().contains("gatt.status  133"));
    }
    @Test public void cancelEndsAttemptAndCloseDismissesDialogs(){
        begin();AlertDialog progress=field("connectionDialog");progress.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();shadowOf(Looper.getMainLooper()).idle();
        assertFalse(progress.isShowing());assertFalse((Boolean)field("connecting"));assertNull(field("errorDialog"));
        assertTrue(((ConnectionTrace)field("trace")).report().contains("ui.cancel"));
        begin();fail("Abbruch");AlertDialog error=field("errorDialog");panel.close();assertFalse(error.isShowing());
    }
    @Test public void workerFailureIsVisibleAndCanceledOldWorkerCannotCloseNewAttempt() throws Exception {
        begin();ModernCameraPanel.Task task=()->{throw new IOException("HTTPS-Anmeldung abgelehnt");};
        ReflectionHelpers.callInstanceMethod(panel,"run",from(ModernCameraPanel.Task.class,task));
        tasks.remove().run();shadowOf(Looper.getMainLooper()).idle();
        assertTrue(((AlertDialog)field("errorDialog")).isShowing());
        begin();ReflectionHelpers.callInstanceMethod(panel,"run",from(ModernCameraPanel.Task.class,task));
        ReflectionHelpers.callInstanceMethod(panel,"disconnectCamera");begin();AlertDialog current=field("connectionDialog");
        tasks.remove().run();shadowOf(Looper.getMainLooper()).idle();
        assertTrue(current.isShowing());assertNull(field("errorDialog"));assertTrue((Boolean)field("connecting"));
    }
}
