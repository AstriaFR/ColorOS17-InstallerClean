package cn.lzy.gt7p.installerclean;

/** Preserve necessary native compatibility handling; simplify optional guide results only. */
final class ParsingPolicy {
    interface Result { Object get() throws Throwable; }

    static Object choose(Result original, Result ordinary) throws Throwable {
        Object result = original.get();
        if (result != null) {
            switch (result.getClass().getName()) {
                // Only known optional guide/version-prompt results may be simplified.
                case "com.oplus.appdetail.modelv2.parsing.b$c":
                case "com.oplus.appdetail.modelv2.parsing.b$d":
                case "com.oplus.appdetail.modelv2.parsing.b$h":
                case "com.oplus.appdetail.modelv2.parsing.b$j":
                case "com.oplus.appdetail.modelv2.parsing.b$k":
                case "com.oplus.appdetail.modelv2.parsing.b$l":
                case "com.oplus.appdetail.modelv2.parsing.b$m":
                case "com.oplus.appdetail.modelv2.parsing.b$n": return ordinary.get();
                default: return result;
            }
        }
        return null;
    }
}
