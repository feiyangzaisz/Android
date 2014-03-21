package cn.hurry.net.wifi.direct;


public interface ConnectToDirectApCallback
{

    public void onConnected(DirectAp ap,RemoteUser user);

    public void onError(DirectAp ap);

}
