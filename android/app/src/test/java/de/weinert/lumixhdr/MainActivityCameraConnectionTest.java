package de.weinert.lumixhdr;

import android.app.*;
import android.os.Looper;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.util.ReflectionHelpers;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

/** Uses the production Activity runner and its real serial worker, not a test task queue. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,35})
@LooperMode(LooperMode.Mode.PAUSED)
public class MainActivityCameraConnectionTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private ModernCameraPanel panel;
    private ExecutorService worker;
    private final CountDownLatch release=new CountDownLatch(1);
    @Before public void setUp(){
        controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();
        panel=ReflectionHelpers.getField(activity,"camera");worker=ReflectionHelpers.getField(activity,"worker");
    }
    @After public void tearDown() throws Exception {
        release.countDown();
        if(!worker.isShutdown())drainWorker();
        if(!(Boolean)ReflectionHelpers.getField(activity,"destroyed"))controller.pause().stop().destroy();
        shadowOf(Looper.getMainLooper()).idle();
    }
    private void begin(){ReflectionHelpers.callInstanceMethod(panel,"beginConnection");}
    private void submitCamera(ModernCameraPanel.Task task){ReflectionHelpers.callInstanceMethod(panel,"run",from(ModernCameraPanel.Task.class,task));}
    private <T>T field(String name){return ReflectionHelpers.getField(panel,name);}
    private boolean execute(MainActivity.ThrowingRunnable task){return ReflectionHelpers.callInstanceMethod(activity,"execute",from(String.class,"Existing work"),from(MainActivity.ThrowingRunnable.class,task));}
    private void drainWorker() throws Exception {worker.submit(()->{}).get(10,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();}
    private int showingDialogs(){int count=0;for(Dialog dialog:ShadowDialog.getShownDialogs())if(dialog.isShowing())count++;return count;}
    private void holdWorker() throws Exception {
        CountDownLatch started=new CountDownLatch(1);
        assertTrue(execute(()->{started.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new IOException("Test worker was not released");}));
        assertTrue(started.await(10,TimeUnit.SECONDS));
    }

    @Test public void actualBusyRunnerRejectsCameraTaskWithVisiblePersistentError() throws Exception {
        holdWorker();begin();AlertDialog progress=field("connectionDialog");AtomicBoolean called=new AtomicBoolean();
        submitCamera(()->called.set(true));
        assertFalse(called.get());assertFalse(progress.isShowing());assertFalse((Boolean)field("connecting"));
        AlertDialog error=field("errorDialog");assertNotNull(error);assertTrue(error.isShowing());assertEquals(1,showingDialogs());
        release.countDown();drainWorker();assertTrue(error.isShowing());assertFalse(called.get());
    }
    @Test public void canceledOldWorkerCannotShowGenericErrorOverNewAttempt() throws Exception {
        begin();CountDownLatch started=new CountDownLatch(1);
        submitCamera(()->{started.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new IOException("Test worker was not released");throw new IOException("Old camera attempt failed");});
        assertTrue(started.await(10,TimeUnit.SECONDS));
        ReflectionHelpers.callInstanceMethod(panel,"disconnectCamera");begin();AlertDialog current=field("connectionDialog");
        release.countDown();drainWorker();
        assertTrue(current.isShowing());assertNull(field("errorDialog"));assertTrue((Boolean)field("connecting"));assertEquals(1,showingDialogs());
    }
    @Test public void currentWorkerErrorShowsOneCopyableCameraErrorWithoutGenericDuplicate() throws Exception {
        begin();submitCamera(()->{throw new IOException("Current camera attempt failed");});drainWorker();
        AlertDialog error=field("errorDialog");assertNotNull(error);assertTrue(error.isShowing());assertFalse((Boolean)field("connecting"));assertEquals(1,showingDialogs());
        assertTrue(shadowOf(error).getMessage().toString().contains("Current camera attempt failed"));
        assertTrue(error.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled());
    }
    @Test public void executeReportsBusyAndDestroyedRejectionWithoutSubmitting() throws Exception {
        holdWorker();AtomicBoolean called=new AtomicBoolean();assertFalse(execute(()->called.set(true)));
        release.countDown();drainWorker();assertFalse(called.get());
        controller.pause().stop().destroy();assertFalse(execute(()->called.set(true)));assertFalse(called.get());
    }
}
