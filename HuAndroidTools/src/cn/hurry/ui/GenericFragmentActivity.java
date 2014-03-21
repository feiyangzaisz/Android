package cn.hurry.ui;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

public abstract class GenericFragmentActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if(!GenericActivity.IS_RUNNING)
        {
            onRestoreStaticState();
            GenericActivity.IS_RUNNING = true;
        }
        super.onCreate(savedInstanceState); // ���ܻᴥ��һЩ�û���Ϊ���ʷ��ڻָ���̬״̬֮��
    }

    protected abstract void onRestoreStaticState();

}
