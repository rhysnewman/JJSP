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
import java.net.*;

import jjsp.util.*;

public class HTTPFilterChain
{ 
    public final String linkName;
    public final HTTPFilterChain previous;

    public String report;
    public Throwable error;

    private static final String EMPTY_REPORT = "";

    public HTTPFilterChain(String linkName)
    {
        this(linkName, null);
    }
    
    public HTTPFilterChain(String linkName, HTTPFilterChain previous)
    {
        this.linkName = linkName;
        this.previous = previous;
        report = EMPTY_REPORT;
    }

    public void setReport(String report)
    {
        this.report = report;
    }
    
    public void appendReport(String toAppend)
    {
        report += toAppend.replace("\n", " ");
    }
    
    public Throwable getPrimaryError()
    {
        if (previous != null)
        {
            Throwable err = previous.getPrimaryError();
            if (err != null)
                return err;
        }

        return error;
    }

    public String getFullReport()
    {
        String result = "";
        if ((report != null) && (error != null))
            result = "["+report+": "+error+"]";
        else if ((report == null) && (error != null))
            result = "["+error+"]";
        else if ((report != null) && (error == null))
            result = "["+report+"]";

        if (previous == null)
            return linkName+result;
        return previous.getFullReport()+"/"+linkName+result;
    }

    public String getPath()
    {
        if (previous == null)
            return linkName;
        return previous.getPath()+"/"+linkName;
    }

    public String toJSON()
    {
        StringBuffer buf = new StringBuffer();
        for (HTTPFilterChain ch = this; ch != null; ch = ch.previous)
        {
            buf.append(",{\"name\":\""+linkName+"\"");
            if (report != null)
                buf.append(",\"report\":\""+report.replace("\"", "\\\"")+"\"");
            if (error != null)
                buf.append(",\"error\":\""+error.toString().replace("\"", "\\\"")+"\"");
            buf.append("}");
        }
        buf.setCharAt(0, '[');
        buf.append("]");

        return buf.toString();
    }
}
