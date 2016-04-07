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

/**
 * Created by Jason Lau - 18 Jan 2016 22:43:28
 */

public class SitemapIndex
{
    protected String urlStem;
    protected TreeMap registeredSitemapURLs;

    public SitemapIndex(String urlStem)
    {
        this.urlStem = urlStem;
        registeredSitemapURLs = new TreeMap();
    }

    public void registerSitemapURL(String url)
    {
        LocalDate now = LocalDate.now();
        registerSitemapURL(url, now.getYear(), now.getMonthValue(), now.getDayOfMonth());
    }

    public void registerSitemapURL(String url, int lastChangeYear, int lastChangeMonth, int lastChangeDay)
    {
        registerSitemapURL(url, String.format("%04d-%02d-%02d", lastChangeYear, lastChangeMonth, lastChangeDay));
    }

    public void registerSitemapURL(String url, String lastChangeString)
    {
        registeredSitemapURLs.put(url, lastChangeString);
    }

    public String toXMLString()
    {
        StringWriter sw = new StringWriter();

        sw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sw.write("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        Iterator itt = registeredSitemapURLs.keySet().iterator();
        while (itt.hasNext())
        {
            String key = (String) itt.next();
            String val = (String) registeredSitemapURLs.get(key);

            sw.write("<sitemap>\n");
            sw.write("<loc>" + urlStem + key + "</loc>\n");
            if ( val != null )
                sw.write("<lastmod>" + val + "</lastmod>\n");
            sw.write("</sitemap>\n");
        }
        sw.write("</sitemapindex>\n");
        return sw.toString();
    }
}
