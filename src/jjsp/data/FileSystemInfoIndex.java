package jjsp.data;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;
import java.security.*;

import jjsp.http.*;
import jjsp.util.*;

public class FileSystemInfoIndex implements DataInfoIndex
{
    private final File rootDir;
    private OrderedValueMap index;

    public FileSystemInfoIndex(File rootDir) throws Exception
    {
        this.rootDir = rootDir;
        index = new OrderedValueMap();
        scan(index, rootDir, new HashSet());

        Thread t = new Thread(new Reindexer());
        t.setDaemon(true);
        t.start();
    }

    class Reindexer implements Runnable
    {
        public void run()
        {
            while (true)
            {
                try
                {
                    Thread.sleep(300000);

                    Set existing = new HashSet();
                    synchronized (Reindexer.this)
                    {
                        String[] allKeys = index.getOrderedKeys();
                        for (int i=0; i<allKeys.length; i++)
                        {
                            Comparable[] versions = index.getAllValuesFor(allKeys[i]);
                            for (int j=0; j<versions.length; j++)
                                existing.add(((FileInfo) versions[j]).src.getAbsolutePath());
                        }
                    }

                    OrderedValueMap newIndex = new OrderedValueMap();
                    
                    System.out.println(new Date()+" Scanning source Directory: "+rootDir);
                    scan(newIndex, rootDir, existing);
                    System.out.println(new Date()+" Scan complete");
                    
                    synchronized (Reindexer.this)
                    {
                        index = newIndex;
                    }
                }
                catch (Exception e) {System.out.println("WARNING: Failed to Reindex File System Index rooted at "+rootDir+"  "+e);}
            }
        }
    }

    class FileInfo extends DataInfo
    {
        final File src;

        FileInfo(String keyName, String versionKey, File f, String md5, java.util.Date created) throws Exception
        {
            super(keyName, versionKey, f.length(), md5, created);
            src = f;
        }
    }

    private void scan(OrderedValueMap index, File f, Set existing) throws Exception
    {
        if (f.isFile())
        {
            try
            {
                if (f.length() > 10*1024*1024)
                    System.out.println("Scanning large file "+f+" to generate MD5....");
                
                MessageDigest md5Alg = MessageDigest.getInstance("md5");
                byte[] buffer = new byte[1024*1024];

                InputStream fin = new BufferedInputStream(new FileInputStream(f));
                while (true)
                {
                    int r = fin.read(buffer, 0, buffer.length);
                    if (r < 0)
                        break;
                    md5Alg.update(buffer, 0, r);
                }
                byte[] rawDigest = md5Alg.digest();
                
                String md5 = Utils.toHexString(rawDigest);

                URI rootURI = rootDir.toURI();
                URI uri = rootURI.relativize(f.toURI());

                String keyName = uri.toASCIIString();
                if (keyName.startsWith("/") || keyName.startsWith("\\"))
                    keyName = keyName.substring(1);

                int dot = keyName.lastIndexOf(".");
                int dateIndex = keyName.lastIndexOf("__");
                String versionKey = "";

                java.util.Date d = new java.util.Date();
                if (dateIndex < 0)
                    d = new java.util.Date(f.lastModified());
                else
                {
                    String dateSpec = "";

                    if (dot > 0)
                    {
                        dateSpec = keyName.substring(dateIndex+2, dot);
                        keyName = keyName.substring(0, dateIndex)+keyName.substring(dot);
                    }
                    else
                    {
                        dateSpec = keyName.substring(dateIndex+2);
                        keyName = keyName.substring(0, dateIndex);
                    }

                    int ddash = keyName.lastIndexOf("__");
                    if (ddash > 0)
                    {
                        versionKey = keyName.substring(ddash+2);
                        keyName = keyName.substring(0, ddash);
                    }
                    
                    String[] fmts = new String[]{"yyyy-MM-dd'T'HH-mm", "yyyy-MM-dd_HH-mm", "yyyy_MM_dd_HH_mm", "yyyy-MM-dd-HH-mm", "yyyy-MM-dd", "yyyy_MM_dd"};
                    for (int i=0; i<fmts.length; i++)
                    {
                        try
                        {
                            SimpleDateFormat fmt = new SimpleDateFormat(fmts[i]);
                            d = fmt.parse(dateSpec);
                            break;
                        }
                        catch (Exception e){}
                    }
                }

                FileInfo info = new FileInfo(keyName, versionKey, f, md5, d);
                index.putValueFor(keyName, info);

                if (!existing.contains(f.getAbsolutePath()))
                    System.out.println("Info: Adding "+info.keyName+"  [Date "+info.created+"  Version: "+info.contentHash+"  Total: "+index.getNumberOfValuesFor(keyName)+"]");
            }
            catch (Exception e)
            {
                System.out.println("WARNING: failed to load information about file "+f+" ("+e+")");
            }
        }
        else
        {
            if (f.getName().startsWith(".")) // Skip any .name files
                return;

            File[] ff = f.listFiles();
            for (int i=0; i<ff.length; i++)
                scan(index, ff[i], existing);
        }
    }

    public synchronized String[] getKeyNames() throws IOException
    {
        return index.getOrderedKeys();
    }

    public synchronized DataInfo[] getVersions(String key) throws IOException
    {
        Comparable[] values = index.getAllValuesFor(key);
        if (values == null)
            return null;

        DataInfo[] result = new DataInfo[values.length];
        System.arraycopy(values, 0, result, 0, values.length);
        return result;
    }

    public InputStream getDataStream(DataInfo info) throws IOException
    {
        FileInfo fi = (FileInfo) index.getMatchingValue(info.keyName, info);
        if (fi == null)
            return null;
        return new FileInputStream(fi.src);
    }
}
