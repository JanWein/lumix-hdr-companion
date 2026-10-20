package de.weinert.lumixhdr;

import android.content.Context;
import android.net.*;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Handler;
import android.os.Looper;
import java.net.Inet4Address;

/** Holds Android's consented, local-only camera network for the lifetime of a session. */
final class CameraWifiConnector implements AutoCloseable {
    interface Callback {
        void connected(Network network, String gateway);
        void lost();
        void error(String message);
    }
    private final ConnectivityManager manager;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Callback callback;
    private boolean closed,registered,delivered;
    private Network selected;
    private final Runnable timeout=()->fail("Kamera-WLAN nicht erreichbar. Android-Freigabe und Kamera prüfen.");
    private final ConnectivityManager.NetworkCallback observer=new ConnectivityManager.NetworkCallback(){
        @Override public void onAvailable(Network network){if(!closed)selected=network;}
        @Override public void onLinkPropertiesChanged(Network network,LinkProperties properties){
            if(closed||delivered||!network.equals(selected))return;
            String gateway="";
            for(RouteInfo route:properties.getRoutes())if(route.isDefaultRoute()&&route.getGateway() instanceof Inet4Address){gateway=route.getGateway().getHostAddress();break;}
            if(gateway.isEmpty())return; // DHCP may publish addresses and routes in separate updates.
            delivered=true;handler.removeCallbacks(timeout);callback.connected(network,gateway);
        }
        @Override public void onUnavailable(){fail("Kamera-WLAN abgelehnt oder nicht gefunden. Erneut verbinden und Androids WLAN-Dialog bestätigen.");}
        @Override public void onLost(Network network){
            if(closed||!network.equals(selected))return;
            boolean wasConnected=delivered;close();
            if(wasConnected)callback.lost();else callback.error("Kamera-WLAN während des Verbindens verloren.");
        }
    };
    CameraWifiConnector(Context context,Callback callback){manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);this.callback=callback;}
    void connect(String ssid,String password){
        if(closed||registered)throw new IllegalStateException("WLAN-Anfrage wurde bereits verwendet");
        try{
            WifiNetworkSpecifier.Builder spec=new WifiNetworkSpecifier.Builder().setSsid(ssid);
            if(password!=null&&!password.isEmpty())spec.setWpa2Passphrase(password);
            NetworkRequest request=new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).setNetworkSpecifier(spec.build()).build();
            manager.requestNetwork(request,observer,handler);registered=true;handler.postDelayed(timeout,45000);
        }catch(SecurityException e){fail("WLAN-Freigabe fehlt. Geräte in der Nähe und auf Android 10–12 den Standortzugriff erlauben.");}
        catch(IllegalArgumentException e){fail("Die Kamera hat ungültige WLAN-Verbindungsdaten geliefert.");}
    }
    private void fail(String message){if(closed)return;close();callback.error(message);}
    @Override public void close(){
        if(closed)return;closed=true;handler.removeCallbacks(timeout);
        if(registered){manager.unregisterNetworkCallback(observer);registered=false;}selected=null;
    }
}
