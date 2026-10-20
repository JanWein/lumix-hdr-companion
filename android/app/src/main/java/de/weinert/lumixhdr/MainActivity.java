package de.weinert.lumixhdr;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.provider.*;
import android.view.*;
import android.widget.*;
import androidx.documentfile.provider.DocumentFile;
import org.opencv.android.OpenCVLoader;
import java.io.*;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;private StudioTheme ui;
    private FrameLayout pages;private final List<View> screens=new ArrayList<>();private final List<LinearLayout> navItems=new ArrayList<>();
    private LinearLayout sourceCard,resultsList,settingsBody,taskStrip;private TextView status,createButton,sourceButton,cameraLabel;private ProgressBar progress;
    private ModernCameraPanel camera;private Uri tree;private File latestHdr,pendingJpeg;private Uri latestJpeg;
    private final List<File> prepared=new ArrayList<>();private final Set<String> imported=new HashSet<>();
    private boolean busy,resumed,watching,destroyed;private int currentTab;
    private final Runnable tick=new Runnable(){public void run(){if(watching&&resumed&&!busy&&tree!=null)scanTree();if(watching&&resumed)handler.postDelayed(this,5000);}};
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);prefs=getSharedPreferences("hdr",MODE_PRIVATE);ui=new StudioTheme(this);
        if(saved!=null){ArrayList<String> paths=saved.getStringArrayList("selected");if(paths!=null)for(String p:paths){File f=new File(p);if(f.isFile())prepared.add(f);}}
        String folder=prefs.getString("input_tree",null);if(folder!=null)tree=Uri.parse(folder);
        if(saved!=null){String path=saved.getString("export_jpeg");if(path!=null)pendingJpeg=new File(path);String hdrPath=saved.getString("export_hdr");if(hdrPath!=null)latestHdr=new File(hdrPath);}
        build();
        if(saved!=null)showTab(saved.getInt("tab",0));
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->{if(currentTab!=0)showTab(0);else finish();});
        Intent intent=getIntent();if(Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())){ArrayList<Uri> uris=intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);if(uris!=null)importUris(uris);}
    }
    private void build(){
        LinearLayout shell=ui.column();shell.setBackgroundColor(ui.background);setContentView(shell);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(ui.background);getWindow().setNavigationBarColor(ui.surface);
        if(Build.VERSION.SDK_INT>=30)getWindow().getInsetsController().setSystemBarsAppearance(ui.dark?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        else getWindow().getDecorView().setSystemUiVisibility(ui.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        shell.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());v.setPadding(i.left,i.top,i.right,i.bottom);}else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout toolbar=ui.row();toolbar.setPadding(ui.dp(24),0,ui.dp(14),0);TextView brand=ui.text("LUMIX  /  HDR",11,ui.secondary,true);brand.setLetterSpacing(.16f);toolbar.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        FrameLayout settings=new FrameLayout(this);View gear=ui.icon("settings",ui.text,22);FrameLayout.LayoutParams gp=new FrameLayout.LayoutParams(ui.dp(22),ui.dp(22),Gravity.CENTER);settings.addView(gear,gp);settings.setContentDescription("Einstellungen");settings.setOnClickListener(v->showTab(3));settings.setFocusable(true);ui.buttonAccessibility(settings);toolbar.addView(settings,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));shell.addView(toolbar);
        pages=new FrameLayout(this);shell.addView(pages,new LinearLayout.LayoutParams(-1,0,1));
        buildStudio();LinearLayout cameraBody=ui.screen("Kamera","Deine S9. Direkt verbunden.");addPage(cameraBody);
        camera=new ModernCameraPanel(this,cameraBody,prefs,task->execute("Kamera wird vorbereitet …",task::run),this::stageCameraImages);
        camera.setStatusListener(()->cameraLabel.setText(camera.isConnected()?"LUMIX S9 · Verbunden":"LUMIX S9 verbinden"));
        buildResults();buildSettings();
        taskStrip=ui.row();taskStrip.setPadding(ui.dp(24),ui.dp(10),ui.dp(24),ui.dp(10));progress=new ProgressBar(this);taskStrip.addView(progress,new LinearLayout.LayoutParams(ui.dp(24),ui.dp(24)));status=ui.caption("");LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-2,1);sp.leftMargin=ui.dp(12);taskStrip.addView(status,sp);taskStrip.setVisibility(View.GONE);shell.addView(taskStrip);
        View divider=new View(this);divider.setBackgroundColor(ui.line);shell.addView(divider,new LinearLayout.LayoutParams(-1,ui.dp(1)));
        LinearLayout nav=ui.row();nav.setBackgroundColor(ui.surface);nav.setPadding(ui.dp(16),ui.dp(6),ui.dp(16),ui.dp(6));
        String[] labels={"Studio","Kamera","Bilder"},icons={"aperture","camera","photos"};
        for(int i=0;i<3;i++){final int index=i;LinearLayout item=ui.column();item.setGravity(Gravity.CENTER);item.setPadding(0,ui.dp(7),0,ui.dp(7));item.setMinimumHeight(ui.dp(58));item.addView(ui.icon(icons[i],ui.secondary,23));ui.gap(item,5);TextView navCaption=ui.text(labels[i],11,ui.secondary,true);navCaption.setGravity(Gravity.CENTER);item.addView(navCaption);item.setContentDescription(labels[i]);item.setOnClickListener(v->showTab(index));item.setFocusable(true);ui.buttonAccessibility(item);nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));navItems.add(item);}
        shell.addView(nav);showTab(0);
    }
    private void addPage(LinearLayout body){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        FrameLayout centered=new FrameLayout(this);int max=ui.dp(680),width=getResources().getDisplayMetrics().widthPixels;centered.addView(body,new FrameLayout.LayoutParams(Math.min(width,max),-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));scroll.addView(centered);pages.addView(scroll,new FrameLayout.LayoutParams(-1,-1));screens.add(scroll);
    }
    private void buildStudio(){
        LinearLayout root=ui.screen("HDR Studio","Mehr Details. In jedem Licht.");addPage(root);
        LinearLayout connection=ui.row();connection.setPadding(ui.dp(16),ui.dp(9),ui.dp(12),ui.dp(9));connection.setBackground(ui.shape(ui.surface,16));connection.addView(ui.icon("camera",ui.accent,22));
        cameraLabel=ui.text("LUMIX S9 verbinden",15,ui.text,true);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=ui.dp(12);connection.addView(cameraLabel,cp);connection.addView(ui.icon("arrow",ui.secondary,18));connection.setMinimumHeight(ui.dp(54));connection.setOnClickListener(v->showTab(1));connection.setFocusable(true);ui.buttonAccessibility(connection);root.addView(connection);ui.gap(root,20);
        sourceCard=ui.card(root);renderSelection();
        LinearLayout info=ui.row();View check=ui.icon("check",ui.success,18);info.addView(check);TextView privacy=ui.caption("Auf deinem Gerät. Deine Originale bleiben erhalten.");LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-2,1);ip.leftMargin=ui.dp(10);info.addView(privacy,ip);root.addView(info);
    }
    private void renderSelection(){
        sourceCard.removeAllViews();
        if(prepared.isEmpty()){
            ExposureIllustration illustration=new ExposureIllustration();sourceCard.addView(illustration,new LinearLayout.LayoutParams(-1,ui.dp(144)));ui.gap(sourceCard,10);
            TextView title=ui.text("Ein Bild. Alle Details.",22,ui.text,true);title.setGravity(Gravity.CENTER);sourceCard.addView(title);ui.gap(sourceCard,10);
            TextView subtitle=ui.caption("Wähle die Aufnahmen deiner\nBelichtungsreihe aus.");subtitle.setGravity(Gravity.CENTER);sourceCard.addView(subtitle);ui.gap(sourceCard,24);
            sourceButton=ui.action("Fotos auswählen",true,v->pickPhotos());sourceButton.setTag("pick-photos");sourceCard.addView(sourceButton);createButton=null;
            TextView demo=ui.link("Mit Beispielbildern ausprobieren",v->stageDemo());demo.setGravity(Gravity.CENTER);sourceCard.addView(demo);
        }else{
            LinearLayout heading=ui.row();heading.addView(ui.text(prepared.size()+" Aufnahmen",22,ui.text,true),new LinearLayout.LayoutParams(0,-2,1));TextView change=ui.link("Ändern",v->pickPhotos());heading.addView(change);sourceCard.addView(heading);ui.gap(sourceCard,16);
            LinearLayout thumbs=ui.row();int count=Math.min(3,prepared.size());for(int i=0;i<count;i++){ImageView image=thumbnail(prepared.get(i),600);image.setContentDescription("Ausgewähltes Bild "+(i+1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ui.dp(112),1);if(i>0)lp.leftMargin=ui.dp(8);thumbs.addView(image,lp);}sourceCard.addView(thumbs);ui.gap(sourceCard,16);
            sourceCard.addView(ui.caption(prepared.size()==count?"Bereit für dein nächstes Bild.":"+ "+(prepared.size()-count)+" weitere Aufnahmen"));ui.gap(sourceCard,12);
            LinearLayout output=ui.row();output.addView(ui.caption("Ausgabe"),new LinearLayout.LayoutParams(0,-2,1));TextView choice=ui.link(hdr()?"HDR + JPEG  ›":"JPEG-Fusion  ›",v->chooseOutput());output.addView(choice);sourceCard.addView(output);ui.gap(sourceCard,8);
            createButton=ui.action("HDR erstellen",true,v->createHdr());createButton.setTag("create-hdr");sourceCard.addView(createButton);ui.enabled(createButton,prepared.size()>=3&&!busy);
            TextView clear=ui.link("Auswahl aufheben",v->{if(!busy){prepared.clear();renderSelection();}});clear.setGravity(Gravity.CENTER);sourceCard.addView(clear);sourceButton=null;
        }
    }
    private void buildResults(){LinearLayout root=ui.screen("Deine Bilder","Dein Licht. Festgehalten.");addPage(root);resultsList=ui.column();root.addView(resultsList);refreshResults();}
    private void refreshResults(){
        resultsList.removeAllViews();File directory=new File(getExternalFilesDir(null),"results");List<ResultFiles.Entry> files=ResultFiles.list(directory);
        if(files.isEmpty()){LinearLayout empty=ui.card(resultsList);empty.setGravity(Gravity.CENTER);ui.gap(empty,32);empty.addView(ui.icon("photos",ui.secondary,48));ui.gap(empty,20);empty.addView(ui.text("Platz für deine Perspektive.",20,ui.text,true));ui.gap(empty,10);TextView c=ui.caption("Hier erscheinen deine fertigen Bilder.");c.setGravity(Gravity.CENTER);empty.addView(c);ui.gap(empty,26);empty.addView(ui.action("Zum Studio",false,v->showTab(0)));ui.gap(empty,8);return;}
        for(ResultFiles.Entry entry:files){
            LinearLayout card=ui.card(resultsList);
            if(entry.jpeg.isFile()){ImageView image=thumbnail(entry.jpeg,1200);image.setContentDescription("Ergebnis vom "+DateFormat.getDateInstance().format(new Date(entry.modified())));card.addView(image,new LinearLayout.LayoutParams(-1,ui.dp(220)));}
            else{LinearLayout placeholder=ui.column();placeholder.setGravity(Gravity.CENTER);placeholder.setBackground(ui.shape(ui.soft,14));placeholder.addView(ui.icon("photos",ui.accent,40));ui.gap(placeholder,12);placeholder.addView(ui.caption("HDR-Datei gespeichert"));card.addView(placeholder,new LinearLayout.LayoutParams(-1,ui.dp(180)));}
            ui.gap(card,16);TextView title=ui.text(resultTitle(entry.jpeg),20,ui.text,true);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(title);
            LinearLayout heading=ui.row();TextView date=ui.caption(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(entry.modified())));heading.addView(date,new LinearLayout.LayoutParams(0,-2,1));
            FrameLayout rename=new FrameLayout(this);rename.addView(ui.icon("pencil",ui.accent,22),new FrameLayout.LayoutParams(ui.dp(22),ui.dp(22),Gravity.CENTER));rename.setContentDescription("Bild umbenennen");rename.setTag("rename-result");rename.setFocusable(true);ui.buttonAccessibility(rename);rename.setOnClickListener(v->renameResult(entry));heading.addView(rename,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
            FrameLayout delete=new FrameLayout(this);delete.addView(ui.icon("trash",ui.destructive,22),new FrameLayout.LayoutParams(ui.dp(22),ui.dp(22),Gravity.CENTER));delete.setContentDescription("Bild löschen");delete.setTag("delete-result");delete.setFocusable(true);ui.buttonAccessibility(delete);delete.setOnClickListener(v->confirmDelete(entry));heading.addView(delete,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));card.addView(heading);
            ui.gap(card,8);
            TextView export=ui.action("Als JPEG exportieren",false,v->exportJpeg(entry));export.setTag("export-jpeg");card.addView(export);
            LinearLayout actions=ui.row();TextView share=ui.link("Teilen",v->shareResult(entry));actions.addView(share,new LinearLayout.LayoutParams(0,-2,1));if(entry.hdr!=null)actions.addView(ui.link("HDR exportieren",v->exportHdr(entry)));card.addView(actions);
        }
    }
    private String resultName(File jpeg){return prefs.getString("name:"+jpeg.getName(),null);}
    private String resultTitle(File jpeg){String name=resultName(jpeg);return name==null?"Dein Bild":name;}
    private String exportName(File jpeg,String extension){
        String name=resultName(jpeg);
        return (name==null?"Lumix_HDR_"+new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT).format(new Date(jpeg.lastModified()>0?jpeg.lastModified():System.currentTimeMillis())):name)+extension;
    }
    private void renameResult(ResultFiles.Entry entry){
        if(busy)return;
        LinearLayout body=ui.column();body.setPadding(ui.dp(24),ui.dp(12),ui.dp(24),0);
        EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(ui.text);input.setHintTextColor(ui.secondary);input.setTextSize(17);input.setHint("z. B. Abend am See");input.setContentDescription("Bildname");input.setTag("result-name-input");input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        String previous=resultName(entry.jpeg);if(previous!=null){input.setText(previous);input.selectAll();}
        body.addView(input,new LinearLayout.LayoutParams(-1,-2));ui.gap(body,12);body.addView(ui.caption("Der Name gilt für JPEG und HDR. Dateiendungen ergänzt die App."));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Bild umbenennen").setView(body).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();
        dialog.setOnShowListener(d->{
            Runnable save=()->{try{String name=ResultNames.clean(input.getText().toString());prefs.edit().putString("name:"+entry.jpeg.getName(),name).apply();dialog.dismiss();refreshResults();}catch(IllegalArgumentException e){input.setError(e.getMessage());input.requestFocus();}};
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->save.run());
            input.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){save.run();return true;}return false;});input.requestFocus();
        });
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);dialog.show();
    }
    private void confirmDelete(ResultFiles.Entry entry){
        if(busy)return;
        String result=entry.hdr==null?"Dieses JPEG wird":"Dieses HDR-Ergebnis samt vorhandener JPEG-Vorschau wird";
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Bild löschen?")
                .setMessage(result+" aus der App gelöscht. Originalaufnahmen und bereits gespeicherte Kopien in deiner Galerie oder anderen Ordnern bleiben erhalten.")
                .setNegativeButton("Abbrechen",null).setPositiveButton("Löschen",(d,w)->deleteResult(entry)).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ui.destructive));dialog.show();
    }
    private void deleteResult(ResultFiles.Entry entry){
        execute("Bild wird gelöscht …",()->{
            try{
                ResultFiles.delete(new File(getExternalFilesDir(null),"results"),entry);
                prefs.edit().remove("saved:"+entry.jpeg.getName()).remove("name:"+entry.jpeg.getName()).apply();
                runOnUiThread(()->{if(destroyed)return;if(entry.jpeg.equals(pendingJpeg))pendingJpeg=null;if(entry.hdr!=null&&entry.hdr.equals(latestHdr))latestHdr=null;Toast.makeText(this,"Bild gelöscht.",Toast.LENGTH_SHORT).show();});
            }finally{runOnUiThread(()->{if(!destroyed)refreshResults();});}
        });
    }
    private void buildSettings(){settingsBody=ui.screen("Einstellungen","So, wie du fotografierst.");addPage(settingsBody);renderSettings();}
    private void renderSettings(){
        while(settingsBody.getChildCount()>4)settingsBody.removeViewAt(4);
        LinearLayout output=ui.card(settingsBody);section(output,"Entwicklung");row(output,"Ausgabe",hdr()?"HDR + JPEG":"JPEG-Fusion",v->chooseOutput());row(output,"Bilder je Reihe",String.valueOf(bracketCount()),v->chooseCount());row(output,"Belichtungsschritt",prefs.getString("ev_label","1 EV"),v->chooseEv());
        LinearLayout switchRow=ui.row();TextView label=ui.text("Bilder ausrichten",16,ui.text,false);switchRow.addView(label,new LinearLayout.LayoutParams(0,-2,1));Switch align=new Switch(this);align.setChecked(alignment());align.setContentDescription("Bilder ausrichten");align.setThumbTintList(android.content.res.ColorStateList.valueOf(ui.accent));align.setOnCheckedChangeListener((b,on)->prefs.edit().putBoolean("align",on).apply());switchRow.addView(align);switchRow.setMinimumHeight(ui.dp(54));output.addView(switchRow);
        ui.gap(output,8);output.addView(ui.caption("HDR speichert eine Radiance-Datei und eine JPEG-Vorschau. JPEG-Fusion erzeugt ein normales Foto."));
        LinearLayout automation=ui.card(settingsBody);section(automation,"Ordner-Automatik");row(automation,"Eingangsordner",tree==null?"Wählen":"Ändern",v->pickFolder());row(automation,watching?"Automatik läuft":"Automatik pausiert",watching?"Stoppen":"Starten",v->toggleWatch());ui.gap(automation,6);automation.addView(ui.caption("Neue Reihen werden verarbeitet, solange die App geöffnet ist."));
        LinearLayout advanced=ui.card(settingsBody);section(advanced,"Für deinen ersten Versuch");advanced.addView(ui.caption("Nutze ein ruhiges Motiv, feste ISO und Blende sowie JPEG oder RAW + JPEG. Starte mit 3 Aufnahmen und 1 EV Abstand."));ui.gap(advanced,12);advanced.addView(ui.link("Kameradiagnose öffnen",v->{showTab(1);camera.openDiagnostics();}));advanced.addView(ui.link("Ältere WLAN-Verbindung testen",v->legacyDialog()));
        TextView version=ui.caption("Lumix HDR · 0.3.4\nLokal entwickelt. Ohne Cloud.");version.setGravity(Gravity.CENTER);settingsBody.addView(version);
    }
    private void section(LinearLayout root,String text){root.addView(ui.text(text,19,ui.text,true));ui.gap(root,10);}
    private void row(LinearLayout root,String label,String value,View.OnClickListener listener){LinearLayout r=ui.row();r.setMinimumHeight(ui.dp(56));TextView l=ui.text(label,16,ui.text,false);r.addView(l,new LinearLayout.LayoutParams(0,-2,1));TextView v=ui.text(value+"  ›",14,ui.secondary,false);v.setGravity(Gravity.END);r.addView(v,new LinearLayout.LayoutParams(ui.dp(120),-2));r.setOnClickListener(listener);r.setFocusable(true);r.setContentDescription(label+", "+value);ui.buttonAccessibility(r);root.addView(r);}
    private int bracketCount(){return prefs.getInt("bracket_count",3);}private double ev(){return Double.longBitsToDouble(prefs.getLong("ev",Double.doubleToLongBits(1.)));}private boolean hdr(){return prefs.getBoolean("output_hdr",true);}private boolean alignment(){return prefs.getBoolean("align",true);}
    private void chooseOutput(){new AlertDialog.Builder(this).setTitle("Deine Ausgabe").setSingleChoiceItems(new String[]{"HDR + JPEG-Vorschau","JPEG-Fusion"},hdr()?0:1,(d,i)->{prefs.edit().putBoolean("output_hdr",i==0).apply();d.dismiss();renderSelection();renderSettings();}).setNegativeButton("Zurück",null).show();}
    private void chooseCount(){new AlertDialog.Builder(this).setTitle("Bilder je Belichtungsreihe").setSingleChoiceItems(new String[]{"3 Bilder","5 Bilder","7 Bilder"},(bracketCount()-3)/2,(d,i)->{prefs.edit().putInt("bracket_count",3+i*2).apply();d.dismiss();renderSettings();}).setNegativeButton("Zurück",null).show();}
    private void chooseEv(){String[] labels={"1 EV","⅔ EV","⅓ EV"};new AlertDialog.Builder(this).setTitle("Belichtungsschritt").setItems(labels,(d,i)->{double[] values={1,2./3,1./3};prefs.edit().putLong("ev",Double.doubleToLongBits(values[i])).putString("ev_label",labels[i]).apply();renderSettings();}).show();}
    private void showTab(int index){if(index<0||index>=screens.size())return;currentTab=index;for(int i=0;i<screens.size();i++)screens.get(i).setVisibility(i==index?View.VISIBLE:View.GONE);for(int i=0;i<navItems.size();i++){LinearLayout item=navItems.get(i);boolean selected=i==index;item.setBackground(ui.shape(selected?ui.soft:ui.surface,16));item.setSelected(selected);((TextView)item.getChildAt(2)).setTextColor(selected?ui.accent:ui.secondary);((StudioTheme.Glyph)item.getChildAt(0)).tint(selected?ui.accent:ui.secondary);}if(index==2)refreshResults();if(index==3)renderSettings();}
    private void pickPhotos(){if(busy)return;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/jpeg");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,1);}
    private void pickFolder(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,2);}
    private void exportHdr(ResultFiles.Entry entry){if(busy)return;latestHdr=entry.hdr;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/octet-stream");i.putExtra(Intent.EXTRA_TITLE,exportName(entry.jpeg,".hdr"));startActivityForResult(i,3);}
    private File ensureJpeg(ResultFiles.Entry entry) throws Exception {
        if(entry.jpeg.isFile())return entry.jpeg;
        if(entry.hdr==null)throw new IOException("Die Bilddatei ist nicht mehr vorhanden.");
        if(!OpenCVLoader.initLocal())throw new IOException("Die Bildverarbeitung konnte nicht gestartet werden.");
        return HdrEngine.exportJpeg(entry.hdr,entry.jpeg);
    }
    private void exportJpeg(ResultFiles.Entry entry){
        if(busy)return;
        if(entry.jpeg.isFile()){chooseJpegDestination(entry.jpeg);return;}
        execute("JPEG wird aus deinem HDR erstellt …",()->{File file=ensureJpeg(entry);runOnUiThread(()->{if(!destroyed){refreshResults();chooseJpegDestination(file);}});});
    }
    private void chooseJpegDestination(File file){
        pendingJpeg=file;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("image/jpeg");intent.putExtra(Intent.EXTRA_TITLE,exportName(file,".jpg"));startActivityForResult(intent,4);
    }
    private void toggleWatch(){if(tree==null){pickFolder();return;}watching=!watching;handler.removeCallbacks(tick);if(watching)handler.post(tick);renderSettings();}
    interface ThrowingRunnable{void run() throws Exception;}
    private boolean execute(String label,ThrowingRunnable task){
        if(destroyed)return false;
        if(busy){Toast.makeText(this,"Einen Moment, die Verarbeitung läuft noch.",Toast.LENGTH_SHORT).show();return false;}busy=true;setBusy(label,true);
        worker.execute(()->{try{task.run();}catch(Exception e){runOnUiThread(()->{if(!destroyed)showError(e);});}finally{runOnUiThread(()->{busy=false;if(!destroyed){setBusy("",false);renderSelection();}});}});
        return true;
    }
    private void showError(Exception error){
        AlertDialog.Builder dialog=new AlertDialog.Builder(this);
        if(error instanceof HdrEngine.MissingExposureMetadataException){
            dialog.setTitle("Belichtungsdaten fehlen").setMessage("Diese Fotos enthalten nicht alle Daten für ein HDR. Wähle die Original-JPEGs deiner Kamera oder erstelle eine JPEG-Fusion. Dabei entsteht ein normales JPEG ohne Radiance-HDR.")
                .setPositiveButton("JPEG-Fusion erstellen",(d,w)->createHdr(false))
                .setNegativeButton("Originalfotos wählen",(d,w)->pickPhotos())
                .setNeutralButton("Schließen",null);
        }else dialog.setTitle("Das hat noch nicht geklappt").setMessage(error.getMessage()==null?"Bitte versuche es erneut.":error.getMessage()).setPositiveButton("Verstanden",null);
        dialog.show();
    }
    private void setBusy(String label,boolean on){status.setText(label);taskStrip.setVisibility(on?View.VISIBLE:View.GONE);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);if(createButton!=null)ui.enabled(createButton,!on&&prepared.size()>=3);if(sourceButton!=null)ui.enabled(sourceButton,!on);}
    private void stageFiles(List<File> files){runOnUiThread(()->{if(destroyed)return;prepared.clear();prepared.addAll(new LinkedHashSet<>(files));renderSelection();showTab(0);});}
    private void importUris(List<Uri> uris){execute("Fotos werden vorbereitet …",()->{List<File> files=new ArrayList<>();for(Uri uri:uris)files.add(importFile(getContentResolver().openInputStream(uri)));stageFiles(files);});}
    private void stageDemo(){execute("Beispielbilder werden geladen …",()->{List<File> files=new ArrayList<>();for(int i=0;i<3;i++)files.add(importFile(getAssets().open("demo/BRACKET_"+i+".jpg")));stageFiles(files);});}
    private void stageCameraImages(List<LumixLabClient.Image> selected){execute("Fotos werden übertragen …",()->{List<File> temporary=camera.download(selected,getCacheDir()),files=new ArrayList<>();try{for(File f:temporary)files.add(importFile(new FileInputStream(f)));}finally{for(File f:temporary)f.delete();}stageFiles(files);});}
    private void createHdr(){createHdr(hdr());}
    private void createHdr(boolean requestedHdr){List<File> files=new ArrayList<>(prepared);if(files.size()<3)return;int n=bracketCount();double step=ev();boolean output=requestedHdr,align=alignment();execute("Dein Bild entsteht …",()->{if(output&&(files.size()==3||files.size()==5||files.size()==7))HdrEngine.hdrExposureTimes(files);if(!OpenCVLoader.initLocal())throw new IOException("Die Bildverarbeitung konnte nicht gestartet werden.");int created;if(files.size()==3||files.size()==5||files.size()==7){process(files,output,align);created=1;}else created=processGroups(files,n,step,output,align);if(created==0)throw new IOException("Keine vollständige Belichtungsreihe gefunden. Wähle zusammengehörige Aufnahmen aus.");runOnUiThread(()->{if(!destroyed){showTab(2);Toast.makeText(this,created==1?"Dein Bild ist bereit.":created+" Bilder sind bereit.",Toast.LENGTH_SHORT).show();}});});}
    private File importFile(InputStream stream) throws Exception {
        if(stream==null)throw new IOException("Das Foto konnte nicht geöffnet werden.");File temporary=File.createTempFile("in_",".jpg",getCacheDir());MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=stream;OutputStream out=new FileOutputStream(temporary)){byte[] b=new byte[32768];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>40*1024*1024)throw new IOException("Ein Foto ist größer als 40 MB.");digest.update(b,0,n);out.write(b,0,n);}}catch(Exception e){temporary.delete();throw e;}
        StringBuilder key=new StringBuilder();for(byte b:digest.digest())key.append(String.format(Locale.ROOT,"%02x",b&255));File directory=new File(getFilesDir(),"inputs");directory.mkdirs();File target=new File(directory,key+".jpg");if(target.exists())temporary.delete();else if(!temporary.renameTo(target))throw new IOException("Das Foto konnte nicht gespeichert werden.");return target;
    }
    private void process(List<File> files,boolean hdr,boolean align) throws Exception {File[] outputs=HdrEngine.merge(files,new File(getExternalFilesDir(null),"results"),hdr,align);saveJpeg(outputs[0]);latestHdr=outputs.length>1?outputs[1]:null;}
    private int processGroups(List<File> files,int n,double step,boolean output,boolean align) throws Exception {
        List<BracketRules.Frame> frames=new ArrayList<>();for(File f:files)try{frames.add(HdrEngine.metadata(f));}catch(Exception ignored){}int created=0;
        for(List<BracketRules.Frame> group:BracketRules.group(frames,n,step,8.)){List<File> selected=new ArrayList<>();List<String> keys=new ArrayList<>();for(BracketRules.Frame f:group){selected.add(new File(f.path));keys.add(new File(f.path).getName());}Collections.sort(keys);String key="done:"+output+":"+align+":"+keys;if(prefs.getBoolean(key,false))continue;process(selected,output,align);prefs.edit().putBoolean(key,true).apply();created++;}return created;
    }
    private void saveJpeg(File file) throws Exception {
        String key="saved:"+file.getName();String saved=prefs.getString(key,null);if(saved!=null){latestJpeg=Uri.parse(saved);return;}
        ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,file.getName());values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/LumixHDR");values.put(MediaStore.Images.Media.IS_PENDING,1);Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("Das Foto konnte nicht in der Galerie gespeichert werden.");
        try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(uri)){copy(in,out);}catch(Exception e){getContentResolver().delete(uri,null,null);throw e;}values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(uri,values,null,null);prefs.edit().putString(key,uri.toString()).apply();latestJpeg=uri;
    }
    private void shareResult(ResultFiles.Entry entry){execute("Foto wird vorbereitet …",()->{File file=ensureJpeg(entry);saveJpeg(file);Uri image=latestJpeg;runOnUiThread(()->{if(destroyed)return;Intent send=new Intent(Intent.ACTION_SEND);send.setType("image/jpeg");send.putExtra(Intent.EXTRA_STREAM,image);send.setClipData(ClipData.newRawUri("Lumix HDR",image));send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(send,"Dein Bild teilen"));});});}
    private void scanTree(){Uri source=tree;int n=bracketCount();double step=ev();boolean output=hdr(),align=alignment();execute("Neue Fotos werden geprüft …",()->{DocumentFile folder=DocumentFile.fromTreeUri(this,source);if(folder==null||!folder.canRead())throw new IOException("Der Eingangsordner ist nicht mehr erreichbar.");for(DocumentFile entry:folder.listFiles()){String name=entry.getName();if(!entry.isFile()||name==null||!name.toLowerCase(Locale.ROOT).matches(".*\\.jpe?g"))continue;long modified=entry.lastModified();if(modified>0&&System.currentTimeMillis()-modified<2000)continue;String key=entry.getUri()+":"+entry.length()+":"+modified;if(imported.contains(key))continue;importFile(getContentResolver().openInputStream(entry.getUri()));imported.add(key);}File[] all=new File(getFilesDir(),"inputs").listFiles((d,name)->name.endsWith(".jpg"));if(!OpenCVLoader.initLocal())throw new IOException("Die Bildverarbeitung konnte nicht gestartet werden.");processGroups(all==null?Collections.emptyList():Arrays.asList(all),n,step,output,align);});}
    private void legacyDialog(){EditText address=new EditText(this);address.setText(prefs.getString("camera_host","192.168.54.1"));address.setSingleLine();new AlertDialog.Builder(this).setTitle("Ältere WLAN-Verbindung").setMessage("Nur für ältere Lumix-Protokolle. Der Test wechselt kurz in die Wiedergabe. Die S9-Unterstützung ist unbestätigt.").setView(address).setNegativeButton("Abbrechen",null).setPositiveButton("Testen",(d,w)->{String host=address.getText().toString().trim();execute("Fotos werden gesucht …",()->{ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);Network wifi=null;for(Network network:cm.getAllNetworks()){NetworkCapabilities caps=cm.getNetworkCapabilities(network);if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){wifi=network;break;}}if(wifi==null)throw new IOException("Verbinde dein Telefon zuerst mit dem Kamera-WLAN.");List<File> temporary=new LumixWifi(wifi,host).downloadVisibleJpegs(getCacheDir()),files=new ArrayList<>();try{for(File f:temporary)files.add(importFile(new FileInputStream(f)));}finally{for(File f:temporary)f.delete();}stageFiles(files);});}).show();}
    private ImageView thumbnail(File file,int max){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>max)o.inSampleSize*=2;o.inJustDecodeBounds=false;ImageView v=new ImageView(this);Bitmap bitmap=BitmapFactory.decodeFile(file.getPath(),o);if(bitmap!=null)try{androidx.exifinterface.media.ExifInterface e=new androidx.exifinterface.media.ExifInterface(file);Matrix m=new Matrix();if(e.isFlipped())m.postScale(-1,1);m.postRotate(e.getRotationDegrees());if(!m.isIdentity()){Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,true);if(rotated!=bitmap)bitmap.recycle();bitmap=rotated;}}catch(IOException ignored){}v.setImageBitmap(bitmap);v.setScaleType(ImageView.ScaleType.CENTER_CROP);v.setBackground(ui.shape(ui.soft,14));v.setClipToOutline(true);return v;}
    private static void copy(InputStream in,OutputStream out) throws IOException{if(in==null||out==null)throw new IOException("Dateizugriff fehlgeschlagen.");byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
    @Override public void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==4){
            File source=pendingJpeg;pendingJpeg=null;
            if(result!=RESULT_OK||data==null||data.getData()==null||source==null)return;
            Uri destination=data.getData();execute("JPEG wird exportiert …",()->{try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(destination)){copy(in,out);}runOnUiThread(()->Toast.makeText(this,"JPEG gespeichert.",Toast.LENGTH_SHORT).show());});return;
        }
        if(result!=RESULT_OK||data==null)return;
        if(request==1){List<Uri> uris=new ArrayList<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)uris.add(data.getData());importUris(uris);}
        if(request==2){tree=data.getData();if(tree!=null){getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION);prefs.edit().putString("input_tree",tree.toString()).apply();renderSettings();}}
        if(request==3&&latestHdr!=null){File source=latestHdr;Uri destination=data.getData();execute("HDR wird exportiert …",()->{try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(destination)){copy(in,out);}runOnUiThread(()->Toast.makeText(this,"HDR gespeichert.",Toast.LENGTH_SHORT).show());});}
    }
    @Override public void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);ArrayList<String> paths=new ArrayList<>();for(File f:prepared)paths.add(f.getPath());state.putStringArrayList("selected",paths);state.putInt("tab",currentTab);if(pendingJpeg!=null)state.putString("export_jpeg",pendingJpeg.getPath());if(latestHdr!=null)state.putString("export_hdr",latestHdr.getPath());}
    @Override public void onBackPressed(){if(currentTab!=0){showTab(0);return;}super.onBackPressed();}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);if(camera!=null)camera.onPermissionsResult(requestCode);}
    @Override protected void onResume(){super.onResume();resumed=true;if(camera!=null)camera.refreshConnection();if(watching){handler.removeCallbacks(tick);handler.post(tick);}}
    @Override protected void onPause(){super.onPause();resumed=false;handler.removeCallbacks(tick);}
    @Override protected void onDestroy(){destroyed=true;watching=false;handler.removeCallbacks(tick);if(camera!=null)camera.close();worker.shutdownNow();super.onDestroy();}
    private final class ExposureIllustration extends View {
        private final Paint p=new Paint(3);ExposureIllustration(){super(MainActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){float s=Math.min(getWidth()/280f,getHeight()/144f);c.save();c.translate((getWidth()-280*s)/2,0);c.scale(s,s);for(int i=0;i<3;i++){c.save();c.rotate((i-1)*10,140,78);float x=70+i*9,y=22+i*3;p.setStyle(Paint.Style.FILL);p.setColor(Color.parseColor(ui.dark?new String[]{"#273142","#394A63","#A2BCDF"}[i]:new String[]{"#CDD8E9","#9BB5DB","#EDF3FE"}[i]));c.drawRoundRect(x,y,x+124,y+94,14,14,p);if(i==2){p.setShader(new LinearGradient(x,y,x,y+94,new int[]{Color.parseColor("#BCD4F3"),Color.parseColor("#E6EEF9")},null,Shader.TileMode.CLAMP));c.drawRoundRect(x+7,y+7,x+117,y+87,9,9,p);p.setShader(null);p.setColor(Color.parseColor("#FFF8DD"));c.drawCircle(x+82,y+31,11,p);Path hill=new Path();hill.moveTo(x+7,y+74);hill.cubicTo(x+42,y+28,x+78,y+99,x+117,y+52);hill.lineTo(x+117,y+87);hill.lineTo(x+7,y+87);hill.close();p.setColor(Color.parseColor("#718DAB"));c.drawPath(hill,p);}c.restore();}c.restore();}
    }
}
