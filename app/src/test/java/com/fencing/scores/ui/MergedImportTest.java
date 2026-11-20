package com.fencing.scores.ui;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.*;

public class MergedImportTest {
    private static final String MERGED = "Nr,Name,#,V,→,←,I,%,P,Grp,FinalPos\n"
            + "1,Alice,1,1,5,2,3,100,2,B,4\n2,Bob,1,0,2,5,-3,0,1,A,\n";
    private static final String BOUTS = "Nr,Name,1,2,3,V,→,←,I,%,P\n"
            + "1,Alice,,5,,1,5,2,3,100,1\n2,Bob,2,,,0,2,5,-3,0,2\n3,,,,,,,,,,\n";
    private static final String ROUND_QR = "ROUND_DATA\n3\nAlice,Bob,\n-1,5,-1\n2,-1,-1\n-1,-1,-1\n";

    private static MergedImport csv(String data) throws Exception {
        return MergedImport.decode(new StringReader(data), false);
    }

    private static MergedImport qr(String data) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
            out.write(data.getBytes(StandardCharsets.UTF_8));
        }
        return MergedImport.decodeQr(Base64.getEncoder().encodeToString(bytes.toByteArray()));
    }

    @Test public void mergedCsvAndQrKeepSavedPositionsEvenWhenPerformanceDiffers() throws Exception {
        for (MergedImport imported : new MergedImport[]{csv(MERGED), qr(MERGED)}) {
            assertEquals(2, imported.rows.get(0).p);
            assertEquals("B", imported.rows.get(0).grp);
            assertEquals(Integer.valueOf(4), imported.rows.get(0).finalPos);
            assertNull(imported.rows.get(1).finalPos);
        }
    }

    @Test public void boutsExportsProduceSameRowsAndAddToExistingParticipant() throws Exception {
        for (MergedImport imported : new MergedImport[]{csv(BOUTS), qr(ROUND_QR + "P,1,2,\n"), qr(ROUND_QR)}) {
            assertEquals(2, imported.rows.size());
            MergedFragment.Row bob = imported.rows.get(1);
            assertEquals("Bob", bob.name);
            assertEquals(1, bob.matches);
            assertEquals(0, bob.victories);
            assertEquals(-3, bob.index);
            assertEquals(2, bob.p);
            MergedFragment.Row current = csv(MERGED).rows.get(1);
            MergedFragment.addBoutTotals(current, bob);
            assertEquals(2, current.matches);
            assertEquals(4, current.given);
            assertEquals(10, current.received);
        }
    }

    @Test public void rejectWrongPageEmptyAndPartlyMalformedExports() {
        for (String invalid : new String[]{"KO_DATA\n1,Alice", MERGED.substring(0, MERGED.indexOf('\n')),
                MERGED + "3,Charlie\n", MERGED.replace("-3,0", "oops,0"), BOUTS.replace("2,Bob", "1,Bob")}) {
            assertThrows(IllegalArgumentException.class, () -> csv(invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> qr(ROUND_QR + "P,1,2\n"));
        assertThrows(IllegalArgumentException.class, () -> qr(ROUND_QR.replace("2,-1,-1", "2,-1")));
        assertThrows(IllegalArgumentException.class, () -> MergedImport.decodeQr("not a QR export"));
    }
}
