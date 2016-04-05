
package jjsp.engine;

import java.io.*;
import java.net.*;

public class ServerSocketInfo
{
    public final int port;
    public final boolean isSecure;
    public final InetAddress ipAddress;

    public ServerSocketInfo(int port, boolean isSecure, InetAddress ip)
    {
        this.port = port;
        this.isSecure = isSecure;
        this.ipAddress = ip;
    }

    public boolean equals(Object another)
    {
        try
        {
            ServerSocketInfo ss = (ServerSocketInfo) another;
            if ((ipAddress == null) && (ss.ipAddress != null))
                return false;
            else if ((ipAddress != null) && (ss.ipAddress == null))
                return false;
            else if ((ipAddress == ss.ipAddress) || ipAddress.equals(ss.ipAddress))
                return (ss.port == port) && (isSecure == ss.isSecure);
        }
        catch (Exception e) {}
        return false;
    }

    public String toString()
    {
        String ipStr = "[ALL ADAPTERS]";
        if (ipAddress != null)
            ipStr = ipAddress.toString();

        if (isSecure)
            return port+" (SSL) IP= "+ipStr;
        else
            return port+" (Not SSL) IP= "+ipStr;
    }
}
