package cn.hurry.net.wifi.direct;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import android.content.Context;
import android.os.Handler;
import cn.hurry.telephony.TelephonyMgr;
import cn.hurry.util.MathUtilities;
import cn.hurry.util.StringUtilities;
import cn.hurry.util.jlog.LogManager;

public abstract class RemoteCallback implements Runnable
{

    Context appContext = null;
    private Selector    selector         = null;
    private List<Runnable> runnables = new LinkedList<Runnable>();
    private Handler     handler          = new Handler();

    public RemoteCallback(Context context)
    {
        appContext = context.getApplicationContext();
    }

    void bindSelector(Selector selector)
    {
        if (selector == null)
            throw new NullPointerException();
        if (this.selector != null)
            throw new UnsupportedOperationException("bindSelector(Selector) can be called only once.");
        this.selector = selector;
    }

    void post(Runnable runnable)
    {
        synchronized (runnables)
        {
            runnables.add(runnable);
        }
    }

    @Override
    public final void run()
    {
        while (true)
        {
            if (selector == null)
                return;

            List<Runnable> curRunnables = new LinkedList<Runnable>();
            synchronized (runnables)
            {
                Iterator<Runnable> iterator = runnables.iterator();
                while(iterator.hasNext())
                {
                    curRunnables.add(iterator.next());
                    iterator.remove();
                }
            }
            for(Runnable runnable:curRunnables)
            {
                runnable.run();
            }
            int readyCount = 0;
            try
            {
                readyCount = selector.select(300);
            } catch (IOException e)
            {
                LogManager.logW(RemoteCallback.class, "running has been stopped.", e);
                return;
            } catch (ClosedSelectorException e)
            {
                LogManager.logW(RemoteCallback.class, "running has been stopped.", e);
                return;
            }
            if (readyCount <= 0)
                continue;
            Set<SelectionKey> keys = null;
            try
            {
                keys = selector.selectedKeys();
            } catch (ClosedSelectorException e)
            {
                LogManager.logW(RemoteCallback.class, "running has been stopped.", e);
                return;
            }
            Iterator<SelectionKey> iter = keys.iterator();
            while (iter.hasNext())
            {
                try
                {
                    SelectionKey key = iter.next();
                    iter.remove(); // 当前事件要从keys中删去
                    if (key.isAcceptable())
                    {
                        Object[] objs = (Object[]) key.attachment();
                        SocketChannel sc = null;
                        try
                        {
                            sc = ((ServerSocketChannel) key.channel()).accept();
                            sc.configureBlocking(false);
                            sc.register(selector, SelectionKey.OP_READ, new Object[] { null, "length", ByteBuffer.allocate(4), objs[0] });
                        } catch (Exception e)
                        {
                            try
                            {
                                if (sc != null)
                                    sc.close();
                            } catch (IOException e1)
                            {
                                LogManager.logE(RemoteCallback.class, "close socket channel failed.", e1);
                            }
                            LogManager.logE(RemoteCallback.class, "handle accept socket channel failed.", e);
                        }
                    } else if (key.isConnectable())
                    {
                        Object[] objs = (Object[]) key.attachment();
                        if (objs[1].equals("connect"))
                        {
                            final RemoteUser user = (RemoteUser) objs[0];
                            SocketChannel sc = null;
                            try
                            {
                                sc = (SocketChannel) key.channel();
                                if (sc.isConnectionPending())
                                    sc.finishConnect();
                            } catch (final IOException e)
                            {
                                try
                                {
                                    key.cancel();
                                    if (sc != null)
                                        sc.close();
                                } catch (IOException e1)
                                {
                                    LogManager.logE(RemoteCallback.class, "close socket channel failed.", e1);
                                }
                                user.state = 1;
                                handler.post(new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        onConnectedFailed(user, e);
                                    }
                                });
                                continue;
                            }
                            key.attach(new Object[] { user, "info_send", objs[2] });
                            key.interestOps(SelectionKey.OP_WRITE);
                        } else if (objs[1].equals("transfer_connect"))
                        {
                            RemoteUser user = (RemoteUser) objs[0];
                            final TransferEntity transfer = (TransferEntity) objs[2];
                            SocketChannel sc = null;
                            try
                            {
                                sc = (SocketChannel) key.channel();
                                if (sc.isConnectionPending())
                                    sc.finishConnect();
                            } catch (final IOException e)
                            {
                                try
                                {
                                    user.getTransfers().remove(transfer);
                                    key.cancel();
                                    if (sc != null)
                                        sc.close();
                                } catch (IOException e1)
                                {
                                    LogManager.logE(RemoteCallback.class, "close socket channel failed.", e1);
                                }
                                transfer.state = 1;
                                handler.post(new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        onTransferFailed(transfer, e);
                                    }
                                });
                                continue;
                            }
                            key.attach(new Object[] { user, "transfer_send", transfer });
                            key.interestOps(SelectionKey.OP_WRITE);
                        }
                    } else if (key.isReadable())
                    {
                        SelectableChannel keyChannel = key.channel();
                        if(keyChannel instanceof DatagramChannel)
                        {
                            DatagramChannel dc = (DatagramChannel)keyChannel;
                            Object[] objs = (Object[]) key.attachment();
                            ByteBuffer buff = null;
                            SocketAddress addr = null;
                            try
                            {
                                buff = ByteBuffer.allocate(3 * User.MAX_NAME_LENGTH);
                                addr = dc.receive(buff);
                            }catch (IOException e)
                            {
                                LogManager.logE(RemoteCallback.class, "receiving remote failed.", e);
                                continue;
                            }
                            if(dc.socket().getLocalPort() != User.LISTENING_PORT_UDP) // 排除定时发送数据包的DatagramChannel意外收到数据包的情况
                                continue;
                            String ip = ((InetSocketAddress)addr).getAddress().getHostAddress();
                            List<String> localIps = getLocalIpAddress();
                            if(localIps.contains(ip)) // 发送给自己的广播要忽略
                                continue;
                            buff.flip();
                            String name = null;
                            try
                            {
                                name = Charset.forName("UTF-8").newDecoder().decode(buff).toString();
                            } catch (CharacterCodingException e)
                            {
                                LogManager.logE(RemoteCallback.class, "decode remote name failed.", e);
                                continue;
                            }
                            RemoteUser curUser = new RemoteUser(name);
                            curUser.setIp(ip);
                            curUser.setRefreshTime(System.currentTimeMillis());
                            curUser.state = 1;
                            List<RemoteUser> users = ((User)objs[0]).scanUsers;
                            synchronized (users)
                            {
                                users.remove(curUser);
                                users.add(curUser);
                            }
                        }else
                        {
                            SocketChannel sc = (SocketChannel) keyChannel;
                            Object[] objs = (Object[]) key.attachment();
                            if (objs[2] instanceof ByteBuffer)
                            {
                                final RemoteUser remoteUser = (RemoteUser) objs[0];
                                ByteBuffer bb = (ByteBuffer) objs[2];
                                User user = (User)objs[3];
                                int len = 0;
                                try
                                {
                                    len = sc.read(bb);
                                } catch (IOException e)
                                {
                                    LogManager.logE(RemoteCallback.class, "reading remote failed.", e);
                                }
                                if (len == -1)
                                {
                                    try
                                    {
                                        key.cancel();
                                        sc.close();
                                    } catch (IOException e)
                                    {
                                        LogManager.logE(RemoteCallback.class, "close remote user failed when remote is close.", e);
                                    }
                                    if(remoteUser != null)
                                    {
                                        remoteUser.state = 1;
                                        user.connUsers.remove(remoteUser);
                                        handler.post(new Runnable()
                                        {
                                            @Override
                                            public void run()
                                            {
                                                onDisconnected(remoteUser);
                                            }
                                        });
                                    }
                                    continue;
                                }
                                if (!bb.hasRemaining())
                                {
                                    bb.flip();
                                    if (objs[1].equals("length"))
                                    {
                                        key.attach(new Object[] { remoteUser, "content", ByteBuffer.allocate(bb.getInt()), user });
                                    } else if (objs[1].equals("content"))
                                    {
                                        String content = null;
                                        try
                                        {
                                            content = Charset.forName("UTF-8").newDecoder().decode(bb).toString();
                                        } catch (CharacterCodingException e)
                                        {
                                            LogManager.logE(RemoteCallback.class, "decode remote content failed.", e);
                                            key.attach(new Object[] { remoteUser, "length", ByteBuffer.allocate(4), user });
                                            continue;
                                        }
                                        String[] contentArr = StringUtilities.parseFromCSV(content);
                                        if (contentArr[0].equals("info_send"))
                                        {
                                            final RemoteUser remote = new RemoteUser(contentArr[1]);
                                            remote.setIp(((InetSocketAddress) sc.socket().getRemoteSocketAddress()).getAddress().getHostAddress());
                                            remote.setKey(key);
                                            remote.state = 2;
                                            int index = user.connUsers.indexOf(remote);
                                            if(index != -1)
                                            {
                                                RemoteUser old = user.connUsers.get(index);
                                                user.disconnectUser(old);
                                            }
                                            user.connUsers.add(remote);
                                            handler.post(new Runnable()
                                            {
                                                @Override
                                                public void run()
                                                {
                                                    onConnected(remote);
                                                }
                                            });
                                            key.attach(new Object[] { remote, "length", ByteBuffer.allocate(4), user });
                                        } else if (contentArr[0].equals("message"))
                                        {
                                            if (remoteUser != null)
                                            {
                                                final String msgType = contentArr[1];
                                                final String msg = contentArr[2];
                                                handler.post(new Runnable()
                                                {
                                                    @Override
                                                    public void run()
                                                    {
                                                        onMessageOK(remoteUser,false,msgType,msg);
                                                    }
                                                });
                                            }
                                            key.attach(new Object[] { remoteUser, "length", ByteBuffer.allocate(4), user });
                                        } else if (contentArr[0].equals("transfer_send"))
                                        {
                                            String addr = ((InetSocketAddress) sc.socket().getRemoteSocketAddress()).getAddress().getHostAddress();
                                            RemoteUser queryUser = new RemoteUser("test");
                                            queryUser.setIp(addr);
                                            queryUser.state = 1;
                                            int index = user.connUsers.indexOf(queryUser);
                                            if(index == -1)
                                            {
                                                try
                                                {
                                                    key.cancel();
                                                    sc.close();
                                                }catch (IOException e)
                                                {
                                                    LogManager.logE(RemoteCallback.class, "close socket channel failed.", e);
                                                }
                                            }else
                                            {
                                                queryUser = user.connUsers.get(index);
                                                final TransferEntity transfer = new TransferEntity();
                                                transfer.setRemoteUser(queryUser);
                                                transfer.setSendPath(contentArr[1]);
                                                transfer.setSize(Long.parseLong(contentArr[2]));
                                                transfer.setSender(false);
                                                if (contentArr.length == 3)
                                                    transfer.setExtraDescription(null);
                                                else
                                                    transfer.setExtraDescription(contentArr[3]);
                                                transfer.setSavingPath(onGetSavingPathInBackground(queryUser, transfer.getSendPath(), transfer.getSize(), transfer.getExtraDescription()));
                                                transfer.setSelectionKey(key);
                                                transfer.state = 0;
                                                queryUser.getTransfers().add(transfer);
                                                handler.post(new Runnable()
                                                {
                                                    @Override
                                                    public void run()
                                                    {
                                                        onTransferProgress(transfer, 0);
                                                    }
                                                });
                                                try
                                                {
                                                    File file = new File(transfer.getSavingPath());
                                                    File parentPath = file.getParentFile();
                                                    if (parentPath != null)
                                                    {
                                                        if (!parentPath.exists())
                                                            if (!parentPath.mkdirs())
                                                                throw new IOException("can not create saving path.");
                                                        if (TelephonyMgr.getFileStorageAvailableSize(parentPath) < transfer.getSize())
                                                            throw new SpaceNotEnoughException();
                                                    }
                                                    key.attach(new Object[] { queryUser, "transfer_progress", transfer, ByteBuffer.allocate(2 * 1024), new FileOutputStream(file).getChannel(), 0, 0 });
                                                } catch (final IOException e)
                                                {
                                                    try
                                                    {
                                                        queryUser.getTransfers().remove(transfer);
                                                        key.cancel();
                                                        sc.close();
                                                    } catch (IOException e1)
                                                    {
                                                        LogManager.logE(RemoteCallback.class, "close socket channel failed.", e1);
                                                    }
                                                    transfer.state = 1;
                                                    handler.post(new Runnable()
                                                    {
                                                        @Override
                                                        public void run()
                                                        {
                                                            onTransferFailed(transfer, e);
                                                        }
                                                    });
                                                }
                                            }
                                        }
                                    }
                                }
                            } else if (objs[2] instanceof TransferEntity)
                            {
                                if (objs[1].equals("transfer_progress"))
                                {
                                    final TransferEntity transfer = (TransferEntity) objs[2];
                                    ByteBuffer cache = (ByteBuffer) objs[3];
                                    FileChannel channel = (FileChannel) objs[4];
                                    try
                                    {
                                        int len = sc.read(cache);
                                        long curSize = Long.parseLong(String.valueOf(objs[5]));
                                        curSize = curSize + cache.position();
                                        if (curSize >= transfer.getSize())
                                        {
                                            cache.flip();
                                            channel.write(cache);
                                            try
                                            {
                                                channel.close();
                                            } catch (IOException e)
                                            {
                                                LogManager.logE(RemoteCallback.class, " close file channel failed.", e);
                                            }
                                            try
                                            {
                                                transfer.getRemoteUser().getTransfers().remove(transfer);
                                                key.cancel();
                                                sc.close();
                                            } catch (IOException e)
                                            {
                                                LogManager.logE(RemoteCallback.class, "close socket channel failed.", e);
                                            }
                                            transfer.state = 1;
                                            handler.post(new Runnable()
                                            {
                                                @Override
                                                public void run()
                                                {
                                                    onTransferProgress(transfer, 100);
                                                }
                                            });
                                            continue;
                                        }
                                        if (len == -1)
                                        {
                                            try
                                            {
                                                channel.close();
                                            } catch (IOException e)
                                            {
                                                LogManager.logE(RemoteCallback.class, "close file channel failed.", e);
                                            }
                                            try
                                            {
                                                transfer.getRemoteUser().getTransfers().remove(transfer);
                                                key.cancel();
                                                sc.close();
                                            } catch (IOException e)
                                            {
                                                LogManager.logE(RemoteCallback.class, "close socket channel failed.", e);
                                            }
                                            transfer.state = 1;
                                            handler.post(new Runnable()
                                            {
                                                @Override
                                                public void run()
                                                {
                                                    onTransferFailed(transfer, new RuntimeException("remote is closed."));
                                                }
                                            });
                                            continue;
                                        }
                                        if (!cache.hasRemaining())
                                        {
                                            cache.flip();
                                            channel.write(cache);
                                            int lastPublishProgress = Integer.parseInt(String.valueOf(objs[6]));
                                            final int curProgress = (int) MathUtilities.mul(MathUtilities.div(curSize, transfer.getSize(), 2), 100);
                                            if(curProgress < 100) // 由于四舍五入的关系，未传输完时可能也为100
                                            {
                                                if (curProgress - lastPublishProgress >= 5)
                                                {
                                                    lastPublishProgress = curProgress;
                                                    handler.post(new Runnable()
                                                    {
                                                        @Override
                                                        public void run()
                                                        {
                                                            onTransferProgress(transfer, curProgress);
                                                        }
                                                    });
                                                }
                                            }
                                            key.attach(new Object[] { objs[0], "transfer_progress", transfer, ByteBuffer.allocate(2 * 1024), channel, curSize, lastPublishProgress });
                                        }
                                    } catch (final IOException e)
                                    {
                                        try
                                        {
                                            channel.close();
                                        } catch (IOException e1)
                                        {
                                            LogManager.logE(RemoteCallback.class, " close file channel failed.", e1);
                                        }
                                        try
                                        {
                                            transfer.getRemoteUser().getTransfers().remove(transfer);
                                            key.cancel();
                                            sc.close();
                                        } catch (IOException e1)
                                        {
                                            LogManager.logE(RemoteCallback.class, "close socket channel failed.", e1);
                                        }
                                        transfer.state = 1;
                                        handler.post(new Runnable()
                                        {
                                            @Override
                                            public void run()
                                            {
                                                onTransferFailed(transfer, e);
                                            }
                                        });
                                    }
                                }
                            }
                        }
                    } else if (key.isWritable())
                    {
                        SelectableChannel keyChannel = key.channel();
                        if(keyChannel instanceof DatagramChannel)
                        {
                            DatagramChannel dc = (DatagramChannel)keyChannel;
                            Object[] objs = (Object[]) key.attachment();
                            User user = (User)objs[0];
                            try
                            {
                                ByteBuffer buff = ByteBuffer.allocate(3 * User.MAX_NAME_LENGTH);
                                buff.put(user.getName().getBytes("UTF-8"));
                                buff.flip();
                                dc.send(buff,new InetSocketAddress(InetAddress.getByName("255.255.255.255"),User.LISTENING_PORT_UDP));
                            }catch (IOException e)
                            {
                                LogManager.logE(RemoteCallback.class,"send udp message for scanning user failed.",e);
                            }finally
                            {
                                key.interestOps(SelectionKey.OP_READ);
                            }
                        }else
                        {
                            SocketChannel sc = (SocketChannel) keyChannel;
                            final Object[] objs = (Object[]) key.attachment();
                            if (objs[1].equals("info_send"))
                            {
                                final RemoteUser remoteUser = (RemoteUser) objs[0];
                                try
                                {
                                    ByteBuffer sendBuff = null;
                                    User user = null;
                                    if (objs[2] instanceof ByteBuffer)
                                    {
                                        sendBuff = (ByteBuffer) objs[2];
                                        user = (User) objs[3];
                                    } else
                                    {
                                        user = (User) objs[2];
                                        String msg = StringUtilities.concatByCSV(new String[] { "info_send", user.getName() });
                                        byte[] msgByte = msg.getBytes("UTF-8");
                                        sendBuff = ByteBuffer.allocate(4 + msgByte.length);
                                        sendBuff.putInt(msgByte.length);
                                        sendBuff.put(msgByte);
                                        sendBuff.flip();
                                    }
                                    sc.write(sendBuff);
                                    if (sendBuff.hasRemaining())
                                    {
                                        key.attach(new Object[] { objs[0], "info_send", sendBuff, user });
                                    } else
                                    {
                                        key.attach(new Object[] { objs[0], "length", ByteBuffer.allocate(4), user });
                                        key.interestOps(SelectionKey.OP_READ);
                                        remoteUser.setKey(key);
                                        remoteUser.state = 2;
                                        user.connUsers.add(remoteUser);
                                        handler.post(new Runnable()
                                        {
                                            @Override
                                            public void run()
                                            {
                                                onConnected(remoteUser);
                                            }
                                        });
                                    }
                                } catch (final IOException e)
                                {
                                    try
                                    {
                                        key.cancel();
                                        sc.close();
                                    } catch (IOException e1)
                                    {
                                        LogManager.logE(RemoteCallback.class, "close socket channel failed.", e1);
                                    }
                                    remoteUser.state = 1;
                                    handler.post(new Runnable()
                                    {
                                        @Override
                                        public void run()
                                        {
                                            onConnectedFailed(remoteUser, e);
                                        }
                                    });
                                }
                            } else if (objs[1].equals("message"))
                            {
                                try
                                {
                                    ByteBuffer sendBuff = null;
                                    if (objs.length == 6)
                                    {
                                        sendBuff = (ByteBuffer) objs[5];
                                    } else
                                    {
                                        String msg = StringUtilities.concatByCSV(new String[] { "message", (String)objs[2], (String)objs[3] });
                                        byte[] msgByte = msg.getBytes("UTF-8");
                                        sendBuff = ByteBuffer.allocate(4 + msgByte.length);
                                        sendBuff.putInt(msgByte.length);
                                        sendBuff.put(msgByte);
                                        sendBuff.flip();
                                    }
                                    sc.write(sendBuff);
                                    if (sendBuff.hasRemaining())
                                    {
                                        key.attach(new Object[] { objs[0], objs[1], objs[2], objs[3], objs[4], sendBuff });
                                    } else
                                    {
                                        key.attach(new Object[] { objs[0], "length", ByteBuffer.allocate(4), objs[4] });
                                        key.interestOps(SelectionKey.OP_READ);
                                        handler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                onMessageOK((RemoteUser)objs[0],true,(String)objs[2],(String)objs[3]);
                                            }
                                        });
                                    }
                                } catch (final IOException e)
                                {
                                    key.attach(new Object[] { objs[0], "length", ByteBuffer.allocate(4), objs[4] });
                                    key.interestOps(SelectionKey.OP_READ);
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            onMessageFailed((RemoteUser)objs[0],true,(String)objs[2],(String)objs[3],e);
                                        }
                                    });
                                }
                            } else if (objs[1].equals("transfer_send"))
                            {
                                TransferEntity transfer = null;
                                try
                                {
                                    ByteBuffer sendBuff = null;
                                    if (objs[2] instanceof ByteBuffer)
                                    {
                                        sendBuff = (ByteBuffer) objs[2];
                                        transfer = (TransferEntity) objs[3];
                                    } else
                                    {
                                        transfer = (TransferEntity) objs[2];
                                        String msg = null;
                                        String extraDescription = transfer.getExtraDescription();
                                        if (extraDescription == null)
                                            msg = StringUtilities.concatByCSV(new String[] { "transfer_send", transfer.getSendPath(), String.valueOf(transfer.getSize()) });
                                        else
                                            msg = StringUtilities.concatByCSV(new String[] { "transfer_send", transfer.getSendPath(), String.valueOf(transfer.getSize()), extraDescription });
                                        byte[] msgByte = msg.getBytes("UTF-8");
                                        sendBuff = ByteBuffer.allocate(4 + msgByte.length);
                                        sendBuff.putInt(msgByte.length);
                                        sendBuff.put(msgByte);
                                        sendBuff.flip();
                                    }
                                    sc.write(sendBuff);
                                    if (sendBuff.hasRemaining())
                                    {
                                        key.attach(new Object[] { objs[0], "transfer_send", sendBuff, transfer });
                                    } else
                                    {
                                        key.attach(new Object[] { objs[0], "transfer_progress", transfer, new FileInputStream(transfer.getSendPath()).getChannel(), 0, 0 });
                                    }
                                } catch (final IOException e)
                                {
                                    try
                                    {
                                        transfer.getRemoteUser().getTransfers().remove(transfer);
                                        key.cancel();
                                        sc.close();
                                    } catch (IOException e1)
                                    {
                                        LogManager.logE(RemoteCallback.class, "close socket channel failed.", e1);
                                    }
                                    final TransferEntity transferPoint = transfer;
                                    transferPoint.state = 1;
                                    handler.post(new Runnable()
                                    {
                                        @Override
                                        public void run()
                                        {
                                            onTransferFailed(transferPoint, e);
                                        }
                                    });
                                }
                            } else if (objs[1].equals("transfer_progress"))
                            {
                                TransferEntity transfer = null;
                                FileChannel channel = null;
                                long curSize;
                                int lastPublishProgress;
                                try
                                {
                                    ByteBuffer sendBuff = null;
                                    if (objs[2] instanceof ByteBuffer)
                                    {
                                        sendBuff = (ByteBuffer) objs[2];
                                        transfer = (TransferEntity) objs[3];
                                        channel = (FileChannel) objs[4];
                                        curSize = Long.parseLong(String.valueOf(objs[5]));
                                        lastPublishProgress = Integer.parseInt(String.valueOf(objs[6]));
                                    } else
                                    {
                                        transfer = (TransferEntity) objs[2];
                                        channel = (FileChannel) objs[3];
                                        curSize = Long.parseLong(String.valueOf(objs[4]));
                                        lastPublishProgress = Integer.parseInt(String.valueOf(objs[5]));
                                        sendBuff = ByteBuffer.allocate(2 * 1024);
                                        int len = channel.read(sendBuff);
                                        if (len == -1)
                                        {
                                            try
                                            {
                                                transfer.getRemoteUser().getTransfers().remove(transfer);
                                                key.cancel();
                                                sc.close();
                                            } catch (IOException e)
                                            {
                                                LogManager.logE(RemoteCallback.class, "close socket channel failed.", e);
                                            }
                                            try
                                            {
                                                channel.close();
                                            } catch (IOException e)
                                            {
                                                LogManager.logE(RemoteCallback.class, "close file channel failed.", e);
                                            }
                                            final TransferEntity transferPoint = transfer;
                                            transferPoint.state = 1;
                                            handler.post(new Runnable()
                                            {
                                                @Override
                                                public void run()
                                                {
                                                    onTransferProgress(transferPoint, 100);
                                                }
                                            });
                                            continue;
                                        }
                                        sendBuff.flip();
                                    }
                                    sc.write(sendBuff);
                                    if (sendBuff.hasRemaining())
                                    {
                                        key.attach(new Object[] { objs[0], "transfer_progress", sendBuff, transfer, channel, curSize, lastPublishProgress });
                                    } else
                                    {
                                        curSize = curSize + sendBuff.position();
                                        final int curProgress = (int) MathUtilities.mul(MathUtilities.div(curSize, transfer.getSize(), 2), 100);
                                        if(curProgress < 100) // 可能已经传输完成或由于四舍五入导致为100，但将在上面统一判断
                                        {
                                            if (curProgress - lastPublishProgress >= 5)
                                            {
                                                lastPublishProgress = curProgress;
                                                final TransferEntity transferPoint = transfer;
                                                handler.post(new Runnable()
                                                {
                                                    @Override
                                                    public void run()
                                                    {
                                                        onTransferProgress(transferPoint, curProgress);
                                                    }
                                                });
                                            }
                                        }
                                        key.attach(new Object[] { objs[0], "transfer_progress", transfer, channel, curSize, lastPublishProgress });
                                    }
                                } catch (final IOException e)
                                {
                                    try
                                    {
                                        transfer.getRemoteUser().getTransfers().remove(transfer);
                                        key.cancel();
                                        sc.close();
                                    } catch (IOException e1)
                                    {
                                        LogManager.logE(RemoteCallback.class, "close socket channel failed.", e1);
                                    }
                                    try
                                    {
                                        channel.close();
                                    } catch (IOException e1)
                                    {
                                        LogManager.logE(RemoteCallback.class, "close file channel failed.", e1);
                                    }
                                    final TransferEntity transferPoint = transfer;
                                    transferPoint.state = 1;
                                    handler.post(new Runnable()
                                    {
                                        @Override
                                        public void run()
                                        {
                                            onTransferFailed(transferPoint, e);
                                        }
                                    });
                                }
                            }
                        }
                    }
                } catch (RuntimeException e) // 最外层只用于捕获未知异常，以防止异常导致循环退出
                {
                    LogManager.logE(RemoteCallback.class, "deal cur selection key failed,try next one...", e);
                }
            }
        }
    }

    private List<String> getLocalIpAddress() {
        List<String> returnVal = new LinkedList<String>();
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (inetAddress.isSiteLocalAddress()) {
                        returnVal.add(inetAddress.getHostAddress());
                    }
                }
            }
        } catch (SocketException ex) {
            LogManager.logE(RemoteCallback.class,"get local ip failed.",ex);
        }
        return returnVal;
    }

    public abstract void onConnected(RemoteUser user);

    public abstract void onConnectedFailed(RemoteUser user, Exception e);

    public abstract void onDisconnected(RemoteUser user);

    public abstract void onDisconnectedFailed(RemoteUser user, Exception e);

    public abstract void onMessageOK(RemoteUser user,boolean isSender,String msgType,String msg);

    public abstract void onMessageFailed(RemoteUser user,boolean isSender,String msgType,String msg,Exception e);

    public abstract String onGetSavingPathInBackground(RemoteUser user, String sendPath, long size, String extraDescription);

    /**
     * @param transfer 如果是发送文件的话，transfer.getSavingPath()返回的值为null
     * @param progress 肯定会传入0和100用以给外部初始化下载和结束下载提供入口
     */
    public abstract void onTransferProgress(TransferEntity transfer, int progress);

    /**
     * @param transfer 如果是发送文件的话，transfer.getSavingPath()返回的值为null
     * @param e
     */
    public abstract void onTransferFailed(TransferEntity transfer, Exception e);

}
