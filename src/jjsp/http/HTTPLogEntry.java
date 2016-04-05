package jjsp.http;

import java.io.*;
import java.util.*;
import java.net.*;

import jjsp.util.*;

public class HTTPLogEntry 
{
    public final String SESSION_ID = "SID";

    public final boolean isSecure;
    public final String clientAddress;
    public final Map reqHeaders, respHeaders;
    public final HTTPFilterChain filterChain;
    public final long requestReceived, processStart, responseStart, responseSent, bytesRead, bytesWritten;

    public HTTPLogEntry(boolean isSecure, String clientAddress, long requestReceived, long processStart, long responseStart, long responseSent, long bytesRead, long bytesWritten, HTTPFilterChain filterChain, HTTPRequestHeaders req, HTTPResponseHeaders resp)
    {
        this.isSecure = isSecure;
        this.clientAddress = clientAddress;
        this.requestReceived = requestReceived;
        this.processStart = processStart;
        this.responseStart = responseStart;
        this.responseSent = responseSent;
        this.bytesRead = bytesRead;
        this.bytesWritten = bytesWritten;
        this.filterChain = filterChain;

        reqHeaders = req.toMap();
        respHeaders = resp.toMap();
    }

    public String getSessionID()
    {
        String id = (String) respHeaders.get("Set-Cookie:"+SESSION_ID);
        if (id != null)
            return id;
        String cookies = (String) respHeaders.get("Cookie");
        if (cookies == null)
            return null;
        
        int idx = cookies.indexOf(SESSION_ID);
        if (idx < 0)
            return null;
        int eq = cookies.indexOf("=", idx);
        int sc = cookies.indexOf(";", idx);
        if ((eq < 0) || (sc < 0))
            return null;
        return cookies.substring(eq+1, sc).trim();
    }

    public String getRequestMainLine()
    {
        return (String) reqHeaders.get(HTTPHeaders.MAIN_LINE);
    }

    public String getResponseMainLine()
    {
        return (String) respHeaders.get(HTTPHeaders.MAIN_LINE);
    }

    public long totalRequestTime()
    {
        return Math.max(responseSent - requestReceived, 0);
    }

    public long requestReadTime()
    {
        return Math.max(0, processStart - requestReceived);
    }

    public long responseTime()
    {
        return Math.max(responseStart - processStart, 0);
    }

    public long responseWriteTime()
    {
        return Math.max(responseSent - responseStart, 0);
    }

    public String toString()
    {
        return clientAddress+" "+totalRequestTime()+"ms "+filterChain.getFullReport()+"  "+reqHeaders.get(HTTPHeaders.MAIN_LINE)+" -> "+respHeaders.get(HTTPHeaders.MAIN_LINE);
    }

    public String toJSON()
    {
        StringBuffer buf = new StringBuffer("{");
        buf.append("\"requestDate\":"+requestReceived+",");
        buf.append("\"totalTime\":"+totalRequestTime()+",");
        buf.append("\"requestReadTime\":"+requestReadTime()+",");
        buf.append("\"responseTime\":"+responseTime()+",");
        buf.append("\"responseWriteTime\":"+responseWriteTime()+",");
        buf.append("\"address\":\""+clientAddress+"\",");
        buf.append("\"req\":"+JSONParser.toString(reqHeaders)+",");
        buf.append("\"resp\":"+JSONParser.toString(respHeaders)+",");
        buf.append("\"chain\":"+filterChain.toJSON());
        buf.append("}");
        return buf.toString();
    }

    public static void appendException(String linePrefix, Throwable t, StringBuilder buf, int maxExceptionLines)
    {
        if (t == null)
            return;

        int lineCount = 0;
        for (Throwable tt = t; tt != null; tt = tt.getCause())
        {
            buf.append(linePrefix+tt+"\n");

            StackTraceElement[] stack = tt.getStackTrace();
            for (int i=0; i<stack.length; i++)
            {
                buf.append(linePrefix);
                buf.append(stack[i]);
                buf.append("\r\n");

                if (lineCount++ >= maxExceptionLines)
                    return;
            }
        }
    }

    public String toLogString(int maxExceptionLines)
    {
        // host ident authuser date request status bytes
        // e.g. 127.0.0.1 user-identifier frank [10/Oct/2000:13:55:36 -0700] "GET /apache_pb.gif HTTP/1.0" 200 2326

        StringBuilder log = new StringBuilder();
        log.append(clientAddress);
        log.append(" ");
        String id = getSessionID();
        if (id == null)
            id = "-";
        log.append(id);
        log.append(" - [");
        log.append(new Date(requestReceived));
        log.append("] \"");
        log.append(reqHeaders.get(HTTPHeaders.MAIN_LINE));
        log.append("\" ");
        String resp = (String) respHeaders.get(HTTPHeaders.MAIN_LINE);
        if (resp != null)
            log.append(resp.substring(9, 12));
        else
            log.append("-");
        log.append(" "+bytesWritten+"\r\n");

        log.append("##QS "+new Date(requestReceived)+" ["+clientAddress+"] "+totalRequestTime()+"ms "+requestReadTime()+"ms "+responseTime()+"ms "+responseWriteTime()+"ms "+bytesRead+" "+bytesWritten+"\r\n");
        log.append("##REQ "+reqHeaders+"\r\n");
        log.append("##RSP "+respHeaders+"\r\n");

        HTTPFilterChain chain = filterChain;
        log.append("##CR "+chain.getFullReport()+"\r\n");
        if (chain.getPrimaryError() != null)
            appendException("##TR ", chain.getPrimaryError(), log, maxExceptionLines);

        return log.toString();
    }
}
