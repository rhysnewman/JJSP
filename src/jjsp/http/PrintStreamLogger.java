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

public class PrintStreamLogger implements HTTPServerLogger 
{
    public static final int DEFAULT_MAX_EXCEPTION_LINES = 10;

    private final PrintStream ps;
    private final int maxExceptionLines;
    private final boolean logSocketExceptions, logHTTPExceptions, autoFlush;

    public PrintStreamLogger()
    {
        this(DEFAULT_MAX_EXCEPTION_LINES);
    }

    public PrintStreamLogger(int maxExceptionLines)
    {
        this(maxExceptionLines, System.out, true, true, true);
    }

    public PrintStreamLogger(int maxExceptionLines, File ff, boolean autoFlush) throws IOException
    {
        this(ff, autoFlush, true, true);
    }

    public PrintStreamLogger(File ff, boolean autoFlush, boolean logSocketExceptions, boolean logHTTPExceptions) throws IOException
    {
        this(DEFAULT_MAX_EXCEPTION_LINES, new PrintStream(new FileOutputStream(ff, true), false), autoFlush, logSocketExceptions, logHTTPExceptions);
    }

    public PrintStreamLogger(int maxExceptionLines, File ff, boolean autoFlush, boolean logSocketExceptions, boolean logHTTPExceptions) throws IOException
    {
        this(maxExceptionLines, new PrintStream(new FileOutputStream(ff, true), false), autoFlush, logSocketExceptions, logHTTPExceptions);
    }

    public PrintStreamLogger(int maxExceptionLines, boolean logSocketExceptions, boolean logHTTPExceptions) 
    {
        this(maxExceptionLines, System.out, true, logSocketExceptions, logHTTPExceptions);
    }

    public PrintStreamLogger(boolean logSocketExceptions, boolean logHTTPExceptions) 
    {
        this(DEFAULT_MAX_EXCEPTION_LINES, System.out, true, logSocketExceptions, logHTTPExceptions);
    }

    public PrintStreamLogger(int maxExceptionLines, PrintStream ps, boolean autoFlush, boolean logSocketExceptions, boolean logHTTPExceptions) 
    {
        this.autoFlush = autoFlush;
        this.maxExceptionLines = Math.max(maxExceptionLines, 0);
        this.logSocketExceptions = logSocketExceptions;
        this.logHTTPExceptions = logHTTPExceptions;

        if (ps == null)
            this.ps = System.out;
        else
            this.ps = ps;

        this.ps.println("\n##LS "+new Date()+" Server Log Started");
        if (autoFlush)
            this.ps.flush();
    }

    public void socketException(int port, boolean isSecure, InetSocketAddress clientAddress, Throwable t) 
    {
        StringBuilder buf = new StringBuilder();
        buf.append("##SE "+new Date()+" EXCEPTION ("+port+") "+(isSecure ? "SSL" : "TCP")+" ["+clientAddress+"] "+(t != null ? t.getMessage() : "")+"\n");
        if (logSocketExceptions)
            HTTPLogEntry.appendException("##TR ", t, buf, maxExceptionLines);

        String s = buf.toString();
        synchronized (this)
        {
            ps.print(s);
            if (autoFlush)
                ps.flush();
        }
    }

    public void requestProcessed(HTTPLogEntry logEntry)
    {
        String logString = logEntry.toLogString(maxExceptionLines);
        
        synchronized (this)
        {
            ps.print(logString);
            if (autoFlush)
                ps.flush();
        }
    }

    public synchronized void close() 
    {
        try
        {
            ps.close();
        }
        catch (Exception e) {}
    }
}
