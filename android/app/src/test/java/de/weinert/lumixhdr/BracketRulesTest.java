package de.weinert.lumixhdr;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class BracketRulesTest {
 private BracketRules.Frame f(String path,double t,double seconds,double bias,double iso) {
  return new BracketRules.Frame(path,"S9-fixture",t,seconds,8,iso,bias,600,400);
 }
 private List<BracketRules.Frame> good() {
  return Arrays.asList(f("0",0,.125,0,100),f("1",.2,.0625,-1,100),f("2",.4,.25,1,100));
 }
 @Test public void complete(){assertEquals(1,BracketRules.group(good(),3,1,8).size());}
 @Test public void missing(){assertTrue(BracketRules.group(good().subList(0,2),3,1,8).isEmpty());}
 @Test public void mixedIso(){List<BracketRules.Frame> a=new ArrayList<>(good());a.set(2,f("2",.4,.25,1,200));assertFalse(BracketRules.valid(a,3,1,8));}
 @Test public void repeatedExposure(){List<BracketRules.Frame> a=new ArrayList<>(good());a.set(2,f("2",.4,.125,0,100));assertFalse(BracketRules.valid(a,3,1,8));}
 @Test public void lateFrame(){List<BracketRules.Frame> a=new ArrayList<>(good());a.set(2,f("2",30,.25,1,100));assertFalse(BracketRules.valid(a,3,1,8));}
 @Test public void noDuplicates(){List<BracketRules.Frame> a=new ArrayList<>(good());a.set(2,f("0",.4,.25,1,100));assertFalse(BracketRules.valid(a,3,1,8));}
 @Test public void twoSeries(){List<BracketRules.Frame> a=new ArrayList<>(good());a.addAll(Arrays.asList(f("3",10,.125,0,100),f("4",10.2,.0625,-1,100),f("5",10.4,.25,1,100)));assertEquals(2,BracketRules.group(a,3,1,8).size());}
 @Test public void shuffled(){List<BracketRules.Frame> a=new ArrayList<>(good());Collections.reverse(a);assertEquals(1,BracketRules.group(a,3,1,8).size());}
}
