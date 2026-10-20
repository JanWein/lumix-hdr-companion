package de.weinert.lumixhdr;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.photo.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgcodecs.Imgcodecs;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class HdrEngine {
    public static final class MissingExposureMetadataException extends IOException {
        MissingExposureMetadataException(){super("Für ein HDR fehlen Belichtungsdaten. Wähle die Original-JPEGs deiner Kamera oder erstelle stattdessen eine JPEG-Fusion.");}
    }
    /** Manual HDR needs exposure time, aperture and ISO, not grouping timestamps or EV bias. */
    public static float[] hdrExposureTimes(List<File> files) throws Exception {
        if(files.size()!=3&&files.size()!=5&&files.size()!=7)throw new IOException("Wähle 3, 5 oder 7 zusammengehörige JPEGs.");
        float[] times=new float[files.size()];double firstIso=0,firstAperture=0,min=Double.POSITIVE_INFINITY,max=0;
        for(int i=0;i<files.size();i++){
            ExifInterface exif=new ExifInterface(files.get(i));
            double seconds=exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME,Double.NaN);
            double aperture=exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER,Double.NaN);
            double iso=exif.getAttributeDouble(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,Double.NaN);
            if(!Double.isFinite(seconds)||seconds<=0||!Double.isFinite(aperture)||aperture<=0||!Double.isFinite(iso)||iso<=0)throw new MissingExposureMetadataException();
            if(i==0){firstIso=iso;firstAperture=aperture;}
            else if(Math.abs(iso-firstIso)>.5||Math.abs(aperture-firstAperture)>.02)throw new IOException("Die Aufnahmen haben unterschiedliche ISO- oder Blendenwerte. Verwende eine Reihe mit festen Einstellungen.");
            times[i]=(float)seconds;if(!Float.isFinite(times[i])||times[i]<=0)throw new MissingExposureMetadataException();
            min=Math.min(min,seconds);max=Math.max(max,seconds);
        }
        if(max/min<1.41)throw new IOException("Die Belichtungen unterscheiden sich zu wenig. Wähle eine vollständige Belichtungsreihe.");
        return times;
    }
    public static BracketRules.Frame metadata(File file) throws Exception {
        ExifInterface exif=new ExifInterface(file);
        String[] required={ExifInterface.TAG_EXPOSURE_TIME,ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,ExifInterface.TAG_EXPOSURE_BIAS_VALUE,ExifInterface.TAG_DATETIME_ORIGINAL};
        for(String key:required) if(exif.getAttribute(key)==null) throw new IOException("Für die automatische Reihenerkennung fehlen Aufnahmedaten. Wähle die zusammengehörigen Originalfotos einzeln aus.");
        SimpleDateFormat df=new SimpleDateFormat("yyyy:MM:dd HH:mm:ss",Locale.ROOT);df.setLenient(false);
        double timestamp=df.parse(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)).getTime()/1000.;
        String sub=exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL);
        if(sub!=null&&sub.matches("[0-9]+")) timestamp+=Double.parseDouble("0."+sub);
        int w=exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH,0),h=exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH,0);
        if(w==0||h==0) {BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);w=o.outWidth;h=o.outHeight;}
        int rotation=exif.getRotationDegrees(); if(rotation==90||rotation==270) {int t=w;w=h;h=t;}
        return new BracketRules.Frame(file.getPath(),exif.getAttribute(ExifInterface.TAG_MODEL),timestamp,
            exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME,Double.NaN),
            exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER,Double.NaN),
            exif.getAttributeDouble(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,Double.NaN),
            exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_BIAS_VALUE,Double.NaN),w,h);
    }
    private static Mat load(File file) throws Exception {
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);
        if(o.outWidth<=0) throw new IOException("JPEG konnte nicht gelesen werden");
        o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>2400) o.inSampleSize*=2;
        o.inJustDecodeBounds=false;o.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap image=BitmapFactory.decodeFile(file.getPath(),o);
        if(image==null) throw new IOException("Bild konnte nicht geladen werden");
        ExifInterface e=new ExifInterface(file);Matrix matrix=new Matrix();
        if(e.isFlipped()) matrix.postScale(-1,1);
        matrix.postRotate(e.getRotationDegrees());
        Bitmap oriented=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),matrix,true);
        if(oriented!=image) image.recycle();
        double scale=Math.min(1.,1600./Math.max(oriented.getWidth(),oriented.getHeight()));
        Bitmap resized=Bitmap.createScaledBitmap(oriented,(int)Math.round(oriented.getWidth()*scale),(int)Math.round(oriented.getHeight()*scale),true);
        if(resized!=oriented) oriented.recycle();
        Mat rgba=new Mat(),bgr=new Mat();
        try {Utils.bitmapToMat(resized,rgba);Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);return bgr;}
        finally {resized.recycle();rgba.release();}
    }
    /** Recover a normal JPEG from a saved HDR, keeping its full current dimensions. */
    public static File exportJpeg(File hdr,File destination) throws Exception {
        if(hdr.getCanonicalFile().equals(destination.getCanonicalFile()))throw new IOException("HDR und JPEG müssen unterschiedliche Dateien sein.");
        RadiancePreview.validateHeader(hdr);
        File parent=destination.getAbsoluteFile().getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Der Exportordner konnte nicht angelegt werden.");
        File temporary=File.createTempFile("jpeg_export_",".jpg",parent);boolean committed=false;
        Mat radiance=Imgcodecs.imread(hdr.getPath(),Imgcodecs.IMREAD_UNCHANGED),mapped=new Mat(),bytes=new Mat();MatOfInt quality=new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY,95);
        try{
            if(radiance.empty()||radiance.type()!=CvType.CV_32FC3)throw new IOException("Die HDR-Datei konnte nicht gelesen werden.");
            float[] pixels=new float[(int)(radiance.total()*3)];radiance.get(0,0,pixels);RadiancePreview.toneMap(pixels);
            mapped.create(radiance.rows(),radiance.cols(),CvType.CV_32FC3);mapped.put(0,0,pixels);mapped.convertTo(bytes,CvType.CV_8UC3,255.);
            if(!Imgcodecs.imwrite(temporary.getPath(),bytes,quality))throw new IOException("Das JPEG konnte nicht erzeugt werden.");
            if(destination.exists())throw new IOException("Die JPEG-Datei ist bereits vorhanden.");
            if(!temporary.renameTo(destination))throw new IOException("Das JPEG konnte nicht gespeichert werden.");
            committed=true;return destination;
        }finally{radiance.release();mapped.release();bytes.release();quality.release();if(!committed)temporary.delete();}
    }
    public static File[] merge(List<File> files,File output,boolean hdr,boolean align) throws Exception {
        if(files.size()!=3&&files.size()!=5&&files.size()!=7) throw new IOException("3, 5 oder 7 JPEGs wählen");
        float[] exposures=hdr?hdrExposureTimes(files):null;
        List<Mat> images=new ArrayList<>();Mat result=new Mat(),radiance=new Mat(),response=new Mat(),times=new Mat(),bytes=new Mat();
        try {
            for(File file:files) images.add(load(file));
            for(Mat image:images) if(image.rows()!=images.get(0).rows()||image.cols()!=images.get(0).cols()) throw new IOException("Bildgrößen stimmen nicht überein");
            if(align) {
                AlignMTB aligner=Photo.createAlignMTB(5,4,false);
                List<Mat> aligned=new ArrayList<>();
                try {
                    // Explicit shift + common overlap, preserving valid pixels only.
                    Mat reference=new Mat();
                    try {
                        Imgproc.cvtColor(images.get(images.size()/2),reference,Imgproc.COLOR_BGR2GRAY);
                        int left=0,top=0,right=reference.cols(),bottom=reference.rows();
                        for(Mat image:images) {
                            Mat gray=new Mat(),shifted=new Mat();
                            Point shift;
                            try {Imgproc.cvtColor(image,gray,Imgproc.COLOR_BGR2GRAY);shift=aligner.calculateShift(reference,gray);aligner.shiftMat(image,shifted,shift);}
                            finally {gray.release();}
                            aligned.add(shifted);
                            left=Math.max(left,(int)shift.x);top=Math.max(top,(int)shift.y);
                            right=Math.min(right,reference.cols()+(int)shift.x);bottom=Math.min(bottom,reference.rows()+(int)shift.y);
                        }
                        if(right-left<.8*reference.cols()||bottom-top<.8*reference.rows()) throw new IOException("Zu starke Kamerabewegung; Stativ nutzen oder Ausrichtung abschalten");
                        Rect overlap=new Rect(left,top,right-left,bottom-top);
                        for(int i=0;i<images.size();i++) {
                            Mat roi=aligned.get(i).submat(overlap),cropped;
                            try {cropped=roi.clone();} finally {roi.release();}
                            images.get(i).release();images.set(i,cropped);
                        }
                    } finally {reference.release();}
                } finally {for(Mat m:aligned)m.release();aligner.clear();}
            }
            if(hdr) {
                times.create(exposures.length,1,CvType.CV_32F);times.put(0,0,exposures);
                Core.setRNGSeed(7);
                CalibrateDebevec calibration=Photo.createCalibrateDebevec(70,10.f,false);
                MergeDebevec merger=Photo.createMergeDebevec();
                try {calibration.process(images,response,times);merger.process(images,radiance,times,response);}
                finally {calibration.clear();merger.clear();}
                if(!Core.checkRange(radiance)) throw new IOException("Ungültige HDR-Radiance");
                // Same global photographic preview mapping as the Python application.
                float[] pixels=new float[(int)(radiance.total()*3)];radiance.get(0,0,pixels);
                RadiancePreview.toneMap(pixels);
                result.create(radiance.rows(),radiance.cols(),CvType.CV_32FC3);result.put(0,0,pixels);
            } else {
                MergeMertens merger=Photo.createMergeMertens(1.f,1.f,1.f);
                try {merger.process(images,result);} finally {merger.clear();}
            }
            output.mkdirs();String name="HDR_"+UUID.randomUUID().toString();
            File jpg=new File(output,name+".jpg"),hdrFile=new File(output,name+".hdr");
            result.convertTo(bytes,CvType.CV_8UC3,255.);
            if(!Imgcodecs.imwrite(jpg.getPath(),bytes)) throw new IOException("JPEG-Export fehlgeschlagen");
            if(hdr&&!Imgcodecs.imwrite(hdrFile.getPath(),radiance)) {jpg.delete();throw new IOException("HDR-Export fehlgeschlagen");}
            return hdr?new File[]{jpg,hdrFile}:new File[]{jpg};
        } finally {for(Mat image:images)image.release();result.release();radiance.release();response.release();times.release();bytes.release();}
    }
}
