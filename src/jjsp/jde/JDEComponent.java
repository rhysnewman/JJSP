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
