package jjsp.util;

import java.net.*;
import java.util.*;
import java.io.*;

public class MyIPAddress
{
    private static final String[] defaultNetworkAdapterExclude = {"Virtual"};

    public static InetAddress getLocalHost() throws UnknownHostException
    {
        return getLocalhost();
    }

    public static InetAddress getLocalhost() throws UnknownHostException
    {
        InetAddress addr = getAddress(defaultNetworkAdapterExclude, true);
        if (addr != null)
            return addr;
        return getAddress(defaultNetworkAdapterExclude, false);
    }

    public static InetAddress getAddress() throws UnknownHostException
    {
        InetAddress addr = getAddress(defaultNetworkAdapterExclude, false);
        if (addr != null)
            return addr;
        return getAddress(defaultNetworkAdapterExclude, true);
    }
    
    public static InetAddress getAddress(String[] nameExclude, boolean loopbackOnly) throws UnknownHostException
    {
        try
        {
            Enumeration ifs = NetworkInterface.getNetworkInterfaces();
            outerloop: 
            while (ifs.hasMoreElements())
            {
                NetworkInterface nif = (NetworkInterface) ifs.nextElement();
                if (loopbackOnly && !nif.isLoopback())
                    continue;
                else if (!loopbackOnly && nif.isLoopback())
                    continue;

                if (!nif.isUp() || nif.isVirtual())
                    continue;
                
                if (nameExclude != null)
                {
                    String name = nif.getName().toLowerCase();
                    String displayName = nif.getDisplayName().toLowerCase();

                    for (int i=0; i<nameExclude.length; i++)
                    {
                        if (name.indexOf(nameExclude[i].toLowerCase()) >= 0)
                            continue outerloop;
                        if (displayName.indexOf(nameExclude[i].toLowerCase()) >= 0)
                            continue outerloop;
                    }
                }

                try
                {
                    Enumeration addrs = nif.getInetAddresses();
                    while (addrs.hasMoreElements())
                    {
                        InetAddress addr = (InetAddress) addrs.nextElement();
                        if (addr == null)
                            continue;
                        if (!(addr instanceof Inet4Address))
                            continue;
                        return addr;
                    }
                }
                catch (Exception e) {}
            }
        }
        catch (Exception e) {}

        return InetAddress.getLocalHost();
    }
}
