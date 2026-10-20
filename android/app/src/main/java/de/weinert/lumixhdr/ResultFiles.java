package de.weinert.lumixhdr;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** One result per stem, including HDRs whose JPEG preview is no longer present. */
final class ResultFiles {
    static final class Entry {
        final File jpeg,hdr;
        Entry(File jpeg,File hdr){this.jpeg=jpeg;this.hdr=hdr;}
        long modified(){return hdr!=null?hdr.lastModified():jpeg.lastModified();}
    }
    static List<Entry> list(File directory){
        Map<String,File> jpegs=new HashMap<>(),hdrs=new HashMap<>();File[] files=directory.listFiles();
        if(files==null)return Collections.emptyList();
        for(File file:files){if(!file.isFile())continue;String name=file.getName();int dot=name.lastIndexOf('.');if(dot<1)continue;String ext=name.substring(dot).toLowerCase(Locale.ROOT),stem=name.substring(0,dot);if(ext.equals(".jpg"))jpegs.put(stem,file);else if(ext.equals(".hdr"))hdrs.put(stem,file);}
        Set<String> stems=new HashSet<>(jpegs.keySet());stems.addAll(hdrs.keySet());List<Entry> result=new ArrayList<>();
        for(String stem:stems)result.add(new Entry(jpegs.getOrDefault(stem,new File(directory,stem+".jpg")),hdrs.get(stem)));
        result.sort(Comparator.comparingLong(Entry::modified).reversed().thenComparing(e->e.jpeg.getName()));return result;
    }
    /** Delete only this app result. Validate both paths before changing either file. */
    static void delete(File directory,Entry entry) throws IOException {
        File parent=directory.getCanonicalFile();
        validate(parent,entry.jpeg,".jpg");
        if(entry.hdr!=null){
            validate(parent,entry.hdr,".hdr");
            String jpegStem=entry.jpeg.getName().substring(0,entry.jpeg.getName().length()-4);
            String hdrStem=entry.hdr.getName().substring(0,entry.hdr.getName().length()-4);
            if(!jpegStem.equals(hdrStem))throw new IOException("Die Bilddateien gehören nicht zum selben Ergebnis.");
        }
        for(File file:new File[]{entry.jpeg,entry.hdr})if(file!=null&&file.exists()&&!file.delete())
            throw new IOException("Das Bild konnte nicht vollständig gelöscht werden. Bitte versuche es erneut.");
    }
    private static void validate(File parent,File file,String extension) throws IOException {
        if(file==null||!parent.equals(file.getCanonicalFile().getParentFile())||
                !file.getName().toLowerCase(Locale.ROOT).endsWith(extension)||
                (file.exists()&&!file.isFile()))
            throw new IOException("Diese Datei kann nicht aus der Bilderübersicht gelöscht werden.");
    }
}
