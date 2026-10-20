package de.weinert.lumixhdr;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.Arrays;

/** Independent encoding of the length-prefixed, reversed/XOR BLE blocks observed in Lab 3.1.0. */
final class LumixBleCodec {
    private LumixBleCodec(){}
    private static void key(byte[] key) throws IOException {if(key==null||key.length<4)throw new IOException("Kamera liefert keinen gültigen Bluetooth-Anmeldeschlüssel");}
    static byte[] encode(byte[] raw,byte[] key) throws IOException {
        key(key);if(raw==null||raw.length>255)throw new IOException("Bluetooth-Nutzdaten zu lang");
        int padding=(4-raw.length%4)%4,size=((raw.length+padding+4)/4)*4;
        byte[] packed=new byte[size];Arrays.fill(packed,(byte)0xff);packed[0]=(byte)raw.length;
        System.arraycopy(raw,0,packed,1,raw.length);Arrays.fill(packed,raw.length+1,raw.length+1+padding,(byte)0);
        byte[] encoded=new byte[size];
        for(int i=0;i<size;i+=4)for(int j=0;j<4;j++)encoded[i+j]=(byte)(packed[i+3-j]^key[j]);
        return encoded;
    }
    static byte[] decode(byte[] encoded,byte[] key) throws IOException {
        key(key);if(encoded==null||encoded.length==0||encoded.length%4!=0)throw new IOException("Ungültige Bluetooth-Blocklänge");
        byte[] packed=new byte[encoded.length];
        for(int i=0;i<encoded.length;i+=4)for(int j=0;j<4;j++)packed[i+j]=(byte)(encoded[i+3-j]^key[3-j]);
        int length=packed[0]&255;if(length>packed.length-1)throw new IOException("Ungültige Bluetooth-Nutzdatenlänge");
        return Arrays.copyOfRange(packed,1,length+1);
    }
    static String text(byte[] data) throws IOException {
        if(data==null)throw new IOException("Leere Bluetooth-Antwort");
        int end=0;while(end<data.length&&data[end]!=0)end++;
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data,0,end)).toString().trim();}
        catch(CharacterCodingException e){throw new IOException("Ungültiger Bluetooth-Text",e);}
    }
    static byte[] uuid(String uuid) throws IOException {
        if(uuid==null||!uuid.matches("[0-9a-fA-F]{32}"))throw new IOException("Eigene App-ID muss aus 32 Hex-Zeichen bestehen");
        byte[] raw=new byte[16];for(int i=0;i<16;i++)raw[i]=(byte)Integer.parseInt(uuid.substring(i*2,i*2+2),16);return raw;
    }
    static byte[] appInfo(){return Arrays.copyOf("Lumix HDR Companion 0.3.5".getBytes(StandardCharsets.US_ASCII),32);}
}
