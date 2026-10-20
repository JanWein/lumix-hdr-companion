package de.weinert.lumixhdr;

import java.text.Normalizer;
import java.util.Locale;

/** A user-facing name, independent of the engine's stable result filenames. */
final class ResultNames {
    static String clean(String value){
        String name=Normalizer.normalize(value.trim(),Normalizer.Form.NFC);
        String lower=name.toLowerCase(Locale.ROOT);
        for(String extension:new String[]{".jpeg",".jpg",".hdr"})if(lower.endsWith(extension)){
            name=name.substring(0,name.length()-extension.length()).trim();break;
        }
        if(name.isEmpty())throw new IllegalArgumentException("Bitte gib einen Namen ein.");
        if(name.codePointCount(0,name.length())>60)throw new IllegalArgumentException("Bitte verwende höchstens 60 Zeichen.");
        if(name.startsWith(".")||name.endsWith(".")||name.codePoints().anyMatch(c->Character.isISOControl(c)||"/\\:*?\"<>|".indexOf(c)>=0))
            throw new IllegalArgumentException("Bitte verwende einen Namen ohne Dateipfade oder Sonderzeichen wie / : * ? < > |.");
        return name;
    }
}
