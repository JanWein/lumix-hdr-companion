package de.weinert.lumixhdr;

import android.bluetooth.*;
import android.bluetooth.le.*;
import android.os.Looper;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowBluetoothLeScanner;
import org.robolectric.util.ReflectionHelpers;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,35})
@LooperMode(LooperMode.Mode.PAUSED)
public class CameraBleScannerTest {
    private BluetoothAdapter adapter;
    private ShadowBluetoothLeScanner platform;
    private CameraBleScanner scanner;
    private final List<BluetoothDevice> found=new ArrayList<>();
    private final List<String> errors=new ArrayList<>();
    private int completed;
    private final CameraBleScanner.Callback callback=new CameraBleScanner.Callback(){
        @Override public void discovered(BluetoothDevice device,String name){assertEquals(Looper.getMainLooper(),Looper.myLooper());found.add(device);}
        @Override public void complete(){assertEquals(Looper.getMainLooper(),Looper.myLooper());completed++;}
        @Override public void error(String message){assertEquals(Looper.getMainLooper(),Looper.myLooper());errors.add(message);}
    };
    @Before public void setUp(){
        adapter=BluetoothAdapter.getDefaultAdapter();shadowOf(adapter).setEnabled(true);
        platform=shadowOf(adapter.getBluetoothLeScanner());
        scanner=new CameraBleScanner(Collections.emptySet(),callback);
    }
    @After public void tearDown(){scanner.close();shadowOf(Looper.getMainLooper()).idle();}
    private void start(){scanner.start(adapter);shadowOf(Looper.getMainLooper()).idle();}
    private ScanCallback platformCallback(){return platform.getScanCallbacks().iterator().next();}
    private ScanResult camera(String address,String name){BluetoothDevice device=adapter.getRemoteDevice(address);shadowOf(device).setName(name);return new ScanResult(device,null,-40,0);}

