package de.weinert.lumixhdr;

import android.content.Context;
import android.net.*;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.net.Inet4Address;
import java.util.Locale;

/** Holds Android's consented, local-only camera network for the lifetime of a session. */
final class CameraWifiConnector implements AutoCloseable {
    interface Callback {
        void connected(Network network, String gateway);
        void lost();
        void error(String message);
        default void diagnostic(String event,int value){}
    }
    private final ConnectivityManager manager;
    private final Context context;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Callback callback;
    private boolean closed,registered,delivered;
    private Network selected;
    private final Runnable timeout=()->timedOut();
    private void timedOut(){callback.diagnostic("wifi.timeout",0);fail("Kamera-WLAN nicht erreichbar. Android-Freigabe und Kamera prüfen.");}
    private final ConnectivityManager.NetworkCallback observer=new ConnectivityManager.NetworkCallback(){
        @Override public void onAvailable(Network network){if(!closed){selected=network;callback.diagnostic("wifi.available",1);}}
        @Override public void onLinkPropertiesChanged(Network network,LinkProperties properties){
            if(closed||delivered||!network.equals(selected))return;
            String gateway="";
            for(RouteInfo route:properties.getRoutes())if(route.isDefaultRoute()&&route.getGateway() instanceof Inet4Address){gateway=route.getGateway().getHostAddress();break;}
            callback.diagnostic("wifi.dhcp",gateway.isEmpty()?0:1);
            if(gateway.isEmpty())return; // DHCP may publish addresses and routes in separate updates.
            delivered=true;handler.removeCallbacks(timeout);callback.connected(network,gateway);
        }
        @Override public void onUnavailable(){if(closed)return;callback.diagnostic("wifi.unavailable",0);fail("Kamera-WLAN abgelehnt oder nicht gefunden. Erneut verbinden und Androids WLAN-Dialog bestätigen.");}
        @Override public void onLost(Network network){
            if(closed||!network.equals(selected))return;
            boolean wasConnected=delivered;callback.diagnostic("wifi.lost",wasConnected?1:0);close();
            if(wasConnected)callback.lost();else callback.error("Kamera-WLAN während des Verbindens verloren.");
        }
    };
    CameraWifiConnector(Context context,Callback callback){manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);this.context=context.getApplicationContext();this.callback=callback;}
    static boolean harmonyCompatibility(String manufacturer,String brand,String osVersion,String vmName,String enabled,String osBrand){
        return "true".equals(enabled)||("HUAWEI".equalsIgnoreCase(manufacturer)&&"HMS".equalsIgnoreCase(brand))
            ||containsHarmony(osVersion)||containsHarmony(vmName)||"Harmony".equalsIgnoreCase(osBrand);
    }
    private static boolean containsHarmony(String value){return value!=null&&value.toLowerCase(Locale.ROOT).contains("harmony");}
    private static String systemProperty(String name){try{return System.getProperty(name,"");}catch(SecurityException e){return "";}}
    private static String harmonyEnabled(){
        try{return String.valueOf(Class.forName("android.os.SystemProperties").getDeclaredMethod("get",String.class).invoke(null,"hw_sc.build.os.enable"));}
        catch(Exception|LinkageError e){return "";}
    }
    private static String harmonyOsBrand(){
        try{return String.valueOf(Class.forName("com.huawei.system.BuildEx").getMethod("getOsBrand").invoke(null));}
        catch(Exception|LinkageError e){return "";}
    }
    static boolean prefersWpa3(Context context){
        WifiManager wifi=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wifi!=null&&wifi.isWpa3SaeSupported()&&!harmonyCompatibility(Build.MANUFACTURER,Build.BRAND,systemProperty("os.version"),systemProperty("java.vm.name"),harmonyEnabled(),harmonyOsBrand());
    }
    void connect(String ssid,String password){
        if(closed||registered)throw new IllegalStateException("WLAN-Anfrage wurde bereits verwendet");
        try{
            WifiNetworkSpecifier.Builder spec=new WifiNetworkSpecifier.Builder().setSsid(ssid);
            int security=0;
            if(password!=null&&!password.isEmpty()){
                if(prefersWpa3(context)){spec.setWpa3Passphrase(password);security=3;}else{spec.setWpa2Passphrase(password);security=2;}
            }
            callback.diagnostic("wifi.security",security);
            NetworkRequest request=new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).setNetworkSpecifier(spec.build()).build();
            manager.requestNetwork(request,observer,handler);registered=true;handler.postDelayed(timeout,45000);
        }catch(SecurityException e){fail("WLAN-Freigabe fehlt. Geräte in der Nähe und auf Android 10–12 den Standortzugriff erlauben.");}
        catch(IllegalArgumentException e){fail("Die Kamera hat ungültige WLAN-Verbindungsdaten geliefert.");}
    }
    private void fail(String message){if(closed)return;close();callback.error(message);}
    @Override public void close(){
        if(closed)return;closed=true;handler.removeCallbacks(timeout);
        callback.diagnostic("wifi.closed",0);
        if(registered){manager.unregisterNetworkCallback(observer);registered=false;}selected=null;
    }
}
