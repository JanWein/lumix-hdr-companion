package de.weinert.lumixhdr;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import org.json.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.*;

public class LumixLabClientTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private static final String PIN="00".repeat(32);
    private static final String UUID="0123456789abcdef0123456789abcdef";
    static final class Reply extends HttpsURLConnection {
        final byte[] data;final int status;final ByteArrayOutputStream body=new ByteArrayOutputStream();
        String contentType="application/json",range;boolean disconnected;
        Reply(int status,String data) throws Exception {this(status,data.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        Reply(int status,byte[] data) throws Exception {super(new URL("https://192.168.54.1/"));this.status=status;this.data=data;}
        public void connect(){}public void disconnect(){disconnected=true;}public boolean usingProxy(){return false;}
        public String getCipherSuite(){return "TEST";}
        public Certificate[] getLocalCertificates(){return null;}public Certificate[] getServerCertificates(){return null;}
        public OutputStream getOutputStream(){return body;}public InputStream getInputStream(){return new ByteArrayInputStream(data);}
        public int getResponseCode(){return status;}public String getContentType(){return contentType;}
        public String getHeaderField(String name){return "Content-Range".equals(name)?range:null;}
        JSONObject request() throws Exception{return new JSONObject(body.toString("UTF-8"));}
    }
    static final class Transport implements LumixLabClient.ConnectionFactory {
        final Queue<Reply> replies=new ArrayDeque<>();final List<URL> urls=new ArrayList<>();
        public HttpURLConnection open(URL url) throws IOException {urls.add(url);Reply r=replies.poll();if(r==null)throw new IOException("Unexpected extra request");return r;}
        Reply json(String json) throws Exception {Reply r=new Reply(200,json);replies.add(r);return r;}
        Reply binary(int status,byte[] data,String range) throws Exception{Reply r=new Reply(status,data);r.contentType="application/octet-stream";r.range=range;replies.add(r);return r;}
        LumixLabClient authenticated() throws Exception {json("{\"Header\":{\"ResponseCode\":200},\"Data\":{\"AuthToken\":\"test-session\"}}");LumixLabClient c=new LumixLabClient(this,"192.168.54.1",PIN);c.authenticate(UUID,"Test");return c;}
    }
    private static String ok="{\"Header\":{\"ResponseCode\":200},\"Data\":{}}";
    private static byte[] jpeg(int size){byte[] b=new byte[size];b[0]=(byte)255;b[1]=(byte)216;b[2]=(byte)255;return b;}

    @Test public void privateAddressesAndPinsAreStrict() throws Exception {
        for(String bad:new String[]{"8.8.8.8","localhost","192.168.001.1","192.168.999.1","https://192.168.54.1/","192.168.54.1@evil"})assertThrows(IOException.class,()->CameraTrust.localHost(bad));
        assertEquals("192.168.54.1",CameraTrust.localHost("192.168.54.1"));
        assertEquals(PIN,CameraTrust.formatPin(CameraTrust.parsePin("00:".repeat(31)+"00")));
        assertThrows(IOException.class,()->CameraTrust.parsePin(""));assertThrows(IOException.class,()->CameraTrust.parsePin("gg".repeat(32)));
    }
    @Test public void authenticationAndQuotedBearerMatchObservedWire() throws Exception {
        Transport t=new Transport();LumixLabClient c=t.authenticated();Reply r=t.json(ok);c.state();
        assertEquals("https://192.168.54.1/auth",t.urls.get(0).toString());
        assertEquals("POST",r.getRequestMethod());assertEquals("'Bearer test-session'",r.getRequestProperty("Authorization"));
        assertEquals("application/json",r.getRequestProperty("Content-Type"));assertEquals(0,r.body.size());assertFalse(r.getInstanceFollowRedirects());assertTrue(r.disconnected);
    }
    @Test public void ownIdentityAndNoCopiedPanasonicAppInfo() throws Exception {
        Transport t=new Transport();Reply r=t.json("{\"Header\":{\"ResponseCode\":200},\"Data\":{\"AuthToken\":\"token\"}}");
        LumixLabClient c=new LumixLabClient(t,"192.168.54.1",PIN);c.authenticate(UUID,"Own Android");
        assertEquals(UUID,r.request().getString("Uuid"));assertEquals("Lumix HDR Companion 0.3.5",r.request().getString("AppInfo"));assertEquals("Own Android",r.request().getString("FriendlyName"));assertNull(r.getRequestProperty("Authorization"));
    }
    @Test public void invalidOrMissingAuthTokenCannotCreateSession() throws Exception {
        for(String data:new String[]{"{}","{\"AuthToken\":123}","{\"AuthToken\":\"\"}","{\"AuthToken\":\"bad\\r\\nvalue\"}","{\"AuthToken\":\"bad'value\"}"}){
            Transport t=new Transport();t.json("{\"Header\":{\"ResponseCode\":200},\"Data\":"+data+"}");LumixLabClient c=new LumixLabClient(t,"192.168.54.1",PIN);
            assertThrows(IOException.class,()->c.authenticate(UUID,"Test"));assertFalse(c.isAuthenticated());
        }
    }
    @Test public void application401ClearsSessionEvenWhenHttp200() throws Exception {
        Transport t=new Transport();LumixLabClient c=t.authenticated();t.json("{\"Header\":{\"ResponseCode\":401}}");
        assertThrows(IOException.class,c::state);assertFalse(c.isAuthenticated());int n=t.urls.size();assertThrows(IOException.class,c::state);assertEquals(n,t.urls.size());
    }
    @Test public void http403ClearsSessionAndDoesNotLeakResponse() throws Exception {
        Transport t=new Transport();LumixLabClient c=t.authenticated();t.replies.add(new Reply(403,"secret-body"));IOException e=assertThrows(IOException.class,c::state);assertFalse(c.isAuthenticated());assertFalse(e.getMessage().contains("secret"));
    }
    @Test public void redirectsAreRejectedWithoutFollowing() throws Exception {
        Transport t=new Transport();LumixLabClient c=t.authenticated();t.replies.add(new Reply(302,""));assertThrows(IOException.class,c::state);assertEquals(2,t.urls.size());
    }
    @Test public void rejectedReauthenticationCannotRetainOldToken() throws Exception {
        Transport t=new Transport();LumixLabClient c=t.authenticated();t.json("{\"Header\":{\"ResponseCode\":500}}");assertThrows(IOException.class,()->c.authenticate(UUID,"Test"));assertFalse(c.isAuthenticated());
    }
    @Test public void captureAndSettingBodiesAreCaseSensitive() throws Exception {
        Transport t=new Transport();LumixLabClient c=t.authenticated();Reply settings=t.json(ok),capture=t.json(ok),control=t.json(ok);
        c.setSetting("shtrspeed","camera-code","sub-code");c.command("capture");c.control("focusmode","camera-focus",null);
        assertEquals("camera-code",settings.request().getString("Value"));assertEquals("sub-code",settings.request().getString("Value2"));assertEquals("shtrspeed",settings.request().getString("Type"));
        assertEquals("capture",capture.request().getString("Command"));assertFalse(control.request().has("Value2"));
        assertThrows(IOException.class,()->c.command("delete"));assertThrows(IOException.class,()->c.setSetting("invented","1",null));
    }
    @Test public void settingSelectionUsesOnlyAdvertisedItemsOfSelectedType() throws Exception {
        JSONObject response=new JSONObject("{\"Data\":{\"shtrspeed\":[{\"item\":\"opaque-01\",\"subitem\":[\"not-a-main-value\"]},{\"item\":\"opaque-02\"}],\"iso\":[{\"item\":\"other-type\"}]}}");
        assertEquals(Arrays.asList("opaque-01","opaque-02"),LumixLabClient.advertisedValues(response,"shtrspeed"));
        assertTrue(LumixLabClient.advertisedValues(response,"focal").isEmpty());
    }
    @Test public void galleryPaginationAndFilteringUseServerIdsAndTypes() throws Exception {
        Transport t=new Transport();LumixLabClient c=t.authenticated();Reply r=t.json(ok);c.gallery(30,30);assertEquals(30,r.request().getInt("StartIndex"));assertEquals(30,r.request().getInt("Limit"));
        JSONObject data=new JSONObject("{\"Data\":{\"DataList\":[{\"ImageId\":\"abc\",\"Type\":7,\"OriginalFileName\":\"P001.JPG\",\"Size\":123},{\"ImageId\":\"raw\",\"OriginalFileName\":\"P001.RW2\"},{\"ImageId\":\"burst\",\"OriginalFileName\":\"P002.JPG\",\"BurstCount\":3}]}}");
        List<LumixLabClient.Image> images=LumixLabClient.Image.jpegGallery(data,1);assertEquals(1,images.size());assertEquals("abc",images.get(0).id);assertEquals(7,images.get(0).type);
        assertThrows(IOException.class,()->c.gallery(-1,30));
    }
    @Test public void jpegDownloadUsesOctetBodyAndExactId() throws Exception {
        Transport t=new Transport();LumixLabClient c=t.authenticated();Reply r=t.binary(200,jpeg(1024),null);
        File f=c.downloadJpeg(new LumixLabClient.Image("id","name",7,1,1024,"burst"),temporary.getRoot(),null);
        assertEquals(1024,f.length());assertEquals("application/octet-stream",r.getRequestProperty("Content-Type"));assertEquals("binary/octet-stream",r.getRequestProperty("Accept"));assertEquals("id",r.request().getString("ImageId"));assertEquals("burst",r.request().getString("BurstId"));assertEquals(7,r.request().getInt("Type"));assertNull(r.getRequestProperty("Range"));
    }
    @Test public void largeImageIsAssembledFromValidatedRanges() throws Exception {
        Transport t=new Transport();LumixLabClient c=t.authenticated();int total=LumixLabClient.CHUNK+33;
        Reply first=t.binary(206,jpeg(LumixLabClient.CHUNK),"bytes 0-4194303/"+total),second=t.binary(206,new byte[33],"bytes 4194304-4194336/"+total);
        File f=c.downloadJpeg(new LumixLabClient.Image("id","name",0,1,total,null),temporary.getRoot(),null);
        assertEquals(total,f.length());assertEquals("bytes=0-4194303",first.getRequestProperty("Range"));assertEquals("bytes=4194304-4194336",second.getRequestProperty("Range"));
    }
    @Test public void full200IsAcceptedOnlyAsInitialRangeFallback() throws Exception {
        Transport t=new Transport();LumixLabClient c=t.authenticated();int total=LumixLabClient.CHUNK+1;t.binary(200,jpeg(total),null);
        assertEquals(total,c.downloadJpeg(new LumixLabClient.Image("id","name",0,1,total,null),temporary.getRoot(),null).length());
    }
    @Test public void corruptRangeDeletesPartialFile() throws Exception {
        Transport t=new Transport();LumixLabClient c=t.authenticated();int total=LumixLabClient.CHUNK+1;t.binary(206,jpeg(LumixLabClient.CHUNK),"bytes 1-4194304/"+total);
        assertThrows(IOException.class,()->c.downloadJpeg(new LumixLabClient.Image("id","name",0,1,total,null),temporary.getRoot(),null));assertEquals(0,temporary.getRoot().list().length);
    }
    @Test public void serverIgnoringSecondRangeCannotDuplicateImage() throws Exception {
        Transport t=new Transport();LumixLabClient c=t.authenticated();int total=LumixLabClient.CHUNK+1;t.binary(206,jpeg(LumixLabClient.CHUNK),"bytes 0-4194303/"+total);t.binary(200,jpeg(total),null);
        assertThrows(IOException.class,()->c.downloadJpeg(new LumixLabClient.Image("id","name",0,1,total,null),temporary.getRoot(),null));assertEquals(0,temporary.getRoot().list().length);
    }
    @Test public void truncatedAndNonJpegResponsesAreRemoved() throws Exception {
        for(byte[] bytes:new byte[][]{jpeg(99),new byte[100]}){
            Transport t=new Transport();LumixLabClient c=t.authenticated();t.binary(200,bytes,null);assertThrows(IOException.class,()->c.downloadJpeg(new LumixLabClient.Image("id","name",0,1,100,null),temporary.getRoot(),null));assertEquals(0,temporary.getRoot().list().length);
        }
    }
    @Test public void jsonErrorIsNeverImportedAsImage() throws Exception {
        Transport t=new Transport();LumixLabClient c=t.authenticated();t.json("{\"Header\":{\"ResponseCode\":401}}");assertThrows(IOException.class,()->c.downloadJpeg(new LumixLabClient.Image("id","name",0,1,0,null),temporary.getRoot(),null));assertEquals(0,temporary.getRoot().list().length);
    }
    @Test public void liveViewRequestsPreserveIntegerPortAndNoBodyForStop() throws Exception {
        Transport t=new Transport();LumixLabClient c=t.authenticated();Reply start=t.json(ok),stop=t.json(ok);c.startStream(49152);c.stopStream();assertEquals(49152,start.request().getInt("UDPPortNumber"));assertEquals(0,stop.body.size());assertThrows(IOException.class,()->c.startStream(0));
    }
    @Test public void malformedAndOversizedJsonIsRejected() throws Exception {
        Transport t=new Transport();LumixLabClient c=t.authenticated();t.json("{\"Data\":{}}");assertThrows(IOException.class,c::state);
        t.json("x".repeat(2*1024*1024+1));assertThrows(IOException.class,c::state);
    }
    @Test public void diagnosticRedactionRemovesNestedTokensAndPasswords() throws Exception {
        JSONObject input=new JSONObject("{\"Data\":{\"AuthToken\":\"secret\",\"List\":[{\"Password\":\"hidden\"}],\"Value\":\"visible\"}}");String result=ModernCameraPanel.redact(input).toString();assertFalse(result.contains("secret"));assertFalse(result.contains("hidden"));assertTrue(result.contains("visible"));
    }
    @Test public void realTlsHandshakeRejectsWrongCertificatePin() throws Exception {
        // Generate a disposable key locally. No private key fixture is distributed.
        String password=java.util.UUID.randomUUID().toString();
        File keystore=new File(temporary.getRoot(),"camera-test-only.p12"),log=new File(temporary.getRoot(),"keytool.log");
        String executable="keytool"+(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?".exe":"");
        File keytool=new File(System.getProperty("java.home"),"bin/"+executable);
        Process generation=new ProcessBuilder(keytool.getPath(),"-genkeypair","-storetype","PKCS12","-keystore",keystore.getPath(),"-alias","camera-test","-keyalg","RSA","-keysize","2048","-validity","2","-storepass",password,"-keypass",password,"-dname","CN=localhost","-noprompt").redirectErrorStream(true).redirectOutput(log).start();
        if(!generation.waitFor(20,TimeUnit.SECONDS)){generation.destroyForcibly();throw new IOException("Timed out generating the disposable TLS test key.");}
        assertEquals("Generate disposable TLS test key with the JDK keytool",0,generation.exitValue());
        KeyStore keys=KeyStore.getInstance("PKCS12");try(InputStream in=new FileInputStream(keystore)){keys.load(in,password.toCharArray());}
        String alias=keys.aliases().nextElement();byte[] expected=MessageDigest.getInstance("SHA-256").digest(keys.getCertificate(alias).getEncoded());
        CameraTrust.verify(expected,keys.getCertificate(alias));assertThrows(GeneralSecurityException.class,()->CameraTrust.verify(new byte[32],keys.getCertificate(alias)));
        KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,password.toCharArray());SSLContext serverContext=SSLContext.getInstance("TLS");serverContext.init(km.getKeyManagers(),null,null);
        for(boolean correct:new boolean[]{true,false}){
            ExecutorService pool=Executors.newSingleThreadExecutor();
            try(SSLServerSocket server=(SSLServerSocket)serverContext.getServerSocketFactory().createServerSocket(0,1,InetAddress.getLoopbackAddress())){
                server.setSoTimeout(5000);Future<?> accept=pool.submit(()->{try(SSLSocket peer=(SSLSocket)server.accept()){peer.setSoTimeout(5000);peer.startHandshake();}catch(IOException ignored){}});
                try(SSLSocket socket=(SSLSocket)CameraTrust.socketFactory(correct?expected:new byte[32]).createSocket(InetAddress.getLoopbackAddress(),server.getLocalPort())){socket.setSoTimeout(5000);if(correct){socket.startHandshake();assertTrue(CameraTrust.verifier("192.168.54.1",expected).verify("192.168.54.1",socket.getSession()));assertFalse(CameraTrust.verifier("192.168.54.1",expected).verify("192.168.54.2",socket.getSession()));assertFalse(CameraTrust.verifier("192.168.54.1",new byte[32]).verify("192.168.54.1",socket.getSession()));}else assertThrows(IOException.class,socket::startHandshake);}
                accept.get(6,TimeUnit.SECONDS);
            }finally{pool.shutdownNow();}
        }
    }
}
