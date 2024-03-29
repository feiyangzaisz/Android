package cn.hurry.memory;

import java.util.List;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.AdapterView;
import cn.hurry.util.jlog.LogManager;

public final class MemoryManager
{

    private MemoryManager()
    {
    }

    /**
     * <p>获取指定了缩放尺寸限制的BitmapFactory.Options
     * 
     * @param options 能获得outWidth和outHeight的BitmapFactory.Options， 通常的做法是decode参数inJustDecodeBounds为true的BitmapFactory.Options来获得此参数，此时只包含了Bitmap的尺寸信息，可节约内存
     * @param minSideLength 最小的宽度或高度，不使用此限制可传-1，将只使用maxNumOfPixels来限制， 若两参数都为-1，将使用原始尺寸；若两参数都指定，将使用较小的尺寸限制
     * @param maxNumOfPixels 最大的宽度*高度，不使用此限制可传-1，将只使用minSideLength来限制， 若两参数都为-1，将使用原始尺寸；若两参数都指定，将使用较小的尺寸限制
     * @return
     */
    public static BitmapFactory.Options createSampleSizeOptions(BitmapFactory.Options options, int minSideLength, int maxNumOfPixels)
    {
        BitmapFactory.Options returnOptions = new BitmapFactory.Options();
        returnOptions.inDither = false;
        returnOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
        returnOptions.inSampleSize = computeSampleSize(options, minSideLength, maxNumOfPixels);
        return returnOptions;
    }

    /**
     * <p>获取指定了具体缩放值的BitmapFactory.Options
     * 
     * @param inSampleSize 缩放成1/inSampleSize
     * @return
     */
    public static BitmapFactory.Options createSampleSizeOptions(int inSampleSize)
    {
        BitmapFactory.Options returnOptions = new BitmapFactory.Options();
        returnOptions.inDither = false;
        returnOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
        returnOptions.inSampleSize = inSampleSize;
        return returnOptions;
    }

    /**
     * <p>获取使用系统托管的BitmapFactory.Options，系统会在内存不足时回收Bitmap并在再次需要时重新decode
     * 
     * @return
     */
    public static BitmapFactory.Options createPurgeableOptions()
    {
        BitmapFactory.Options returnOptions = new BitmapFactory.Options();
        returnOptions.inPreferredConfig = Bitmap.Config.RGB_565;
        returnOptions.inPurgeable = true;
        returnOptions.inInputShareable = true;
        return returnOptions;
    }

    /**
     * <p>获取BitmapFactory.Options使用到的inSampleSize值
     * 
     * @param options
     * @param minSideLength 最小的宽度或高度，不使用此限制可传-1，将只使用maxNumOfPixels来限制， 若两参数都为-1，将使用原始尺寸；若两参数都指定，将使用较小的尺寸限制
     * @param maxNumOfPixels 最大的宽度*高度，不使用此限制可传-1，将只使用minSideLength来限制， 若两参数都为-1，将使用原始尺寸；若两参数都指定，将使用较小的尺寸限制
     * @return
     */
    public static int computeSampleSize(BitmapFactory.Options options, int minSideLength, int maxNumOfPixels)
    {
        int initialSize = computeInitialSampleSize(options, minSideLength, maxNumOfPixels);

        int roundedSize;
        if (initialSize <= 8)
        {
            roundedSize = 1;
            while (roundedSize < initialSize)
            {
                roundedSize <<= 1;
            }
        } else
        {
            roundedSize = (initialSize + 7) / 8 * 8;
        }

        return roundedSize;
    }

    private static int computeInitialSampleSize(BitmapFactory.Options options, int minSideLength, int maxNumOfPixels)
    {
        double w = options.outWidth;
        double h = options.outHeight;

        int lowerBound = (maxNumOfPixels == -1) ? 1 : (int) Math.ceil(Math.sqrt(w * h / maxNumOfPixels));
        int upperBound = (minSideLength == -1) ? 128 : (int) Math.min(Math.floor(w / minSideLength), Math.floor(h / minSideLength));

        if (upperBound < lowerBound)
        {
            // return the larger one when there is no overlapping zone.
            return lowerBound;
        }

        if ((maxNumOfPixels == -1) && (minSideLength == -1))
        {
            return 1;
        } else if (minSideLength == -1)
        {
            return lowerBound;
        } else
        {
            return upperBound;
        }
    }

    /**
     * <p>与recycleBitmaps方法不同的是，当前方法一般用于缓存的AdapterView，作用是释放AdapterView的各项对Bitmap的引用，这样使用软引用实现的Adapter对应的Bitmap便可以被虚拟机回收 <p>释放引用的通常做法是对AdapterView的各项重新设置一个初始的、默认的Bitmap
     * <p>缓存的AdapterView在被重新布局显示时会自动调用Adapter重新渲染数据，这也就是释放引用只能针对AdapterView的原因
     * 
     * @param view
     * @param callback
     */
    public static void releaseBitmaps(AdapterView<? extends Adapter> view, MemoryManager.ReleaseBitmapsCallback callback)
    {
        int count = view.getChildCount();
        for (int i = 0; i < count; i++)
        {
            View child = view.getChildAt(i);
            if (!callback.isHeaderOrFooter(child)) // 不能包含header和footer
            {
                callback.releaseBitmaps(child);
            }
        }
    }

    public static interface ReleaseBitmapsCallback
    {
        public boolean isHeaderOrFooter(View child);

        public void releaseBitmaps(View child);
    }

    /**
     * <p>回收Bitmaps
     * 
     * @param view
     * @param callback 对select方法的实现需要注意不能包含通过resource.getDrawable得到的Bitmap，这些Bitmap只会被缓存一个实例，若回收，将会影响后续的使用
     */
    public static void recycleBitmaps(View view, MemoryManager.RecycleBitmapsCallback callback)
    {
        if (view == null || callback == null)
            throw new NullPointerException();
        if (view instanceof ViewGroup)
        {
            ViewGroup container = (ViewGroup) view;
            if (callback.isContinue(container))
            {
                for (int i = 0; i < container.getChildCount(); i++)
                {
                    View child = container.getChildAt(i);
                    recycleBitmaps(child, callback);
                }
            }
        } else
        {
            List<Bitmap> bitmaps = callback.select(view);
            if (bitmaps != null)
            {
                for (Bitmap bitmap : bitmaps)
                {
                    bitmap.recycle();
                }
            }
        }
    }

    public static interface RecycleBitmapsCallback
    {
        public boolean isContinue(ViewGroup container);

        public List<Bitmap> select(View view);
    }

    public static boolean isLowMemory(Context context)
    {
        ActivityManager actMgr = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        actMgr.getMemoryInfo(memoryInfo);
        LogManager.logI(MemoryManager.class, "Avail Mem=" + (memoryInfo.availMem >> 20) + "M");
        LogManager.logI(MemoryManager.class, "Threshold=" + (memoryInfo.threshold >> 20) + "M");
        LogManager.logI(MemoryManager.class, "Is Low Mem=" + memoryInfo.lowMemory);
        return memoryInfo.lowMemory;
    }

}
