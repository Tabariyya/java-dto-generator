package com.tabariyya.dtogenerator.fields;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import javax.annotation.processing.ProcessingEnvironment;

/**
 * Finds the real javac environment behind whatever a build tool wrapped it in.
 *
 * <p>Both Gradle and IntelliJ's JPS hand processors a stand-in rather than javac's own
 * {@code ProcessingEnvironment}, and javac's services reject it: {@code Trees.instance} on a wrapper
 * throws {@code IllegalArgumentException}, which is a failed build for everyone using this library
 * under those tools. Each wrapper has to be unwrapped its own way.
 */
public final class ProcessingEnvironments {

    private static final String JPS_WRAPPERS = "org.jetbrains.jps.javac.APIWrappers";

    private ProcessingEnvironments() {}

    /** The environment javac itself created, or the one given if it is not wrapped. */
    public static ProcessingEnvironment unwrap(ProcessingEnvironment processingEnv) {
        ProcessingEnvironment unwrapped = throughJps(processingEnv);
        return unwrapped != null ? unwrapped : throughProxy(processingEnv);
    }

    /** JPS documents this entry point; it is on the processor's own class loader when JPS is running. */
    private static ProcessingEnvironment throughJps(ProcessingEnvironment processingEnv) {
        try {
            Class<?> wrappers = processingEnv.getClass().getClassLoader().loadClass(JPS_WRAPPERS);
            Method unwrap = wrappers.getDeclaredMethod("unwrap", Class.class, Object.class);
            Object unwrapped = unwrap.invoke(null, ProcessingEnvironment.class, processingEnv);
            return unwrapped instanceof ProcessingEnvironment ? (ProcessingEnvironment) unwrapped : null;
        } catch (Throwable notJps) {
            return null;
        }
    }

    /** Gradle wraps it in a dynamic proxy holding the real environment in a field. */
    private static ProcessingEnvironment throughProxy(ProcessingEnvironment processingEnv) {
        ProcessingEnvironment current = processingEnv;
        while (current instanceof Proxy) {
            ProcessingEnvironment delegate = delegateOf(Proxy.getInvocationHandler(current));
            if (delegate == null) {
                return processingEnv;
            }
            current = delegate;
        }
        return current;
    }

    private static ProcessingEnvironment delegateOf(InvocationHandler handler) {
        for (java.lang.reflect.Field field : handler.getClass().getDeclaredFields()) {
            if (ProcessingEnvironment.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    return (ProcessingEnvironment) field.get(handler);
                } catch (Throwable inaccessible) {
                    return null;
                }
            }
        }
        return null;
    }
}
