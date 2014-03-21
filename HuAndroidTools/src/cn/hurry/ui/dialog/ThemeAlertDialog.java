package cn.hurry.ui.dialog;

import android.app.AlertDialog;
import android.content.Context;

/**
 * <p>��Ӧ��ָ������ĶԻ����ࡣ����ͨ���̳�AlertDialog����¶���ܱ����Ĺ��캯����ʵ�� <p>����ProgressDialog��Ҳ���ڴ���������ʽ�Ĺ��캯��������setView��Ч���ʶ�������ǰ���Ǳ�Ҫ��
 * 
 * 
 */
public class ThemeAlertDialog extends AlertDialog
{

    public ThemeAlertDialog(Context context)
    {
        super(context);
    }

    public ThemeAlertDialog(Context context, int theme)
    {
        super(context, theme);
    }

    public ThemeAlertDialog(Context context, boolean cancelable, OnCancelListener cancelListener)
    {
        super(context, cancelable, cancelListener);
    }

}
