package cn.lzy.gt7p.installerclean;

/** Preserve necessary native compatibility handling; simplify optional guide results only. */
final class ParsingPolicy {
    interface Result { Object get() throws Throwable; }

    static Object choose(Result original, Result ordinary) throws Throwable {
        Object result = original.get();
        if (result != null) {
            switch (result.getClass().getName()) {
                case "com.oplus.appdetail.modelv2.parsing.b$a":
                case "com.oplus.appdetail.modelv2.parsing.b$e":
                case "com.oplus.appdetail.modelv2.parsing.b$f":
                case "com.oplus.appdetail.modelv2.parsing.b$i": return result;
                default: break;
            }
        }
        return ordinary.get();
    }
}
