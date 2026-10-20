package de.weinert.lumixhdr;

import androidx.exifinterface.media.ExifInterface;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class MetadataRegressionTest {
    @Test public void shippedDemoCanBeReadByTheActualAndroidExifParser() throws Exception {
        double[] expected={.125,.0625,.25};
        List<BracketRules.Frame> frames=new ArrayList<>();
        for(int i=0;i<3;i++){
            File file=new File(RuntimeEnvironment.getApplication().getCacheDir(),"demo_"+i+".jpg");
            try(InputStream in=RuntimeEnvironment.getApplication().getAssets().open("demo/BRACKET_"+i+".jpg");OutputStream out=new FileOutputStream(file)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
            ExifInterface exif=new ExifInterface(file);
            String[] keys={ExifInterface.TAG_EXPOSURE_TIME,ExifInterface.TAG_F_NUMBER,ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,ExifInterface.TAG_EXPOSURE_BIAS_VALUE,ExifInterface.TAG_DATETIME_ORIGINAL};
            List<String> missing=new ArrayList<>();for(String key:keys)if(exif.getAttribute(key)==null)missing.add(key);
            assertTrue("Image "+i+" missing Android EXIF fields "+missing,missing.isEmpty());
            BracketRules.Frame frame=HdrEngine.metadata(file);assertEquals(expected[i],frame.seconds,.000001);assertEquals(100,frame.iso,0);assertEquals(8,frame.aperture,0);
            frames.add(frame);
        }
        assertEquals(1,BracketRules.group(frames,3,1,8).size());
    }
    private File fixture(int index,boolean old) throws Exception {
        File file=File.createTempFile("fixture_",".jpg",RuntimeEnvironment.getApplication().getCacheDir());
        InputStream input=old?getClass().getResourceAsStream("/regression/v03/BRACKET_"+index+".jpg"):RuntimeEnvironment.getApplication().getAssets().open("demo/BRACKET_"+index+".jpg");
        assertNotNull(input);try(InputStream in=input;OutputStream out=new FileOutputStream(file)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return file;
    }
    @Test public void exactOldDemoStillWorksForManualHdrDespiteRejectedBiasTag() throws Exception {
        List<File> files=new ArrayList<>();for(int i=0;i<3;i++)files.add(fixture(i,true));
        assertNull(new ExifInterface(files.get(0)).getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE));
        assertArrayEquals(new float[]{.125f,.0625f,.25f},HdrEngine.hdrExposureTimes(files),.000001f);
    }
    @Test public void manualHdrDoesNotRequireGroupingTimestampOrBias() throws Exception {
        List<File> files=new ArrayList<>();for(int i=0;i<3;i++){File f=fixture(i,false);ExifInterface e=new ExifInterface(f);e.setAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE,null);e.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL,null);e.saveAttributes();files.add(f);}
        assertArrayEquals(new float[]{.125f,.0625f,.25f},HdrEngine.hdrExposureTimes(files),.000001f);
        assertThrows(IOException.class,()->HdrEngine.metadata(files.get(0)));
    }
    @Test public void missingRealExposureDataIsNotInventedAndDoesNotLeakCacheFilename() throws Exception {
        List<File> files=Arrays.asList(fixture(0,false),fixture(1,false),fixture(2,false));
        ExifInterface e=new ExifInterface(files.get(0));e.setAttribute(ExifInterface.TAG_EXPOSURE_TIME,null);e.saveAttributes();
        HdrEngine.MissingExposureMetadataException error=assertThrows(HdrEngine.MissingExposureMetadataException.class,()->HdrEngine.hdrExposureTimes(files));
        assertFalse(error.getMessage().contains(files.get(0).getName()));assertTrue(error.getMessage().contains("Original-JPEGs"));
    }
    @Test public void varyingIsoStillRejectsRadianceReconstruction() throws Exception {
        List<File> files=Arrays.asList(fixture(0,false),fixture(1,false),fixture(2,false));
        ExifInterface e=new ExifInterface(files.get(1));e.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,"200");e.saveAttributes();
        assertThrows(IOException.class,()->HdrEngine.hdrExposureTimes(files));
    }
}
