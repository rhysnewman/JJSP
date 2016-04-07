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

import javafx.application.*;
import javafx.event.*;
import javafx.scene.*;
import javafx.beans.*;
import javafx.beans.value.*;
import javafx.scene.paint.*;
import javafx.scene.text.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.*;
import javafx.scene.layout.*;
import javafx.scene.input.*;
import javafx.scene.image.*;
import javafx.scene.web.*;

import javafx.geometry.*;
import javafx.util.*;
import javafx.stage.*;
import javafx.collections.*;
import javafx.collections.transformation.*;

import jjsp.util.*;
import jjsp.http.*;
import jjsp.engine.*;

public class URIResourceTree extends TreeView 
{
    private TreeItem root;
    private ImageIconCache iconCache;

    private volatile URI[] allURIs, uriRoots;
    
    private static final int UPDATE_DELAY = 2000;

    public URIResourceTree(ImageIconCache iconCache)
    {
        super(null);
        this.iconCache = iconCache;
        allURIs = new URI[0];
        uriRoots = new URI[0];

        setMinWidth(200);
        setPrefWidth(350);
        setMaxWidth(600);
        setMaxHeight(Double.MAX_VALUE);
            
        root = new TreeItem("Root");
        setRoot(root);
            
        setShowRoot(false);
        root.setExpanded(true);
        setStyle("-fx-font-size: 14px;"); //Sets the increment and decrement button sizes 

        setCellFactory((tree) -> 
                       { 
                           TextFieldTreeCell result = new TextFieldTreeCell(); 
                           result.setOnMouseClicked((evt) -> 
                                                 { 
                                                     URITreeItem item = (URITreeItem) result.getTreeItem();
                                                     if (evt.getClickCount() >= 2)
                                                     {
                                                         if (shouldOpenItem(item.getURI()))
                                                             showItem(item.uri);
                                                         else
                                                             item.forceUpdate();
                                                     }
                                                 });
                        
                           result.setContextMenu(new TreeContextMenu(result));
                           return result;
                       });
    }

    protected MenuItem[] additionalPopupItems(URI uri, TreeCell target)
    {
        return null;
    }

    class TreeContextMenu extends ContextMenu
    {
        TextFieldTreeCell treeCell;
        MenuItem openWeb, copyFilename, removeURI, open, unmountDir, launchExternalBrowser, openAsText;

        TreeContextMenu(TextFieldTreeCell treeCell)
        {
            this.treeCell = treeCell;
            
            openWeb = new MenuItem("Open in Web View");
            openWeb.setOnAction((evt) -> showItem(getURI()) ); 
            removeURI = new MenuItem("Remove from Tree");
            removeURI.setOnAction((evt) -> removeURI(getURI()) ); 
            open = new MenuItem("Open View");
            open.setOnAction((evt) -> showItem(getURI()) ); 
            copyFilename = new MenuItem("Copy Name");
            copyFilename.setOnAction((evt) -> 
                                     {
                                         Clipboard clipboard = Clipboard.getSystemClipboard();
                                         ClipboardContent content = new ClipboardContent();
                                         content.putString(treeCell.getText());
                                         clipboard.setContent(content);
                                     });

            unmountDir = new MenuItem("Remove Directory from Tree");
            unmountDir.setOnAction((evt) -> removeURI(getURI()) ); 
            launchExternalBrowser = new MenuItem("Launch in External Browser");
            launchExternalBrowser.setOnAction((evt) ->  {try { java.awt.Desktop.getDesktop().browse(getURI()); } catch (Exception e) {}}); 

            openAsText = new MenuItem("Open in Text Editor");
            openAsText.setOnAction((evt) -> showItemAsText(getURI()));
        }
        
        protected URI getURI()
        {
            URITreeItem item = (URITreeItem) treeCell.getTreeItem();
            if (item == null)
                return null;
            return item.uri;
        }

        protected boolean shouldShow()
        {
            URI uri = getURI();
            if (uri == null)
                return false;

            boolean isFile = uri.getScheme().equals("file");   
            if (isFile)
            {
                if (isDirectory(uri))
                {
                    if (isRoot(uri))
                        getItems().setAll(open, copyFilename, openAsText, new SeparatorMenuItem(), unmountDir);
                    else
                        getItems().setAll(open, copyFilename, openAsText);
                }
                else
                    getItems().setAll(copyFilename, openAsText);
            }
            else
                getItems().setAll(openWeb, launchExternalBrowser, copyFilename, openAsText, new SeparatorMenuItem(), removeURI);

            MenuItem[] additionalItems = additionalPopupItems(uri, treeCell);
            if (additionalItems != null)
                getItems().addAll(additionalItems);
            
            return true;
        }
        
        public void show(Node anchor, double x, double y)
        {
            if (shouldShow())
                super.show(anchor, x, y);
        }

