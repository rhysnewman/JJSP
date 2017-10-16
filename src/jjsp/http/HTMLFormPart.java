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

    private static final byte[] CRLF = "\r\n".getBytes(HTTPUtils.UTF8);

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

    public String getDataAsString() throws IllegalAccessException {
        if ( attributes.containsKey("contentType") )
            throw new IllegalAccessException("Data for '" + attributes.get("name") + "' cannot be parsed as string, content-type: " + attributes.get("contentType"));

        return new String(rawData, HTTPUtils.UTF8);
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
        if ( seq.length > data.length )
            return -1;
        end = end - seq.length + 1;

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
    public static HTMLFormPart[] processPostedFormData(HTTPHeaders headers, byte[] rawBody) {
        String boundaryString = extractFormDataBoundary(headers);
        if ( boundaryString == null )
            return null;

        byte[] submissionBoundary = boundaryString.getBytes(HTTPUtils.UTF8);
        int startPos = seekSequence(rawBody, 0, submissionBoundary);
        if ( startPos < 0 )
            return null;
        startPos += submissionBoundary.length;

        Vector formParts = new Vector();
        while ( true ) {
            int endPos = seekSequence(rawBody, startPos, submissionBoundary);
            if ( endPos < 0 )
                break;

            int valStart = seekSequence(rawBody, startPos, endPos, CONTENT_DISPOSITION_KEY.getBytes(HTTPUtils.UTF8));
            if ( valStart < 0 )
                continue;

            valStart += CONTENT_DISPOSITION_KEY.length();
            int valEnd = seekSequence(rawBody, valStart, CRLF);
            if ( valEnd < 0 )
                continue;

            Map attributes = extractAttrs(new String(rawBody, valStart, valEnd - valStart));
            int dataStart = valEnd;

            // put content type to attributes for binary data, images, files, etc
            int cTypeStart = seekSequence(rawBody, startPos, endPos, CONTENT_TYPE_KEY.getBytes(HTTPUtils.UTF8));
            if ( cTypeStart >= 0 ) {
                cTypeStart += CONTENT_TYPE_KEY.length() + 1; // "Content-Type:"
                int cTypeEnd = seekSequence(rawBody, cTypeStart, CRLF);
                if ( cTypeEnd >= 0 ) {
                    String contentType = new String(rawBody, cTypeStart, cTypeEnd - cTypeStart);
                    attributes.put("contentType", contentType.trim());
                    dataStart = cTypeEnd;
                }
            }

            while ( seekSequence(rawBody, dataStart, endPos, CRLF) == dataStart )
                dataStart += 2;
            if ( dataStart >= endPos ) {
                startPos = endPos + submissionBoundary.length;
                continue;
            }

            int endPosCRLFCount = 0;
            while ( seekSequence(rawBody, endPos - 2, endPos, CRLF) == endPos - 2 ) {
                endPos -= 2;
                endPosCRLFCount++;
            }
            if ( endPos <= startPos ) {
                startPos = endPos + submissionBoundary.length + endPosCRLFCount * 2;
                continue;
            }

            byte[] raw = new byte[endPos - dataStart];
            System.arraycopy(rawBody, dataStart, raw, 0, raw.length);

            HTMLFormPart part = new HTMLFormPart(attributes, raw);
            formParts.add(part);

            startPos = endPos + submissionBoundary.length + endPosCRLFCount * 2;
        }

        HTMLFormPart[] results = new HTMLFormPart[formParts.size()];
        formParts.toArray(results);
        return results;
    }
}
