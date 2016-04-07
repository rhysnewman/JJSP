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
