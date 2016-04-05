
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
