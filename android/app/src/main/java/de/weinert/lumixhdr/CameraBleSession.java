package de.weinert.lumixhdr;

import android.bluetooth.*;
import android.content.Context;
import android.os.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One foreground BLE handshake; protocol observations and remaining limits are in docs/S9_CONNECTION.md. */
@SuppressWarnings({"MissingPermission","deprecation"})
final class CameraBleSession implements AutoCloseable {
    static final UUID SERVICE=id("1fe71b85-8344-4e6a-bf1a-ac7acdb3f8af"),
        INITIAL=id("239c7203-922f-43e9-bdf1-6d1fedf6f43f"),APP_UUID=id("bff12e7f-5944-4eb6-a90f-48d8786157ea"),
        APP_INFO=id("ed8b1352-5aa7-4155-b7c9-521292dd1932"),NAME=id("05a797b6-9c2f-4d18-99a7-fa61a3880bfe"),
        REGISTER=id("36f99090-e79b-4060-9ac7-89f3f8bf88d5"),SSID=id("657edba2-5bc8-42dd-974f-21871d7dbcd1"),
        PASSWORD=id("42d509f7-f122-4816-92d1-5e402d9453cb"),FINGERPRINT=CameraFingerprintReader.FINGERPRINT,
        ACTION=id("7644371c-1ddc-4b16-832f-f07c322e311b"),AP_STATE=id("dcba3a74-80bc-4919-8ef4-0c9f99cc20dd"),
        ACTION_RESULT=id("6c875728-7636-425e-818a-f1d93a39e075"),CCCD=id("00002902-0000-1000-8000-00805f9b34fb");
    interface Callback {void progress(String message);void ready(String ssid,String password,String fingerprint);void error(String message);}
    interface Received {void accept(byte[] value) throws Exception;}
    private static UUID id(String value){return UUID.fromString(value);}
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Callback callback;
    private final ArrayDeque<Operation> queue=new ArrayDeque<>();
    private BluetoothGatt gatt;private BluetoothGattService service;private Operation pending;
    private boolean closed,started,waitingAp,delivered,apReady,actionWritten;
    private byte[] key;private String ssid,password,fingerprint,uuid,name;
    private final Runnable deadline=()->fail("Bluetooth-Einrichtung nach 60 Sekunden abgebrochen. Kopplung an der Kamera bestätigen und erneut suchen.");
    private final Runnable operationTimeout=()->fail("Keine Bluetooth-Antwort bei "+stage()+". Kamera in Kopplungsbereitschaft versetzen.");
    private String stage(){return pending==null?"Verbindungsaufbau":pending.label;}
    private static final class Operation {
        final UUID uuid;final byte[] value;final boolean descriptor;final int writeType;final String label;final Received received;
        Operation(UUID uuid,byte[] value,boolean descriptor,int writeType,String label,Received received){this.uuid=uuid;this.value=value;this.descriptor=descriptor;this.writeType=writeType;this.label=label;this.received=received;}
    }
    CameraBleSession(Callback callback){this.callback=callback;}
    void connect(Context context,BluetoothDevice device,String ownUuid,String friendlyName){
        if(started||closed)throw new IllegalStateException("Bluetooth-Sitzung wurde bereits verwendet");started=true;uuid=ownUuid;name=friendlyName;
        handler.postDelayed(deadline,60000);callback.progress("Verbinde Bluetooth mit deiner Kamera …");
        try{gatt=device.connectGatt(context,false,observer,BluetoothDevice.TRANSPORT_LE);if(gatt==null)fail("Bluetooth-Verbindung konnte nicht gestartet werden.");}
        catch(SecurityException e){fail("Bluetooth-Freigabe fehlt.");}
    }
    private final BluetoothGattCallback observer=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt connection,int status,int state){post(()->{
            if(status!=BluetoothGatt.GATT_SUCCESS){fail("Bluetooth-Verbindung fehlgeschlagen ("+status+").");return;}
            if(state==BluetoothProfile.STATE_CONNECTED){if(!connection.requestMtu(57))fail("Bluetooth-MTU konnte nicht angefordert werden.");}
            else if(state==BluetoothProfile.STATE_DISCONNECTED)fail("Bluetooth-Verbindung zur Kamera getrennt.");
        });}
        @Override public void onMtuChanged(BluetoothGatt connection,int mtu,int status){post(()->{
            if(status!=BluetoothGatt.GATT_SUCCESS||mtu<39){fail("Bluetooth-MTU reicht für die Kamera-Anmeldung nicht aus.");return;}
            if(!connection.discoverServices())fail("Bluetooth-Dienste konnten nicht gelesen werden.");
        });}
        @Override public void onServicesDiscovered(BluetoothGatt connection,int status){post(()->{
            if(status!=BluetoothGatt.GATT_SUCCESS){fail("Bluetooth-Dienstabfrage fehlgeschlagen ("+status+").");return;}
            service=connection.getService(SERVICE);if(service==null){fail("Die Kamera bietet den LUMIX-Lab-Bluetooth-Dienst nicht an.");return;}
            subscribe(AP_STATE);subscribe(ACTION_RESULT);
            read(INITIAL,"Kameraschlüssel lesen",value->{
                if(value==null||value.length<4)throw new IOException("Kamera-Anmeldeschlüssel fehlt");key=value.clone();
                callback.progress("Melde diese App bei deiner Kamera an. Bestätige eine Kopplungsabfrage an der Kamera.");
                write(APP_UUID,LumixBleCodec.encode(LumixBleCodec.uuid(uuid),key),2,"Eigene App-ID anmelden",v->{});
                write(APP_INFO,LumixBleCodec.appInfo(),2,"App-Information anmelden",v->{});
                write(NAME,LumixBleCodec.encode(name.getBytes(StandardCharsets.UTF_8),key),2,"App-Name anmelden",v->{});
                write(REGISTER,new byte[]{2},2,"Kamera-Anmeldung abschließen",v->readCredentials());
            });pump();
        });}
        @Override public void onCharacteristicRead(BluetoothGatt connection,BluetoothGattCharacteristic c,int status){readResult(c,c.getValue(),status);}
        @Override public void onCharacteristicRead(BluetoothGatt connection,BluetoothGattCharacteristic c,byte[] value,int status){readResult(c,value,status);}
        @Override public void onCharacteristicWrite(BluetoothGatt connection,BluetoothGattCharacteristic c,int status){post(()->complete(c.getUuid(),false,status,null));}
        @Override public void onDescriptorWrite(BluetoothGatt connection,BluetoothGattDescriptor descriptor,int status){post(()->complete(descriptor.getCharacteristic().getUuid(),true,status,null));}
        @Override public void onCharacteristicChanged(BluetoothGatt connection,BluetoothGattCharacteristic c){notification(c.getUuid(),c.getValue());}
        @Override public void onCharacteristicChanged(BluetoothGatt connection,BluetoothGattCharacteristic c,byte[] value){notification(c.getUuid(),value);}
    };
    private void readResult(BluetoothGattCharacteristic characteristic,byte[] value,int status){byte[] snapshot=value==null?null:value.clone();post(()->complete(characteristic.getUuid(),false,status,snapshot));}
    private void notification(UUID characteristic,byte[] value){byte[] snapshot=value==null?null:value.clone();post(()->{
        if(ACTION_RESULT.equals(characteristic)&&snapshot!=null&&snapshot.length>0&&snapshot[0]==1){fail("Kamera hat den WLAN-Start abgelehnt. Erneut verbinden und die Kamera bestätigen.");return;}
        if(AP_STATE.equals(characteristic)&&snapshot!=null&&snapshot.length>0){apReady=snapshot[0]==1;deliver();}
    });}
    private void readCredentials(){
        read(SSID,"Kamera-WLAN lesen",value->{ssid=LumixBleCodec.text(value);if(ssid.isEmpty())throw new IOException("Kamera liefert keinen WLAN-Namen");});
        read(PASSWORD,"Kamera-WLAN-Schlüssel lesen",value->password=LumixBleCodec.text(LumixBleCodec.decode(value,key)));
        read(FINGERPRINT,"Kamera-Zertifikat lesen",value->{fingerprint=CameraTrust.formatPin(CameraTrust.parsePin(LumixBleCodec.text(value)));startAccessPoint();});
    }
    private void startAccessPoint() throws Exception {
        int length=password.getBytes(StandardCharsets.UTF_8).length;
        if(length<8||length>63)throw new IOException("Kamera liefert keinen gültigen WPA2-WLAN-Schlüssel");
        waitingAp=true;apReady=false;actionWritten=false;callback.progress("Starte Kamera-WLAN über Bluetooth …");
        write(ACTION,new byte[]{1},BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,"Kamera-WLAN starten",v->{actionWritten=true;deliver();});
        BluetoothGattCharacteristic state=characteristic(AP_STATE);
        if((state.getProperties()&BluetoothGattCharacteristic.PROPERTY_READ)!=0)read(AP_STATE,"Kamera-WLAN-Status lesen",value->{if(value!=null&&value.length>0){apReady=value[0]==1;deliver();}});
    }
    private void deliver(){
        if(!waitingAp||!actionWritten||!apReady||delivered||fingerprint==null)return;
        delivered=true;handler.removeCallbacks(deadline);handler.removeCallbacks(operationTimeout);queue.clear();pending=null;
        callback.ready(ssid,password,fingerprint);password=null;
    }
    private void subscribe(UUID uuid) throws Exception {
        BluetoothGattCharacteristic c=characteristic(uuid);
        if((c.getProperties()&(BluetoothGattCharacteristic.PROPERTY_NOTIFY|BluetoothGattCharacteristic.PROPERTY_INDICATE))==0)return;
        if(!gatt.setCharacteristicNotification(c,true))throw new IOException("Bluetooth-Benachrichtigungen konnten nicht aktiviert werden");
        if(c.getDescriptor(CCCD)!=null){byte[] value=(c.getProperties()&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0?BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE:BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;queue.add(new Operation(uuid,value,true,2,"Kamera-Benachrichtigungen aktivieren",v->{}));}
    }
    private BluetoothGattCharacteristic characteristic(UUID uuid) throws IOException {BluetoothGattCharacteristic c=service.getCharacteristic(uuid);if(c==null)throw new IOException("Bluetooth-Merkmal fehlt: "+uuid);return c;}
    private void read(UUID uuid,String label,Received result){queue.add(new Operation(uuid,null,false,0,label,result));}
    private void write(UUID uuid,byte[] value,int writeType,String label,Received done){queue.add(new Operation(uuid,value,false,writeType,label,done));}
    private void pump() throws Exception {
        if(closed||pending!=null||queue.isEmpty())return;pending=queue.remove();BluetoothGattCharacteristic c=characteristic(pending.uuid);boolean accepted;
        if(pending.descriptor){BluetoothGattDescriptor descriptor=c.getDescriptor(CCCD);descriptor.setValue(pending.value);accepted=gatt.writeDescriptor(descriptor);}
        else if(pending.value==null)accepted=gatt.readCharacteristic(c);
        else{c.setWriteType(pending.writeType);c.setValue(pending.value);accepted=gatt.writeCharacteristic(c);}
        if(!accepted)throw new IOException("Bluetooth-Auftrag nicht gestartet: "+pending.label);
        handler.postDelayed(operationTimeout,15000);
    }
    private void complete(UUID uuid,boolean descriptor,int status,byte[] value) throws Exception {
        if(pending==null||pending.descriptor!=descriptor||!pending.uuid.equals(uuid))return;
        handler.removeCallbacks(operationTimeout);Operation done=pending;pending=null;
        if(status!=BluetoothGatt.GATT_SUCCESS)throw new IOException(done.label+": Bluetooth-Code "+status+". Kopplung an der Kamera bestätigen.");
        done.received.accept(value);pump();
    }
    private interface Checked {void run() throws Exception;}
    private void post(Checked action){handler.post(()->{if(closed)return;try{action.run();}catch(Exception e){fail(e instanceof SecurityException?"Bluetooth-Freigabe fehlt.":e.getMessage());}});}
    private void fail(String message){if(closed)return;close();callback.error(message==null?"Bluetooth-Einrichtung fehlgeschlagen.":message);}
    @Override public void close(){
        if(closed)return;closed=true;handler.removeCallbacksAndMessages(null);queue.clear();pending=null;password=null;
        if(key!=null){Arrays.fill(key,(byte)0);key=null;}
        if(gatt!=null){try{gatt.disconnect();}catch(SecurityException ignored){}try{gatt.close();}catch(SecurityException ignored){}gatt=null;}
    }
}
