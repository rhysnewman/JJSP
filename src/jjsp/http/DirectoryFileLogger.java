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
import java.text.*;

import jjsp.util.*;

public class DirectoryFileLogger implements HTTPServerLogger 
{
    public static final long HOURLY = 60*60*1000;
    public static final long DAILY = 24*HOURLY;
    
    private final File logDir;
    private final long logPeriod;
    private final boolean autoFlush;
    private final int maxExceptionLines;

    private long currentPeriod;
    private PrintStreamLogger currentLogger;
    
    public DirectoryFileLogger(int maxExceptionLines, long logPeriod, File logDir) throws IOException
    {
        this(maxExceptionLines, logPeriod, logDir, true);
    }

    public DirectoryFileLogger(int maxExceptionLines, long logPeriod, File logDir, boolean autoFlush) throws IOException
    {
        this.logDir = logDir;
        this.autoFlush = autoFlush;
        this.logPeriod = logPeriod;
        this.maxExceptionLines = Math.max(maxExceptionLines, 1);

        logDir.mkdir();

        currentPeriod = -1;
        currentLogger = getCurrentLogger();
    }

    protected synchronized PrintStreamLogger getCurrentLogger() throws IOException
    {
        long now = System.currentTimeMillis();
        if ((now / logPeriod) == currentPeriod)
            return currentLogger;

        currentPeriod = now / logPeriod;
        String logFileName = "HTTPLog_"+new SimpleDateFormat("yyyy-MM-dd_hh-mm-ss").format(new Date())+".log";
        try
        {
            currentLogger.close();
        }
        catch (Exception e) {}

        File ff = new File(logDir, logFileName);
        currentLogger = new PrintStreamLogger(maxExceptionLines, ff, autoFlush);
        return currentLogger;
    }
        
    public void socketException(int port, boolean isSecure, InetSocketAddress clientAddress, Throwable t) 
    {
        try
        {
            PrintStreamLogger log = getCurrentLogger();
            log.socketException(port, isSecure, clientAddress, t);
        }
        catch (Exception e)  {}
    }
    
    public void requestProcessed(HTTPLogEntry logEntry)
    {
        try
        {
            getCurrentLogger().requestProcessed(logEntry);
        }
        catch (Exception e) {}
    }

    public synchronized void close() 
    {
        try
        {
            currentLogger.close();
        }
        catch (Exception e) {}
        currentLogger = null;
    }

    public static void main(String[] args) throws Exception
    {
        HTTPServerLogger logger = new DirectoryFileLogger(10, 10000, new File("LogTEST"));
        
        byte[] raw = Utils.getAsciiBytes("GET /test.html HTTP/1.1\r\nUser-Agent: tester\r\nX-Forwarded-For:192.55.33.2\r\n\r\n");
        HTTPRequestHeaders req = new HTTPRequestHeaders();
        req.readHeadersFromStream(new ByteArrayInputStream(raw));
        
        HTTPResponseHeaders resp = new HTTPResponseHeaders();
        resp.configureAsOK();
        resp.setContentType("text/test");

        long now = System.currentTimeMillis();
        HTTPFilterChain chain = new HTTPFilterChain("link1", null);
        for (int i=0; i<100000; i++)
        {
            now += 3600*1000;
            HTTPLogEntry entry = new HTTPLogEntry(false, req.getClientIPAddress(), now, now+3*i, now+10*i, now+12*i, 20*i, 200*i, chain, req, resp);
            logger.requestProcessed(entry);

            Thread.sleep(100);

            System.out.println("LOGGED "+entry.toString());
        }
    }
}
