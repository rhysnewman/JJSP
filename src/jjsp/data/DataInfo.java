package jjsp.data;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;

import jjsp.http.*;

public class DataInfo implements Comparable
{
    public final long length;
    public final Date created;
    public final String keyName, versionName;
    public final String contentHash, mimeType, createdString;

    public DataInfo(DataInfo info) throws IOException
    {
        this(info.keyName, info.versionName, info.length, info.contentHash, info.created);
    }

    public DataInfo(String keyName, String versionName, long length, String contentHash, long created)
    {
        this(keyName, versionName, length, contentHash, new Date(created));
    }

    public DataInfo(String keyName, String versionName, long length, String contentHash, Date created)
    {
        this(keyName, versionName, length, created, contentHash, HTTPHeaders.guessMIMEType(keyName));
    }

    public DataInfo(String keyName, String versionName, long length, Date created, String contentHash, String mimeType)
    {
        this.keyName = keyName;
        if (keyName == null)
            throw new NullPointerException("Key Name cannot be null");
        
        this.versionName = (versionName == null) ? "": versionName;
        this.length = length;
        this.created = created;
        this.contentHash = contentHash;
        this.mimeType = mimeType;
        this.createdString = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(created);
    }

    public int hashCode()
    {
        return contentHash.hashCode();
    }

    public boolean equals(Object another)
    {
        if (another == null)
            return false;
        if (another == this)
            return true;

        try
        {
            DataInfo di = (DataInfo) another;
            return di.contentHash.equals(contentHash) && di.created.equals(created) && di.keyName.equals(keyName) && di.versionName.equals(versionName);
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public String toString()
    {
        return keyName+":"+versionName+" ["+contentHash+"] {"+created+"} "+length+" bytes";
    }

    public int compareTo(Object another)
    {
        try
        {
            return created.compareTo(((DataInfo) another).created);
        }
        catch (Exception e)
        {
            return -1;
        }
    }
}
