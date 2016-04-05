package jjsp.engine;

import java.io.*;
import java.util.*;

/**
 * Created by Jason Lau - 21 Jan 2016 17:18:49
 */

public class RobotsTxt
{
    protected String urlStem;
    protected TreeMap<String, String[]> userAgentBlocks;
    protected ArrayList<String> sitemaps;

    public RobotsTxt(String urlStem)
    {
        this.urlStem = urlStem;
        userAgentBlocks = new TreeMap<String, String[]>();
        sitemaps = new ArrayList<String>();
    }

    public void registerUserAgent(String userAgent, String[] disallowUrls)
    {
        userAgentBlocks.put(userAgent, disallowUrls);
    }

    public void registerSitemapUrl(String sitemapUrl)
    {
        sitemaps.add(sitemapUrl);
    }

    public String toTxtString()
    {
        StringWriter sw = new StringWriter();

        Iterator itt = userAgentBlocks.keySet().iterator();
        while (itt.hasNext())
        {
            String key = (String) itt.next();
            sw.write("User-agent: " + key + "\n");

            String[] disallowUrls = userAgentBlocks.get(key);
            for ( int i = 0; i < disallowUrls.length; i++ ) {
                if ( disallowUrls[i] != null )
                    sw.write("Disallow: " + disallowUrls[i] + "\n");
            }

            sw.write("\n");

            for ( int i = 0; i < sitemaps.size(); i++ ) {
                sw.write("Sitemap: " + urlStem + sitemaps.get(i) + "\n");
            }
        }

        return sw.toString();
    }
}