        public void show(Node anchor, Side side, double dx, double dy)
        {
            if (shouldShow())
                super.show(anchor, side, dx, dy);
        }
    }

    public static String uriToString(URI uri)
    {
        if (uri == null)
            return "";

        String s = uri.toString();
        if (s.startsWith("file:///"))
            return "file:/"+s.substring(8);
        return s;
    }
 
    public static String getLabel(URI uri, String pathLabel)
    {
        if (uri == null)
            return pathLabel;
        
        if (pathLabel == null)
            pathLabel = uri.getPath().trim();
        if (pathLabel.equals("/"))
            pathLabel = "";

        String host = uri.getHost();
        if (host == null)
            host = "";
        else if (host.endsWith("/"))
            host = host.substring(0, host.length()-1);

        if (uri.getScheme().equals("http"))
        {
            int port = uri.getPort();
            if ((port != 80) && (port > 0))
                return host+":"+port+pathLabel;
            else
                return host+pathLabel;
        }
        else if (uri.getScheme().equals("https"))
        {
            int port = uri.getPort();
            if ((port != 443) && (port > 0))
                return host+":"+port+pathLabel;
            else
                return host+pathLabel;
        }
        else if (uri.getScheme().equals("file"))
        {
            if (pathLabel.length() == 0)
                return "/";
            return pathLabel;
        }
        else
            return uriToString(uri);
    }
        
    public static String getShortLabel(URI uri)
    {
        if (uri == null)
            return "";

        String s = uri.getPath();
        if (s.endsWith("/"))
            s = s.substring(0, s.length()-1);
        int slash = s.lastIndexOf("/");
        if ((slash >= 0) && (slash < s.length()-1))
            s = s.substring(slash+1);
        
        if (s.length() > 0)
            return s;
        
        return getLabel(uri, "");
    }

    public static URI getRootURI(URI uri)
    {
        if (uri == null)
            return null;
        return uri.resolve("/");
    }

    public static URI getParentURI(URI uri)
    {
        try
        {
            URI parentURI = uri.resolve(".");
            if (parentURI.equals(uri))
                parentURI = uri.resolve("..");

            String s = uriToString(parentURI);
            if (s.equals("file:/") || s.equals(uri+".."))
                return null;

            return parentURI;
        }
        catch (Exception e)
        {
            return null;
        }
    }
    
    public static URI[] getURIPaths(URI uri)
    {
        ArrayList buf = new ArrayList();
        while (uri != null)
        {
            buf.add(0, uri);
            uri = getParentURI(uri);
        }

        URI[] result = new URI[buf.size()];
        buf.toArray(result);
        return result;
    }

    protected URI[] findURIRoots()
    {
        ArrayList buf = new ArrayList();
        for (int i=0; i<allURIs.length; i++)
        {
            URI tgt = allURIs[i];
            String ss = uriToString(tgt);
            boolean isRoot = true;

            for (int j=0; j<allURIs.length; j++)
            {
                if (i == j)
                    continue;
                if (ss.startsWith(uriToString(allURIs[j])))
                {
                    isRoot = false;
                    break;
                }
            }

            if (isRoot)
                buf.add(tgt);
        }

        URI[] result = new URI[buf.size()];
        buf.toArray(result);
        return result;
    }

    public boolean isRoot(URI uri)
    {
        if (uri == null)
            return false;

        for (int i=0; i<uriRoots.length; i++)
            if (uri.equals(uriRoots[i]))
                return true;
        return false;
    }

    public boolean contains(URI uri)
    {
        if (uri == null)
            return false;

        for (int i=0; i<allURIs.length; i++)
            if (uri.equals(allURIs[i]))
                return true;
        return false;
    }

    public boolean containsRootFor(URI uri)
    {
        URI[] path = getURIPaths(uri);
        for (int i=0; i<uriRoots.length; i++)
            for (int j=0; j<path.length; j++)
                if (uriRoots[i].equals(path[j]))
                    return true;

        return false;
    }

    public boolean addURI(URI uri)
    {
        if (uri == null)
            return false;
        
        TreeSet ts = new TreeSet();
        for (int i=0; i<allURIs.length; i++)
            ts.add(allURIs[i]);

        if (ts.contains(uri))
            return false;

        ts.add(uri);
        allURIs = new URI[ts.size()];
        ts.toArray(allURIs);
        uriRoots = findURIRoots();

        forceUpdate();
        rescanTree();
        return true;
    }

    public void removeAll()
    {
        URI[] current = allURIs;
        for (int i=0; i<current.length; i++)
            try
            {
                removeURI(current[i]);
            }
            catch (Exception e) {}
    }

