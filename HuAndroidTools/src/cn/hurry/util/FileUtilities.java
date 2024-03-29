package cn.hurry.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

/**
 * <p>关于文件的抽象实用类
 * 
 */
public abstract class FileUtilities
{

    /**
     * <p>通用的读取文件内容，并写入到指定输出流的方法
     * 
     * @param targetFile
     * @param output
     * @param cacheBytesLength
     * @throws IOException
     */
    public static void readFromFile(File targetFile, OutputStream output, int cacheBytesLength) throws IOException
    {
        if (targetFile == null || output == null)
            throw new NullPointerException();
        if (cacheBytesLength <= 0)
            throw new IllegalArgumentException("The parameter of cacheBytesLength should be great than zero.");
        InputStream input = null;
        try
        {
            input = new FileInputStream(targetFile);
            readAndWrite(input, output, cacheBytesLength);
        } finally
        {
            if (input != null)
                input.close();
        }
    }

    /**
     * <p>通用的将输入流内容写入文件的方法。需要指出的是，若指定的文件所在目录不存在，当前方法将尝试创建
     * 
     * @param input
     * @param targetFile
     * @param cacheBytesLength
     * @throws IOException
     */
    public static void writeToFile(InputStream input, File targetFile, int cacheBytesLength) throws IOException
    {
        if (input == null || targetFile == null)
            throw new NullPointerException();
        if (cacheBytesLength <= 0)
            throw new IllegalArgumentException("The parameter of cacheBytesLength should be great than zero.");
        OutputStream output = null;
        try
        {
            File parentFile = targetFile.getParentFile();
            if (!parentFile.exists())
                if (!parentFile.mkdirs())
                    throw new IOException("could not create the path:" + parentFile.getPath());
            output = new FileOutputStream(targetFile);
            readAndWrite(input, output, cacheBytesLength);
        } finally
        {
            if (output != null)
                output.close();
        }
    }

    /**
     * <p>读取输入流的数据写入到输出流，除非到达输入流的末尾，否则该方法将一直读取
     * 
     * @param input
     * @param output
     * @param cacheBytesLength
     * @throws IOException
     */
    public static void readAndWrite(InputStream input, OutputStream output, int cacheBytesLength) throws IOException
    {
        if (input == null || output == null)
            throw new NullPointerException();
        if (cacheBytesLength <= 0)
            throw new IllegalArgumentException("The parameter of cacheBytesLength should be great than zero.");
        BufferedInputStream buffInput = new BufferedInputStream(input);
        BufferedOutputStream buffOutput = new BufferedOutputStream(output);
        byte[] b = new byte[cacheBytesLength];
        int len;
        while ((len = buffInput.read(b)) > 0)
        {
            buffOutput.write(b, 0, len);
        }
        buffOutput.flush();
    }

    /**
     * <p>删除一个目录。该方法递归删除当前目录下的所有文件，然后再删除自己
     * 
     * @param f
     * @throws IOException
     */
    public static void delDirectory(File f) throws IOException
    {
        if (f.isDirectory())
        {// 判断是文件还是目录
            if (f.listFiles().length == 0)
            {// 若目录下没有文件则直接删除
                if (!f.delete())
                    throw new IOException("删除失败!");
            } else
            {// 若有则把文件放进数组，并判断是否有下级目录
                File[] delFile = f.listFiles();
                int i = delFile.length;
                for (int j = 0; j < i; j++)
                {
                    delDirectory(delFile[j]); // 递归调用delDirectory方法
                }
                if (!f.delete())
                    throw new IOException("删除失败!");
            }
        } else
        {
            if (!f.delete())
                throw new IOException("删除失败!");
        }
    }

    /**
     * <p>递归base，返回符合filter的File，File将以List的形式返回
     * 
     * @param base
     * @param filter
     * @param listAll 是否列出所有，false将只返回一个符合条件的File
     * @return
     */
    public static List<File> recursionFile(File base, FileFilter filter, boolean listAll)
    {
        List<File> list = new LinkedList<File>();
        if (filter == null || filter.accept(base))
        {
            list.add(base);
            if (!listAll)
                return list;
        }
        if (base != null && base.isDirectory())
        {
            File[] f = base.listFiles();
            for (int i = 0; i < f.length; i++)
            {
                List<File> subList = recursionFile(f[i], filter, listAll);
                list.addAll(subList);
                if (!listAll && list.size() > 0)
                    return list;
            }
        }
        return list;
    }

}
