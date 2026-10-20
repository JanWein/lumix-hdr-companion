package de.weinert.lumixhdr;

import android.net.Network;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Experimental legacy Lumix Wi-Fi client. S9 compatibility is NOT established. */
public final class LumixWifi {
    private final Network network;
    private final String host;
    public LumixWifi(Network network,String host) throws IOException {
        // Local IPv4 literal only. No URLs, DNS names or public destinations.
        if(!host.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")) throw new IOException("Lokale IPv4-Adresse eingeben");
        InetAddress address=InetAddress.getByName(host);
        if(!address.isSiteLocalAddress()) throw new IOException("Nur private Kamera-Adressen sind erlaubt");
        this.network=network;this.host=host;
    }
    private byte[] request(URL url,String body,int limit) throws Exception {
        if(!url.getHost().equals(host)||!url.getProtocol().equals("http")||url.getUserInfo()!=null) throw new IOException("Ungültiges Kamera-Ziel");
        HttpURLConnection c=(HttpURLConnection)network.openConnection(url);
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(5000);c.setReadTimeout(10000);
        try {
            if(body!=null) {
                c.setRequestMethod("POST");c.setDoOutput(true);
                c.setRequestProperty("Content-Type","text/xml; charset=utf-8");
                c.setRequestProperty("SOAPAction","\"urn:schemas-upnp-org:service:ContentDirectory:1#Browse\"");
                try(OutputStream out=c.getOutputStream()) {out.write(body.getBytes(StandardCharsets.UTF_8));}
            }
            if(c.getResponseCode()!=200) throw new IOException("Kamera HTTP "+c.getResponseCode());
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream input=c.getInputStream()) {
                byte[] buf=new byte[32768];int n;
                while((n=input.read(buf))!=-1) {if(out.size()+n>limit) throw new IOException("Kamera-Antwort zu groß");out.write(buf,0,n);}
            }
            return out.toByteArray();
        } finally {c.disconnect();}
    }
    private String command(String query) throws Exception {
        String result=new String(request(new URL("http://"+host+"/cam.cgi?"+query),null,1024*1024),StandardCharsets.UTF_8).trim();
        boolean ok=result.equals("ok")||result.startsWith("ok,");
        if(result.startsWith("<")) {NodeList nodes=parse(result).getElementsByTagNameNS("*","result");ok=nodes.getLength()>0&&nodes.item(0).getTextContent().trim().equals("ok");}
        if(!ok) throw new IOException("Kamera lehnt alte WLAN-API ab. Anmeldung/Protokoll dieser Firmware ist nicht unterstützt. Bitte LUMIX Lab + Dateiimport verwenden.");
        return result;
    }
    static Document parse(String xml) throws Exception {
        if(xml.contains("<!DOCTYPE")||xml.contains("<!ENTITY")) throw new IOException("Unerwartetes XML");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setExpandEntityReferences(false);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
    private Document browse(int start,int count) throws Exception {
        String body="<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body>"+
            "<u:Browse xmlns:u=\"urn:schemas-upnp-org:service:ContentDirectory:1\" xmlns:pana=\"urn:schemas-panasonic-com:pana\">"+
            "<ObjectID>0</ObjectID><BrowseFlag>BrowseDirectChildren</BrowseFlag><Filter>*</Filter><StartingIndex>"+start+
            "</StartingIndex><RequestedCount>"+count+"</RequestedCount><SortCriteria></SortCriteria><pana:X_FromCP>LumixLink2.0</pana:X_FromCP>"+
            "</u:Browse></s:Body></s:Envelope>";
        return parse(new String(request(new URL("http://"+host+":60606/Server0/CDS_control"),body,4*1024*1024),StandardCharsets.UTF_8));
    }
    public List<File> downloadVisibleJpegs(File cache) throws Exception {
        command("mode=accctrl&type=req_acc&value=0&value2=LumixHDR");
        List<File> files=new ArrayList<>();boolean play=false;
        try {
            command("mode=camcmd&value=playmode");play=true;
            Document response=browse(0,30);
            NodeList results=response.getElementsByTagNameNS("*","Result");
            if(results.getLength()==0) throw new IOException("Keine Medienliste. Aktuelle S9-WLAN-API nicht bestätigt.");
            Document didl=parse(results.item(0).getTextContent());
            NodeList items=didl.getElementsByTagNameNS("*","item");
            for(int i=0;i<items.getLength();i++) {
                Element item=(Element)items.item(i);NodeList resources=item.getElementsByTagNameNS("*","res");
                for(int j=0;j<resources.getLength();j++) {
                    Element resource=(Element)resources.item(j);String protocol=resource.getAttribute("protocolInfo");
                    // Only originals retain the metadata needed for automatic bracket validation.
                    if(!protocol.contains("image/jpeg")||!protocol.contains("PANASONIC.COM_PN=CAM_ORG")) continue;
                    byte[] bytes=request(new URL(resource.getTextContent().trim()),null,40*1024*1024);
                    File temporary=File.createTempFile("camera_",".jpg",cache);
                    try(FileOutputStream out=new FileOutputStream(temporary)) {out.write(bytes);}
                    files.add(temporary);break;
                }
            }
            if(files.isEmpty()) throw new IOException("Keine Original-JPEGs in den ersten 30 Kamera-Einträgen. RAW+JPEG aktivieren oder Dateiimport nutzen.");
            return files;
        } finally {if(play)try {command("mode=camcmd&value=recmode");}catch(Exception ignored) {}}
    }
}
