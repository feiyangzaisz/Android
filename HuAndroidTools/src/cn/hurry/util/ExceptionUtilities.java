package cn.hurry.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

/**
 * <p>关于Exception方面的实用抽象类
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
