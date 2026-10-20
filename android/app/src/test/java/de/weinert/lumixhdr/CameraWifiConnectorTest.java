package de.weinert.lumixhdr;

import android.content.Context;
import android.net.*;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import org.robolectric.util.ReflectionHelpers;
import java.net.InetAddress;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,35},shadows=CameraWifiConnectorTest.CapturingConnectivity.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class CameraWifiConnectorTest {
    private CameraWifiConnector connector;
    private CapturingConnectivity connectivity;
    private final List<Network> connected=new ArrayList<>();
    private final List<String> gateways=new ArrayList<>(),errors=new ArrayList<>();
    private int lost;
    @Before public void setUp(){
        connectivity=(CapturingConnectivity)shadowOf((ConnectivityManager)RuntimeEnvironment.getApplication().getSystemService(Context.CONNECTIVITY_SERVICE));
        connectivity.denied=false;
        connector=new CameraWifiConnector(RuntimeEnvironment.getApplication(),new CameraWifiConnector.Callback(){
            public void connected(Network n,String gateway){connected.add(n);gateways.add(gateway);}
            public void lost(){lost++;}
            public void error(String message){errors.add(message);}
        });
    }
    @After public void tearDown(){connector.close();}
    private void connect(){connector.connect("LUMIX-S9","testpassword");}
    private ConnectivityManager.NetworkCallback observer(){return connectivity.getNetworkCallbacks().iterator().next();}
    private LinkProperties routes() throws Exception {
        LinkProperties properties=new LinkProperties();
        ReflectionHelpers.callInstanceMethod(properties,"setInterfaceName",from(String.class,"wlan0"));
        RouteInfo route=ReflectionHelpers.callConstructor(RouteInfo.class,from(IpPrefix.class,new IpPrefix(InetAddress.getByName("0.0.0.0"),0)),from(InetAddress.class,InetAddress.getByName("192.168.54.1")),from(String.class,"wlan0"));
        ReflectionHelpers.callInstanceMethod(properties,"addRoute",from(RouteInfo.class,route));return properties;
    }
    @Test public void requestsSpecificLocalWifiWithoutInternetCapability(){
        connect();NetworkCapabilities caps=ReflectionHelpers.getField(connectivity.request,"networkCapabilities");
        assertTrue(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));assertFalse(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        WifiNetworkSpecifier spec=ReflectionHelpers.callInstanceMethod(caps,"getNetworkSpecifier");
        PatternMatcher ssid=ReflectionHelpers.getField(spec,"ssidPatternMatcher");assertTrue(ssid.match("LUMIX-S9"));assertFalse(ssid.match("Home Wifi"));
        assertEquals(Looper.getMainLooper(),connectivity.handler.getLooper());assertEquals(1,connectivity.getNetworkCallbacks().size());
    }
    @Test public void keepsNetworkRequestedAfterDeliveryUntilExplicitClose() throws Exception {
        connect();Network network=ShadowNetwork.newInstance(60);ConnectivityManager.NetworkCallback cb=observer();
        cb.onAvailable(network);cb.onLinkPropertiesChanged(network,routes());cb.onLinkPropertiesChanged(network,routes());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1));
        assertEquals(Collections.singletonList(network),connected);assertEquals(Collections.singletonList("192.168.54.1"),gateways);assertTrue(errors.isEmpty());
        assertEquals(1,connectivity.getNetworkCallbacks().size());connector.close();connector.close();assertTrue(connectivity.getNetworkCallbacks().isEmpty());
    }
    @Test public void selectedNetworkLossReportsOnceAndUnregisters() throws Exception {
        connect();Network network=ShadowNetwork.newInstance(60);ConnectivityManager.NetworkCallback cb=observer();cb.onAvailable(network);cb.onLinkPropertiesChanged(network,routes());
        cb.onLost(ShadowNetwork.newInstance(99));assertEquals(0,lost);cb.onLost(network);cb.onLost(network);
        assertEquals(1,lost);assertTrue(errors.isEmpty());assertTrue(connectivity.getNetworkCallbacks().isEmpty());
    }
    @Test public void waitsForIpv4GatewayAcrossSeparateDhcpUpdates() throws Exception {
        connect();Network network=ShadowNetwork.newInstance(60);ConnectivityManager.NetworkCallback cb=observer();cb.onAvailable(network);
        cb.onLinkPropertiesChanged(network,new LinkProperties());assertTrue(connected.isEmpty());
        cb.onLinkPropertiesChanged(network,routes());assertEquals(Collections.singletonList("192.168.54.1"),gateways);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1));assertTrue(errors.isEmpty());
    }
    @Test public void lossBeforeLinkPropertiesReportsConnectionError(){
        connect();Network network=ShadowNetwork.newInstance(60);ConnectivityManager.NetworkCallback cb=observer();cb.onAvailable(network);cb.onLost(network);
        assertEquals(1,errors.size());assertEquals(0,lost);assertTrue(connected.isEmpty());assertTrue(connectivity.getNetworkCallbacks().isEmpty());
    }
    @Test public void unavailableAndTimeoutAreTerminalAndReleaseRequest(){
        connect();observer().onUnavailable();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1));
        assertEquals(1,errors.size());assertTrue(connectivity.getNetworkCallbacks().isEmpty());
    }
    @Test public void missingNetworkTimesOutAndUnregisters(){
        connect();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(44));assertTrue(errors.isEmpty());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));assertEquals(1,errors.size());assertTrue(connectivity.getNetworkCallbacks().isEmpty());
    }
    @Test public void cancellationSuppressesLateCallbacksAndTimeout() throws Exception {
        connect();ConnectivityManager.NetworkCallback cb=observer();connector.close();Network n=ShadowNetwork.newInstance(60);
        cb.onAvailable(n);cb.onLinkPropertiesChanged(n,routes());cb.onUnavailable();cb.onLost(n);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1));
        assertTrue(connected.isEmpty());assertTrue(errors.isEmpty());assertEquals(0,lost);assertTrue(connectivity.getNetworkCallbacks().isEmpty());
    }
    @Test public void permissionDenialIsReportedWithoutLeavingRequest(){
        connectivity.denied=true;connect();assertEquals(1,errors.size());assertTrue(errors.get(0).contains("Freigabe"));assertTrue(connectivity.getNetworkCallbacks().isEmpty());
    }
    @Implements(ConnectivityManager.class)
    public static class CapturingConnectivity extends ShadowConnectivityManager {
        NetworkRequest request;Handler handler;boolean denied;
        @Implementation protected void requestNetwork(NetworkRequest request,ConnectivityManager.NetworkCallback callback,Handler handler){
            if(denied)throw new SecurityException("Test permission denied");this.request=request;this.handler=handler;super.requestNetwork(request,callback,handler);
        }
    }
}
