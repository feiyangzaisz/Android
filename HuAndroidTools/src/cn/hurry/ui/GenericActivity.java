package cn.hurry.ui;

import android.app.Activity;
import android.os.Bundle;


public abstract class GenericActivity extends Activity {

    static boolean IS_RUNNING = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if(!IS_RUNNING)
        {
            onRestoreStaticState();
            IS_RUNNING = true;
        }
        super.onCreate(savedInstanceState); // 可能会触发一些用户行为，故放在恢复静态状态之后
    }

    protected abstract void onRestoreStaticState();

}
