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
package jjsp.engine;

import java.io.*;
import java.util.*;
import java.time.LocalDate;

public class Sitemap
{
    protected String urlStem;
    protected TreeMap registeredURLs;

    public static enum ChangeFreq 
    {
        ALWAYS("always"), HOURLY("hourly"), DAILY("daily"), WEEKLY("weekly"), MONTHLY("monthly"), YEARLY("yearly"), NEVER("never");

        private final String xmlString;

        private ChangeFreq(String xmlString)
        {
            this.xmlString = xmlString;
        }
        
        public String toString()
        {
            return xmlString;
        }
    }

    public Sitemap(String urlStem)
    {
        this.urlStem = urlStem;
        registeredURLs = new TreeMap();
    }
    
    public void registerURL(String url)
    {
    	ChangeFreq freq = ChangeFreq.MONTHLY;
    	LocalDate now = LocalDate.now();
    	registerURL(url, now.getYear(), now.getMonthValue(), now.getDayOfMonth(), freq);
    }
    
    public void registerURL(String url, ChangeFreq freq)
    {
        registerURL(url, null, freq, 50);
    }
    
    public void registerURL(String url, String freq, int priorityPercent)
    {
    	LocalDate now = LocalDate.now();
    	String lastChangeString = String.format("%04d-%02d-%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
    	registerURL(url, lastChangeString, ChangeFreq.valueOf(freq), priorityPercent);
    }

    public void registerURL(String url, ChangeFreq freq, int priorityPercent)
    {
        registerURL(url, null, freq, priorityPercent);
    }

    public void registerURL(String url, int lastModYear, int lastModMonth, int lastModDay, ChangeFreq freq)
    {
        registerURL(url, String.format("%04d-%02d-%02d", lastModYear, lastModMonth, lastModDay), freq, 50);
    }

    public void registerURL(String url, String lastChangeString, ChangeFreq freq)
    {
        registerURL(url, lastChangeString, freq, 50);
    }

    public void registerURL(String url, String lastChangeString, ChangeFreq freq, int priorityPercent)
    {
        registeredURLs.put(url, new Object[]{lastChangeString, freq, Integer.valueOf(priorityPercent)});
    }

    public static ChangeFreq lookupFreq(Object ff)
    {
        String s = ff.toString().toLowerCase();
        switch (s)
        {
        case "always":
            return ChangeFreq.ALWAYS;
        case "hourly":
            return ChangeFreq.HOURLY;
        case "daily":
            return ChangeFreq.DAILY;
        case "never":
            return ChangeFreq.NEVER;
        }
        return ChangeFreq.WEEKLY;
    }

    public String toXMLString()
    {
        StringWriter sw = new StringWriter();
        
        sw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sw.write("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        Iterator itt = registeredURLs.keySet().iterator();
        while (itt.hasNext())
        {
            String key = (String) itt.next();
            Object[] vals = (Object[]) registeredURLs.get(key);
            
            sw.write("<url>\n");
            sw.write("<loc>"+urlStem+key+"</loc>\n");
            if (vals[0] != null)
                sw.write("<lastmod>"+vals[0]+"</lastmod>\n");
            if (vals[1] != null)
                sw.write("<changefreq>"+vals[1]+"</changefreq>\n");
            sw.write("<priority>"+(((Number) vals[2]).doubleValue()/100)+"</priority>\n");
            sw.write("</url>\n");
        }

        sw.write("</urlset>\n");
        return sw.toString();
    }
}
