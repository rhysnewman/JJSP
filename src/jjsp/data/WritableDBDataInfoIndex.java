package jjsp.data;

import java.io.*;
import java.net.*;
import java.util.*;
import java.sql.*;

import jjsp.http.*;
import jjsp.util.*;
import jjsp.engine.*;

/*
// Here is a SQL script for creating a suitable table for this data in MySQL

create table dataSource (
	ID bigint PRIMARY KEY NOT NULL AUTO_INCREMENT,
	keyName varchar(256) not null,
	versionName varchar(256) not null,
        length bigint not null,
	contentHash varchar(128) not null,
	created timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
	data LONGBLOB not null,

    Index  Ix_keyName(keyName),
    Index  Ix_md5(contentHash),
    Unique Index  Ix_name_contentHash_created(keyName, versionKey, contentHash, created)
)

*/

public class WritableDBDataInfoIndex implements WritableDataInfoIndex
{
    public static final long CACHE_TIME_LIMIT = 60000;
    public static final long CONNECTION_TIME_LIMIT = 20000;

    private SQLDriver driver;

    private long cacheTime;
    private OrderedValueMap valueMap;

    public WritableDBDataInfoIndex(String dbURL, String userName, String password) throws Exception
    {
        this(dbURL, userName, password, "com.mysql.jdbc.Driver", WritableDBDataInfoIndex.class.getClassLoader());
    }

    public WritableDBDataInfoIndex(String dbURL, String userName, String password, String driverName, ClassLoader loader) throws Exception
    {
        Map m = new HashMap();
        m.put(SQLDriver.URL, dbURL);
        m.put(SQLDriver.USER, userName);
        m.put(SQLDriver.PASSWORD, password);
        m.put(SQLDriver.DRIVER, driverName);

        driver = new SQLDriver(m, loader);

        cacheTime = 0;
        valueMap = new OrderedValueMap();
    }

    public SQLDriver getSQLDriver()
    {
        return driver;
    }

    private void refreshInfoCache() throws IOException
    {
        ResultSet rss = null;
        SQLDriver.ConnectionWrapper cw = null;

        OrderedValueMap valMap = new OrderedValueMap();
        try
        {
            cw = driver.getConnection(CONNECTION_TIME_LIMIT);
            PreparedStatement ps = cw.getPreparedStatement("select keyName, versionName, length, created, contentHash from dataSource");
            rss = ps.executeQuery();
            while (rss.next())
            {
                String keyName = rss.getString(1);
                String versionName = rss.getString(2);
                long length = rss.getLong(3);
                java.sql.Timestamp time = rss.getTimestamp(4);
                String md5 = rss.getString(5);

                DataInfo info = new DataInfo(keyName, versionName, length, md5, new java.util.Date(time.getTime()));
                valMap.putValueFor(keyName, info);
            }
            cw.commit();
        }
        catch (SQLException e)
        {
            throw new IOException(e);
        }
        finally
        {
            try
            {
                rss.close();
            }
            catch (Exception e) {}
            if (cw != null)
                cw.returnToPool();
        }

        synchronized (this)
        {
            valueMap = valMap;
            cacheTime = System.currentTimeMillis();
        }
    }

    private synchronized OrderedValueMap getValueMap()
    {
        return valueMap;
    }

    private synchronized boolean checkCache()
    {
        return System.currentTimeMillis() - cacheTime < CACHE_TIME_LIMIT;
    }

    public String[] getKeyNames() throws IOException
    {
        if (!checkCache())
            refreshInfoCache();

        return getValueMap().getOrderedKeys();
    }

    public DataInfo[] getVersions(String reqPath) throws IOException
    {
        if (!checkCache())
            refreshInfoCache();

        OrderedValueMap vm = getValueMap();
        Comparable[] cc = vm.getAllValuesFor(reqPath);
        if (cc == null)
            return null;

        DataInfo[] result = new DataInfo[cc.length];
        System.arraycopy(cc, 0, result, 0, result.length);
        return result;
    }

    class DBStream extends FilterInputStream
    {
        private ResultSet rss;
        private SQLDriver.ConnectionWrapper wrapper;
        
        DBStream(InputStream src, ResultSet rss, SQLDriver.ConnectionWrapper wrapper)
        {
            super(src);
            
            this.rss = rss;
            this.wrapper = wrapper;
        }

        public void close() throws IOException
        {
            try
            {
                rss.close();
            }
            catch (Exception e) {}
            wrapper.returnToPool();
        }
    }

    public InputStream getDataStream(DataInfo info) throws IOException
    {
        try
        {
            SQLDriver.ConnectionWrapper cw = driver.getConnection(CONNECTION_TIME_LIMIT);
            PreparedStatement ps = cw.getPreparedStatement("select data from dataSource where keyName=? and contentHash=? and created=? and versionName=?");
            ps.setString(1,info.keyName);
            ps.setString(2, info.contentHash);
            ps.setTimestamp(3, new java.sql.Timestamp(info.created.getTime()));
            ps.setString(4, info.versionName);

            ResultSet rss = ps.executeQuery();
            if (!rss.next())
                throw new IOException("Data for info "+info+" not found");

            return new DBStream(rss.getBinaryStream(1), rss, cw);
        }
        catch (SQLException e)
        {
            throw new IOException(e);
        }
    }

    public DataInfo writeVersion(DataInfo info, byte[] contents) throws IOException
    {
        if (info.length > DATA_BLOB_LIMIT)
            throw new IOException("Data content too large (limited to "+DATA_BLOB_LIMIT+" bytes");
        if ((info.length >= 0) && (contents.length != info.length))
            throw new IOException("Data info length differs from supplied contents length");

        String md5 = Utils.toHexString(Utils.generateMD5Checksum(contents));
        if (!md5.equals(info.contentHash))
            throw new IOException("Content Hash mismatch. Data info md5 does not match that of supplied content");

        ResultSet rss = null;
        SQLDriver.ConnectionWrapper cw = null;
        try
        {
            cw = driver.getConnection(CONNECTION_TIME_LIMIT);
            PreparedStatement ps = cw.getPreparedStatement("insert into dataSource (keyName, versionName, length, created, contentHash, data) values (?,?,?,?,?,?)");
            ps.setString(1, info.keyName);
            ps.setString(2, info.versionName);
            ps.setLong(3, contents.length);
            ps.setTimestamp(4, new java.sql.Timestamp(info.created.getTime()));
            ps.setString(5, md5);

            Blob blob = cw.getConnection().createBlob();
            blob.setBytes(1, contents);
            ps.setBlob(6, blob);

            if (ps.executeUpdate() != 1)
                throw new IllegalStateException("Failed to insert new data row");
            cw.commit();
            return info;
        }
        catch (SQLException e)
        {
            throw new IOException(e);
        }
        finally
        {
            try
            {
                rss.close();
            }
            catch (Exception e) {}
            if (cw != null)
                cw.returnToPool();
        }
    }
    
    public static void transferData(DataInfoIndex src, WritableDataInfoIndex dest) throws Exception
    {
        String[] allKeys = src.getKeyNames();
        for (int i=0; i<allKeys.length; i++)
        {
            DataInfo info = src.getLatestVersion(allKeys[i]);
            byte[] contents = src.getData(info);

            dest.writeVersion(info, contents);
        }
    }
}
