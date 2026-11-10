package dev.chromamix.extra.client;
public final class RgbItemRenderContext {
 private static final ThreadLocal<Integer> RGB=new ThreadLocal<>();
 private RgbItemRenderContext(){}
 public static void set(Integer color){if(color==null)RGB.remove();else RGB.set(color);}
 public static Integer get(){return RGB.get();}
 public static void clear(){RGB.remove();}
}
