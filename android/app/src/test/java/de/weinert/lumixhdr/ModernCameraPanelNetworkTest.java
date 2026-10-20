package de.weinert.lumixhdr;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.*;
import android.os.Looper;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import org.robolectric.util.ReflectionHelpers;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Exercises the actual panel and authentication code without camera or Internet access. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,35},shadows=ModernCameraPanelNetworkTest.CameraNetworkShadow.class)
public class ModernCameraPanelNetworkTest {
    private Activity activity;
    private SharedPreferences prefs;
    private ModernCameraPanel panel;
    private ShadowConnectivityManager connectivity;
    private Network cameraNetwork;
    private ConnectivityManager.NetworkCallback callback;
    private final Queue<ModernCameraPanel.Task> tasks=new ArrayDeque<>();
    private static final String PIN="00".repeat(32);

    @Before public void setUp(){
        activity=Robolectric.buildActivity(Activity.class).setup().get();
        prefs=activity.getSharedPreferences("network-test",Context.MODE_PRIVATE);
        prefs.edit().clear().putString("camera_pin",PIN).apply();
        connectivity=shadowOf((ConnectivityManager)activity.getSystemService(Context.CONNECTIVITY_SERVICE));
        cameraNetwork=ShadowNetwork.newInstance(50);
        connectivity.addNetwork(cameraNetwork,ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,ConnectivityManager.TYPE_WIFI,0,true,true));
        connectivity.setNetworkCapabilities(cameraNetwork,wifi());
        CameraNetworkShadow.openCount=0;
        CameraNetworkShadow.beforeResponse=()->{};
        panel=new ModernCameraPanel(activity,new LinearLayout(activity),prefs,tasks::add,images->{});
        callback=ReflectionHelpers.getField(panel,"networkCallback");
    }
    @After public void tearDown(){panel.close();activity.finish();}

    private NetworkCapabilities wifi(){return capabilities(NetworkCapabilities.TRANSPORT_WIFI);}
    private NetworkCapabilities capabilities(int transport){NetworkCapabilities caps=ShadowNetworkCapabilities.newInstance();shadowOf(caps).addTransportType(transport);return caps;}
    private void select(){ReflectionHelpers.callInstanceMethod(panel,"selectedNetwork",ReflectionHelpers.ClassParameter.from(Network.class,cameraNetwork),ReflectionHelpers.ClassParameter.from(boolean.class,false));}
    private void authenticate() throws Exception {select();tasks.remove().run();shadowOf(Looper.getMainLooper()).idle();}
    private Object field(String name){return ReflectionHelpers.getField(panel,name);}
    private int epoch(){return ((AtomicInteger)field("configuration")).get();}

    @Test public void registersWifiObserverAndClosesItIdempotently(){
        assertTrue(connectivity.getNetworkCallbacks().contains(callback));
        panel.close();panel.close();
        assertFalse(connectivity.getNetworkCallbacks().contains(callback));
    }
    @Test public void acceptsCameraWifiWithoutInternetWhileCellularIsDefault() throws Exception {
        assertFalse(wifi().hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        assertFalse(wifi().hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        authenticate();
        assertTrue(panel.isConnected());
        assertEquals(1,CameraNetworkShadow.openCount);
    }
    @Test public void selectedNetworkLossClearsSessionAndUiImmediately() throws Exception {
        authenticate();LumixLabClient old=(LumixLabClient)field("client");int before=epoch();
        callback.onLost(cameraNetwork);
        assertFalse(old.isAuthenticated());assertFalse(panel.isConnected());
        assertNull(field("network"));assertNull(field("pendingNetwork"));assertTrue(epoch()>before);
        assertFalse(((TextView)field("browseAction")).isEnabled());
    }
    @Test public void unrelatedNetworkLossKeepsCameraSession() throws Exception {
        authenticate();int before=epoch();
        callback.onLost(ShadowNetwork.newInstance(99));
        assertTrue(panel.isConnected());assertEquals(before,epoch());
    }
    @Test public void losingWifiTransportInvalidatesSession() throws Exception {
        authenticate();
        callback.onCapabilitiesChanged(cameraNetwork,capabilities(NetworkCapabilities.TRANSPORT_CELLULAR));
        assertFalse(panel.isConnected());assertNull(field("network"));
    }
    @Test public void networkLostBeforeWorkerStartsDoesNotOpenConnection() throws Exception {
        select();connectivity.setNetworkCapabilities(cameraNetwork,null);
        tasks.remove().run();
        assertEquals(0,CameraNetworkShadow.openCount);assertFalse(panel.isConnected());
    }
    @Test public void lossDuringAuthenticationCannotPublishEvenIfCapabilitiesReturn() throws Exception {
        int before=epoch();
        CameraNetworkShadow.beforeResponse=()->callback.onLost(cameraNetwork);
        select();tasks.remove().run();
        assertTrue(epoch()>before);assertFalse(panel.isConnected());assertNull(field("pendingNetwork"));
        assertFalse(((TextView)field("state")).getText().toString().contains("ist verbunden"));
    }
    @Test public void postAuthenticationCheckRejectsLossBeforeCallbackArrives() throws Exception {
        CameraNetworkShadow.beforeResponse=()->connectivity.setNetworkCapabilities(cameraNetwork,null);
        select();tasks.remove().run();
        assertFalse(panel.isConnected());assertNull(field("network"));assertNull(field("pendingNetwork"));
    }
    @Test public void explicitDisconnectClearsNetworkAndSession() throws Exception {
        authenticate();((TextView)field("disconnectAction")).performClick();
        assertFalse(panel.isConnected());assertNull(field("network"));assertNull(field("pendingNetwork"));
    }
    @Test public void queuedSuccessCannotOverwriteNetworkLossOrPersistStaleSettings() throws Exception {
        select();ModernCameraPanel.Task task=tasks.remove();AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread worker=new Thread(()->{try{task.run();}catch(Throwable error){failure.set(error);}});
        worker.start();worker.join(10000);assertFalse(worker.isAlive());assertNull(failure.get());
        callback.onLost(cameraNetwork);shadowOf(Looper.getMainLooper()).idle();
        assertFalse(panel.isConnected());assertFalse(prefs.contains("camera_host"));
        assertFalse(((TextView)field("state")).getText().toString().contains("ist verbunden"));
    }

    @Implements(Network.class)
    public static class CameraNetworkShadow extends ShadowNetwork {
        static int openCount;static Runnable beforeResponse;
        @Implementation protected URLConnection openConnection(URL url){
            openCount++;
            return new HttpsURLConnection(url){
                @Override public OutputStream getOutputStream(){return new ByteArrayOutputStream();}
                @Override public int getResponseCode(){return 200;}
                @Override public InputStream getInputStream(){
                    beforeResponse.run();
                    return new ByteArrayInputStream("{\"Header\":{\"ResponseCode\":200},\"Data\":{\"AuthToken\":\"test-camera-token\"}}".getBytes(StandardCharsets.UTF_8));
                }
                @Override public void connect(){}
                @Override public void disconnect(){}
                @Override public boolean usingProxy(){return false;}
                @Override public String getCipherSuite(){return "test";}
                @Override public Certificate[] getLocalCertificates(){return null;}
                @Override public Certificate[] getServerCertificates(){return null;}
            };
        }
    }
}
