package cn.hurry.ui;

import android.app.AliasActivity;
import android.os.Bundle;


public abstract class GenericActionBarActivity extends AliasActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if(!GenericActivity.IS_RUNNING)
        {
            onRestoreStaticState();
            GenericActivity.IS_RUNNING = true;
        }
        super.onCreate(savedInstanceState); // 可能会触发一些用户行为，故放在恢复静态状态之后
    }

    protected abstract void onRestoreStaticState();

}
