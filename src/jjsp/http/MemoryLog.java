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
package jjsp.http;

import java.io.*;
import java.util.*;
import java.net.*;

public class MemoryLog implements HTTPServerLogger 
{
    public static final int DEFAULT_LENGTH = 1024*1024;
    
    protected int pos;
    protected HTTPLogEntry[] log;

    public MemoryLog()
    {
        this(DEFAULT_LENGTH);
    }

    public MemoryLog(int maxLength)
    {
        maxLength = Math.max(128, maxLength);
        pos = 0;
        log = new HTTPLogEntry[maxLength];
    }

    public void requestProcessed(HTTPLogEntry entry)
    {
        synchronized (log)
        {
            if (pos == log.length)
            {
                int midPoint = log.length/2;
                System.arraycopy(log, midPoint, log, 0, log.length - midPoint);
                pos = midPoint;
            }
            log[pos++] = entry;
        }
    }

    public int getSize()
    {
        synchronized (log)
        {
            return pos;
        }
    }

    public HTTPLogEntry getEntry(int fromTop)
    {
        synchronized (log)
        {
            int index = pos - 1 - fromTop;
            if (index < 0)
                return null;
            return log[index];
        }
    }
}
