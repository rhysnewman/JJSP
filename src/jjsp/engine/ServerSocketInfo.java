/*
JJSP - Java and Javascript Server Pages 
Copyright (C) 2016 Global Travel Ventures Ltd

This program is free software: you can redistribute it and/or modify 
it under the terms of the GNU General Public License as published by 
the Free Software Foundation, either version 3 of the License, or 
(at your option) any later version.

This program is distributed in the hope that it will be useful, but 
WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
for more details.

You should have received a copy of the GNU General Public License along with 
this program. If not, see http://www.gnu.org/licenses/.
*/

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
