package de.weinert.lumixhdr;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.Files;

public class ResultDeleteTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private File write(File directory,String name) throws IOException {
        File file=new File(directory,name);Files.write(file.toPath(),new byte[]{1,2,3});return file;
    }
    @Test public void deletesPairKeepingOtherResultsOriginalsAndExportedCopies() throws Exception {
        File results=temporary.newFolder("results"),inputs=temporary.newFolder("inputs"),gallery=temporary.newFolder("gallery");
        File jpeg=write(results,"a.jpg"),hdr=write(results,"a.hdr"),other=write(results,"b.jpg"),original=write(inputs,"a.jpg"),copy=write(gallery,"a.jpg");
        ResultFiles.delete(results,new ResultFiles.Entry(jpeg,hdr));
        assertFalse(jpeg.exists());assertFalse(hdr.exists());assertTrue(other.isFile());assertTrue(original.isFile());assertTrue(copy.isFile());assertEquals(1,ResultFiles.list(results).size());
    }
    @Test public void deletesJpegOnlyAndRepeatedDeletionIsHarmless() throws Exception {
        File dir=temporary.newFolder("results"),jpeg=write(dir,"fusion.jpg");ResultFiles.Entry entry=new ResultFiles.Entry(jpeg,null);
        ResultFiles.delete(dir,entry);ResultFiles.delete(dir,entry);assertTrue(ResultFiles.list(dir).isEmpty());
    }
    @Test public void deletesHdrWithoutPreview() throws Exception {
        File dir=temporary.newFolder("results"),hdr=write(dir,"orphan.hdr");
        ResultFiles.delete(dir,ResultFiles.list(dir).get(0));assertFalse(hdr.exists());assertTrue(ResultFiles.list(dir).isEmpty());
    }
    @Test public void validatesBothPathsBeforeDeletingEither() throws Exception {
        File dir=temporary.newFolder("results"),jpeg=write(dir,"a.jpg"),outside=write(temporary.getRoot(),"a.hdr");
        assertThrows(IOException.class,()->ResultFiles.delete(dir,new ResultFiles.Entry(jpeg,outside)));
        assertTrue(jpeg.isFile());assertTrue(outside.isFile());
    }
    @Test public void rejectsMismatchedPairsAndDirectories() throws Exception {
        File dir=temporary.newFolder("results"),jpeg=write(dir,"a.jpg"),hdr=write(dir,"b.hdr"),subdir=new File(dir,"folder.jpg");assertTrue(subdir.mkdir());
        assertThrows(IOException.class,()->ResultFiles.delete(dir,new ResultFiles.Entry(jpeg,hdr)));
        assertThrows(IOException.class,()->ResultFiles.delete(dir,new ResultFiles.Entry(subdir,null)));
        assertTrue(jpeg.isFile());assertTrue(hdr.isFile());assertTrue(subdir.isDirectory());
    }
    @Test public void rejectsSymlinkEscapingResultDirectory() throws Exception {
        File dir=temporary.newFolder("results"),original=write(temporary.getRoot(),"original.jpg"),link=new File(dir,"a.jpg");
        Files.createSymbolicLink(link.toPath(),original.toPath());
        assertThrows(IOException.class,()->ResultFiles.delete(dir,new ResultFiles.Entry(link,null)));assertTrue(original.isFile());
    }
}
