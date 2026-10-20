package de.weinert.lumixhdr;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetAddress;
import java.security.*;
import java.security.cert.*;
import java.util.Locale;

/** Per-camera certificate pin, acquired independently of the HTTPS connection. */
public final class CameraTrust {
    private CameraTrust() {}
    public static String localHost(String host) throws IOException {
        if (host == null || !host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))
            throw new IOException("Private IPv4-Adresse der Kamera eingeben");
        String[] parts=host.split("\\.");
        for(String part:parts) if(Integer.parseInt(part)>255 || (part.length()>1 && part.startsWith("0")))
            throw new IOException("Ungültige IPv4-Adresse");
        if(!InetAddress.getByName(host).isSiteLocalAddress()) throw new IOException("Nur private Kamera-Adressen erlaubt");
        return host;
    }
    public static byte[] parsePin(String text) throws IOException {
        String s=text==null?"":text.trim().replace(":","").replace(" ","").toLowerCase(Locale.ROOT);
        if(!s.matches("[0-9a-f]{64}")) throw new IOException("SHA-256-Fingerabdruck fehlt: 64 Hex-Zeichen aus vertrauenswürdiger Quelle erforderlich");
        byte[] bytes=new byte[32];
        for(int i=0;i<32;i++) bytes[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);
        return bytes;
    }
    public static String formatPin(byte[] bytes) {
        StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();
    }
    static void verify(byte[] pin, java.security.cert.Certificate leaf) throws GeneralSecurityException {
        byte[] actual=MessageDigest.getInstance("SHA-256").digest(leaf.getEncoded());
        if(!MessageDigest.isEqual(pin,actual)) throw new CertificateException("Kamera-Zertifikat stimmt nicht mit dem gespeicherten Fingerabdruck überein");
    }
    public static SSLSocketFactory socketFactory(byte[] expected) throws GeneralSecurityException {
        byte[] pin=expected.clone();
        X509TrustManager trust=new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] chain,String type) throws CertificateException {throw new CertificateException("Keine Client-Zertifikate");}
            public void checkServerTrusted(X509Certificate[] chain,String type) throws CertificateException {
                if(chain==null||chain.length==0)throw new CertificateException("Kamera-Zertifikat fehlt");
                try {verify(pin,chain[0]);}catch(GeneralSecurityException e){throw new CertificateException(e.getMessage(),e);}
            }
        };
        SSLContext ctx=SSLContext.getInstance("TLS");ctx.init(null,new TrustManager[]{trust},new SecureRandom());return ctx.getSocketFactory();
    }
    public static HostnameVerifier verifier(String expectedHost,byte[] expected) {
        byte[] pin=expected.clone();
        return (host,session)->{
            if(!expectedHost.equals(host))return false;
            try {verify(pin,session.getPeerCertificates()[0]);return true;}catch(Exception e){return false;}
        };
    }
}
