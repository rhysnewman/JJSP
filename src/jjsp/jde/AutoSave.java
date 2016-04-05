package jjsp.jde;

import java.net.*;
import java.io.*;
import java.util.*;

import jjsp.util.*;

public class AutoSave
{
    private static final long MAGIC_NUMBER = 0xF78298AABC728128l;
    private static final long DEFAULT_SAVE_DELAY = 20000;

    private File saveFile;
    private long saveDelay;
    private ReplicatedFile rfile;
    private boolean dataModified;
    private TreeMap dataIndex;
    private HashMap modificationTimes;

    public AutoSave(File saveFile) throws IOException
    {
        this(saveFile, DEFAULT_SAVE_DELAY);
    }

    public AutoSave(File saveFile, long saveDelay) throws IOException
    {
        this.saveFile = saveFile;
        this.saveDelay = saveDelay;
        rfile = new ReplicatedFile(saveFile, MAGIC_NUMBER);

        dataIndex = new TreeMap();
        modificationTimes = new HashMap();
        dataModified = false;
        loadData();

        Thread t = new Thread(new Flusher());
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY-3);
        t.start();
    }

    private void loadData() throws IOException
    {
        byte[] latestData = rfile.getLatestEntryData();
        if (latestData.length == 0)
            return;

        DataInputStream din = new DataInputStream(new ByteArrayInputStream(latestData));
        int count = din.readInt();
        for (int i=0; i<count; i++)
        {
            String filePath = din.readUTF();
            int length = din.readInt();
            long modTime = din.readLong();
            
            byte[] raw = new byte[length];
            din.readFully(raw);

            dataIndex.put(filePath, raw);
            modificationTimes.put(filePath, Long.valueOf(modTime));
        }
    }

    public synchronized File[] getSavedFiles()
    {
        String[] paths = new String[dataIndex.size()];
        dataIndex.keySet().toArray(paths);
        File[] files = new File[paths.length];
        for (int i=0; i<files.length; i++)
            files[i] = new File(paths[i]);

        return files;
    }

    public synchronized long getModificationTimeOf(File src)
    {
        String path = src.getAbsolutePath();
        Long time = (Long) modificationTimes.get(path);
        if (time == null)
            return -1;
        return time.longValue();
    }

    public synchronized byte[] getLatest(File src)
    {
        String path = src.getAbsolutePath();
        return (byte[]) dataIndex.get(path);
    }

    public synchronized boolean save(File src)
    {
        return save(new byte[0], src);
    }

    public synchronized boolean save(byte[] data, File src)
    {
        if (src.equals(saveFile))
            throw new IllegalStateException("Cannot auto-save the auto-save file!");

        String path = src.getAbsolutePath();
        byte[] existing = (byte[]) dataIndex.get(path);
        if (Arrays.equals(existing, data))
            return false;
        
        dataModified = true;
        dataIndex.put(src.getAbsolutePath(), data);
        modificationTimes.put(src.getAbsolutePath(), Long.valueOf(System.currentTimeMillis()));
        return true;
    }

    public void flushToDisk() throws IOException
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        
        synchronized (this)
        {
            if (!dataModified)
                return;

            dout.writeInt(dataIndex.size());
            Iterator itt = dataIndex.keySet().iterator();
            while (itt.hasNext())
            {
                String path = (String) itt.next();
                byte[] data = (byte[]) dataIndex.get(path);
                Long time = (Long) modificationTimes.get(path);

                dout.writeUTF(path);
                dout.writeInt(data.length);
                dout.writeLong(time.longValue());
                dout.write(data);
            }
            dout.close();
        }

        byte[] raw = bout.toByteArray();
        rfile.storeData(raw);
    }

    class Flusher implements Runnable
    {
        public void run()
        {
            while (true)
            {
                try
                {
                    Thread.sleep(saveDelay);
                    flushToDisk();
                }
                catch (Throwable t) {}
            }
        }
    }
}
