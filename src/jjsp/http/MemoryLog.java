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
