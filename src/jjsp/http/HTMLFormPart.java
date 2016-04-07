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

/**
   A class for representing part of a HTML form submission. Browsers can use HTTP post to send data to the server and there can be multiple parts to this data. The static processPostedFormData() of this class can extract the form parts from the raw data submitted.
 */
public class HTMLFormPart
{
    public static final String CONTENT_DISPOSITION_KEY = "Content-Disposition:";
    public static final String CONTENT_TYPE_KEY = "Content-Type";
    public static final String BOUNDARY_KEY = "boundary";

    private static final byte[] CRLF_CRLF = "\r\n\r\n".getBytes(HTTPUtils.ASCII);
    private static final byte[] CRLF = "\r\n".getBytes(HTTPUtils.ASCII);

    private Map attributes;
    private byte[] rawData;
    
    public HTMLFormPart(Map attrs, byte[] data)
    {
        attributes = attrs;
        rawData = data;
    }

    public String getAttribute(String key)
    {
        Object val = attributes.get(key);
        if (val == null)
            return null;
        return val.toString();
    }

    public Map getAttributes()
    {
        return attributes;
    }

    public byte[] getData()
    {
        return rawData;
    }
    
    public String toString()
    {
        return attributes+"  ["+rawData.length+"]";
    }

    private static int seekSequence(byte[] data, int start, byte[] seq)
    {
        return seekSequence(data, start, data.length, seq);
    }

    private static int seekSequence(byte[] data, int start, int end, byte[] seq)
    {
        end = Math.min(end, data.length-2) - seq.length;
        
        for (int i=start; i<end; i++)
        {
            int match = 0;
            for (int j=0, k=i; j<seq.length; j++, k++)
            {
                if (data[k] != seq[j])
                    break;
                else
                    match++;
            }
            
            if (match == seq.length)
                return i;
        }
        
        return -1;
    }

    private static Map extractAttrs(String src)
    {
        HashMap result = new HashMap();
        StringTokenizer tokens = new StringTokenizer(src, ";");
        while (tokens.hasMoreTokens())
        {
            String tkn = tokens.nextToken();
            int eq = tkn.indexOf("=");
            if (eq < 0)
                result.put(tkn.trim(), "");
            else
            {
                String key = tkn.substring(0, eq).trim();
                String val = tkn.substring(eq+1).trim();
                if (val.startsWith("\"") && val.endsWith("\""))
                    val = val.substring(1, val.length()-1).trim();
                result.put(key, val);
            }
        }
        
        return result;
    }

    /**
       Returns the unique string data used to delineate boundaries in the form data stream.
     */
    public static String extractFormDataBoundary(HTTPHeaders headers)
    {
        String type = headers.getHeader(CONTENT_TYPE_KEY, null);
        if (type == null)
            return null;
        
        int index = type.toLowerCase().indexOf(BOUNDARY_KEY);
        if (index < 0)
            return null;
        index = type.indexOf("=", index+8);
        if (index < 0)
            return null;
        return "--"+type.substring(index+1).trim();
    }

    /**
       The main means of getting the parts of an HTML form from the raw posted data. 
     */
    public static HTMLFormPart[] processPostedFormData(HTTPHeaders headers, byte[] rawBody)
    {
        String boundaryString = extractFormDataBoundary(headers);
        if (boundaryString == null)
            return null;

        byte[] submissionBoundary = boundaryString.getBytes(HTTPUtils.ASCII);
        int pos = seekSequence(rawBody, 0, submissionBoundary);
        if (pos < 0)
            return null;
        pos += submissionBoundary.length;
        
        Vector buffer = new Vector();
        while (true)
        {
            int nextPos = seekSequence(rawBody, pos, submissionBoundary);
            if (nextPos < 0)
                break;
            
            int bodyEnd = nextPos;
            if (rawBody[bodyEnd - 1] == '\n')
                bodyEnd--;
            if (rawBody[bodyEnd - 1] == '\r')
                bodyEnd--;
            
            int end = seekSequence(rawBody, pos, CRLF_CRLF);
            int bodyStart = -1;
            if (end < 0)
            {
                end = seekSequence(rawBody, pos, CRLF);
                if (end < 0)
                    break;
                bodyStart = end + 2;
            }
            else
                bodyStart = end + 4;
            
            int valStart = seekSequence(rawBody, pos, CONTENT_DISPOSITION_KEY.getBytes(HTTPUtils.ASCII));
            if (valStart < 0)
                break;
            valStart += CONTENT_DISPOSITION_KEY.length();
            int valEnd = seekSequence(rawBody, valStart, CRLF);
            if (valEnd < 0)
                break;
            
            Map attrs = extractAttrs(new String(rawBody, valStart, valEnd - valStart));
            byte[] raw = new byte[bodyEnd - bodyStart];
            System.arraycopy(rawBody, bodyStart, raw, 0, raw.length);
            
            HTMLFormPart part = new HTMLFormPart(attrs, raw);
            buffer.add(part);
            
            pos = nextPos+submissionBoundary.length;
        }

        HTMLFormPart[] result = new HTMLFormPart[buffer.size()];
        buffer.toArray(result);
        return result;
    }
}
