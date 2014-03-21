package cn.hurry.net.wifi.direct;

import java.util.List;


public interface ScanDirectApsCallback
{

    public void onScanned(List<DirectAp> aps);

    public void onError();

}
