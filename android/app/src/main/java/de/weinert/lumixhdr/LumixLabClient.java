package de.weinert.lumixhdr;

import org.json.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Independent implementation of the observed Lab 3.1.0 local API. Hardware unverified. */
public final class LumixLabClient {
    public interface ConnectionFactory { HttpURLConnection open(URL url) throws IOException; }
    public interface Progress { void update(long bytes,long total); }
    public static final int MAX_IMAGE=40*1024*1024, CHUNK=4*1024*1024;
    private final ConnectionFactory connections;
    private final String host;
    private final SSLSocketFactory tls;
    private final HostnameVerifier verifier;
    private volatile String token;
    private static final Set<String> SETTINGS=new HashSet<>(Arrays.asList("shtrspeed","iso","exposure","focal","quality","drivemode","focusmode","whitebalance","recmode1","recmode2","operatepriority","liveviewsize"));
    public LumixLabClient(ConnectionFactory connections,String host,String fingerprint) throws Exception {
        this.connections=connections;this.host=CameraTrust.localHost(host);
        byte[] pin=CameraTrust.parsePin(fingerprint);tls=CameraTrust.socketFactory(pin);verifier=CameraTrust.verifier(host,pin);
    }
    public boolean isAuthenticated(){return token!=null;}
    public void disconnect(){token=null;}
    static String authorization(String token) throws IOException {
        if(token==null||token.isEmpty()||token.length()>8192||token.chars().anyMatch(c->c<32||c>126||c=='\''))throw new IOException("Ungültiger oder fehlender Sitzungstoken");
        return "'Bearer "+token+"'";
    }
    private HttpsURLConnection open(String path,JSONObject body,boolean auth,boolean binary,String range) throws Exception {
        if(auth&&!isAuthenticated())throw new IOException("Zuerst an der Kamera anmelden");
        HttpURLConnection raw=connections.open(new URL("https://"+host+"/"+path));
        if(!(raw instanceof HttpsURLConnection)){raw.disconnect();throw new IOException("HTTPS-Verbindung erforderlich");}
        HttpsURLConnection c=(HttpsURLConnection)raw;
        try {
            c.setSSLSocketFactory(tls);c.setHostnameVerifier(verifier);
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(7000);c.setReadTimeout(binary?20000:12000);
            c.setUseCaches(false);c.setRequestMethod("POST");c.setRequestProperty("Accept-Encoding","identity");
            c.setRequestProperty("Content-Type",binary?"application/octet-stream":"application/json");
            c.setRequestProperty("Accept",binary?"binary/octet-stream":"application/json");
            if(auth)c.setRequestProperty("Authorization",authorization(token));
            if(range!=null)c.setRequestProperty("Range",range);
            if(body!=null){byte[] data=body.toString().getBytes(StandardCharsets.UTF_8);c.setDoOutput(true);c.setFixedLengthStreamingMode(data.length);try(OutputStream out=c.getOutputStream()){out.write(data);}}
            return c;
        } catch(Exception error){c.disconnect();throw error;}
    }
    private void httpStatus(HttpsURLConnection c,String path) throws IOException {
        int status=c.getResponseCode();
        if(status==401||status==403)disconnect();
        if(status!=200&&status!=206)throw new IOException(path+": HTTP "+status+(status==401||status==403?". Erneut anmelden; eigene Kameraregistrierung eventuell erforderlich":""));
    }
    static byte[] readLimited(InputStream in,int limit) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[32768];int n;
        while((n=in.read(b))!=-1){if((long)out.size()+n>limit)throw new IOException("Kamera-Antwort überschreitet das Größenlimit");out.write(b,0,n);}return out.toByteArray();
    }
    private JSONObject json(String path,JSONObject body,boolean auth) throws Exception {
        HttpsURLConnection c=open(path,body,auth,false,null);
        try {
            httpStatus(c,path);
            JSONObject result;
            try(InputStream in=c.getInputStream()){result=new JSONObject(new String(readLimited(in,2*1024*1024),StandardCharsets.UTF_8));}
            JSONObject header=result.optJSONObject("Header");
            if(header==null||!header.has("ResponseCode"))throw new IOException(path+": Kamera-Antwort ohne Header.ResponseCode");
            int code=header.getInt("ResponseCode");if(code==401||code==403)disconnect();
            if(code!=200)throw new IOException(path+": Kamera-Code "+code+(code==401||code==403?". Erneut anmelden oder eigene App registrieren":""));
            return result;
        }finally{c.disconnect();}
    }
    public void authenticate(String uuid,String friendlyName) throws Exception {
        disconnect();if(!uuid.matches("[a-fA-F0-9]{32}"))throw new IOException("Eigene App-UUID muss aus 32 Hex-Zeichen bestehen");
        JSONObject body=new JSONObject().put("AppInfo","Lumix HDR Companion 0.3.5").put("Uuid",uuid).put("FriendlyName",friendlyName);
        JSONObject data=json("auth",body,false).optJSONObject("Data");
        Object raw=data==null?null:data.opt("AuthToken");String next=raw instanceof String?(String)raw:null;authorization(next);token=next;
    }
    public JSONObject state() throws Exception{return json("remote/get-state",null,true);}
    private static void settingType(String type) throws IOException {if(!SETTINGS.contains(type))throw new IOException("Nicht unterstützter Einstellungstyp");}
    public JSONObject getSetting(String type) throws Exception{settingType(type);return json("remote/get-setting",new JSONObject().put("Type",type),true);}
    public JSONObject setSetting(String type,String value,String value2) throws Exception {
        settingType(type);if(value==null||value.isEmpty())throw new IOException("Kameraseitigen Value-Code auswählen");
        JSONObject b=new JSONObject().put("Type",type).put("Value",value);if(value2!=null&&!value2.isEmpty())b.put("Value2",value2);
        return json("remote/set-setting",b,true);
    }
    public JSONObject command(String command) throws Exception {
        if(!Arrays.asList("capture","capture_cancel","recmode","playmode").contains(command))throw new IOException("Nicht unterstützter Kamerabefehl");
        return json("remote/camcmd",new JSONObject().put("Command",command),true);
    }
    public JSONObject control(String type,String value,String value2) throws Exception {
        settingType(type);JSONObject b=new JSONObject().put("Type",type).put("Value",value);if(value2!=null&&!value2.isEmpty())b.put("Value2",value2);return json("remote/camctrl",b,true);
    }
    public JSONObject liveViewPort() throws Exception{return json("remote/get-lvport",null,true);}
    public JSONObject startStream(int port) throws Exception{if(port<1024||port>65535)throw new IOException("Ungültiger UDP-Port");return json("remote/start-stream",new JSONObject().put("UDPPortNumber",port),true);}
    public JSONObject stopStream() throws Exception{return json("remote/stop-stream",null,true);}
    public JSONObject galleryCount() throws Exception{return json("media/gallery/count",null,true);}
    public JSONObject gallery(int start,int limit) throws Exception {
        if(start<0||limit<1||limit>100)throw new IOException("Ungültige Galerieseite");
        return json("media/gallery/info",new JSONObject().put("StartIndex",start).put("Limit",limit),true);
    }
    public static final class Image {
        public final String id,name,burstId;public final int type,imageSize;public final long bytes;
        public Image(String id,String name,int type,int imageSize,long bytes,String burstId) throws IOException {
            if(id==null||id.isEmpty()||id.length()>512||type<0||(imageSize!=1&&imageSize!=2)||bytes<0||bytes>MAX_IMAGE)throw new IOException("Ungültiger oder zu großer Bildeintrag");
            this.id=id;this.name=name==null?id:name;this.type=type;this.imageSize=imageSize;this.bytes=bytes;this.burstId=burstId;
        }
        public JSONObject body() throws JSONException {
            JSONObject b=new JSONObject().put("ImageId",id).put("Type",type).put("ImageSize",imageSize);if(burstId!=null&&!burstId.isEmpty())b.put("BurstId",burstId);return b;
        }
        public static List<Image> jpegGallery(JSONObject response,int imageSize) throws Exception {
            JSONArray list=response.getJSONObject("Data").getJSONArray("DataList");List<Image> images=new ArrayList<>();
            for(int i=0;i<list.length();i++){
                JSONObject item=list.getJSONObject(i);String name=item.optString("OriginalFileName","");
                if(!name.toLowerCase(Locale.ROOT).matches(".*\\.jpe?g")||item.optInt("BurstCount",0)>1)continue;
                long size=item.optLong("Size",0);if(size<0||size>MAX_IMAGE)continue;
                images.add(new Image(item.getString("ImageId"),name,item.getInt("Type"),imageSize,size,null));
            }return images;
        }
    }
    static long validateRange(String header,long start,long end,long total) throws IOException {
        Matcher m=Pattern.compile("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matcher(header==null?"":header);
        try {
            if(!m.matches()||Long.parseLong(m.group(1))!=start||Long.parseLong(m.group(2))!=end||Long.parseLong(m.group(3))!=total)throw new IOException("Content-Range stimmt nicht mit angefordertem Bildabschnitt überein");
        }catch(NumberFormatException e){throw new IOException("Ungültiger Content-Range",e);}
        return end-start+1;
    }
    /** New transfer, bounded chunks; no append/resume across camera sessions. */
    public File downloadJpeg(Image image,File cache,Progress progress) throws Exception {
        File file=File.createTempFile("lab_",".jpg",cache);boolean done=false;
        try(FileOutputStream out=new FileOutputStream(file)) {
            long offset=0;boolean chunked=image.bytes>CHUNK;
            do {
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Übertragung abgebrochen");
                long end=chunked?Math.min(image.bytes-1,offset+CHUNK-1):0;
                HttpsURLConnection c=open("media/image",image.body(),true,true,chunked?"bytes="+offset+"-"+end:null);
                try {
                    httpStatus(c,"media/image");int status=c.getResponseCode();
                    String ct=c.getContentType();if(ct!=null&&(ct.toLowerCase(Locale.ROOT).contains("json")||ct.toLowerCase(Locale.ROOT).contains("text")))throw new IOException("Kamera liefert einen Fehlertext statt Bilddaten");
                    long expected=chunked?end-offset+1:image.bytes;
                    if(status==206){if(!chunked)throw new IOException("Unerwartete Teilantwort");validateRange(c.getHeaderField("Content-Range"),offset,end,image.bytes);}
                    else if(chunked){if(offset!=0)throw new IOException("Kamera ignoriert Range nach erstem Abschnitt");expected=image.bytes;}
                    long received=0;
                    try(InputStream in=c.getInputStream()) {
                        byte[] b=new byte[32768];int n;
                        while((n=in.read(b))!=-1){received+=n;if(offset+received>MAX_IMAGE||(expected>0&&received>expected))throw new IOException("Bildantwort größer als erwartet");out.write(b,0,n);if(progress!=null)progress.update(offset+received,image.bytes);}
                    }
                    if(received==0||(expected>0&&received!=expected))throw new IOException("Unvollständige Bildübertragung");
                    offset+=received;
                    if(!chunked||status==200)break;
                }finally{c.disconnect();}
            }while(offset<image.bytes);
            out.flush();
            try(InputStream in=new FileInputStream(file)){if(in.read()!=255||in.read()!=216||in.read()!=255)throw new IOException("Übertragene Datei ist kein JPEG; Dateityp-/Größencode prüfen");}
            done=true;return file;
        }finally{if(!done)file.delete();}
    }
    /** Only item values advertised in the selected setting branch are selectable. */
    public static List<String> advertisedValues(JSONObject response,String type) throws JSONException {
        JSONObject data=response.optJSONObject("Data");if(data==null)return Collections.emptyList();
        Object branch=data.opt(type);if(branch==null){for(Iterator<String> keys=data.keys();keys.hasNext();){String k=keys.next();if(k.equalsIgnoreCase(type)){branch=data.opt(k);break;}}}
        LinkedHashSet<String> values=new LinkedHashSet<>();collectItems(branch,values);return new ArrayList<>(values);
    }
    private static void collectItems(Object node,Set<String> values) throws JSONException {
        if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++)collectItems(a.get(i),values);}
        else if(node instanceof JSONObject){JSONObject o=(JSONObject)node;Object item=o.opt("item");if(item instanceof String)values.add((String)item);}
    }
}
