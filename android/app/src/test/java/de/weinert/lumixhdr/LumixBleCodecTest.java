package de.weinert.lumixhdr;

import org.junit.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Literal byte fixtures for the independently reconstructed BLE packing, without camera access. */
public class LumixBleCodecTest {
    private static final byte[] KEY=hex("01234567");
    private static byte[] hex(String text){byte[] result=new byte[text.length()/2];for(int i=0;i<result.length;i++)result[i]=(byte)Integer.parseInt(text.substring(i*2,i*2+2),16);return result;}
    private static byte[] ascii(String text){return text.getBytes(StandardCharsets.US_ASCII);}

    @Test public void uuidParsingProducesBinaryIdentityAndUsesFixedEncodingFixture() throws Exception {
        byte[] uuid=LumixBleCodec.uuid("00112233445566778899AABBCCDDEEFF");
        assertArrayEquals(hex("00112233445566778899aabbccddeeff"),uuid);
        // Five reversed 4-byte blocks: length+UUID, then three 0xff trailer bytes.
        assertArrayEquals(hex("2332457767760154abbacd10effe89dcfedcba98"),LumixBleCodec.encode(uuid,KEY));
        assertArrayEquals(uuid,LumixBleCodec.decode(hex("2332457767760154abbacd10effe89dcfedcba98"),KEY));
    }
    @Test public void shortPayloadFixturesCoverEachPaddingBoundary() throws Exception {
        String[] plain={"","A","S9","S9!","S9!!"};
        String[] wire={"fedcba67","01230466fedcba67","011a1665fedcba67","201a1664fedcba67","201a1663fedcba46"};
        for(int i=0;i<plain.length;i++){
            assertArrayEquals(plain[i],hex(wire[i]),LumixBleCodec.encode(ascii(plain[i]),KEY));
            assertArrayEquals(plain[i],ascii(plain[i]),LumixBleCodec.decode(hex(wire[i]),KEY));
        }
    }
    @Test public void fixedString32UsesNineBlocksAndPreservesAllBytes() throws Exception {
        byte[] raw=ascii("0123456789abcdefghijklmnopqrstuv");
        byte[] wire=hex("3312754737167154601a7d5064472605684b22016c4f2e0d70532a0974573615fedcba11");
        assertEquals(32,raw.length);assertEquals(36,wire.length);
        assertArrayEquals(wire,LumixBleCodec.encode(raw,KEY));assertArrayEquals(raw,LumixBleCodec.decode(wire,KEY));
    }
    @Test public void usesFirstFourBytesOfInitialInformResponse() throws Exception {
        assertArrayEquals(hex("201a1664fedcba67"),LumixBleCodec.encode(ascii("S9!"),hex("0123456789abcdef")));
        assertArrayEquals(ascii("S9!"),LumixBleCodec.decode(hex("201a1664fedcba67"),hex("0123456789abcdef")));
    }
    @Test public void appInfoIsExactly32AsciiBytesWithZeroTermination(){
        byte[] app=LumixBleCodec.appInfo();byte[] name=ascii("Lumix HDR Companion 0.3.5");
        assertEquals(32,app.length);assertArrayEquals(name,Arrays.copyOf(app,name.length));
        for(int i=name.length;i<app.length;i++)assertEquals(0,app[i]);
    }
    @Test public void preservesPasswordPayloadAtWpa2BoundaryLengths() throws Exception {
        for(int length:new int[]{8,63}){
            byte[] password=ascii("p".repeat(length));
            assertArrayEquals(password,LumixBleCodec.decode(LumixBleCodec.encode(password,KEY),KEY));
        }
    }
    @Test public void rejectsMissingAndShortInitialInformKey(){
        for(byte[] key:new byte[][]{null,new byte[0],new byte[3]}){
            assertThrows(IOException.class,()->LumixBleCodec.encode(ascii("S9"),key));
            assertThrows(IOException.class,()->LumixBleCodec.decode(hex("00000000"),key));
        }
    }
    @Test public void rejectsMalformedBlockLengthAndLengthPrefix(){
        for(byte[] wire:new byte[][]{null,new byte[0],new byte[1],new byte[3],new byte[5]})assertThrows(IOException.class,()->LumixBleCodec.decode(wire,KEY));
        // Zero key exposes packed length 4 in a block containing only 3 available payload bytes.
        assertThrows(IOException.class,()->LumixBleCodec.decode(hex("00000004"),new byte[4]));
    }
    @Test public void rejectsPayloadBeyondUnsignedByteLength(){
        assertThrows(IOException.class,()->LumixBleCodec.encode(null,KEY));
        assertThrows(IOException.class,()->LumixBleCodec.encode(new byte[256],KEY));
    }
    @Test public void preservesUnsigned255ByteLength() throws Exception {
        byte[] raw=new byte[255];Arrays.fill(raw,(byte)0x9a);
        byte[] wire=LumixBleCodec.encode(raw,KEY);assertEquals(260,wire.length);assertArrayEquals(raw,LumixBleCodec.decode(wire,KEY));
    }
    @Test public void textStopsAtNulAndRejectsMalformedUtf8() throws Exception {
        assertEquals("LUMIX S9",LumixBleCodec.text(new byte[]{' ','L','U','M','I','X',' ','S','9',' ',0,(byte)0xc3}));
        assertEquals("Kamera ü",LumixBleCodec.text("Kamera ü".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IOException.class,()->LumixBleCodec.text(null));
        assertThrows(IOException.class,()->LumixBleCodec.text(new byte[]{(byte)0xc3,0x28}));
    }
    @Test public void rejectsMalformedUuid(){
        for(String uuid:new String[]{null,"","00112233-4455-6677-8899-aabbccddeeff","z".repeat(32),"0".repeat(31),"0".repeat(33)})assertThrows(IOException.class,()->LumixBleCodec.uuid(uuid));
    }
}