    public boolean removeURI(URI uri)
    {
        if (uri == null)
            return false;

        String ss = uriToString(uri);
        TreeSet ts = new TreeSet();

        boolean removed = false;
        for (int i=0; i<allURIs.length; i++)
            if (!uriToString(allURIs[i]).startsWith(ss))
                ts.add(allURIs[i]);
            else
                removed = true;
        
        if (!removed)
            return false;

        allURIs = new URI[ts.size()];
        ts.toArray(allURIs);
        uriRoots = findURIRoots();

        forceUpdate();
        rescanTree();
        return true;
    }

    public void removeDeletedFileURIs()
    {
        ArrayList buf = new ArrayList();
        for (int i=0; i<allURIs.length; i++)
        {
            URI uri = allURIs[i];
            if (uri.getScheme().equals("file"))
            {
                File f = new File(uri);
                if (f.exists())
                    buf.add(uri);
            }
            else
                buf.add(uri);
        }
        
        allURIs = new URI[buf.size()];
        buf.toArray(allURIs);
    }
        
    protected String getLabelFor(URI uri)
    {
        for (int i=0; i<uriRoots.length; i++)
            if (uriRoots[i].equals(uri))
                return getLabel(uri, null);
        return getShortLabel(uri);
    }

    public URI[] getURIRoots()
    {
        URI[] roots = uriRoots;
        URI[] result = new URI[roots.length];
        System.arraycopy(roots, 0, result, 0, result.length);
        return result;
    }

    public URI[] getURIs()
    {
        URI[] all = allURIs;
        URI[] result = new URI[all.length];
        System.arraycopy(all, 0, result, 0, result.length);
        return result;
    }

    public URI[] getChildrenURIs(URI uri)
    {
        if (uri == null)
            return new URI[0];

        ArrayList buf = new ArrayList();
        try
        {
            File f = new File(uri);
            if (f.isDirectory())
            {
                File[] ff = f.listFiles();
                for (int i=0; i<ff.length; i++)
                    if (!ff[i].getName().startsWith("."))
                        buf.add(ff[i].toURI());
            }
        }
        catch (Exception e) {}
            
        for (int i=0; i<allURIs.length; i++)
        {
            URI[] paths = getURIPaths(allURIs[i]);
            for (int j=0; j<paths.length-1; j++)
            {
                if (!paths[j].equals(uri))
                    continue;

                buf.add(paths[j+1]);
                break;    
            }
        }
            
        URI[] result = new URI[buf.size()];
        buf.toArray(result);
        return result;
    }

    public boolean isDirectory(URI uri)
    {
        if (uri == null)
            return false;
        try
        {
            return new File(uri).isDirectory();
        }
        catch (Exception e) {}
        return  uri.resolve(".").equals(uri);
    }

    protected boolean shouldOpenItem(URI uri)
    {
        return !isDirectory(uri) || uri.getScheme().startsWith("http");
    }
        
    protected void showItem(URI uri)
    {
    }
   
    protected void showItemAsText(URI uri)
    {
    }

    public int getDisplayRowOf(URI uri)
    {
        for (int i=0; true; i++)
        {
            TreeItem item = getTreeItem(i);
            if (item == null)
                return -1;
            if (!(item instanceof URITreeItem))
                continue;
            if (((URITreeItem) item).getURI().equals(uri))
                return i;
        }
    }

    public int getDisplayRowOf(TreeItem node)
    {
        for (int i=0; true; i++)
        {
            TreeItem item = getTreeItem(i);
            if (item == null)
                return -1;
            if (node == item)
                return i;
        }
    }

    private URITreeItem getTreeItemFor(TreeItem tt, URI uri)
    {
        if (tt instanceof URITreeItem)
        {
            URITreeItem uriItem = (URITreeItem) tt;
            if (uriItem.uri.equals(uri))
                return uriItem;
        }

        ObservableList ll = tt.getChildren();
        for (int i=0; i<ll.size(); i++)
        {
            URITreeItem item = (URITreeItem) ll.get(i);
            URITreeItem result = getTreeItemFor(item, uri);
            if (result != null)
                return result;
        }

        return null;
    }

    public URITreeItem getTreeItemFor(URI uri)
    {
        return getTreeItemFor(root, uri);
    }

    public boolean expandAndShow(URI uri)
    {
        URI[] path = getURIPaths(uri);

        ObservableList ll = root.getChildren();
        for (int i=0; i<ll.size(); i++)
        {
            URITreeItem tt = (URITreeItem) ll.get(i);
            URI startURI = tt.getURI();
            
            for (int j=0; j<path.length; j++)
            {
                if (!path[j].equals(startURI))
                    continue;
                
                URITreeItem item = tt.find(path, j, true);
                if (item == null)
                    item = tt;
                
                int displayIndex = getDisplayRowOf(item);
                if (displayIndex >= 0)
                {
                    scrollTo(displayIndex);
                    getSelectionModel().select(displayIndex);
                }
                return true;
            }
        }

        return false;
    }

