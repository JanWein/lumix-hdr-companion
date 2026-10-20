package de.weinert.lumixhdr;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.*;
import android.os.Build;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Experimental camera operations kept separate from the existing local HDR flow. */
final class ModernCameraPanel implements AutoCloseable {
    interface Task{void run() throws Exception;}
    interface Runner{void run(Task task);}
    interface DownloadAction{void download(List<LumixLabClient.Image> selected);}
    private final Activity activity;private final SharedPreferences prefs;private final Runner runner;private final DownloadAction downloadAction;
    private final ConnectivityManager connectivity;
    private EditText host,pin,page;private TextView state,details;private Spinner types,values,imageSize;
    private volatile LumixLabClient client;private Network network;
    private volatile String settingType;private volatile List<String> settingValues=Collections.emptyList();private volatile List<LumixLabClient.Image> gallery=Collections.emptyList();
    private final AtomicInteger configuration=new AtomicInteger();
    private CameraFingerprintReader reader;private volatile boolean closed;
    private final StudioTheme style;
    private LinearLayout diagnostics;
    private LinearLayout setupCard,photosCard;
    private TextView disconnectAction;
    private TextView connectionTitle,connectionBadge,trustStep,networkStep,connectAction,browseAction,captureAction;
    private Runnable statusListener=()->{};
    private int galleryOffset;
    ModernCameraPanel(Activity a,LinearLayout root,SharedPreferences p,Runner r,DownloadAction d){
        activity=a;prefs=p;runner=r;downloadAction=d;style=new StudioTheme(a);connectivity=(ConnectivityManager)a.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(!prefs.contains("camera_uuid"))prefs.edit().putString("camera_uuid",UUID.randomUUID().toString().replace("-","")).apply();
        buildDiagnostics();
        LinearLayout device=style.card(root);LinearLayout hero=style.row();LinearLayout icon=style.column();icon.setGravity(android.view.Gravity.CENTER);icon.setBackground(style.shape(style.soft,18));icon.addView(style.icon("camera",style.accent,32));hero.addView(icon,new LinearLayout.LayoutParams(style.dp(64),style.dp(64)));
        LinearLayout text=style.column();connectionTitle=style.text("LUMIX S9",23,style.text,true);text.addView(connectionTitle);style.gap(text,7);connectionBadge=style.caption("Noch nicht verbunden");text.addView(connectionBadge);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=style.dp(16);hero.addView(text,tp);device.addView(hero);
        style.gap(device,22);state=style.caption("Verbinde dein Telefon mit der Kamera. Danach bestätigst du einmal ihre Identität.");state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);device.addView(state);
        disconnectAction=style.link("Verbindung trennen",v->{invalidate();message("Verbindung getrennt.");});device.addView(disconnectAction);
        LinearLayout setup=style.card(root);setupCard=setup;setup.addView(style.text("In drei Schritten bereit",20,style.text,true));style.gap(setup,18);
        networkStep=step(setup,"1","Kamera-WLAN wählen","In den WLAN-Einstellungen deines Telefons.","WLAN öffnen",v->activity.startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)));
        style.divider(setup);
        trustStep=step(setup,"2","Deine Kamera bestätigen","Einmalig per Bluetooth oder mit bekanntem Fingerabdruck.","Kamera bestätigen",v->readFingerprint());
        style.divider(setup);
        TextView third=style.text("3  Verbindung herstellen",17,style.text,true);setup.addView(third);style.gap(setup,12);connectAction=style.action("Verbinden",true,v->{if(isConnected()){invalidate();message("Verbindung getrennt.");}else connectGuided();});setup.addView(connectAction);
        LinearLayout photos=style.card(root);photosCard=photos;photos.addView(style.text("Von der Kamera ins Studio",20,style.text,true));style.gap(photos,9);photos.addView(style.caption("Wähle deine Belichtungsreihe. Im Studio entscheidest du, wann dein HDR entsteht."));style.gap(photos,18);
        browseAction=style.action("Fotos auswählen",false,v->browseGallery(false));photos.addView(browseAction);style.gap(photos,4);captureAction=style.link("Aufnahme auslösen",v->command("capture"));captureAction.setGravity(android.view.Gravity.CENTER);photos.addView(captureAction);
        LinearLayout note=style.card(root);note.addView(style.text("Verbindungsvorschau",15,style.text,true));style.gap(note,8);note.addView(style.caption("Die direkte S9-Verbindung ist noch im Test. Die erste Anmeldung kann an der noch offenen Kameraregistrierung scheitern. Du kannst jederzeit Fotos vom Telefon verwenden."));style.gap(note,8);note.addView(style.link("Verbindungsdetails und Diagnose",v->openDiagnostics()));
        android.text.TextWatcher changed=new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){invalidate();message("Verbindungsdaten geändert. Bitte erneut verbinden.");}
            public void afterTextChanged(android.text.Editable e){}
        };host.addTextChangedListener(changed);pin.addTextChangedListener(changed);refreshConnection();
    }
    private TextView step(LinearLayout root,String n,String title,String subtitle,String action,View.OnClickListener click){
        root.addView(style.text(n+"  "+title,17,style.text,true));style.gap(root,6);TextView text=style.caption(subtitle);root.addView(text);root.addView(style.link(action,click));return text;
    }
    private void buildDiagnostics(){
        diagnostics=style.column();diagnostics.setPadding(style.dp(24),style.dp(12),style.dp(24),style.dp(24));
        label(diagnostics,"Verbindungsdetails",23);
        label(diagnostics,"Nur für Einrichtung und Fehlersuche. Technische Werte bleiben von der normalen Bedienung getrennt.",14);
        host=edit(diagnostics,"Kamera-IP",prefs.getString("camera_host","192.168.54.1"));
        add(diagnostics,"WLAN-Gateway übernehmen",v->chooseNetwork(true));
        pin=edit(diagnostics,"SHA-256-Fingerabdruck",prefs.getString("camera_pin",""));
        label(diagnostics,"Aus bekannter Quelle oder per Bluetooth von deiner Kamera. Keine ungeprüfte Übernahme aus HTTPS.",13);
        add(diagnostics,"Fingerabdruck per Bluetooth lesen",v->readFingerprint());
        label(diagnostics,"Eigene App-ID: "+prefs.getString("camera_uuid",""),12);
        add(diagnostics,"Kamerazustand lesen",v->run(()->show(client().state())));
        types=select(diagnostics,"Einstellung",new String[]{"shtrspeed","iso","exposure","focal","quality","drivemode","focusmode","whitebalance","operatepriority","liveviewsize"});
        values=select(diagnostics,"Kameraseitiger Value-Code",new String[]{"Zuerst Werte lesen"});
        add(diagnostics,"Erlaubte Werte lesen",v->{String type=types.getSelectedItem().toString();run(()->{
            JSONObject response=client().getSetting(type);List<String> offered=LumixLabClient.advertisedValues(response,type);
            ui(()->{settingType=type;settingValues=offered;values.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,offered.isEmpty()?Collections.singletonList("Keine auswertbaren Werte"):offered));});show(response);
        });});
        add(diagnostics,"Wert setzen",v->{String type=types.getSelectedItem().toString();int index=values.getSelectedItemPosition();if(!type.equals(settingType)||index<0||index>=settingValues.size()){message("Zuerst erlaubte Werte für diese Einstellung lesen.");return;}String selected=settingValues.get(index);run(()->show(client().setSetting(type,selected,null)));});
        add(diagnostics,"Aufnahmemodus",v->command("recmode"));add(diagnostics,"Aufnahme abbrechen",v->command("capture_cancel"));add(diagnostics,"Wiedergabemodus",v->command("playmode"));
        add(diagnostics,"Bildanzahl lesen",v->run(()->show(client().galleryCount())));
        page=edit(diagnostics,"Galerie-Startindex","0");page.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        imageSize=select(diagnostics,"ImageSize-Code",new String[]{"1 (Standardpfad)","2 (experimentell)"});
        add(diagnostics,"Galerieseite lesen",v->{try{galleryOffset=Integer.parseInt(page.getText().toString());browseGallery(false);}catch(Exception e){message("Gültigen Startindex eingeben.");}});
        add(diagnostics,"Livebild-Port abfragen",v->run(()->show(client().liveViewPort())));
        label(diagnostics,"Livebildanzeige, RAW-/HEIF-Import und vollständige BLE-Registrierung sind noch offen.",13);
        details=label(diagnostics,"Kameraantworten erscheinen hier. Zugangsdaten werden ausgeblendet.",12);details.setTypeface(android.graphics.Typeface.MONOSPACE);details.setTextIsSelectable(true);
    }
    void openDiagnostics(){
        if(diagnostics.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)diagnostics.getParent()).removeView(diagnostics);
        ScrollView scroll=new ScrollView(activity);scroll.addView(diagnostics);new AlertDialog.Builder(activity).setView(scroll).setPositiveButton("Fertig",null).show();
    }
    void setStatusListener(Runnable listener){statusListener=listener;refreshConnection();}
    boolean isConnected(){LumixLabClient c=client;NetworkCapabilities caps=network==null?null:connectivity.getNetworkCapabilities(network);return c!=null&&c.isAuthenticated()&&caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);}
    void refreshConnection(){ui(()->{
        boolean connected=isConnected();connectionBadge.setText(connected?"Verbunden":"Noch nicht verbunden");connectionBadge.setTextColor(connected?style.success:style.secondary);connectAction.setText(connected?"Verbindung trennen":"Verbinden");style.enabled(browseAction,connected);style.enabled(captureAction,connected);setupCard.setVisibility(connected?View.GONE:View.VISIBLE);photosCard.setVisibility(connected?View.VISIBLE:View.GONE);disconnectAction.setVisibility(connected?View.VISIBLE:View.GONE);
        boolean wifi=false;for(Network n:connectivity.getAllNetworks()){NetworkCapabilities caps=connectivity.getNetworkCapabilities(n);if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))wifi=true;}
        networkStep.setText(wifi?"WLAN verfügbar. Wähle das Netz deiner Kamera.":"In den WLAN-Einstellungen deines Telefons.");
        try{CameraTrust.parsePin(pin.getText().toString());trustStep.setText("Fingerabdruck deiner Kamera ist hinterlegt.");}catch(Exception e){trustStep.setText("Einmalig per Bluetooth oder mit bekanntem Fingerabdruck.");}
        statusListener.run();
    });}
    private void connectGuided(){try{CameraTrust.parsePin(pin.getText().toString());chooseNetwork(false);}catch(Exception e){message("Bestätige zuerst deine Kamera in Schritt 2. Falls Bluetooth nicht verfügbar ist, kannst du ihren bekannten Fingerabdruck in den Verbindungsdetails eingeben.");}}
    private void browseGallery(boolean next){
        if(next)galleryOffset+=30;final int start=galleryOffset,size=imageSize.getSelectedItemPosition()+1;
        run(()->{JSONObject response=client().gallery(start,30);List<LumixLabClient.Image> images=LumixLabClient.Image.jpegGallery(response,size);show(response);ui(()->{gallery=images;selectImages();});});
    }
    private TextView label(LinearLayout root,String text,int size){TextView v=style.text(text,size,size>=19?style.text:style.secondary,size>=19);v.setPadding(0,style.dp(12),0,style.dp(6));root.addView(v);return v;}
    private EditText edit(LinearLayout root,String hint,String text){label(root,hint,14);EditText e=new EditText(activity);e.setTextColor(style.text);e.setSingleLine();e.setText(text);e.setSaveEnabled(false);e.setPadding(style.dp(12),style.dp(12),style.dp(12),style.dp(12));e.setBackground(style.outline(style.surface,12));root.addView(e);return e;}
    private Spinner select(LinearLayout root,String label,String[] choices){label(root,label,14);Spinner s=new Spinner(activity);s.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,choices));root.addView(s);return s;}
    private void add(LinearLayout root,String text,View.OnClickListener listener){style.gap(root,8);root.addView(style.action(text,false,listener));}
    private void ui(Runnable r){activity.runOnUiThread(()->{if(!closed)r.run();});}
    private void message(String s){ui(()->state.setText(s));refreshConnection();}
    private void clearLists(){gallery=Collections.emptyList();settingValues=Collections.emptyList();settingType=null;}
    private void invalidate(){synchronized(configuration){configuration.incrementAndGet();if(client!=null)client.disconnect();client=null;clearLists();}}
    private void run(Task t){runner.run(()->{try{t.run();}catch(Exception e){if(client!=null&&!client.isAuthenticated())clearLists();message("Hinweis: "+e.getMessage());throw e;}});}
    private void command(String cmd){run(()->show(client().command(cmd)));}
    private void show(JSONObject response){try{String text=((JSONObject)redact(response)).toString(2);String view=text.length()>18000?text.substring(0,18000)+"\n… gekürzt":text;ui(()->details.setText(view));}catch(Exception e){message("Antwort konnte nicht angezeigt werden");}}
    static Object redact(Object node) throws JSONException {
        if(node instanceof JSONObject){JSONObject copy=new JSONObject();JSONObject o=(JSONObject)node;for(Iterator<String> keys=o.keys();keys.hasNext();){String k=keys.next();String lower=k.toLowerCase(Locale.ROOT);copy.put(k,lower.contains("token")||lower.contains("password")||lower.contains("authorization")?"[ausgeblendet]":redact(o.get(k)));}return copy;}
        if(node instanceof JSONArray){JSONArray copy=new JSONArray();JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++)copy.put(redact(a.get(i)));return copy;}return node;
    }
    private void chooseNetwork(boolean gatewayOnly){
        List<Network> candidates=new ArrayList<>();for(Network n:connectivity.getAllNetworks()){NetworkCapabilities c=connectivity.getNetworkCapabilities(n);if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))candidates.add(n);}
        if(candidates.isEmpty()){message("Zuerst Android mit dem Kamera-WLAN verbinden und ohne Internet verbunden bleiben");return;}
        if(candidates.size()==1){selectedNetwork(candidates.get(0),gatewayOnly);return;}
        String[] names=new String[candidates.size()];for(int i=0;i<names.length;i++)names[i]="WLAN "+candidates.get(i)+" / Gateway "+gateway(candidates.get(i));
        new AlertDialog.Builder(activity).setTitle("Kamera-WLAN auswählen").setItems(names,(dialog,i)->selectedNetwork(candidates.get(i),gatewayOnly)).show();
    }
    private String gateway(Network n){LinkProperties p=connectivity.getLinkProperties(n);if(p!=null)for(RouteInfo r:p.getRoutes())if(r.isDefaultRoute()&&r.getGateway() instanceof java.net.Inet4Address)return r.getGateway().getHostAddress();return "";}
    private void selectedNetwork(Network n,boolean gatewayOnly){
        if(gatewayOnly){String g=gateway(n);if(g.isEmpty()){message("Kein IPv4-Gateway gefunden. Kamera-IP manuell eintragen.");return;}host.setText(g);message("Gateway übernommen. Fingerabdruck prüfen und HTTPS anmelden.");return;}
        final String address=host.getText().toString().trim(),fingerprint=pin.getText().toString().trim();final int epoch=configuration.get();
        run(()->{
            if(client!=null)client.disconnect();client=null;clearLists();
            LumixLabClient next=new LumixLabClient(url->(java.net.HttpURLConnection)n.openConnection(url),address,fingerprint);
            next.authenticate(prefs.getString("camera_uuid",""),"Lumix HDR auf Android");
            synchronized(configuration){
                if(closed||epoch!=configuration.get()){next.disconnect();throw new IOException("Anmeldung verworfen: Verbindungsdaten wurden geändert oder Sitzung getrennt");}
                network=n;client=next;
            }
            prefs.edit().putString("camera_host",address).putString("camera_pin",CameraTrust.formatPin(CameraTrust.parsePin(fingerprint))).apply();
            message("Deine Kamera ist verbunden. Du kannst jetzt Fotos auswählen.");
        });
    }
    private LumixLabClient client() throws Exception {
        LumixLabClient current=client;if(current==null||!current.isAuthenticated())throw new IOException("Zuerst HTTPS anmelden. Bei Ablehnung kann die eigene BLE-Registrierung fehlen.");
        NetworkCapabilities caps=connectivity.getNetworkCapabilities(network);
        if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){current.disconnect();throw new IOException("Kamera-WLAN getrennt. Erneut verbinden und anmelden.");}
        return current;
    }
    private void selectImages(){
        List<LumixLabClient.Image> items=new ArrayList<>(gallery);
        if(items.isEmpty()){
            new AlertDialog.Builder(activity).setTitle("Noch keine einzelnen JPEGs").setMessage("Auf dieser Seite wurden keine einzelnen JPEG-Aufnahmen gefunden. RAW, HEIF und Burst-Gruppen sind noch nicht verfügbar.")
                .setPositiveButton("Weitere Fotos",(d,w)->browseGallery(true)).setNegativeButton("Schließen",null).show();return;
        }
        String[] names=new String[items.size()];boolean[] checked=new boolean[items.size()];
        for(int i=0;i<names.length;i++)names[i]=items.get(i).name;
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Deine Belichtungsreihe").setMultiChoiceItems(names,checked,(d,i,on)->{checked[i]=on;AlertDialog current=(AlertDialog)d;int count=0;for(boolean value:checked)if(value)count++;current.getButton(AlertDialog.BUTTON_POSITIVE).setText(count>0?count+" Fotos laden":"Fotos laden");current.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(count>0);})
            .setPositiveButton("Fotos laden",(d,w)->{List<LumixLabClient.Image> selected=new ArrayList<>();for(int i=0;i<items.size();i++)if(checked[i])selected.add(items.get(i));if(!selected.isEmpty())downloadAction.download(selected);})
            .setNeutralButton("Weitere Fotos",(d,w)->browseGallery(true)).setNegativeButton("Abbrechen",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false));dialog.show();
    }
    /** Invoked on the existing serial processing worker. Caller owns deletion of returned files. */
    List<File> download(List<LumixLabClient.Image> selected,File cache) throws Exception {
        List<File> files=new ArrayList<>();LumixLabClient current=client();long[] last={0};
        try{for(int i=0;i<selected.size();i++){
            LumixLabClient.Image im=selected.get(i);int index=i+1;
            // ImageSize=2 can request a conversion; its size must not be inferred from original metadata.
            LumixLabClient.Image request=im.imageSize==1?im:new LumixLabClient.Image(im.id,im.name,im.type,im.imageSize,0,im.burstId);
            files.add(current.downloadJpeg(request,cache,(bytes,total)->{long now=System.currentTimeMillis();if(now-last[0]>250){last[0]=now;message("Bild "+index+"/"+selected.size()+": "+bytes/1024+" KB"+(total>0?" / "+total/1024+" KB":""));}}));
        }return files;}catch(Exception e){for(File f:files)f.delete();throw e;}
    }
    @SuppressWarnings("MissingPermission") private void readFingerprint(){
        if(Build.VERSION.SDK_INT>=31&&activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){activity.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},42);message("Bluetooth-Zugriff freigeben und den Fingerabdruck erneut lesen");return;}
        BluetoothManager manager=(BluetoothManager)activity.getSystemService(Context.BLUETOOTH_SERVICE);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
        if(adapter==null||!adapter.isEnabled()){message("Bluetooth in Android aktivieren und deine Kamera vorbereiten");return;}
        try{
            List<BluetoothDevice> devices=new ArrayList<>(adapter.getBondedDevices());
            if(devices.isEmpty()){message("Keine in Android gekoppelten Geräte. Kamera zuerst koppeln oder Fingerabdruck aus bekannter Quelle eingeben. Lab-Kopplung allein muss keine Android-Kopplung sein.");return;}
            String[] names=new String[devices.size()];for(int i=0;i<names.length;i++)names[i]=devices.get(i).getName()+" / "+devices.get(i).getAddress();
            new AlertDialog.Builder(activity).setTitle("Deine gekoppelte Kamera auswählen").setItems(names,(dialog,index)->{
                if(reader!=null)reader.close();BluetoothDevice device=devices.get(index);message("Lese nur Zertifikatsfingerabdruck über BLE …");
                reader=new CameraFingerprintReader((fingerprint,error)->{
                    if(closed)return;if(error!=null){message(error);return;}
                    new AlertDialog.Builder(activity).setTitle("Fingerabdruck deiner Kamera übernehmen?").setMessage(names[index]+"\n\n"+fingerprint+"\n\nNur übernehmen, wenn dies deine ausgewählte Kamera ist. Diese Abfrage registriert die App noch nicht bei der Kamera.")
                        .setPositiveButton("Übernehmen",(d,w)->{pin.setText(fingerprint);prefs.edit().putString("camera_pin",fingerprint).apply();message("Fingerabdruck übernommen. Nun über WLAN HTTPS-Anmeldung testen.");}).setNegativeButton("Abbrechen",null).show();
                });reader.read(activity,device);
            }).show();
        }catch(SecurityException e){message("Bluetooth-Freigabe fehlt");}
    }
    @Override public void close(){closed=true;invalidate();if(reader!=null)reader.close();}
}
