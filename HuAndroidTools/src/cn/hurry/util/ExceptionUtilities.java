package cn.hurry.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

/**
 * <p>����Exception�����ʵ�ó�����
 * 
 */
public abstract class ExceptionUtilities
{

    public static String getStackTrace(Throwable throwable)
    {
        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        throwable.printStackTrace(printWriter);
        return writer.toString();
    }

}
