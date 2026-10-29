package cn.lzy.gt7p.installerclean;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Restore only native success actions after optional remote cards were removed. */
final class FinishButtons {
    private final Method status, value, behavior, title, first, second;
    private final Constructor<?> card, button;
    private final Class<?> success;

    FinishButtons(ClassLoader loader, boolean legacy) throws ReflectiveOperationException {
        String model = "com.oplus.appdetail.model.install.viewmodel.";
        String cardType = "com.heytap.cdo.card.domain.dto.installer.InstallerResultCardDto";
        String buttonType = "com.heytap.cdo.installer.InstallerResultDto";
        status = method(loader, model + "PkgInstallViewModel", legacy ? "B" : "C");
        value = method(loader, "androidx.lifecycle.LiveData", "getValue");
        success = Class.forName(model + "c", false, loader);
        card = Class.forName(cardType, false, loader).getDeclaredConstructor();
        button = Class.forName(buttonType, false, loader).getDeclaredConstructor();
        card.setAccessible(true);
        button.setAccessible(true);
        behavior = method(loader, buttonType, "setBehavior", String.class);
        title = method(loader, buttonType, "setTitle", String.class);
        Class<?> item = Class.forName(buttonType, false, loader);
        first = method(loader, cardType, "setFirst", item);
        second = method(loader, cardType, "setSecond", item);
    }

    private static Method method(ClassLoader loader, String owner, String name, Class<?>... args)
            throws ReflectiveOperationException {
        Method result = Class.forName(owner, false, loader).getDeclaredMethod(name, args);
        result.setAccessible(true);
        return result;
    }

    Object restore(Object model, Object original) throws ReflectiveOperationException {
        if (original != null || !success.isInstance(value.invoke(status.invoke(model)))) return original;
        Object result = card.newInstance();
        first.invoke(result, action("open"));
        second.invoke(result, action("complete"));
        // Native binding supplies localized labels, checks launchability, and handles clicks.
        return result;
    }

    private Object action(String name) throws ReflectiveOperationException {
        Object result = button.newInstance();
        behavior.invoke(result, name);
        title.invoke(result, "");
        return result;
    }
}
