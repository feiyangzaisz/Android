package cn.hurry.ui.theme;

import android.os.Bundle;
import cn.hurry.ui.recreate.RecreateActivity;

public abstract class ThemeActivity extends RecreateActivity
{

    @Override
    protected void onCreateImpl(Bundle savedInstanceState)
    {
        super.onCreateImpl(savedInstanceState);
        getLayoutInflater().setFactory(ThemeFactory.createOrUpdateInstance(this, ThemeManager.CUR_PACKAGENAME, ThemeManager.CUR_GENERALTHEME_NAME));
    }

}
