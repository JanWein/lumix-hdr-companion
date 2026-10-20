package de.weinert.lumixhdr;

import org.junit.Test;
import static org.junit.Assert.*;

public class ResultNamesTest {
    @Test public void acceptsReadableNamesAndNormalizesUnicodeAndExtensions(){
        assertEquals("Abend am See",ResultNames.clean("  Abend am See.JPG  "));
        assertEquals("Küste",ResultNames.clean("Ku\u0308ste.hdr"));
        assertEquals("Familie 📷",ResultNames.clean("Familie 📷.jpeg"));
    }
    @Test public void rejectsEmptyNamesPathsAndUnsupportedFileCharacters(){
        for(String name:new String[]{"","   ",".jpg",".","..","../Bild","a/b","a\\b","a:b","a*b","a?b","a\"b","a<b","a>b","a|b","a\nb","a\u0000b","hidden."})
            assertThrows(name,IllegalArgumentException.class,()->ResultNames.clean(name));
    }
    @Test public void limitsNamesByUnicodeCharactersRatherThanUtf16Units(){
        String sixty="📷".repeat(60);assertEquals(sixty,ResultNames.clean(sixty));
        assertThrows(IllegalArgumentException.class,()->ResultNames.clean(sixty+"a"));
    }
}
