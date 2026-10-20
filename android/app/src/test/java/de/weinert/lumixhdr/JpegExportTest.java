package de.weinert.lumixhdr;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class JpegExportTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Test public void resultIndexIncludesHdrWithoutPreviewAndPairsExistingJpeg() throws Exception {
        File dir=temporary.getRoot(),hdr=new File(dir,"a.hdr"),jpeg=new File(dir,"a.jpg"),orphan=new File(dir,"b.hdr");
        Files.write(hdr.toPath(),new byte[]{1});Files.write(jpeg.toPath(),new byte[]{2});Files.write(orphan.toPath(),new byte[]{3});
        List<ResultFiles.Entry> entries=ResultFiles.list(dir);assertEquals(2,entries.size());
        ResultFiles.Entry a=entries.stream().filter(e->e.jpeg.getName().equals("a.jpg")).findFirst().get(),b=entries.stream().filter(e->e.jpeg.getName().equals("b.jpg")).findFirst().get();
        assertEquals(hdr,a.hdr);assertTrue(a.jpeg.isFile());assertEquals(orphan,b.hdr);assertFalse(b.jpeg.exists());
    }
    @Test public void jpegOnlyResultStillExportsAndNonImagesAreIgnored() throws Exception {
        temporary.newFile("photo.jpg");temporary.newFile("notes.txt");temporary.newFolder("directory.hdr");
        List<ResultFiles.Entry> entries=ResultFiles.list(temporary.getRoot());assertEquals(1,entries.size());assertNull(entries.get(0).hdr);
    }
    @Test public void toneMappingRetainsHighlightOrderingAndBounds() throws Exception {
        float[] bgr={.01f,.01f,.01f,1,1,1,100,100,100,Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE};RadiancePreview.toneMap(bgr);
        for(float value:bgr)assertTrue(Float.isFinite(value)&&value>=0&&value<=1);
        assertTrue(bgr[0]<bgr[3]);assertTrue(bgr[3]<bgr[6]);assertTrue(bgr[6]<bgr[9]);
    }
    @Test public void toneMappingIsInvariantToGlobalRadianceScale() throws Exception {
        float[] a={1,2,3,4,5,6},b={100,200,300,400,500,600};RadiancePreview.toneMap(a);RadiancePreview.toneMap(b);assertArrayEquals(a,b,1e-6f);
    }
    @Test public void blackAndInvalidRadianceAreHandledExplicitly() throws Exception {
        float[] black={0,0,0};RadiancePreview.toneMap(black);assertArrayEquals(new float[]{0,0,0},black,0);
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1})assertThrows(IOException.class,()->RadiancePreview.toneMap(new float[]{bad,1,1}));
        assertThrows(IOException.class,()->RadiancePreview.toneMap(new float[]{1,2}));
    }
    @Test public void hdrHeaderAcceptsAppDimensionsAndRejectsOversizedOrCorruptFiles() throws Exception {
        File file=temporary.newFile("frame.hdr");
        Files.write(file.toPath(),"#?RADIANCE\nFORMAT=32-bit_rle_rgbe\n\n-Y 1600 +X 1600\nDATA".getBytes(StandardCharsets.US_ASCII));RadiancePreview.validateHeader(file);
        Files.write(file.toPath(),"#?RADIANCE\nFORMAT=32-bit_rle_rgbe\n\n-Y 99999 +X 99999\nDATA".getBytes(StandardCharsets.US_ASCII));assertThrows(IOException.class,()->RadiancePreview.validateHeader(file));
        Files.write(file.toPath(),"not an HDR".getBytes(StandardCharsets.US_ASCII));assertThrows(IOException.class,()->RadiancePreview.validateHeader(file));
    }
}
