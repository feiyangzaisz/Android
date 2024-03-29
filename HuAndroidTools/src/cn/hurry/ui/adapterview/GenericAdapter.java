package cn.hurry.ui.adapterview;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.BaseAdapter;

public class GenericAdapter extends BaseAdapter
{

    Context                   mContext       = null;
    private List<DataHolder>  mHolders       = null;
    /** 是否循环显示View */
    private boolean           mIsLoopView    = false;
    /** 异步数据的执行对象 */
    private AsyncDataExecutor mExecutor      = null;
    /** View类型的个数 */
    private int               mViewTypeCount = 1;
    private Handler mHandler = new Handler();
    private ViewGroup mRunView = null;

    public GenericAdapter(Context context)
    {
        this(context, 1);
    }

    public GenericAdapter(Context context, int viewTypeCount)
    {
        if (context == null)
            throw new NullPointerException();
        if (viewTypeCount <= 0)
            throw new IllegalArgumentException("viewTypeCount should great than zero.");
        mContext = context;
        mHolders = new ArrayList<DataHolder>();
        this.mViewTypeCount = viewTypeCount;
    }

    public GenericAdapter(Context context, List<DataHolder> holders)
    {
        this(context, holders, 1);
    }

    public GenericAdapter(Context context, List<DataHolder> holders, int viewTypeCount)
    {
        if (context == null || holders == null)
            throw new NullPointerException();
        if (viewTypeCount <= 0)
            throw new IllegalArgumentException("viewTypeCount should great than zero.");
        mContext = context;
        mHolders = new ArrayList<DataHolder>(holders);
        this.mViewTypeCount = viewTypeCount;
    }

    public void bindAsyncDataExecutor(AsyncDataExecutor executor)
    {
        mExecutor = executor;
    }

    public void addDataHolder(DataHolder holder)
    {
        mHolders.add(holder);
        notifyDataSetChanged();
    }

    public void addDataHolder(int location, DataHolder holder)
    {
        if (mIsLoopView)
            location = getRealPosition(location);
        mHolders.add(location, holder);
        notifyDataSetChanged();
    }

    public void addDataHolders(List<DataHolder> holders)
    {
        mHolders.addAll(holders);
        notifyDataSetChanged();
    }

    public void addDataHolders(int location, List<DataHolder> holders)
    {
        if (mIsLoopView)
            location = getRealPosition(location);
        mHolders.addAll(location, holders);
        notifyDataSetChanged();
    }

    public void removeDataHolder(int location)
    {
        if (mIsLoopView)
            location = getRealPosition(location);
        mHolders.remove(location);
        notifyDataSetChanged();
    }

    public void removeDataHolder(DataHolder holder)
    {
        mHolders.remove(holder);
        notifyDataSetChanged();
    }

    public DataHolder queryDataHolder(int location)
    {
        if (mIsLoopView)
            location = getRealPosition(location);
        return mHolders.get(location);
    }

    public int queryDataHolder(DataHolder holder)
    {
        return mHolders.indexOf(holder);
    }

    public void clearDataHolders()
    {
        mHolders.clear();
        notifyDataSetChanged();
    }

    public void setLoopView(boolean isLoopView)
    {
        mIsLoopView = isLoopView;
        notifyDataSetChanged();
    }

    public boolean isLoopView()
    {
        return mIsLoopView;
    }

    @Override
    public final int getCount()
    {
        // TODO Auto-generated method stub
        int size = mHolders.size();
        if (size == 0)
            return size;
        if (mIsLoopView)
            return Integer.MAX_VALUE;
        else
            return size;
    }

    public int getRealCount()
    {
        return mHolders.size();
    }

    public int getRealPosition(int position)
    {
        return position % getRealCount();
    }

    public int getMiddleFirstPosition()
    {
        int realCount = getRealCount();
        if (realCount == 0)
            throw new UnsupportedOperationException("the count for adapter should not be zero");
        int middlePosition = Integer.MAX_VALUE / 2;
        while (middlePosition % realCount != 0)
        {
            middlePosition--;
        }
        return middlePosition;
    }

    @Override
    public final Object getItem(int position)
    {
        // TODO Auto-generated method stub
        return queryDataHolder(position);
    }

    @Override
    public final long getItemId(int position)
    {
        // TODO Auto-generated method stub
        return position;
    }

    @Override
    public final int getItemViewType(int position)
    {
        // TODO Auto-generated method stub
        return queryDataHolder(position).getType();
    }

    @Override
    public final int getViewTypeCount()
    {
        // TODO Auto-generated method stub
        return mViewTypeCount;
    }

    @Override
    public final View getView(int position, View convertView, final ViewGroup parent)
    {
        // TODO Auto-generated method stub
        DataHolder holder = queryDataHolder(position);
        View returnVal;
        holder.mExecuteConfig.mShouldRefresh = true;
        holder.mExecuteConfig.mUnits.clear();
        holder.mExecuteConfig.mPosition = position;
        if (convertView == null)
        {
            returnVal = holder.onCreateView(mContext, position, holder.getData());
        } else
        {
            returnVal = convertView;
            holder.onUpdateView(mContext, position, convertView, holder.getData());
        }
        if(mExecutor != null)
        {
            if(mRunView != parent)
            {
                mRunView = parent;
                final AsyncDataExecutor curExecutor = mExecutor;
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        AsyncDataManager.computeAsyncData((AdapterView<? extends Adapter>)parent,curExecutor);
                        if(mRunView == parent)
                            mRunView = null;
                    }
                },1000);
            }
        }
        return returnVal;
    }

}
