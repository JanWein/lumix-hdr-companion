package de.weinert.lumixhdr;

import android.app.*;
import android.graphics.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import java.io.*;
import java.util.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,qualifiers="w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class StudioUiTest {
    private ActivityController<MainActivity> controller;private MainActivity activity;
    @Before public void start(){controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();}
    @After public void stop(){controller.pause().stop().destroy();}
    private TextView text(View node,String label){if(node.getVisibility()!=View.VISIBLE)return null;if(node instanceof TextView&&((TextView)node).getText().toString().equals(label))return (TextView)node;if(node instanceof ViewGroup){ViewGroup group=(ViewGroup)node;for(int i=0;i<group.getChildCount();i++){TextView found=text(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
    private View root(){return activity.findViewById(android.R.id.content);}
    private void clickText(String name){TextView v=text(root(),name);assertNotNull(name,v);View target=v;while(!target.isClickable()&&target.getParent() instanceof View)target=(View)target.getParent();assertTrue(target.performClick());}
    @Test public void firstScreenHasOnePrimaryImportAndNoProtocolFields() throws Exception {
        assertNotNull(text(root(),"Fotos auswählen"));assertNull(root().findViewWithTag("create-hdr"));
        screenshot("studio-light.png");
    }
    @Test public void navigationKeepsCameraAndEmptyResultsReachable() throws Exception {
        clickText("Kamera");assertNotNull(text(root(),"In drei Schritten bereit"));screenshot("camera-light.png");
        clickText("Bilder");assertNotNull(text(root(),"Platz für deine Perspektive."));screenshot("images-light.png");
    }
    @Test public void selectionIsRestoredWithoutStartingProcessing() throws Exception {
        File f=new File(activity.getFilesDir(),"chosen.jpg");copyDemo(f,0);
        Bundle saved=new Bundle();saved.putStringArrayList("selected",new ArrayList<>(Collections.singletonList(f.getPath())));
        controller.pause().stop().destroy();controller=Robolectric.buildActivity(MainActivity.class).create(saved).start().resume().visible();activity=controller.get();
        View create=root().findViewWithTag("create-hdr");assertNotNull(create);assertFalse(create.isEnabled());
    }
    @Test public void completeSelectionWaitsForExplicitCreate() throws Exception {
        ArrayList<String> files=new ArrayList<>();for(int i=0;i<3;i++){File f=new File(activity.getFilesDir(),"selected_"+i+".jpg");copyDemo(f,i);files.add(f.getPath());}
        Bundle saved=new Bundle();saved.putStringArrayList("selected",files);
        controller.pause().stop().destroy();controller=Robolectric.buildActivity(MainActivity.class).create(saved).start().resume().visible();activity=controller.get();
        View create=root().findViewWithTag("create-hdr");assertNotNull(create);assertTrue(create.isEnabled());assertFalse(new File(activity.getExternalFilesDir(null),"results").exists());screenshot("studio-ready.png");
    }
    @Test public void settingsStayOutsideTheMainFlow() throws Exception {
        View gear=findDescription(root(),"Einstellungen");assertNotNull(gear);gear.performClick();assertNotNull(text(root(),"Entwicklung"));screenshot("settings-light.png");
    }
    @Test public void actualHdrActionWithMissingMetadataOffersExplicitJpegRecovery() throws Exception {
        ArrayList<String> files=new ArrayList<>();for(int i=0;i<3;i++){File f=new File(activity.getFilesDir(),"missing_data_"+i+".jpg");copyDemo(f,i);if(i==0){androidx.exifinterface.media.ExifInterface e=new androidx.exifinterface.media.ExifInterface(f);e.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME,null);e.saveAttributes();}files.add(f.getPath());}
        Bundle saved=new Bundle();saved.putStringArrayList("selected",files);
        controller.pause().stop().destroy();controller=Robolectric.buildActivity(MainActivity.class).create(saved).start().resume().visible();activity=controller.get();
        assertTrue(root().findViewWithTag("create-hdr").performClick());
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("worker");field.setAccessible(true);
        ((java.util.concurrent.ExecutorService)field.get(activity)).submit(()->{}).get(10,java.util.concurrent.TimeUnit.SECONDS);
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
        AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);
        assertEquals("JPEG-Fusion erstellen",dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
        assertEquals("Originalfotos wählen",dialog.getButton(AlertDialog.BUTTON_NEGATIVE).getText().toString());
        TextView message=dialog.findViewById(android.R.id.message);assertNotNull(message);assertFalse(message.getText().toString().contains("missing_data_"));
        assertTrue(activity.getSharedPreferences("hdr",0).getBoolean("output_hdr",true));
    }
    @Test public void jpegExportOpensSaveDialogAndCopiesExactJpegBytesKeepingHdr() throws Exception {
        File dir=new File(activity.getExternalFilesDir(null),"results");dir.mkdirs();File jpeg=new File(dir,"export_test.jpg");copyDemo(jpeg,0);
        File hdr=new File(dir,"export_test.hdr");byte[] originalHdr="#?RADIANCE\nTEST HDR unchanged".getBytes(java.nio.charset.StandardCharsets.US_ASCII);java.nio.file.Files.write(hdr.toPath(),originalHdr);
        clickText("Bilder");clickText("Als JPEG exportieren");Intent save=org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(Intent.ACTION_CREATE_DOCUMENT,save.getAction());assertEquals("image/jpeg",save.getType());assertTrue(save.getStringExtra(Intent.EXTRA_TITLE).startsWith("Lumix_HDR_"));assertTrue(save.getStringExtra(Intent.EXTRA_TITLE).endsWith(".jpg"));
        File destination=new File(activity.getCacheDir(),"exported.jpg");android.net.Uri target=android.net.Uri.parse("content://test.provider/exported.jpg");
        try(OutputStream out=new FileOutputStream(destination)){
            org.robolectric.Shadows.shadowOf(activity.getContentResolver()).registerOutputStream(target,out);
            activity.onActivityResult(4,Activity.RESULT_OK,new Intent().setData(target));
            java.lang.reflect.Field field=MainActivity.class.getDeclaredField("worker");field.setAccessible(true);((java.util.concurrent.ExecutorService)field.get(activity)).submit(()->{}).get(10,java.util.concurrent.TimeUnit.SECONDS);
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        assertArrayEquals(java.nio.file.Files.readAllBytes(jpeg.toPath()),java.nio.file.Files.readAllBytes(destination.toPath()));assertArrayEquals(originalHdr,java.nio.file.Files.readAllBytes(hdr.toPath()));
        assertNotNull(BitmapFactory.decodeFile(destination.getPath()));screenshot("images-jpeg-export.png");
    }
    @Test public void cancellingJpegExportDoesNotChangeAnySourceFile() throws Exception {
        File dir=new File(activity.getExternalFilesDir(null),"results");dir.mkdirs();File jpeg=new File(dir,"cancel.jpg");copyDemo(jpeg,0);byte[] before=java.nio.file.Files.readAllBytes(jpeg.toPath());
        clickText("Bilder");clickText("Als JPEG exportieren");activity.onActivityResult(4,Activity.RESULT_CANCELED,null);assertArrayEquals(before,java.nio.file.Files.readAllBytes(jpeg.toPath()));
    }
    @Test public void hdrWithoutPreviewRemainsVisibleWithJpegExportAction() throws Exception {
        File dir=new File(activity.getExternalFilesDir(null),"results");dir.mkdirs();File hdr=new File(dir,"orphan.hdr");java.nio.file.Files.write(hdr.toPath(),new byte[]{1});
        clickText("Bilder");assertNotNull(text(root(),"HDR-Datei gespeichert"));assertNotNull(text(root(),"Als JPEG exportieren"));assertNotNull(text(root(),"HDR exportieren"));
    }
    @Test public void cancellingDeletionPreservesTheResult() throws Exception {
        File dir=new File(activity.getExternalFilesDir(null),"results");dir.mkdirs();File jpeg=new File(dir,"keep.jpg");copyDemo(jpeg,0);File hdr=new File(dir,"keep.hdr");java.nio.file.Files.write(hdr.toPath(),new byte[]{1});
        clickText("Bilder");View delete=findDescription(root(),"Bild löschen");assertNotNull(delete);delete.performClick();
        AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing());
        assertTrue(((TextView)dialog.findViewById(android.R.id.message)).getText().toString().contains("Originalaufnahmen"));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertTrue(jpeg.isFile());assertTrue(hdr.isFile());assertNotNull(root().findViewWithTag("delete-result"));screenshot("images-delete-action.png");
    }
    @Test public void confirmingDeletionRemovesPairAndShowsEmptyStateKeepingOriginalAndCopy() throws Exception {
        File dir=new File(activity.getExternalFilesDir(null),"results");dir.mkdirs();File jpeg=new File(dir,"delete.jpg");copyDemo(jpeg,0);File hdr=new File(dir,"delete.hdr");java.nio.file.Files.write(hdr.toPath(),new byte[]{1});
        File original=new File(activity.getFilesDir(),"original.jpg"),exported=new File(activity.getCacheDir(),"exported.jpg");copyDemo(original,0);copyDemo(exported,0);
        activity.getSharedPreferences("hdr",0).edit().putString("saved:delete.jpg","content://media/external/images/media/42").commit();
        activity.getSharedPreferences("hdr",0).edit().putString("name:delete.jpg","Wird gelöscht").commit();
        clickText("Bilder");root().findViewWithTag("delete-result").performClick();AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();awaitWorker();
        assertFalse(jpeg.exists());assertFalse(hdr.exists());assertTrue(original.isFile());assertTrue(exported.isFile());assertFalse(activity.getSharedPreferences("hdr",0).contains("saved:delete.jpg"));assertFalse(activity.getSharedPreferences("hdr",0).contains("name:delete.jpg"));
        assertNotNull(text(root(),"Platz für deine Perspektive."));assertNull(root().findViewWithTag("delete-result"));
    }
    @Test public void hdrOnlyResultCanBeDeletedFromTheUi() throws Exception {
        File dir=new File(activity.getExternalFilesDir(null),"results");dir.mkdirs();File hdr=new File(dir,"orphan.hdr");java.nio.file.Files.write(hdr.toPath(),new byte[]{1});
        clickText("Bilder");root().findViewWithTag("delete-result").performClick();org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();awaitWorker();
        assertFalse(hdr.exists());assertNotNull(text(root(),"Platz für deine Perspektive."));
    }
    @Test public void renamedImageSurvivesRestartAndNamesBothExportFormatsWithoutChangingFiles() throws Exception {
        File dir=new File(activity.getExternalFilesDir(null),"results");dir.mkdirs();File jpeg=new File(dir,"named.jpg");copyDemo(jpeg,0);File hdr=new File(dir,"named.hdr");byte[] hdrBytes={1,2,3};java.nio.file.Files.write(hdr.toPath(),hdrBytes);byte[] jpegBytes=java.nio.file.Files.readAllBytes(jpeg.toPath());long modified=jpeg.lastModified();
        activity.getSharedPreferences("hdr",0).edit().putString("saved:named.jpg","content://media/external/images/media/42").commit();
        clickText("Bilder");AlertDialog rename=openRename();EditText input=rename.getWindow().getDecorView().findViewWithTag("result-name-input");input.setText("  Abend am See.jpg ");screenshot(rename.getWindow().getDecorView(),"rename-dialog.png");rename.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertFalse(rename.isShowing());assertNotNull(text(root(),"Abend am See"));screenshot("images-renamed.png");
        controller.pause().stop().destroy();controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();clickText("Bilder");assertNotNull(text(root(),"Abend am See"));
        clickText("Als JPEG exportieren");Intent jpgSave=Shadows.shadowOf(activity).getNextStartedActivity();assertEquals("Abend am See.jpg",jpgSave.getStringExtra(Intent.EXTRA_TITLE));activity.onActivityResult(4,Activity.RESULT_CANCELED,null);
        clickText("HDR exportieren");Intent hdrSave=Shadows.shadowOf(activity).getNextStartedActivity();assertEquals("Abend am See.hdr",hdrSave.getStringExtra(Intent.EXTRA_TITLE));
        assertArrayEquals(jpegBytes,java.nio.file.Files.readAllBytes(jpeg.toPath()));assertArrayEquals(hdrBytes,java.nio.file.Files.readAllBytes(hdr.toPath()));assertEquals(modified,jpeg.lastModified());assertEquals("content://media/external/images/media/42",activity.getSharedPreferences("hdr",0).getString("saved:named.jpg",null));
        rename=openRename();assertEquals("Abend am See",((EditText)rename.getWindow().getDecorView().findViewWithTag("result-name-input")).getText().toString());rename.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
    }
    @Test public void invalidNameStaysInDialogAndCancelPreservesPreviousName() throws Exception {
        File dir=new File(activity.getExternalFilesDir(null),"results");dir.mkdirs();File jpeg=new File(dir,"cancel_name.jpg");copyDemo(jpeg,0);
        activity.getSharedPreferences("hdr",0).edit().putString("name:cancel_name.jpg","Vorher").apply();
        clickText("Bilder");AlertDialog rename=openRename();EditText input=rename.getWindow().getDecorView().findViewWithTag("result-name-input");
        for(String bad:new String[]{" ","../Bild"}){input.setText(bad);rename.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertTrue(rename.isShowing());assertNotNull(input.getError());assertEquals("Vorher",activity.getSharedPreferences("hdr",0).getString("name:cancel_name.jpg",null));}
        input.setText("Nicht speichern");rename.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertNotNull(text(root(),"Vorher"));assertTrue(jpeg.isFile());
    }
    @Test public void namingOneJpegDoesNotRenameOtherResults() throws Exception {
        File dir=new File(activity.getExternalFilesDir(null),"results");dir.mkdirs();copyDemo(new File(dir,"first.jpg"),0);copyDemo(new File(dir,"second.jpg"),0);
        clickText("Bilder");AlertDialog rename=openRename();EditText input=rename.getWindow().getDecorView().findViewWithTag("result-name-input");input.setText("Mein JPEG");input.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        assertEquals(1,activity.getSharedPreferences("hdr",0).getAll().keySet().stream().filter(k->k.startsWith("name:")).count());assertNotNull(text(root(),"Mein JPEG"));assertNotNull(text(root(),"Dein Bild"));
    }
    @Test @Config(qualifiers="w320dp-h700dp-night-mdpi",fontScale=1.3f) public void longHdrNameRendersOnNarrowScreenAndExportsWithItsName() throws Exception {
        File dir=new File(activity.getExternalFilesDir(null),"results");dir.mkdirs();File hdr=new File(dir,"orphan.hdr");java.nio.file.Files.write(hdr.toPath(),new byte[]{1});
        clickText("Bilder");AlertDialog rename=openRename();String name="Sonnenuntergang an der Küste mit der ganzen Familie";((EditText)rename.getWindow().getDecorView().findViewWithTag("result-name-input")).setText(name);rename.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertNotNull(text(root(),name));
        screenshot("images-named-dark-narrow.png");clickText("HDR exportieren");assertEquals(name+".hdr",Shadows.shadowOf(activity).getNextStartedActivity().getStringExtra(Intent.EXTRA_TITLE));assertTrue(hdr.isFile());assertNotNull(root().findViewWithTag("delete-result"));
    }
    private AlertDialog openRename(){assertTrue(root().findViewWithTag("rename-result").performClick());AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(dialog.isShowing());return dialog;}
    private void awaitWorker() throws Exception {
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("worker");field.setAccessible(true);((java.util.concurrent.ExecutorService)field.get(activity)).submit(()->{}).get(10,java.util.concurrent.TimeUnit.SECONDS);Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    private View findDescription(View node,String label){if(label.equals(node.getContentDescription()))return node;if(node instanceof ViewGroup){ViewGroup g=(ViewGroup)node;for(int i=0;i<g.getChildCount();i++){View found=findDescription(g.getChildAt(i),label);if(found!=null)return found;}}return null;}
    private void copyDemo(File target,int index) throws Exception {try(InputStream in=activity.getAssets().open("demo/BRACKET_"+index+".jpg");OutputStream out=new FileOutputStream(target)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}
    @Test @Config(qualifiers="w390dp-h844dp-night-mdpi") public void darkAppearanceRenders() throws Exception {screenshot("studio-dark.png");}
    @Test @Config(qualifiers="w320dp-h700dp-mdpi",fontScale=1.3f) public void narrowScreenWithLargeTypeKeepsPrimaryActionReachable() throws Exception {screenshot("studio-large-type.png");assertNotNull(text(root(),"Fotos auswählen"));}
    private void screenshot(String name) throws Exception {screenshot(activity.getWindow().getDecorView(),name);}
    private void screenshot(View decor,String name) throws Exception {
        int width=activity.getResources().getDisplayMetrics().widthPixels,height=activity.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));decor.layout(0,0,width,height);
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);decor.draw(new Canvas(bitmap));File dir=new File("build/ui-preview");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
}