    @Test public void filtersCameraNamesWithoutListingUnrelatedDevices(){
        for(String name:new String[]{"LUMIX S9","DC-S9_123456","S9","s9-camera","Panasonic LUMIX"})assertTrue(name,CameraBleScanner.cameraName(name));
        for(String name:new String[]{"Headphones","Galaxy S9","Speaker","",null})assertFalse(CameraBleScanner.cameraName(name));
    }
    @Test public void suppliedServiceAllowsNamelessCameraWithoutGuessingOtherServices(){
        UUID service=UUID.randomUUID(),other=UUID.randomUUID();Set<UUID> allowed=Collections.singleton(service);
        assertTrue(CameraBleScanner.cameraCandidate(null,Collections.singleton(service),allowed));
        assertFalse(CameraBleScanner.cameraCandidate(null,Collections.singleton(other),allowed));
        assertFalse(CameraBleScanner.cameraCandidate("Headphones",null,allowed));
    }
    @Test public void stripsObservedModelSuffixOnlyForAdvertisedCameraService(){
        assertEquals("DC-S9",CameraBleScanner.displayName("DC-S9\n\u0006",true));
        assertEquals("DC-S9\n\u0006",CameraBleScanner.displayName("DC-S9\n\u0006",false));
        assertEquals("DC-S9\n",CameraBleScanner.displayName("DC-S9\n",true));
        assertEquals("LUMIX-Kamera",CameraBleScanner.displayName(null,true));
    }
    @Test public void duplicateAdvertisementUpdatesCameraIdentityAndReturnsDefensiveCopies(){
        start();ScanCallback cb=platformCallback();ScanResult first=camera("00:11:22:33:44:55","DC-S9");
        cb.onScanResult(0,first);shadowOf(Looper.getMainLooper()).idle();assertNull(scanner.cameraIdentity(first.getDevice()));
        byte[] bytes={9,(byte)0xff,0x3a,0,0,3,0x12,0x34,0x56,0x78};
        ScanRecord record=ReflectionHelpers.callStaticMethod(ScanRecord.class,"parseFromBytes",ReflectionHelpers.ClassParameter.from(byte[].class,bytes));
        cb.onScanResult(0,new ScanResult(first.getDevice(),record,-40,0));shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1,found.size());assertArrayEquals(new byte[]{0x12,0x34,0x56,0x78},scanner.cameraIdentity(first.getDevice()));
        byte[] identity=scanner.cameraIdentity(first.getDevice());identity[0]=0;
        assertArrayEquals(new byte[]{0x12,0x34,0x56,0x78},scanner.cameraIdentity(first.getDevice()));
        assertNull(scanner.cameraIdentity(adapter.getRemoteDevice("00:11:22:33:44:66")));
    }
    @Test public void rejectsUnexpectedManufacturerIdentityLength(){
        start();ScanResult first=camera("00:11:22:33:44:55","DC-S9");
        byte[] bytes={8,(byte)0xff,0x3a,0,0,3,0x12,0x34,0x56};
        ScanRecord record=ReflectionHelpers.callStaticMethod(ScanRecord.class,"parseFromBytes",ReflectionHelpers.ClassParameter.from(byte[].class,bytes));
        platformCallback().onScanResult(0,new ScanResult(first.getDevice(),record,-40,0));shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1,found.size());assertNull(scanner.cameraIdentity(first.getDevice()));
    }
    @Test public void discoversUnbondedCameraAndDeduplicatesAcrossSingleAndBatchResults(){
        assertTrue(adapter.getBondedDevices().isEmpty());start();
        ScanResult camera=camera("00:11:22:33:44:55","DC-S9_123");
        ScanCallback cb=platformCallback();cb.onScanResult(0,camera);cb.onBatchScanResults(Arrays.asList(camera,camera("00:11:22:33:44:66","Headphones")));
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(Collections.singletonList(camera.getDevice()),found);
    }
    @Test public void boundedLowLatencyScanStopsAndCompletesOnce(){
        start();assertEquals(ScanSettings.SCAN_MODE_LOW_LATENCY,platform.getActiveScans().get(0).scanSettings().getScanMode());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(CameraBleScanner.SCAN_DURATION_MS));
        assertTrue(platform.getScanCallbacks().isEmpty());assertEquals(1,completed);assertTrue(errors.isEmpty());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1));assertEquals(1,completed);
    }
    @Test public void closeStopsScanAndSuppressesQueuedAndLateResults(){
        start();ScanCallback cb=platformCallback();ScanResult camera=camera("00:11:22:33:44:55","LUMIX S9");
        cb.onScanResult(0,camera);scanner.close();scanner.close();cb.onScanResult(0,camera);cb.onScanFailed(3);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1));
        assertTrue(platform.getScanCallbacks().isEmpty());assertTrue(found.isEmpty());assertTrue(errors.isEmpty());assertEquals(0,completed);
    }
    @Test public void scanFailureReportsCodeAndStopsWithoutLaterCompletion(){
        start();platformCallback().onScanFailed(ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1));
        assertTrue(platform.getScanCallbacks().isEmpty());assertEquals(1,errors.size());assertTrue(errors.get(0).contains("(2)"));assertEquals(0,completed);
    }
    @Test public void missingOrDisabledAdapterReportsActionableError(){
        shadowOf(adapter).setEnabled(false);start();
        assertEquals(1,errors.size());assertTrue(errors.get(0).contains("aktivieren"));assertTrue(platform.getScanCallbacks().isEmpty());
    }
    @Test public void repeatedStartCreatesOnlyOneScan(){
        scanner.start(adapter);scanner.start(adapter);shadowOf(Looper.getMainLooper()).idle();assertEquals(1,platform.getActiveScans().size());
    }
    @Test public void callbacksFromBackgroundThreadAreDispatchedOnMain() throws Exception {
        start();ScanCallback cb=platformCallback();ScanResult result=camera("00:11:22:33:44:55","LUMIX S9");
        Thread binder=new Thread(()->cb.onScanResult(0,result));binder.start();binder.join(10000);assertFalse(binder.isAlive());assertTrue(found.isEmpty());
        shadowOf(Looper.getMainLooper()).idle();assertEquals(1,found.size());
    }
    @Test @Config(shadows=PermissionDeniedScanner.class) public void permissionDenialBecomesErrorCallback(){
        start();assertEquals(1,errors.size());assertTrue(errors.get(0).contains("Freigabe"));assertEquals(0,completed);
    }
    @Implements(BluetoothLeScanner.class)
    public static class PermissionDeniedScanner extends ShadowBluetoothLeScanner {
        @Implementation protected void startScan(List<ScanFilter> filters,ScanSettings settings,ScanCallback callback){throw new SecurityException("Test permission denied");}
    }
}
