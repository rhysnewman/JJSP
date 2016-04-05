package jjsp.data;

import java.io.*;
import java.net.*;
import java.util.*;

import jjsp.util.*;

public class HTTPDataInfoIndex implements DataInfoIndex
{
    public static final long DEFAULT_TIMEOUT = 5000;

    private final long timeout;
    private final String urlStem;

    public HTTPDataInfoIndex(String urlStem)
    {
        this(urlStem, DEFAULT_TIMEOUT);
    }
    
    public HTTPDataInfoIndex(String urlStem, long timeout)
    {
        this.urlStem = urlStem;
        this.timeout = timeout;
    }

    private URL formURL(String suffix) throws IOException
    {
        try
        {
            return new URL(urlStem+suffix);
        }
        catch (MalformedURLException ex)
        {
            throw new IOException(ex);
        }
    }

    private InputStream getURLStream(URL url) throws IOException
    {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout((int) timeout);
        conn.setRequestMethod("GET");
        conn.connect();
        return conn.getInputStream();
    }

    public String[] getKeyNames() throws IOException
    {
        URL url = formURL("");
        String rawJSON = Utils.toAsciiString(Utils.load(getURLStream(url)));

        List ll = (List) JSONParser.parse(rawJSON);
        String[] result = new String[ll.size()];
        for (int i=0; i<result.length; i++)
        {
            Map m = (Map) ll.get(i);
            result[i] = (String) m.get("keyName");
        }

        return result;
    }

    public DataInfo[] getVersions(String reqPath) throws IOException
    {
        URL url = formURL(reqPath+"?");
        String rawJSON = Utils.toAsciiString(Utils.load(getURLStream(url)));

        List ll = (List) JSONParser.parse(rawJSON);
        DataInfo[] result = new DataInfo[ll.size()];
        for (int i=0; i<result.length; i++)
        {
            Map m = (Map) ll.get(i);
            String keyName = (String) m.get("keyName");
            String versionName = (String) m.get("versionName");
            long length = ((Number) m.get("length")).longValue();
            String hash = (String) m.get("contentHash");
            Date d = new Date(((Number) m.get("time")).longValue());
            
            result[i] = new DataInfo(keyName, versionName, length, hash, d);
        }

        return result;
    }
    
    public InputStream getDataStream(DataInfo info) throws IOException
    {
        URL url = formURL(info.keyName+"."+info.contentHash);
        return getURLStream(url);
    }

    public String toString()
    {
        return "HTTPDataInfoIndex["+urlStem+"]";
    }

    public static void main(String[] args) throws Exception
    {
        HTTPDataInfoIndex index2 = new HTTPDataInfoIndex("http://localhost/db/");
        SimpleDirectoryDataIndex cache2 = new SimpleDirectoryDataIndex(new File(".HTTPDataVersionCache"));

        CachedDataIndex index = new CachedDataIndex(index2, cache2, 2000, true);

        String[] list = index.getKeyNames();

        for (int i=0; i<list.length; i++)
        {
            System.out.println("Listing "+i+"  "+list[i]);

            DataInfo[] infos = index.getVersions(list[i]);
            for (int j=0; j<infos.length; j++)
            {
                System.out.println("    V"+j+"  "+infos[j]);
                byte[] contents = index.getData(infos[j]);
                System.out.println("        Got "+contents.length+" bytes OK");
            }
        }
    }
}
