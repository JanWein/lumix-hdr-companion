package de.weinert.lumixhdr;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/** Bounded connection events. Never accepts request bodies, credentials, addresses or free-form errors. */
final class ConnectionTrace {
    private static final Set<String> EVENTS=new HashSet<>(Arrays.asList(
        "ui.start","ui.cancel","ui.error","ui.permissions","gatt.connected","gatt.disconnected",
        "gatt.connection","gatt.mtu","gatt.services","gatt.timeout","ap.state","ap.result",
        "gatt.status","gatt.state","gatt.mtu.request","gatt.mtu.status","gatt.services.status",
        "ble.ap.state","ble.action.result","ble.security","ble.ready","ble.closed","ble.timeout.session","ble.timeout.operation",
        "wifi.request","wifi.security","wifi.available","wifi.dhcp","wifi.lost","wifi.unavailable","wifi.timeout","wifi.closed","wifi.error","https.start","https.success","https.error"));
    private final ArrayDeque<String> rows=new ArrayDeque<>();
    private final String version;private final int sdk;private long start=System.nanoTime();
    ConnectionTrace(String version,int sdk){
        if(version==null||!version.matches("[0-9.]{1,16}"))throw new IllegalArgumentException("Invalid app version");
        this.version=version;this.sdk=sdk;
    }
    synchronized void reset(){rows.clear();start=System.nanoTime();record("ui.start",0);}
    synchronized void record(String event,int code){
        if(event==null||(!EVENTS.contains(event)&&!event.matches("(gatt|ble)\\.(read|write|read-result|write-result|complete|notify)\\.[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")))return;
        if(rows.size()==100)rows.removeFirst();
        rows.add(String.format(Locale.ROOT,"%6d ms  %s  %d",(System.nanoTime()-start)/1_000_000,event,code));
    }
    synchronized String report(){return "Lumix HDR "+version+" | Android API "+sdk+"\nVerbindungsdiagnose ohne Zugangsdaten\n"+String.join("\n",rows);}
}