    public void forceUpdate()
    {
        ObservableList ll = root.getChildren();
        for (int i=ll.size()-1; i>=0; i--)
            ((URITreeItem) ll.get(i)).forceUpdate();
    }
        
    public void rescanTree()
    {
        removeDeletedFileURIs();
            
        URI[] roots = getURIRoots();
        ObservableList ll = root.getChildren();

        alignChildrenToList(ll, roots);
        for (int i=ll.size()-1; i>=0; i--)
            ((URITreeItem) ll.get(i)).rescanNode();
    }

    private void alignChildrenToList(ObservableList ll, URI[] uris)
    {
        HashSet set = new HashSet();
        for (int i=0; i<uris.length; i++)
            set.add(uris[i]);

        for (int i=ll.size()-1; i>=0; i--)
        {
            URI u = ((URITreeItem) ll.get(i)).uri;
            if (set.contains(u))
                set.remove(u);
            else
                ll.remove(i);
        }

        Iterator itt = set.iterator();
        while (itt.hasNext())
        {
            URI uri = (URI) itt.next();
            addChildInLexographicOrder(ll, new URITreeItem(uri));
        }
    }

    private int compare(URI uri1, URI uri2)
    {
        String s1 = uriToString(uri1).toLowerCase();
        String s2 = uriToString(uri2).toLowerCase();
        boolean http1 = s1.startsWith("http");
        boolean http2 = s2.startsWith("http");
        
        if (!http1 && !http2)
        {
            boolean dir1 = isDirectory(uri1);
            boolean dir2 = isDirectory(uri2);
            
            if (dir1 && !dir2)
                return -1;
            else if (!dir1 && dir2)
                return 1;
            return s1.compareTo(s2);
        }
        else if (http1 && http2)
            return s1.compareTo(s2);
        else if (http1)
            return -1;
        else
            return 1;
    }

    private void addChildInLexographicOrder(ObservableList ll, URITreeItem newChild)
    {
        URI childURI = newChild.uri;
            
        for (int i=0; i<ll.size(); i++)
        {
            URITreeItem next = (URITreeItem) ll.get(i);
            if (compare(childURI, next.uri) > 0)
                continue;
                        
            ll.add(i, newChild);
            return;
        }

        ll.add(newChild);
    }

    public class URITreeItem extends TreeItem
    {
        URI uri;
        boolean isLeaf;
        long lastCheckTime;

        public URITreeItem(URI uri)
        {
            this(getLabelFor(uri), uri);
        }

        public URITreeItem(String label, URI uri)
        {
            super(label);
            this.uri = uri;
            lastCheckTime = 0;
            isLeaf = !isDirectory(uri) || (getChildrenURIs(uri).length == 0);
            setGraphic(iconCache.createImageViewFor(uri));
        }

        URITreeItem find(URI[] path, int pos, boolean expand)
        {
            if (path[pos].equals(uri))
            {
                if (pos == path.length-1)
                    return this;
                if (expand)
                    setExpanded(true);

                ObservableList cc = getChildren();
                for (int i=0; i<cc.size(); i++)
                {
                    URITreeItem result = ((URITreeItem) cc.get(i)).find(path, pos+1, expand);
                    if (result != null)
                        return result;
                }
            }
            
            return null;
        }
        

        public URI getURI()
        {
            return uri;
        }

        public boolean isLeaf()
        {
            return isLeaf;
        }

        public ObservableList getChildren()
        {
            if (System.currentTimeMillis() - lastCheckTime > UPDATE_DELAY)
            {
                lastCheckTime = System.currentTimeMillis();
                URI[] uris = getChildrenURIs(uri);
                if (uris.length > 256)
                {
                    URI[] reduced = new URI[256];
                    System.arraycopy(uris, 0, reduced, 0, 256);
                    uris = reduced;
                }
                isLeaf = !isDirectory(uri) || (uris.length == 0);
                alignChildrenToList(super.getChildren(), uris);
            }
            
            return super.getChildren();            
        }

        void forceUpdate()
        {
            lastCheckTime = 0;

            ObservableList cc = null;
            if (isDirectory(uri))
                cc = getChildren();

            if (!isExpanded())
                return;
            if (cc == null)
                cc = getChildren();

            for (int i=0; i<cc.size(); i++)
                ((URITreeItem) cc.get(i)).forceUpdate();
        }
            
        void rescanNode()
        {
            if (!isExpanded())
                return;

            ObservableList cc = getChildren();
            for (int i=0; i<cc.size(); i++)
                ((URITreeItem) cc.get(i)).rescanNode();
        }

        public String toString()
        {
            return "TreeItem["+uri+"]";
        }
    }
}
