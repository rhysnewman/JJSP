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

import jjsp.http.*;
import jjsp.util.*;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CBench
{
    static {
        System.setProperty("http.maxConnections", "100000");
    }

    public static void main(String[] args) throws Exception {
        Args.parse(args);

        int threads = Args.getInt("threads", 4);
        int reps = Args.getInt("reps", 100);
        int rampup = Args.getInt("ramp", 10);
        int delay = rampup*1000/threads;
        boolean https = Args.hasArg("https");
        if (https)
            System.setProperty("javax.net.ssl.trustStore", Args.getArg("certsStore", "gtvTruststore"));
        int port = Args.getInt("port", 80);
        System.out.println("Thread start delay: "+delay +" mS");
        System.out.println("Started at " + new Date());
        String host = Args.getArg("host");
        List<CompletableFuture<Stats>> futs = new ArrayList();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        double worst = 0.05;
        for (int i = 0; i < threads; i++) {
            CompletableFuture<Stats> fut = CompletableFuture.supplyAsync(() -> test(https, host, port, reps, worst), pool);
            futs.add(fut);
            try {Thread.sleep(delay);} catch (InterruptedException e) {}
        }
        Stats mean = new Stats();
        Stats bestMean = new Stats();
        Stats worstMean = new Stats();
        Stats minWorst = new Stats();
        Stats maxWorst = new Stats();
        for(CompletableFuture<Stats> fut: futs) {
            Stats res = fut.get();
            mean.addMeasurement(res.mean(), null);
            bestMean.addMeasurement(res.bestMean(worst), null);
            worstMean.addMeasurement(res.worstMean(worst), null);
            minWorst.addMeasurement(res.minWorst(worst), null);
            maxWorst.addMeasurement(res.worst().t, res.worst().start);
        }
        System.out.println("*************************GLOBAL********************************");
        System.out.printf("Mean: %6.2f, best mean: %6.2f, Worst %d percent mean: %7.2f, min worst %7.2f, max worst %8.2f (%s)\n", mean.mean(), bestMean.mean(), (int)(100*worst), worstMean.mean(), minWorst.mean(), maxWorst.worst().t, maxWorst.worst().start);
        pool.shutdown();
    }

    public static Stats test(boolean https, String host, int port, int reps, double worst) {
        Stats stats = new Stats();
        List<Measurement> timings = downloadIgnore(https, host, port, reps);
        for (Measurement m: timings) {
            double time = (m.t) / 1000000.0;
            stats.addMeasurement(time, m.start);
        }
        synchronized (System.out) {
            System.out.printf("Mean: %6.2f, best mean: %6.2f, Worst %d percent mean: %7.2f, min worst %7.2f, max worst %8.2f (%s)\n", stats.mean(), stats.bestMean(worst), (int)(100*worst), stats.worstMean(worst), stats.minWorst(worst), stats.worst().t, stats.worst().start);
        }
        return stats;
    }

    public static class Measurement implements Comparable<Measurement> {
        public final double t;
        public final Date start;
        public Measurement(double t, Date start) {
            this.t = t;
            this.start = start;
        }

        @Override
        public int compareTo(Measurement o) {
            return Double.compare(t, o.t);
        }
    }

    public static class Stats
    {
        private double mean=0.0, m2=0.0;
        private int n=0;
        private List<Measurement> all = new ArrayList<>();

        public void addMeasurement(double x, Date start) {
            n++;
            double delta = x-mean;
            mean += delta/n;
            m2 += delta*(x-mean);
            all.add(new Measurement(x, start));
        }

        public double mean() {
            return mean;
        }

        public double sd() {
            return Math.sqrt(m2/(n-1));
        }

        public int size() {
            return n;
        }

        public Measurement worst() {
            Collections.sort(all);
            return all.get(all.size()-1);
        }

        public double worstMean(double wfrac) {
            Collections.sort(all);
            Stats bad = new Stats();
            int index = (int)(n*(1-wfrac));
            for (int i=index; i < all.size(); i++)
                bad.addMeasurement(all.get(i).t, all.get(i).start);
            return bad.mean();
        }

        public double minWorst(double wfrac) {
            Collections.sort(all);
            int index = (int)(n*(1-wfrac));
            return all.get(index).t;
        }

        public double bestMean(double wfrac) {
            Collections.sort(all);
            Stats good = new Stats();
            int index = (int)(n*(1-wfrac));
            for (int i=0; i < index; i++)
                good.addMeasurement(all.get(i).t, all.get(i).start);
            return good.mean();
        }
    }

    public static String downloadString(String url) {
        return new String(download(url));
    }

    public static long downloadIgnore2(String url) {
        byte[] tmp = new byte[4096];
        HttpURLConnection conn = null;
        try {
            URL target = new URI(url).toURL();
            conn = (HttpURLConnection)target.openConnection();
            long t1 = System.nanoTime();
            InputStream in = conn.getInputStream();
            long t2 = System.nanoTime();
            while (in.read(tmp) >= 0);
            in.close();
            return t2-t1;
        } catch (Exception e) {
            e.printStackTrace();
            InputStream err = conn.getErrorStream();
            try {
                while ((err != null) && err.read(tmp) >= 0) ;
                err.close();
            } catch (IOException f) {f.printStackTrace();}
        } finally {
            conn.disconnect();
        }
        return -1;
    }

    public static List<Measurement> downloadIgnore(boolean https, String host, int port, int reps) {
        byte[] tmp;
        List<Measurement> res = new ArrayList<>(reps);
        try {
            Socket s;
            if(https)
                s = SSLSocketFactory.getDefault().createSocket(host, port);
            else
                s = new Socket(host, port);
            InetAddress addr = s.getInetAddress();
            PrintStream ps = new PrintStream(s.getOutputStream());
            DataInputStream din = new DataInputStream(s.getInputStream());
            tmp = new byte[4096];

            for (int r = 0; r<reps; r++) {
                if (r % 10 == 0)
                    try {Thread.sleep(2000);} catch (InterruptedException e) {}
                long t1 = System.nanoTime();
                Date start = new Date();
                ps.print("GET /test.txt HTTP/1.1\r\n");
                if (r == reps - 1)
                    ps.print("Connection: close\r\n");
                ps.print("\r\n");
                ps.flush();
                HTTPInputStream in = new HTTPInputStream(port, https, new InetSocketAddress(addr, port), din);
                in.readHeaders();

                int len = (int)in.getHeaders().getContentLength();
                if (len > tmp.length)
                    tmp = new byte[len];
                din.readFully(tmp, 0, len);

                long t2 = System.nanoTime();
                res.add(new Measurement(t2-t1, start));
            }
            s.close();
            return res;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] download(String url) {
        try {
            URL target = new URI(url).toURL();
            URLConnection conn = target.openConnection();
            InputStream in = conn.getInputStream();
            byte[] tmp = new byte[4096];
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream dout = new DataOutputStream(bout);
            int read;
            while ((read = in.read(tmp)) >= 0)
                dout.write(tmp, 0, read);
            dout.flush();
            in.close();
            return bout.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
