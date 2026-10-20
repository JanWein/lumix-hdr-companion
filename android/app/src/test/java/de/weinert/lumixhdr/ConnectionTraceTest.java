package de.weinert.lumixhdr;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConnectionTraceTest {
    @Test public void includesProtocolStagesAndCodesWithoutAcceptingFreeText(){
        ConnectionTrace trace=new ConnectionTrace("0.3.6",35);trace.reset();
        trace.record("gatt.status",133);trace.record("ble.timeout.operation",0);
        trace.record("ble.write.36f99090-e79b-4060-9ac7-89f3f8bf88d5",2);
        trace.record("wifi.security",3);trace.record("wifi.dhcp",0);trace.record("wifi.unavailable",0);
        for(String secret:new String[]{"password=test-secret","AuthToken=secret","LUMIX-S9","192.168.54.1","00:11:22:33:44:55"})trace.record(secret,0);
        trace.record(null,0);
        String report=trace.report();assertTrue(report.contains("Lumix HDR 0.3.6 | Android API 35"));
        for(String stage:new String[]{"gatt.status  133","ble.timeout.operation  0","ble.write.36f99090-e79b-4060-9ac7-89f3f8bf88d5  2","wifi.security  3","wifi.dhcp  0","wifi.unavailable  0"})assertTrue(stage,report.contains(stage));
        for(String secret:new String[]{"test-secret","AuthToken","LUMIX-S9","192.168.54.1","00:11:22:33:44:55"})assertFalse(report.contains(secret));
    }
    @Test public void retainsOnlyLastHundredEventsAndResets(){
        ConnectionTrace trace=new ConnectionTrace("0.3.6",29);
        for(int i=0;i<150;i++)trace.record("gatt.status",i);
        String[] rows=trace.report().split("\n");assertEquals(102,rows.length);
        assertTrue(rows[2].endsWith("gatt.status  50"));assertTrue(rows[101].endsWith("gatt.status  149"));
        trace.reset();assertEquals(3,trace.report().split("\n").length);assertFalse(trace.report().contains("gatt.status"));
    }
    @Test public void headerCannotCarryArbitraryData(){assertThrows(IllegalArgumentException.class,()->new ConnectionTrace("password=secret",35));}
}
