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
package jjsp.test;

import java.io.*;
import java.net.*;
import javax.net.*;
import javax.net.ssl.*;
import java.util.*;
import java.util.concurrent.*;

import jjsp.util.*;
import jjsp.http.*;
import jjsp.http.filters.*;


public class HTTPLoadTestClient 
{
    public static final int SAMPLES = 100;
    public static final int PAUSE_DELAY = 100;
    public static final double DELAY_PROBABILITY = 0.1;
    
    static class Runner implements Runnable
    {
        long[] responseTimes = new long[SAMPLES];

        public void run()
        {
            Random random = new Random();
            URL url = null;
            try
            {
                url = new URL("http://localhost/test.txt");
            }
            catch (Exception e) {}
            
            byte[] buffer = new byte[4*1024];
            while (true)
            {
                HttpURLConnection conn = null;

                for (int i=0; i < responseTimes.length; i++)
                {
                    long t0 = System.nanoTime();

                    try
                    {
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setDoOutput(false);

                        int length = conn.getContentLength();
                        if (length >= 0)
                        {
                            if (length > buffer.length)
                                buffer = new byte[length];
                            DataInputStream din = new DataInputStream(conn.getInputStream());
                            din.readFully(buffer, 0, length);
                            din.close();
                        }
                        else
                        {
                            length = 0;
                            InputStream in = conn.getInputStream();
                            while (true)
                            {
                                int r = in.read(buffer);
                                if (r < 0)
                                    break;
                                length += r;
                            }
                            in.close();
                        }
                    }
                    catch (Exception e) {}

                    long t1 = System.nanoTime();
                    responseTimes[i] = t1-t0; 

                    if ((PAUSE_DELAY > 0) && (random.nextDouble() < DELAY_PROBABILITY))
                    {
                        try
                        {
                            Thread.sleep(PAUSE_DELAY);
                        }
                        catch (Exception e) {}
                    }
                }

                conn.disconnect();

                Arrays.sort(responseTimes);
                int percentile = 95*responseTimes.length/100;

                double cutoff = responseTimes[percentile]/1000000.0;
                double min = responseTimes[0]/1000000.0;
                double max = responseTimes[responseTimes.length-1]/1000000.0;

                double mean1 = 0;
                for (int i=0; i<percentile; i++)
                    mean1 += responseTimes[i];
                mean1 /= percentile*1000000;

                double mean2 = 0;
                for (int i=percentile; i<responseTimes.length; i++)
                    mean2 += responseTimes[i];
                mean2 /= (responseTimes.length - percentile)*1000000;
                
                synchronized (System.out)
                {
                    System.out.println(String.format("Min %7.3f   Mean %7.3f  95th %7.3f   Mean %7.3f    Max %7.3f ", min, mean1, cutoff, mean2, max));
                }
            }
        }
    }

    public static void main(String[] args) throws Exception
    {
        for (int i=0; i<10000; i++)
            new Thread(new Runner()).start();
    }
}
