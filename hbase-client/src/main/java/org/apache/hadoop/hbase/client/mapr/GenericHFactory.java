package org.apache.hadoop.hbase.client.mapr;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GenericHFactory<T> {
  protected static final Map<String, Constructor<? extends Object>> CONSTRUCTOR_CACHE =
      new ConcurrentHashMap<String, Constructor<? extends Object>>();

  @SuppressWarnings("unchecked")
  public T getImplementorInstance(String className,
      Object[] params, Class<?>... classes) {
    StringBuffer suffix = new StringBuffer();
    if (classes != null && classes.length > 0) {
      for (Class<?> c : classes) {
        suffix.append("_").append(c.getName());
      }
    }

    try {
      String key = className + suffix;
      Constructor<? extends Object> method = CONSTRUCTOR_CACHE.get(key);
      if (method == null) {
        synchronized (CONSTRUCTOR_CACHE) {
          method = CONSTRUCTOR_CACHE.get(key);
          if (method == null) {
            Class<? extends T> clazz = (Class<? extends T>) Class.forName(className);
            method = (Constructor<? extends Object>) clazz.getDeclaredConstructor(classes);
            method.setAccessible(true);
            CONSTRUCTOR_CACHE.put(key, method);
          }
        }
      }
      return (T) method.newInstance(params);
    }
    catch (Throwable t) {
      throw new RuntimeException(String.format("Error occurred while instantiating %s.\n==> %s.",
        className, getMessage(t)), t);
    }
  }

  private Object getMessage(Throwable t) {
    String msg = t.getMessage();
    while ((t instanceof InvocationTargetException || t instanceof RuntimeException || msg == null)
         && t.getCause() != null && t.getCause() != t) {
      t = t.getCause();
      msg = t.toString();
    }
    return msg;
  }

  public static void handleIOException(Throwable t) throws IOException {
    Throwable ioe = t;
    while (ioe != null && !(ioe instanceof IOException)
        && ioe != ioe.getCause()) {
      ioe = ioe.getCause();
    }
    if (ioe == null || !(ioe instanceof IOException)) {
      throw new IOException (t);
    }
    throw (IOException) ioe;
  }
}
