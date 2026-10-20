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
    interface Runner{boolean run(Task task);}
    interface DownloadAction{void download(List<LumixLabClient.Image> selected);}
    private final Activity activity;private final SharedPreferences prefs;private final Runner runner;private final DownloadAction downloadAction;
    private final ConnectivityManager connectivity;
    private EditText host,pin,page;private TextView state,details;private Spinner types,values,imageSize;
    private volatile LumixLabClient client;private Network network,pendingNetwork;
    private boolean observingNetworks;
    private final ConnectivityManager.NetworkCallback networkCallback=new ConnectivityManager.NetworkCallback(){
        @Override public void onAvailable(Network n){refreshConnection();}
        @Override public void onLost(Network n){networkUnavailable(n);}
        @Override public void onCapabilitiesChanged(Network n,NetworkCapabilities capabilities){
            if(!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))networkUnavailable(n);
            else refreshConnection();
        }
    };
    private volatile String settingType;private volatile List<String> settingValues=Collections.emptyList();private volatile List<LumixLabClient.Image> gallery=Collections.emptyList();
    private final AtomicInteger configuration=new AtomicInteger();
    private CameraBleSession bleSession;private CameraBleScanner scanner;
    private CameraWifiConnector wifiConnector;private Runnable permissionAction;
    private final ConnectionTrace trace=new ConnectionTrace("0.3.6",Build.VERSION.SDK_INT);
    private AlertDialog connectionDialog,errorDialog;private TextView connectionProgress;
    private boolean connecting;
    private static final int CAMERA_PERMISSIONS=42;
    private volatile boolean closed;
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
        style.gap(device,22);state=style.caption("Suche zuerst deine Kamera per Bluetooth. Aktiviere dazu an der Kamera Bluetooth → Kopplung → Neues Gerät hinzufügen.");state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);device.addView(state);
        disconnectAction=style.link("Verbindung trennen",v->{disconnectCamera();message("Verbindung getrennt.");});device.addView(disconnectAction);
        LinearLayout setup=style.card(root);setupCard=setup;setup.addView(style.text("In drei Schritten bereit",20,style.text,true));style.gap(setup,18);
        trustStep=step(setup,"1","Kamera per Bluetooth finden","An der Kamera: Bluetooth → Kopplung → Neues Gerät hinzufügen.","Kamera suchen",v->readFingerprint());
        style.divider(setup);
        networkStep=step(setup,"2","Kamera-WLAN verbinden","Nach der Bluetooth-Anmeldung den WLAN-Dialog von Android bestätigen.","WLAN manuell öffnen",v->activity.startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)));
        style.divider(setup);
        TextView third=style.text("3  Verbindung herstellen",17,style.text,true);setup.addView(third);style.gap(setup,12);connectAction=style.action("Verbinden",true,v->{if(isConnected()){disconnectCamera();message("Verbindung getrennt.");}else connectGuided();});setup.addView(connectAction);
        LinearLayout photos=style.card(root);photosCard=photos;photos.addView(style.text("Von der Kamera ins Studio",20,style.text,true));style.gap(photos,9);photos.addView(style.caption("Wähle deine Belichtungsreihe. Im Studio entscheidest du, wann dein HDR entsteht."));style.gap(photos,18);
        browseAction=style.action("Fotos auswählen",false,v->browseGallery(false));photos.addView(browseAction);style.gap(photos,4);captureAction=style.link("Aufnahme auslösen",v->command("capture"));captureAction.setGravity(android.view.Gravity.CENTER);photos.addView(captureAction);
        LinearLayout note=style.card(root);note.addView(style.text("Verbindungsvorschau",15,style.text,true));style.gap(note,8);note.addView(style.caption("Der Bluetooth- und WLAN-Ablauf wurde aus LUMIX Lab abgeleitet. Ein vollständiger Test an einer S9 steht noch aus. Du kannst jederzeit Fotos vom Telefon verwenden."));style.gap(note,8);note.addView(style.link("Verbindungsdetails und Diagnose",v->openDiagnostics()));
        android.text.TextWatcher changed=new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){invalidate();message("Verbindungsdaten geändert. Bitte erneut verbinden.");}
            public void afterTextChanged(android.text.Editable e){}
        };host.addTextChangedListener(changed);pin.addTextChangedListener(changed);
        // Camera access must also work on Wi-Fi without Internet or validation.
        connectivity.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),networkCallback,new android.os.Handler(android.os.Looper.getMainLooper()));
        observingNetworks=true;refreshConnection();
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
        add(diagnostics,"Kamera per Bluetooth suchen",v->readFingerprint());
        add(diagnostics,"Verbindungsdiagnose kopieren",v->copyConnectionTrace());
        add(diagnostics,"Vorhandenes WLAN manuell per HTTPS anmelden",v->chooseNetwork(false));
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
        label(diagnostics,"Livebildanzeige und RAW-/HEIF-Import sind noch offen. Bluetooth-Anmeldung und WLAN-Übergabe sind experimentell.",13);
        details=label(diagnostics,"Kameraantworten erscheinen hier. Zugangsdaten werden ausgeblendet.",12);details.setTypeface(android.graphics.Typeface.MONOSPACE);details.setTextIsSelectable(true);
    }
    void openDiagnostics(){
        if(diagnostics.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)diagnostics.getParent()).removeView(diagnostics);
        ScrollView scroll=new ScrollView(activity);scroll.addView(diagnostics);new AlertDialog.Builder(activity).setView(scroll).setPositiveButton("Fertig",null).show();
    }
    void setStatusListener(Runnable listener){statusListener=listener;refreshConnection();}
    private boolean isWifi(Network n){NetworkCapabilities caps=n==null?null:connectivity.getNetworkCapabilities(n);return caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);}
    boolean isConnected(){synchronized(configuration){return client!=null&&client.isAuthenticated()&&isWifi(network);}}
    private void networkUnavailable(Network n){
        boolean selected;
        synchronized(configuration){
            if(closed)return;
            selected=n.equals(network)||n.equals(pendingNetwork);
            if(selected)invalidate();
        }
        if(selected)ui(()->{if(connecting)connectionFailure("Kamera-WLAN getrennt. Erneut verbinden und anmelden.");else message("Kamera-WLAN getrennt. Erneut verbinden und anmelden.");});else refreshConnection();
    }
    void refreshConnection(){ui(()->{
        boolean connected=isConnected();connectionBadge.setText(connected?"Verbunden":"Noch nicht verbunden");connectionBadge.setTextColor(connected?style.success:style.secondary);connectAction.setText(connected?"Verbindung trennen":"Verbinden");style.enabled(browseAction,connected);style.enabled(captureAction,connected);setupCard.setVisibility(connected?View.GONE:View.VISIBLE);photosCard.setVisibility(connected?View.VISIBLE:View.GONE);disconnectAction.setVisibility(connected||bleSession!=null||wifiConnector!=null?View.VISIBLE:View.GONE);
        boolean wifi=false;for(Network n:connectivity.getAllNetworks()){NetworkCapabilities caps=connectivity.getNetworkCapabilities(n);if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))wifi=true;}
        networkStep.setText(connected?"Kamera-WLAN verbunden.":wifi?"WLAN verfügbar. Nach der Bluetooth-Anmeldung Kamera-WLAN bestätigen.":"Nach der Bluetooth-Anmeldung den WLAN-Dialog von Android bestätigen.");
        try{CameraTrust.parsePin(pin.getText().toString());trustStep.setText("Fingerabdruck deiner Kamera ist hinterlegt. Bei Bedarf erneut per Bluetooth anmelden.");}catch(Exception e){trustStep.setText("An der Kamera: Bluetooth → Kopplung → Neues Gerät hinzufügen.");}
        statusListener.run();
    });}
    private void connectGuided(){readFingerprint();}
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
    private void connectionProgress(String text){message(text);if(connectionProgress!=null)connectionProgress.setText(text);}
    private void copyConnectionTrace(){
        android.content.ClipboardManager clipboard=(android.content.ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Lumix-Verbindungsdiagnose",trace.report()));
        Toast.makeText(activity,"Verbindungsdiagnose kopiert – ohne WLAN-Schlüssel oder Token",Toast.LENGTH_SHORT).show();
    }
    private void beginConnection(){
        finishConnectionDialog();
        trace.reset();connecting=true;
        if(errorDialog!=null){errorDialog.dismiss();errorDialog=null;}
        LinearLayout body=style.column();body.setPadding(style.dp(24),style.dp(16),style.dp(24),style.dp(16));
        connectionProgress=style.caption("Bluetooth-Verbindung wird vorbereitet …");connectionProgress.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(connectionProgress);
        connectionDialog=new AlertDialog.Builder(activity).setTitle("Mit Kamera verbinden").setView(body)
            .setNegativeButton("Abbrechen",(d,w)->{trace.record("ui.cancel",0);disconnectCamera();message("Verbindung abgebrochen.");})
            .setNeutralButton("Diagnose kopieren",null).create();
        connectionDialog.setCanceledOnTouchOutside(false);
        connectionDialog.setOnCancelListener(d->{trace.record("ui.cancel",0);disconnectCamera();message("Verbindung abgebrochen.");});
        connectionDialog.setOnShowListener(d->((AlertDialog)d).getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->copyConnectionTrace()));
        connectionDialog.show();
    }
    private void finishConnectionDialog(){
        connecting=false;if(connectionDialog!=null){connectionDialog.dismiss();connectionDialog=null;}connectionProgress=null;
    }
    private void connectionFailure(String error){
        if(closed)return;trace.record("ui.error",0);disconnectCamera();message(error);
        if(activity.isFinishing())return;
        if(errorDialog!=null)errorDialog.dismiss();
        errorDialog=new AlertDialog.Builder(activity).setTitle("Verbindung fehlgeschlagen").setMessage(error)
            .setPositiveButton("Fertig",null).setNeutralButton("Diagnose kopieren",(d,w)->copyConnectionTrace()).show();
    }
    private void clearLists(){gallery=Collections.emptyList();settingValues=Collections.emptyList();settingType=null;}
    private void invalidate(){synchronized(configuration){configuration.incrementAndGet();if(client!=null)client.disconnect();client=null;network=null;pendingNetwork=null;clearLists();}}
    private void run(Task t){
        final int epoch=configuration.get();
        boolean accepted=runner.run(()->{try{t.run();}catch(Exception e){ui(()->{
            if(epoch!=configuration.get())return;
            if(client!=null&&!client.isAuthenticated())clearLists();
            if(connecting){trace.record("https.error",0);connectionFailure("Hinweis: "+e.getMessage());}
            else message("Hinweis: "+e.getMessage());
        });}});
        if(!accepted)ui(()->{
            if(epoch!=configuration.get())return;
            String text="Die vorherige Verarbeitung läuft noch. Warte kurz und versuche die Verbindung erneut.";
            if(connecting)connectionFailure(text);else message(text);
        });
    }
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
        if(!connecting)beginConnection();
        final String address=host.getText().toString().trim(),fingerprint=pin.getText().toString().trim();final int epoch=configuration.get();
        run(()->{
            synchronized(configuration){
                if(closed||epoch!=configuration.get())throw new IOException("Anmeldung verworfen: Verbindungsdaten wurden geändert oder Sitzung getrennt");
                if(!isWifi(n))throw new IOException("Kamera-WLAN getrennt. Erneut verbinden und anmelden.");
                if(client!=null)client.disconnect();client=null;network=null;clearLists();pendingNetwork=n;
            }
            LumixLabClient next=null;boolean accepted=false;
            try{
                next=new LumixLabClient(url->(java.net.HttpURLConnection)n.openConnection(url),address,fingerprint);
                final String normalizedPin=CameraTrust.formatPin(CameraTrust.parsePin(fingerprint));
                trace.record("https.start",0);ui(()->connectionProgress("Melde die App über das Kamera-WLAN an …"));
                next.authenticate(prefs.getString("camera_uuid",""),"Lumix HDR Android");
                synchronized(configuration){
                    if(closed||epoch!=configuration.get())throw new IOException("Anmeldung verworfen: Verbindungsdaten wurden geändert oder Sitzung getrennt");
                    if(!isWifi(n))throw new IOException("Kamera-WLAN getrennt. Erneut verbinden und anmelden.");
                    network=n;client=next;pendingNetwork=null;accepted=true;
                }
                final LumixLabClient connected=next;
                ui(()->{synchronized(configuration){
                    if(epoch!=configuration.get()||client!=connected||!isWifi(n))return;
                    prefs.edit().putString("camera_host",address).putString("camera_pin",normalizedPin).apply();
                    trace.record("https.success",200);finishConnectionDialog();
                    message("Deine Kamera ist verbunden. Du kannst jetzt Fotos auswählen.");
                }});
            }finally{
                if(!accepted&&next!=null)next.disconnect();
                synchronized(configuration){if(epoch==configuration.get()&&n.equals(pendingNetwork))pendingNetwork=null;}
            }
        });
    }
    private LumixLabClient client() throws Exception {
        synchronized(configuration){
            LumixLabClient current=client;if(current==null||!current.isAuthenticated())throw new IOException("Zuerst HTTPS anmelden. Bei Ablehnung kann die eigene BLE-Registrierung fehlen.");
            if(!isWifi(network)){invalidate();throw new IOException("Kamera-WLAN getrennt. Erneut verbinden und anmelden.");}
            return current;
        }
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
    private String[] cameraPermissions(){
        List<String> needed=new ArrayList<>();
        if(Build.VERSION.SDK_INT>=31){needed.add(Manifest.permission.BLUETOOTH_SCAN);needed.add(Manifest.permission.BLUETOOTH_CONNECT);}
        if(Build.VERSION.SDK_INT<=32){needed.add(Manifest.permission.ACCESS_FINE_LOCATION);needed.add(Manifest.permission.ACCESS_COARSE_LOCATION);}
        if(Build.VERSION.SDK_INT>=33)needed.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        return needed.toArray(new String[0]);
    }
    private void withCameraPermissions(Runnable action){
        List<String> missing=new ArrayList<>();for(String p:cameraPermissions())if(activity.checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)missing.add(p);
        if(!missing.isEmpty()){permissionAction=action;activity.requestPermissions(missing.toArray(new String[0]),CAMERA_PERMISSIONS);message("Bluetooth- und WLAN-Zugriff erlauben, damit deine Kamera gefunden werden kann.");return;}
        action.run();
    }
    void onPermissionsResult(int requestCode){
        if(requestCode!=CAMERA_PERMISSIONS||closed)return;
        Runnable next=permissionAction;permissionAction=null;
        for(String p:cameraPermissions())if(activity.checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED){message("Kamera-Zugriff fehlt. In den Android-App-Einstellungen Geräte in der Nähe beziehungsweise Standort erlauben.");return;}
        if(next!=null)next.run();
    }
    private void readFingerprint(){withCameraPermissions(this::scanCameras);}
    @SuppressWarnings("MissingPermission") private void scanCameras(){
        disconnectCamera();
        BluetoothManager manager=(BluetoothManager)activity.getSystemService(Context.BLUETOOTH_SERVICE);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
        if(adapter==null){message("Dieses Gerät unterstützt kein Bluetooth.");return;}
        if(!adapter.isEnabled()){activity.startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));message("Bluetooth aktivieren und danach Kamera suchen wählen.");return;}
        if(Build.VERSION.SDK_INT<=32){android.location.LocationManager location=(android.location.LocationManager)activity.getSystemService(Context.LOCATION_SERVICE);if(location!=null&&!location.isLocationEnabled()){message("Auf Android 10–12 muss der Standortdienst für die Kamera-Suche und WLAN-Verbindung eingeschaltet sein.");activity.startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));return;}}
        if(scanner!=null)scanner.close();
        List<BluetoothDevice> devices=new ArrayList<>();List<String> names=new ArrayList<>();
        ArrayAdapter<String> rows=new ArrayAdapter<>(activity,android.R.layout.simple_list_item_1,names);
        LinearLayout body=style.column();body.setPadding(style.dp(24),style.dp(12),style.dp(24),style.dp(12));
        TextView status=style.caption("Suche läuft …\nAn der Kamera: Bluetooth → Kopplung → Neues Gerät hinzufügen.");body.addView(status);
        ListView list=new ListView(activity);list.setAdapter(rows);body.addView(list,new LinearLayout.LayoutParams(-1,style.dp(240)));
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Deine LUMIX auswählen").setView(body).setNegativeButton("Abbrechen",null).create();
        CameraBleScanner search=new CameraBleScanner(Collections.singleton(UUID.fromString("054ac620-3214-11e6-ac0d-0002a5d5c51b")),new CameraBleScanner.Callback(){
            public void discovered(BluetoothDevice device,String name){if(closed||!dialog.isShowing())return;devices.add(device);names.add(name+"\n"+device.getAddress());rows.notifyDataSetChanged();status.setText("Kamera gefunden. Wähle deine Kamera aus.");}
            public void complete(){if(!closed&&dialog.isShowing())status.setText(devices.isEmpty()?"Keine Kamera gefunden. Kamera in Kopplungsbereitschaft versetzen und eine andere Kamera-App schließen. Danach erneut suchen.":"Suche beendet. Wähle deine Kamera aus.");}
            public void error(String error){if(!closed&&dialog.isShowing())status.setText(error);message(error);}
        });scanner=search;
        list.setOnItemClickListener((parent,view,index,id)->{BluetoothDevice device=devices.get(index);dialog.dismiss();cameraSelected(device);});
        dialog.setOnDismissListener(d->search.close());dialog.show();search.start(adapter);
    }
    private void cameraSelected(BluetoothDevice device){
        if(bleSession!=null)bleSession.close();
        beginConnection();
        bleSession=new CameraBleSession(new CameraBleSession.Callback(){
            public void diagnostic(String event,int value){trace.record(event,value);}
            public void progress(String text){if(!closed)connectionProgress(text);}
            public void error(String error){if(!closed)connectionFailure(error);}
            public void ready(String ssid,String password,String fingerprint){
                if(closed)return;pin.setText(fingerprint);
                connectionProgress("Kamera-WLAN ist bereit. Bestätige jetzt die Verbindung im Android-Dialog.");
                if(wifiConnector!=null)wifiConnector.close();
                wifiConnector=new CameraWifiConnector(activity,new CameraWifiConnector.Callback(){
                    public void diagnostic(String event,int value){trace.record(event,value);}
                    public void connected(Network selected,String gateway){if(closed)return;host.setText(gateway);selectedNetwork(selected,false);}
                    public void lost(){if(closed)return;connectionFailure("Kamera-WLAN getrennt. Erneut verbinden.");}
                    public void error(String error){if(!closed){trace.record("wifi.error",0);connectionFailure(error);}}
                });trace.record("wifi.request",0);wifiConnector.connect(ssid,password);
            }
        });bleSession.connect(activity,device,prefs.getString("camera_uuid",""),"Lumix HDR Android");
    }
    private void disconnectCamera(){
        finishConnectionDialog();
        invalidate();permissionAction=null;if(scanner!=null){scanner.close();scanner=null;}
        if(bleSession!=null){bleSession.close();bleSession=null;}
        if(wifiConnector!=null){wifiConnector.close();wifiConnector=null;}
    }
    @Override public void close(){
        synchronized(configuration){closed=true;invalidate();}
        finishConnectionDialog();if(errorDialog!=null){errorDialog.dismiss();errorDialog=null;}
        if(observingNetworks){connectivity.unregisterNetworkCallback(networkCallback);observingNetworks=false;}
        if(bleSession!=null)bleSession.close();
        if(scanner!=null)scanner.close();if(wifiConnector!=null)wifiConnector.close();permissionAction=null;
    }
}
