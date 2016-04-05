package jjsp.data;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;

import jjsp.http.*;
import jjsp.util.*;

public interface DataInfoIndex
{
    public static final long DATA_BLOB_LIMIT = 512*1024*1024;
    
    public String[] getKeyNames() throws IOException;

    public DataInfo[] getVersions(String keyName) throws IOException;

    public InputStream getDataStream(DataInfo info) throws IOException;

    public default DataInfo getLatestVersion(String reqPath) throws IOException
    {
        DataInfo[] versions = getVersions(reqPath);
        if ((versions == null) || (versions.length == 0))
            return null;
        return versions[0];
    }
    
    public default byte[] getData(DataInfo info) throws IOException
    {
        if (info.length >= DATA_BLOB_LIMIT)
            throw new IllegalStateException("Data size too large - use getDataStream instead");
        
        InputStream stream = getDataStream(info);
        if (stream == null)
            return null;
        
        byte[] result = Utils.load(stream);
        String thisMD5 = Utils.toHexString(Utils.generateMD5Checksum(result));
        if (!thisMD5.equals(info.contentHash))
            throw new IOException("Content Hash mismatch. Download or underlying store corrupted");

        return result;
    }
}
