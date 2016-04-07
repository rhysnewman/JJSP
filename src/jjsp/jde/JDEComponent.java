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
package jjsp.jde;

import java.net.*;
import java.io.*;
import java.util.*;

import javafx.scene.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.control.*;

import jjsp.engine.*;
import jjsp.util.*;

public class JDEComponent extends BorderPane
{
    protected URI componentURI;
    protected Throwable currentError;

    protected volatile long lastModified;
    protected volatile boolean isDisplayed;
    protected volatile URI[] generatedOutputs;

    private static String version = Utils.getJarVersion();

    public JDEComponent(URI uri)
    {
        this.componentURI = uri;
        generatedOutputs = new URI[0];
        isDisplayed = false;
        lastModified = getLastModifiedFromURI();
    }

    public URI getURI()
    {
        return componentURI;
    }

    public boolean loadFromURI()
    {
        lastModified = System.currentTimeMillis();
        return true;
    }

    public long getLastModified()
    {
        return lastModified;
    }

    public String getFullTitle()
    {
        return URIResourceTree.getLabel(componentURI, null);
    }

    public String getShortTitle()
    {
        return URIResourceTree.getShortLabel(componentURI);
    }

    public URI[] getGeneratedOutputs()
    {
        return generatedOutputs;
    }

    public Menu[] createMenus()
    {
        return null;
    }

    protected long getLastModifiedFromURI()
    {
        try
        {
            File f = new File(getURI());
            return f.lastModified();
        }
        catch (Exception e) {}

        return System.currentTimeMillis();
    }

    public void reloadContent()
    {
        lastModified = getLastModifiedFromURI();
    }

    public void closeServices()
    {
    }

    public void loadAutoSavedData(byte[] data)
    {
        if (data != null)
            lastModified = System.currentTimeMillis();
    }

    public byte[] getDataForAutoSave()
    {
        return null;
    }

    public void setDisplayed(boolean isShowing) 
    {
        isDisplayed = isShowing;
    }

    public boolean isDisplayed()
    {
        return isDisplayed;
    }

    public boolean isDisposed()
    {
        return false;
    }

    public String getVersion()
    {
        return version;
    }

    public boolean isShowingError()
    {
        return currentError != null;
    }

    public void clearError() 
    {
        currentError = null;
    }

    public void setStatus(String message, Throwable t) 
    {
        currentError = t;
        if (t != null)
            t.printStackTrace();
    }
}
