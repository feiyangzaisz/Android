package cn.hurry.net.wifi.direct;

import java.util.List;

public interface ScanUsersCallback
{

    public void onScanned(List<RemoteUser> users);

}
