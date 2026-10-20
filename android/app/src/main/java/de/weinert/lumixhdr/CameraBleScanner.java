package de.weinert.lumixhdr;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.*;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import java.util.*;

/** One bounded BLE discovery, including cameras that have never been bonded in Android. */
public final class CameraBleScanner implements AutoCloseable {
    public static final long SCAN_DURATION_MS=15000;
    public interface Callback {
        void discovered(BluetoothDevice device,String name);
        void complete();
        void error(String message);
    }
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Set<UUID> cameraServices;
    private final Set<String> seen=new HashSet<>();
    private final Map<String,byte[]> identities=new HashMap<>();
    private final Callback callback;
    private BluetoothLeScanner scanner;
    private boolean started,finished,scanning;
    private volatile boolean closed;
    private final Runnable timeout=()->finish(null);
    private final ScanCallback scanCallback=new ScanCallback(){
        @Override public void onScanResult(int type,ScanResult result){handler.post(()->receive(result));}
        @Override public void onBatchScanResults(List<ScanResult> results){
            if(results==null)return;
            List<ScanResult> snapshot=new ArrayList<>(results);
            handler.post(()->{for(ScanResult result:snapshot)receive(result);});
        }
        @Override public void onScanFailed(int code){handler.post(()->finish("Bluetooth-Suche fehlgeschlagen ("+code+"). Bitte erneut suchen."));}
    };

    /** Services must come from observed camera advertisements; an empty set enables name matching. */
    public CameraBleScanner(Set<UUID> cameraServices,Callback callback){
        this.cameraServices=Collections.unmodifiableSet(new HashSet<>(Objects.requireNonNull(cameraServices)));
        this.callback=Objects.requireNonNull(callback);
    }
    /** Call once after obtaining the Android scan/connect permissions. Callbacks always use main. */
    public void start(BluetoothAdapter adapter){handler.post(()->startOnMain(adapter));}
    /** Observed advertisement identity, distinct from INITIAL_INFORM's credential key. */
    @SuppressWarnings("MissingPermission") public byte[] cameraIdentity(BluetoothDevice device){
        if(device==null)return null;
        try{
            String address=device.getAddress();if(address==null)return null;
            synchronized(identities){byte[] identity=identities.get(address.toUpperCase(Locale.ROOT));return identity==null?null:identity.clone();}
        }catch(SecurityException e){return null;}
    }

    @SuppressWarnings("MissingPermission") private void startOnMain(BluetoothAdapter adapter){
        if(closed||started)return;
        started=true;
        try{
            if(adapter==null||!adapter.isEnabled()){finish("Bluetooth aktivieren und erneut nach deiner Kamera suchen.");return;}
            scanner=adapter.getBluetoothLeScanner();
            if(scanner==null){finish("Bluetooth-Suche ist nicht verfügbar. Bluetooth aktivieren und erneut suchen.");return;}
            scanning=true;
            // Name OR camera-service matching is applied below; no system-bond requirement.
            scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback);
            handler.postDelayed(timeout,SCAN_DURATION_MS);
        }catch(SecurityException e){finish("Bluetooth-Freigabe fehlt. Bluetooth-Suche erlauben und erneut suchen.");}
        catch(IllegalStateException e){finish("Bluetooth-Suche ist nicht verfügbar. Bluetooth aktivieren und erneut suchen.");}
    }
    static boolean cameraName(String name){
        if(name==null)return false;
        String n=name.trim().toUpperCase(Locale.ROOT);
        return n.contains("LUMIX")||n.startsWith("DC-")||n.equals("S9")||n.startsWith("S9-")||n.startsWith("S9_")||n.startsWith("S9 ");
    }
    static boolean cameraCandidate(String name,Collection<UUID> advertised,Set<UUID> cameraServices){
        if(cameraName(name))return true;
        if(advertised!=null)for(UUID service:advertised)if(cameraServices.contains(service))return true;
        return false;
    }
    static String displayName(String name,boolean advertisedCameraService){
        // Lab's camera advertisements append LF and a one-byte model code to the local name.
        if(advertisedCameraService&&name!=null&&name.length()>=2&&name.charAt(name.length()-2)=='\n')name=name.substring(0,name.length()-2);
        return name==null||name.trim().isEmpty()?"LUMIX-Kamera":name;
    }
    @SuppressWarnings("MissingPermission") private void receive(ScanResult result){
        if(closed||finished||!scanning||result==null)return;
        try{
            BluetoothDevice device=result.getDevice();if(device==null)return;
            ScanRecord record=result.getScanRecord();String advertisedName=record==null?null:record.getDeviceName();
            String deviceName=device.getName();String name=advertisedName==null||advertisedName.trim().isEmpty()?deviceName:advertisedName;
            List<UUID> services=new ArrayList<>();
            if(record!=null&&record.getServiceUuids()!=null)for(ParcelUuid uuid:record.getServiceUuids())services.add(uuid.getUuid());
            if(!cameraCandidate(name,services,cameraServices)&&!cameraName(deviceName))return;
            String address=device.getAddress();
            if(address==null||address.isEmpty())return;
            String key=address.toUpperCase(Locale.ROOT);
            byte[] manufacturer=record==null?null:record.getManufacturerSpecificData(0x003a);
            if(manufacturer!=null&&manufacturer.length==6){
                synchronized(identities){identities.put(key,Arrays.copyOfRange(manufacturer,2,6));}
            }
            // Later advertisements can supply identity metadata after the first visible result.
            if(!seen.add(key))return;
            callback.discovered(device,displayName(name,cameraCandidate(null,services,cameraServices)));
        }catch(SecurityException e){finish("Bluetooth-Freigabe fehlt. Bluetooth-Suche erlauben und erneut suchen.");}
    }
    private void finish(String error){
        if(closed||finished)return;
        finished=true;handler.removeCallbacks(timeout);stopScan();
        if(closed)return;
        if(error==null)callback.complete();else callback.error(error);
    }
    @SuppressWarnings("MissingPermission") private void stopScan(){
        BluetoothLeScanner active=scanner;scanner=null;
        boolean stop=scanning;scanning=false;
        if(active!=null&&stop)try{active.stopScan(scanCallback);}catch(SecurityException|IllegalStateException ignored){}
    }
    /** Cancels silently, suppresses queued results, and can be called repeatedly from any thread. */
    @Override public void close(){
        closed=true;handler.removeCallbacksAndMessages(null);
        if(Looper.myLooper()==handler.getLooper())stopScan();else handler.post(this::stopScan);
    }
}
