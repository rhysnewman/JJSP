package jjsp.data;

import java.io.*;
import java.util.*;

import jjsp.util.*;

public class CachedDataIndex implements DataInfoIndex
{
    private final DataInfoIndex src;
    private final WritableDataInfoIndex cache;

    private final long staleReadTimeLimit;
    private final boolean allowStaleReadsWhenDisconnected;

    private HashMap lastRefreshes;
    private OrderedValueMap versionLists;
    
    public CachedDataIndex(DataInfoIndex src, WritableDataInfoIndex cache, long staleReadTimeLimit, boolean allowStaleReadsWhenDisconnected)
    {
        this.src = src;
        this.cache = cache;
        this.staleReadTimeLimit = staleReadTimeLimit;
        this.allowStaleReadsWhenDisconnected = allowStaleReadsWhenDisconnected;

        lastRefreshes = new HashMap();
        versionLists = new OrderedValueMap();
    }

    private synchronized void updateLastReadTime(String key)
    {
        if (key == null)
            key = "";
        lastRefreshes.put(key, new Long(System.currentTimeMillis()));
    }

    private synchronized boolean checkStaleReadOK(String key, Exception srcConnectionException) throws IOException
    {
        if (key == null)
            key = "";

        Long time = (Long) lastRefreshes.get(key);
        if ((time != null) && (System.currentTimeMillis() - time.longValue() < staleReadTimeLimit))
            return true;

        if (srcConnectionException != null)
        {
            if (allowStaleReadsWhenDisconnected)
            {
                updateLastReadTime(key);
                return true;
            }

            throw new IOException("Failed to contact data source - stale read limit expired too.", srcConnectionException);
        }
        return false;
    }

    public String[] getKeyNames() throws IOException
    {
        if (!checkStaleReadOK(null, null))
        {
            String[] cachedNames = cache.getKeyNames();
            for (int i=0; i<cachedNames.length; i++)
                versionLists.putKey(cachedNames[i]);
            
            try
            {
                String[] names = src.getKeyNames();
                for (int i=0; i<names.length; i++)
                    versionLists.putKey(names[i]);
                
                updateLastReadTime(null);
            }
            catch (Exception e)
            {
                checkStaleReadOK(null, e);
            }
        }

        return versionLists.getOrderedKeys();
    }

    public DataInfo[] getVersions(String key) throws IOException
    {
        if (!checkStaleReadOK(key, null))
        {
            DataInfo[] cachedVersions = cache.getVersions(key);
            if (cachedVersions != null)
            {
                for (int i=0; i<cachedVersions.length; i++)
                    versionLists.putValueFor(key, cachedVersions[i]);
            }
            
            try
            {
                DataInfo[] versions = src.getVersions(key);
                updateLastReadTime(key);

                for (int i=0; i<versions.length; i++)
                    versionLists.putValueFor(key, versions[i]);
            }
            catch (Exception e)
            {
                checkStaleReadOK(key, e);
            }
        }

        Comparable[] values = versionLists.getAllValuesFor(key);
        if (values == null)
            return null;
        
        DataInfo[] result = new DataInfo[values.length];
        System.arraycopy(values, 0, result, 0, values.length);
        return result;
    }

    public InputStream getDataStream(DataInfo info) throws IOException
    {
        InputStream cachedDataStream = cache.getDataStream(info);
        if (cachedDataStream != null)
            return cachedDataStream;

        cachedDataStream = src.getDataStream(info);
        if ((info.length < 0) || (info.length > DATA_BLOB_LIMIT))
            return cachedDataStream;

        byte[] rawData = Utils.load(cachedDataStream);

        String checksum = Utils.toHexString(Utils.generateMD5Checksum(rawData));
        if (checksum.equals(info.contentHash))
        {
            try
            {
                cache.writeVersion(info, rawData);
            }
            catch (Exception e) {}
            return new ByteArrayInputStream(rawData);
        }
        else
            throw new IOException("Data content hash mismatch - data corrupted");
    }

    public byte[] getData(DataInfo info) throws IOException
    {
        byte[] cachedResult = cache.getData(info);
        if (cachedResult != null)
            return cachedResult;
        
        byte[] rawResult = src.getData(info);
        if (rawResult == null)
            return null;
        
        String checksum = Utils.toHexString(Utils.generateMD5Checksum(rawResult));
        if (checksum.equals(info.contentHash))
        {
            try
            {
                cache.writeVersion(info, rawResult);
            }
            catch (Exception e) {}
            return rawResult;
        }
        else
            throw new IOException("Data content hash mismatch - data corrupted");
    }

    public String toString()
    {
        return "CachedDataIndex["+cache+" from "+src+"]";
    }
}

