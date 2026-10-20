package de.weinert.lumixhdr;

import android.bluetooth.*;
import android.content.Context;
import android.os.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Reads only the observed CER_FINGER_PRINT characteristic. Does not implement registration. */
public final class CameraFingerprintReader implements AutoCloseable {
    public static final UUID FINGERPRINT=UUID.fromString("1be8a9e3-178b-46c4-b6b3-43d4a5311fc1");
    public interface Callback {void result(String pin,String error);}
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Callback callback;private BluetoothGatt gatt;private boolean finished;
    private final Runnable timeout=()->finish(null,"Kein BLE-Fingerabdruck nach 15 Sekunden. Kamera vorbereiten; Registrierung noch ungeklärt.");
    public CameraFingerprintReader(Callback callback){this.callback=callback;}
    @SuppressWarnings("MissingPermission") public void read(Context context,BluetoothDevice device){
        handler.postDelayed(timeout,15000);
        try{gatt=device.connectGatt(context,false,new BluetoothGattCallback(){
            @Override public void onConnectionStateChange(BluetoothGatt connection,int status,int state){
                if(status!=BluetoothGatt.GATT_SUCCESS){finish(null,"Bluetooth-Verbindung fehlgeschlagen ("+status+")");return;}
                if(state==BluetoothProfile.STATE_CONNECTED){try{if(!connection.discoverServices())finish(null,"BLE-Dienste konnten nicht abgefragt werden");}catch(SecurityException e){finish(null,"Bluetooth-Freigabe fehlt");}}
                else if(state==BluetoothProfile.STATE_DISCONNECTED)finish(null,"Bluetooth-Verbindung getrennt");
            }
            @Override public void onServicesDiscovered(BluetoothGatt connection,int status){
                if(status!=BluetoothGatt.GATT_SUCCESS){finish(null,"BLE-Dienstabfrage fehlgeschlagen");return;}
                for(BluetoothGattService service:connection.getServices()){
                    BluetoothGattCharacteristic c=service.getCharacteristic(FINGERPRINT);
                    if(c!=null){try{if(!connection.readCharacteristic(c))finish(null,"Fingerprint-Lesen nicht gestartet");}catch(SecurityException e){finish(null,"Bluetooth-Freigabe fehlt");}return;}
                }finish(null,"Kamera bietet CER_FINGER_PRINT nicht an oder benötigt vorher Registrierung");
            }
            @Override public void onCharacteristicRead(BluetoothGatt connection,BluetoothGattCharacteristic c,int status){received(c,c.getValue(),status);}
            @Override public void onCharacteristicRead(BluetoothGatt connection,BluetoothGattCharacteristic c,byte[] value,int status){received(c,value,status);}
            private void received(BluetoothGattCharacteristic c,byte[] value,int status){
                if(!FINGERPRINT.equals(c.getUuid()))return;
                if(status!=BluetoothGatt.GATT_SUCCESS){finish(null,"Fingerprint-Lesen abgelehnt ("+status+"). BLE-Registrierung eventuell erforderlich.");return;}
                try {if(value==null)throw new IOException("Leerer Fingerabdruck");String ascii=new String(value,StandardCharsets.US_ASCII).split("\u0000",2)[0];finish(CameraTrust.formatPin(CameraTrust.parsePin(ascii)),null);}
                catch(IOException e){finish(null,"Ungültiger Fingerabdruck von der Kamera");}
            }
        },BluetoothDevice.TRANSPORT_LE);if(gatt==null)finish(null,"Bluetooth-Verbindung konnte nicht gestartet werden");}
        catch(SecurityException e){finish(null,"Bluetooth-Freigabe fehlt");}
    }
    private void finish(String pin,String error){handler.post(()->{if(finished)return;finished=true;handler.removeCallbacks(timeout);release();callback.result(pin,error);});}
    @SuppressWarnings("MissingPermission") private void release(){if(gatt!=null){try{gatt.disconnect();gatt.close();}catch(SecurityException ignored){}gatt=null;}}
    @Override public void close(){finished=true;handler.removeCallbacks(timeout);release();}
}
