package de.weinert.lumixhdr;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;

/** Existing photographic tone map, shared by HDR creation and later JPEG export. */
final class RadiancePreview {
    static void toneMap(float[] bgr) throws IOException {
        if(bgr.length==0||bgr.length%3!=0)throw new IOException("Die HDR-Bilddaten sind unvollständig.");
        double logSum=0;
        for(int i=0;i<bgr.length;i+=3){
            for(int j=0;j<3;j++)if(!Float.isFinite(bgr[i+j])||bgr[i+j]<0)throw new IOException("Die HDR-Datei enthält ungültige Helligkeitswerte.");
            logSum+=Math.log(Math.max(1e-6,.0722*bgr[i]+.7152*bgr[i+1]+.2126*bgr[i+2]));
        }
        double factor=.18/Math.exp(logSum/(bgr.length/3));
        for(int i=0;i<bgr.length;i++){double v=bgr[i]*factor;bgr[i]=(float)Math.pow(v/(1+v),1./2.2);}
    }
    /** Bound dimensions before the native decoder allocates its image buffers. */
    static void validateHeader(File file) throws IOException {
        if(!file.isFile()||file.length()==0||file.length()>160L*1024*1024)throw new IOException("Die HDR-Datei fehlt oder ist zu groß.");
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))){
            String magic=line(in);if(!magic.equals("#?RADIANCE")&&!magic.equals("#?RGBE"))throw new IOException("Dies ist keine Radiance-HDR-Datei.");
            boolean format=false,blank=false;
            for(int i=0;i<64;i++){String l=line(in);if(l.equals("FORMAT=32-bit_rle_rgbe"))format=true;if(l.isEmpty()){blank=true;break;}}
            if(!format||!blank)throw new IOException("Das HDR-Dateiformat wird nicht unterstützt.");
            Matcher m=Pattern.compile("[+-]Y\\s+([0-9]+)\\s+[+-]X\\s+([0-9]+)").matcher(line(in));
            if(!m.matches())throw new IOException("Die HDR-Bildgröße konnte nicht gelesen werden.");
            try{long height=Long.parseLong(m.group(1)),width=Long.parseLong(m.group(2));if(width<1||height<1||width>4096||height>4096||width*height>4_000_000)throw new IOException("Die HDR-Datei ist für diesen Export zu groß.");}catch(NumberFormatException e){throw new IOException("Die HDR-Bildgröße ist ungültig.");}
        }
    }
    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(int i=0;i<512;i++){int b=in.read();if(b<0)throw new IOException("Die HDR-Datei ist unvollständig.");if(b==10)return out.toString(StandardCharsets.US_ASCII.name()).trim();out.write(b);}throw new IOException("Der HDR-Dateikopf ist ungültig.");
    }
}
