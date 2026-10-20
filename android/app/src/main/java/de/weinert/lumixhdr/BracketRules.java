package de.weinert.lumixhdr;

import java.util.*;

/** Pure Java grouping rules, independently unit-testable. EXIF inference is heuristic. */
public final class BracketRules {
    public static final class Frame {
        public final String path, model;
        public final double timestamp, seconds, aperture, iso, bias;
        public final int width, height;
        public Frame(String path, String model, double timestamp, double seconds,
                     double aperture, double iso, double bias, int width, int height) {
            this.path=path; this.model=model; this.timestamp=timestamp; this.seconds=seconds;
            this.aperture=aperture; this.iso=iso; this.bias=bias; this.width=width; this.height=height;
        }
    }
    public static boolean valid(List<Frame> frames, int count, double step, double maxGap) {
        if(frames.size()!=count || (count!=3&&count!=5&&count!=7) || step<=0) return false;
        Frame first=frames.get(0);
        Set<String> paths=new HashSet<>();
        for(int i=0;i<frames.size();i++) {
            Frame f=frames.get(i);
            if(!paths.add(f.path)||!Double.isFinite(f.seconds)||!Double.isFinite(f.bias)||
                !Double.isFinite(f.timestamp)||!Double.isFinite(f.iso)||!Double.isFinite(f.aperture)||
                f.seconds<=0||f.aperture<=0||f.iso<=0) return false;
            if(!Objects.equals(f.model,first.model)||f.width!=first.width||f.height!=first.height||
                Math.abs(f.aperture-first.aperture)>.02||Math.abs(f.iso-first.iso)>.5) return false;
            if(i>0&&(f.timestamp<frames.get(i-1).timestamp||f.timestamp-frames.get(i-1).timestamp>maxGap)) return false;
        }
        List<Frame> ordered=new ArrayList<>(frames);
        ordered.sort(Comparator.comparingDouble(f->f.bias));
        for(int i=1;i<ordered.size();i++) {
            Frame a=ordered.get(i-1),b=ordered.get(i);
            if(Math.abs(b.bias-a.bias-step)>=.12||Math.abs(Math.log(b.seconds/a.seconds)/Math.log(2)-step)>=.15) return false;
        }
        return true;
    }
    public static List<List<Frame>> group(List<Frame> input,int count,double step,double maxGap) {
        List<Frame> sorted=new ArrayList<>(input);
        sorted.sort(Comparator.comparingDouble((Frame f)->f.timestamp).thenComparing(f->f.path));
        List<List<Frame>> result=new ArrayList<>();
        for(int i=0;i+count<=sorted.size();) {
            List<Frame> group=new ArrayList<>(sorted.subList(i,i+count));
            if(valid(group,count,step,maxGap)) {result.add(group);i+=count;} else i++;
        }
        return result;
    }
}
