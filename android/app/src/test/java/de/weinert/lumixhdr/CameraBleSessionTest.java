package de.weinert.lumixhdr;

import android.bluetooth.*;
import android.os.Looper;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowBluetoothGatt;
import org.robolectric.util.ReflectionHelpers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Manually acknowledged GATT operations expose ordering and cancellation races. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,35},shadows=CameraBleSessionTest.ScriptedGatt.class)
@LooperMode(LooperMode.Mode.PAUSED)
@SuppressWarnings("deprecation")
public class CameraBleSessionTest {
    private CameraBleSession session;
    private BluetoothDevice device;
    private BluetoothGatt gatt;
    private ScriptedGatt transport;
    private BluetoothGattCallback observer;
    private final Map<UUID,byte[]> responses=new HashMap<>();
    private final List<String> errors=new ArrayList<>();
    private final List<String[]> ready=new ArrayList<>();
    private int acknowledged;
    private static final String UUID_TEXT="00112233445566778899aabbccddeeff",PIN="11".repeat(32);
    private static byte[] hex(String text){byte[] data=new byte[text.length()/2];for(int i=0;i<data.length;i++)data[i]=(byte)Integer.parseInt(text.substring(i*2,i*2+2),16);return data;}
    private static byte[] ascii(String text){return text.getBytes(StandardCharsets.US_ASCII);}
    @Before public void setUp(){
        device=BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55");
        session=new CameraBleSession(new CameraBleSession.Callback(){
            public void progress(String message){assertEquals(Looper.getMainLooper(),Looper.myLooper());}
            public void ready(String ssid,String password,String fingerprint){assertEquals(Looper.getMainLooper(),Looper.myLooper());ready.add(new String[]{ssid,password,fingerprint});}
            public void error(String message){assertEquals(Looper.getMainLooper(),Looper.myLooper());errors.add(message);}
        });
        responses.put(CameraBleSession.INITIAL,hex("0123456789abcdef"));
        responses.put(CameraBleSession.SSID,ascii("LUMIX-S9"));
        responses.put(CameraBleSession.PASSWORD,hex("7246316f72423513fedcba14")); // literal encrypted 'testpass'
        responses.put(CameraBleSession.FINGERPRINT,ascii(PIN));
        responses.put(CameraBleSession.AP_STATE,new byte[]{0});
        session.connect(RuntimeEnvironment.getApplication(),device,UUID_TEXT,"S9!");
        gatt=shadowOf(device).getBluetoothGatts().get(0);transport=(ScriptedGatt)shadowOf(gatt);observer=transport.getGattCallback();
        transport.service=new BluetoothGattService(CameraBleSession.SERVICE,BluetoothGattService.SERVICE_TYPE_PRIMARY);
        for(UUID id:new UUID[]{CameraBleSession.INITIAL,CameraBleSession.APP_UUID,CameraBleSession.APP_INFO,CameraBleSession.NAME,CameraBleSession.REGISTER,CameraBleSession.SSID,CameraBleSession.PASSWORD,CameraBleSession.FINGERPRINT,CameraBleSession.ACTION,CameraBleSession.AP_STATE,CameraBleSession.ACTION_RESULT}){
            int properties=BluetoothGattCharacteristic.PROPERTY_READ|BluetoothGattCharacteristic.PROPERTY_WRITE;
            if(id.equals(CameraBleSession.ACTION))properties=BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;
            if(id.equals(CameraBleSession.AP_STATE)||id.equals(CameraBleSession.ACTION_RESULT))properties|=BluetoothGattCharacteristic.PROPERTY_NOTIFY;
            BluetoothGattCharacteristic c=new BluetoothGattCharacteristic(id,properties,BluetoothGattCharacteristic.PERMISSION_READ|BluetoothGattCharacteristic.PERMISSION_WRITE);
            if(id.equals(CameraBleSession.AP_STATE)||id.equals(CameraBleSession.ACTION_RESULT))c.addDescriptor(new BluetoothGattDescriptor(CameraBleSession.CCCD,BluetoothGattDescriptor.PERMISSION_WRITE));
            transport.service.addCharacteristic(c);
        }
    }
    @After public void tearDown(){session.close();idle();}
    private void idle(){shadowOf(Looper.getMainLooper()).idle();}
    private void begin(){observer.onConnectionStateChange(gatt,0,BluetoothProfile.STATE_CONNECTED);idle();observer.onMtuChanged(gatt,57,0);idle();observer.onServicesDiscovered(gatt,0);idle();}
    private BluetoothGattCharacteristic c(UUID id){return transport.service.getCharacteristic(id);}
    private Call current(){return transport.calls.get(acknowledged);}
    private void acknowledge(int status){
        Call call=transport.calls.get(acknowledged++);
        if(call.descriptor)observer.onDescriptorWrite(gatt,c(call.uuid).getDescriptor(CameraBleSession.CCCD),status);
        else if(call.value!=null)observer.onCharacteristicWrite(gatt,c(call.uuid),status);
        else{c(call.uuid).setValue(responses.get(call.uuid));observer.onCharacteristicRead(gatt,c(call.uuid),status);}
        idle();
    }
    private void untilAction(){begin();for(int i=0;i<20&&!current().uuid.equals(CameraBleSession.ACTION);i++)acknowledge(0);assertEquals(CameraBleSession.ACTION,current().uuid);}
    private void notify(UUID id,int value){c(id).setValue(new byte[]{(byte)value});observer.onCharacteristicChanged(gatt,c(id));idle();}
    private void readyCamera(){untilAction();acknowledge(0);notify(CameraBleSession.AP_STATE,1);assertEquals(1,ready.size());}

