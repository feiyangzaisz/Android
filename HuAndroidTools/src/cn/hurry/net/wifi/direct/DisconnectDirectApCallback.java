package cn.hurry.net.wifi.direct;


public interface DisconnectDirectApCallback
{

    public void onDisconnected(DirectAp ap);

    public void onError(DirectAp ap, Exception e);

}
