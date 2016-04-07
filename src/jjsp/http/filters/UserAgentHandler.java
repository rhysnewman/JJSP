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
package jjsp.http.filters;

import jjsp.http.*;

import java.io.IOException;

public class UserAgentHandler implements HTTPRequestFilter
{
    public enum DeviceType {DESKTOP, MOBILE, TABLET};

    //kept because I don't know if this is used somewhere else
    public static final int DESKTOP = DeviceType.DESKTOP.ordinal();
    public static final int MOBILE = DeviceType.MOBILE.ordinal();
    public static final int TABLET = DeviceType.TABLET.ordinal();
    
    private String name;

    private final HTTPRequestFilter desktop, mobile, tablet;

    public UserAgentHandler(String name, HTTPRequestFilter desktop, HTTPRequestFilter mobile, HTTPRequestFilter tablet) 
    {
        this.name = name;
        this.desktop = desktop;
        this.mobile = mobile;
        this.tablet = tablet;
    }

    public void close() 
    {
        if (desktop != null)
            desktop.close();
        if (mobile != null)
            mobile.close();
        if (tablet != null)
            tablet.close();
    }

    public HTTPFilterChain filterRequest(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state)
    {
        HTTPFilterChain myChain = new HTTPFilterChain(name, chain);
        HTTPRequestHeaders reqHeaders = request.getHeaders();

        String userAgent = request.getHeaders().getHeader("User-Agent");
        if (userAgent == null)
            userAgent = "";
        //System.out.println(userAgent);
        DeviceType type = getDeviceType(userAgent);

        HTTPRequestFilter result = null;
        switch (type)
        {
        case DESKTOP:
            result = desktop;
            myChain.report = "DESKTOP";
            break;
        case MOBILE:
            result = mobile;
            myChain.report = "MOBILE";
            break;
        case TABLET:
            result = tablet;
            myChain.report = "TABLET";
            break;
        default:
            result = desktop;
            myChain.report = "ASSUMED DESKTOP";
            break;
        }

        return result.filterRequest(myChain, request, response, state);
    }


    public static DeviceType getDeviceType(String userAgent){
        DeviceType type;

        if (userAgent.contains("Linux") && !userAgent.contains("Android") && !userAgent.contains("Silk")) // First two cases handle Amazon devices as well
            type = DeviceType.DESKTOP;
        else if (userAgent.contains("Android"))
        {
            if (userAgent.contains("Mobile") && !userAgent.contains("SCH-I800"))
                type = DeviceType.MOBILE;
            else
                type = DeviceType.TABLET;
        }
        else if (userAgent.contains("iPad")) // also has IPhone
            type = DeviceType.TABLET;
        else if (userAgent.contains("iPhone"))
            type = DeviceType.MOBILE;
        else if (userAgent.contains("Windows"))
        {
            if (userAgent.contains("Phone"))
                type = DeviceType.MOBILE;
            else
                type = DeviceType.DESKTOP;
        }
        else if (userAgent.contains(" KF")) // kindle fire
            type = DeviceType.TABLET;
        else if (userAgent.contains("Macintosh"))
        {
            if (userAgent.contains("Silk"))
                type = DeviceType.TABLET;
            else
                type = DeviceType.DESKTOP;
        }
        else if (userAgent.contains("Symbian"))
            type = DeviceType.MOBILE;
        else if (userAgent.contains("Blackberry") || userAgent.contains("Mobile"))
            type = DeviceType.MOBILE;
        else if (userAgent.contains("PlayBook"))
            type = DeviceType.TABLET;
        else
            type = DeviceType.DESKTOP;

        return type;
    }
}