    @Test public void executesOneOperationAtATimeAndUsesObservedWriteTypesAndFixtures(){
        begin();assertEquals(57,transport.mtu);assertEquals(1,transport.discoveries);assertEquals(1,transport.calls.size());
        assertEquals(CameraBleSession.AP_STATE,current().uuid);assertTrue(current().descriptor);assertArrayEquals(new byte[]{1,0},current().value);
        acknowledge(0);assertEquals(CameraBleSession.ACTION_RESULT,current().uuid);assertTrue(current().descriptor);
        acknowledge(0);assertEquals(CameraBleSession.INITIAL,current().uuid);assertNull(current().value);
        acknowledge(0);assertEquals(CameraBleSession.APP_UUID,current().uuid);assertEquals(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,current().writeType);
        assertArrayEquals(hex("2332457767760154abbacd10effe89dcfedcba98"),current().value);
        int before=transport.calls.size();idle();assertEquals(before,transport.calls.size());
        acknowledge(0);assertEquals(CameraBleSession.APP_INFO,current().uuid);assertEquals(32,current().value.length);
        assertArrayEquals(Arrays.copyOf("Lumix HDR Companion 0.3.5".getBytes(StandardCharsets.US_ASCII),32),current().value);
        acknowledge(0);assertEquals(CameraBleSession.NAME,current().uuid);assertArrayEquals(hex("201a1664fedcba67"),current().value);
        acknowledge(0);assertEquals(CameraBleSession.REGISTER,current().uuid);assertArrayEquals(new byte[]{2},current().value);assertEquals(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,current().writeType);
        acknowledge(0);assertEquals(CameraBleSession.SSID,current().uuid);acknowledge(0);assertEquals(CameraBleSession.PASSWORD,current().uuid);
        acknowledge(0);assertEquals(CameraBleSession.FINGERPRINT,current().uuid);acknowledge(0);
        assertEquals(CameraBleSession.ACTION,current().uuid);assertArrayEquals(new byte[]{1},current().value);assertEquals(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,current().writeType);
    }
    @Test public void apReadyWaitsForActionWriteCallbackAndDoesNotLeaveAnOperationTimeout(){
        untilAction();notify(CameraBleSession.AP_STATE,1);assertTrue(ready.isEmpty());acknowledge(0);
        assertEquals(1,ready.size());assertArrayEquals(new String[]{"LUMIX-S9","testpass",PIN},ready.get(0));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2));assertTrue(errors.isEmpty());assertEquals(1,ready.size());assertNull(ReflectionHelpers.getField(session,"password"));
    }
    @Test public void readyNotificationCancelsPendingStateReadAndIgnoresLateReply(){
        untilAction();acknowledge(0);assertEquals(CameraBleSession.AP_STATE,current().uuid);assertTrue(ready.isEmpty());
        notify(CameraBleSession.AP_STATE,0);assertTrue(ready.isEmpty());notify(CameraBleSession.AP_STATE,1);assertEquals(1,ready.size());
        acknowledge(BluetoothGatt.GATT_FAILURE);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1));assertTrue(errors.isEmpty());assertEquals(1,ready.size());
    }
    @Test public void apStateFromBeforeActionDoesNotBypassFreshState(){
        begin();notify(CameraBleSession.AP_STATE,1);
        for(int i=0;i<20&&!current().uuid.equals(CameraBleSession.ACTION);i++)acknowledge(0);
        acknowledge(0);assertTrue(ready.isEmpty());acknowledge(0);assertTrue(ready.isEmpty());notify(CameraBleSession.AP_STATE,1);assertEquals(1,ready.size());
    }
    @Test public void explicitActionFailureIsTerminalAndDoesNotLeakCredentials(){
        untilAction();notify(CameraBleSession.ACTION_RESULT,1);
        assertEquals(1,errors.size());assertTrue(ready.isEmpty());assertFalse(errors.get(0).contains("testpass"));assertEquals(1,transport.closes);
        notify(CameraBleSession.AP_STATE,1);assertTrue(ready.isEmpty());
    }
    @Test public void failureAfterReadyClosesSessionWithoutRedeliveringCredentials(){
        readyCamera();notify(CameraBleSession.ACTION_RESULT,1);notify(CameraBleSession.ACTION_RESULT,1);
        assertEquals(1,ready.size());assertEquals(1,errors.size());assertFalse(errors.get(0).contains("testpass"));assertNull(ReflectionHelpers.getField(session,"password"));assertNull(ReflectionHelpers.getField(session,"key"));
    }
    @Test public void unknownActionResultDoesNotInventFailure(){
        untilAction();notify(CameraBleSession.ACTION_RESULT,0);notify(CameraBleSession.ACTION_RESULT,2);assertTrue(errors.isEmpty());
    }
    @Test public void gattOperationFailureStopsQueueAndReportsStage(){
        begin();int count=transport.calls.size();acknowledge(133);
        assertEquals(count,transport.calls.size());assertEquals(1,errors.size());assertTrue(errors.get(0).contains("133"));assertEquals(1,transport.closes);
    }
    @Test public void perOperationTimeoutStopsAndSuppressesLateCallbacks(){
        begin();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(15));assertEquals(1,errors.size());assertEquals(1,transport.closes);
        acknowledge(0);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1));assertEquals(1,errors.size());assertTrue(ready.isEmpty());
    }
    @Test public void globalConnectionDeadlineStopsWithoutGattCallbacks(){
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(59));assertTrue(errors.isEmpty());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));assertEquals(1,errors.size());assertEquals(1,transport.closes);
    }
    @Test public void closeCancelsQueuedCallbacksAndCanRepeat(){
        observer.onConnectionStateChange(gatt,0,BluetoothProfile.STATE_CONNECTED);session.close();session.close();idle();
        observer.onServicesDiscovered(gatt,0);notify(CameraBleSession.AP_STATE,1);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2));
        assertEquals(1,transport.closes);assertEquals(0,transport.mtu);assertTrue(errors.isEmpty());assertTrue(ready.isEmpty());
    }
    @Test public void rejectsMtuTooSmallForRegistrationBlocks(){
        observer.onMtuChanged(gatt,23,0);idle();assertEquals(1,errors.size());assertEquals(0,transport.discoveries);
    }
    private void invalidPassword(int length) throws Exception {
        responses.put(CameraBleSession.PASSWORD,LumixBleCodec.encode(ascii("x".repeat(length)),hex("01234567")));begin();
        for(int i=0;i<20&&errors.isEmpty();i++)acknowledge(0);
        assertEquals(1,errors.size());assertTrue(errors.get(0).contains("WPA2"));assertTrue(ready.isEmpty());
        for(Call call:transport.calls)assertNotEquals(CameraBleSession.ACTION,call.uuid);
    }
    @Test public void rejectsSevenByteWpa2PasswordBeforeAction() throws Exception {invalidPassword(7);}
    @Test public void rejectsSixtyFourByteWpa2PasswordBeforeAction() throws Exception {invalidPassword(64);}
    @Test public void cleanupToleratesPermissionRevocation(){
        transport.denyClose=true;session.close();session.close();assertEquals(1,transport.closes);assertTrue(errors.isEmpty());
    }

    private static final class Call {
        final UUID uuid;final boolean descriptor;final byte[] value;final int writeType;
        Call(UUID uuid,boolean descriptor,byte[] value,int type){this.uuid=uuid;this.descriptor=descriptor;this.value=value==null?null:value.clone();writeType=type;}
    }
    @Implements(BluetoothGatt.class)
    public static class ScriptedGatt extends ShadowBluetoothGatt {
        BluetoothGattService service;final List<Call> calls=new ArrayList<>();int mtu,discoveries,closes;boolean denyClose;
        @Implementation protected boolean requestMtu(int mtu){this.mtu=mtu;return true;}
        @Implementation protected boolean discoverServices(){discoveries++;return true;}
        @Implementation protected BluetoothGattService getService(UUID uuid){return service!=null&&service.getUuid().equals(uuid)?service:null;}
        @Implementation protected boolean setCharacteristicNotification(BluetoothGattCharacteristic c,boolean enabled){return true;}
        @Implementation protected boolean readCharacteristic(BluetoothGattCharacteristic c){calls.add(new Call(c.getUuid(),false,null,0));return true;}
        @Implementation protected boolean writeCharacteristic(BluetoothGattCharacteristic c){calls.add(new Call(c.getUuid(),false,c.getValue(),c.getWriteType()));return true;}
        @Implementation protected boolean writeDescriptor(BluetoothGattDescriptor descriptor){calls.add(new Call(descriptor.getCharacteristic().getUuid(),true,descriptor.getValue(),2));return true;}
        @Implementation protected void disconnect(){}
        @Implementation protected void close(){closes++;if(denyClose)throw new SecurityException("Test permission revoked");}
    }
}
