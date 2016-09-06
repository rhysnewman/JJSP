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
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.function.*;
import java.util.*;

import java.awt.Graphics2D;
import java.awt.image.*;

import javax.script.*;

import javafx.application.*;
import javafx.event.*;
import javafx.scene.*;
import javafx.beans.*;
import javafx.beans.value.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.input.*;
import javafx.scene.image.*;
import javafx.scene.transform.*;
import javafx.scene.web.*;
import javafx.scene.effect.*;

import javafx.geometry.*;
import javafx.util.*;
import javafx.stage.*;
import javafx.collections.*;

import jjsp.engine.*;
import jjsp.util.*;
import jjsp.http.*;

public class URITabPane extends TabPane
{
    protected URITab draggingTab;
    protected BorderPane dragImageView;
    protected ImageIconCache iconCache;

    public URITabPane(ImageIconCache iconCache)
    {
        this.iconCache = iconCache;
        dragImageView = new BorderPane();

        DropShadow borderGlow = new DropShadow();
        borderGlow.setOffsetY(10f);
        borderGlow.setOffsetX(10f);
        borderGlow.setColor(Color.BLACK);
        borderGlow.setWidth(80);
        borderGlow.setHeight(80);
        dragImageView.setEffect(borderGlow);

        getSelectionModel().selectedItemProperty().addListener((obj, oldTab, newTab) -> tabSelectionChanged(oldTab, newTab));
        setStyle("-fx-open-tab-animation: NONE; -fx-close-tab-animation: NONE;");

        setOnMousePressed((evt) -> 
                          {
                              Tab tt = getSelectionModel().getSelectedItem();
                              if (tt != null)
                                  ((Node) tt.getContent()).requestFocus();

                              if (draggingTab != null)
                              {
                                  double h1 = ((Region) draggingTab.getContent()).getHeight();
                                  dragImageView.relocate(evt.getX(), getHeight() - h1);
                              }
                          });

        setOnMouseReleased(evt ->
                           {
                               int dragFromIndex = -1;
                               int dragToIndex = -1;
                               
                               ObservableList tabs = getTabs();
                               for (int i=0; i<tabs.size(); i++)
                               {
                                   URITab tt = (URITab) tabs.get(i);
                                   
                                   if (tt.isDropIndicatorShowing())
                                       dragToIndex = i;
                                   if (tt == draggingTab)
                                       dragFromIndex = i;
                                   tt.setDropIndicatorShowing(false);
                               }

                               if ((dragFromIndex != -1) && (dragToIndex != -1) && (dragFromIndex != dragToIndex))
                               {
                                   URITab toMove = (URITab) tabs.remove(dragFromIndex);
                                   int newIndex = Math.max(0, dragToIndex);
                                   tabs.add(newIndex, toMove);
                                   toMove.readded();
                                   getSelectionModel().select(newIndex);
                                   draggingTab.requestFocus();
                               }

                               getChildren().remove(dragImageView);
                               draggingTab = null;
                           });

        setOnMouseDragged(evt -> 
                          {
                              if (draggingTab == null)
                                  return;

                              double h1 = ((Region) draggingTab.getContent()).getHeight();
                              double tabHeaderHeight = getHeight() - h1;
                              dragImageView.relocate(evt.getX(), tabHeaderHeight);
                              
                              SingleSelectionModel sm = getSelectionModel();
                              ObservableList tabs = getTabs();

                              for (int i=0; i<tabs.size(); i++)
                              {
                                  URITab tt = (URITab) tabs.get(i);
                                  Bounds b2 = tt.getGraphic().getLayoutBounds();
                                  Transform tfm2 = tt.getGraphic().getLocalToSceneTransform();
                                  b2 = tfm2.transform(b2);
                                  
                                  boolean isHoveringOver = b2.contains(evt.getSceneX(), b2.getMinY() + b2.getHeight()/2);

                                  if (draggingTab != tt)
                                      tt.setDropIndicatorShowing(isHoveringOver);

                                  if (isHoveringOver && !sm.isSelected(i))
                                  {
                                      sm.select(i);
                                      tt.requestFocus();
                                  }
                              }
                          });
    }

    protected void tabSelectionChanged(Tab oldTab, Tab newTab)
    {
        if (newTab == null)
            selectedComponentChanged(null);
        else 
            selectedComponentChanged((JDEComponent) newTab.getContent());
    }

    protected void selectedComponentChanged(JDEComponent jde)
    {
    }
        
    public URITab getTabByURI(URI uri)
    {
        if (uri == null)
            return null;

        ObservableList tabs = getTabs();
        for (int i=0; i<tabs.size(); i++)
        {
            URITab tt = (URITab) tabs.get(i);
            if (tt.jdeComponent.getURI().equals(uri))
                return tt;
        }

        return null;
    }
    
    public JDEComponent getJDEComponentByURI(URI uri)
    {
        Tab tt = getTabByURI(uri);
        if (tt == null)
            return null;
        return (JDEComponent) tt.getContent();
    }
    
    public JDEComponent[] getJDEComponents()
    {
        ObservableList<Tab> tabs = getTabs();
        JDEComponent[] result = new JDEComponent[tabs.size()];
        for (int i=0; i<result.length; i++)
            result[i] = (JDEComponent) tabs.get(i).getContent();
        return result;
    }
    
    public URI[] getJDEComponentURIs()
    {
        JDEComponent[] comps = getJDEComponents();
        URI[] result = new URI[comps.length];
        for (int i=0; i<result.length; i++)
            result[i] = comps[i].getURI();
        return result;
    }
    
    protected void refreshTab(boolean hasGainedFocus, boolean isDisplayed, boolean wasUpdated, Tab tab, JDEComponent jde)
    {
    }

    public void refreshTabs()
    {
        int selected = getSelectionModel().getSelectedIndex();

        ObservableList<Tab> tabs = getTabs();
        for (int i=tabs.size()-1; i>=0; i--)
        {
            URITab tt = (URITab) tabs.get(i);
            JDEComponent jde = (JDEComponent) tt.getContent();
            if (jde.isDisposed())
            {
                tabs.remove(i);
                tabRemoved(tt, jde);
                continue;
            }

            boolean wasUpdated = tt.updateDisplay();
            refreshTab(tt.hasGainedFocus(), i == selected, wasUpdated, tt, jde);
            jde.setDisplayed(i == selected);
        }
    }

    public class URITab extends Tab
    {
        public final JDEComponent jdeComponent;

        private String fullTitle;
        private Text labelText;
        private URI[] currentOutputs;
        private BorderPane iconBox, mainMarker;
        private CustomGraphic tabHeader;
        private boolean isDropIndicatorShowing, showingError, hasGainedFocus, hasFocus, isMainProject;
        
        public URITab(JDEComponent jde)
        {
            this.jdeComponent = jde;
            setContent(jde);
            currentOutputs = jdeComponent.getGeneratedOutputs();

            iconBox = new BorderPane();
            mainMarker = new BorderPane();
            labelText = new Text();
            tabHeader = new CustomGraphic();
            tabHeader.getStyleClass().add("custom");
            tabHeader.getChildren().addAll(iconBox, labelText, mainMarker);
            setGraphic(tabHeader);
            iconBox.setCenter(iconCache.createImageViewFor(jde.getURI()));

            setContextMenu(new TabContextMenu());
            setOnClosed((evt) -> tabRemoved(this, jdeComponent));

            isDropIndicatorShowing = false;
            hasFocus = hasGainedFocus = false;
            showingError = false;
            fullTitle = "";

            updateDisplay();
        }

        void readded()
        {
            // Need to do this as removing and adding the component appears to lose the event listeners ;-(
            setContextMenu(new TabContextMenu());
            setOnClosed((evt) -> tabRemoved(this, jdeComponent));
        }

        public boolean markedAsMain()
        {
            return isMainProject;
        }

        public void markAsMain(boolean value)
        {
            if (value)
                URITabPane.this.markedAsMain(this);

            isMainProject = value;
            if (value)
                mainMarker.setCenter(new Rectangle(8, 8, Color.ORANGE));
            else
                mainMarker.setCenter(null);
        }

        public URI getURI()
        {
            return jdeComponent.getURI();
        }

        public boolean hasFocus()
        {
            return hasFocus;
        }

        public boolean hasGainedFocus()
        {
            return hasGainedFocus;
        }

        public void requestFocus()
        {
            jdeComponent.requestFocus();
        }

        public void clearError()
        {
            jdeComponent.clearError();
            updateDisplay();
        }

        public boolean updateDisplay()
        {
            boolean wasUpdated = false;

            URI uri = jdeComponent.getURI();
            if (!uri.equals(tabHeader.currentURI) || !fullTitle.equals(jdeComponent.getFullTitle()))
            {
                labelText.setText(jdeComponent.getShortTitle());
                setText(jdeComponent.getShortTitle());
                fullTitle = jdeComponent.getFullTitle();

                String s = uri.getScheme();
                if (s.equals("file"))
                    labelText.setFill(Color.BLUE);
                else if (s.startsWith("http"))
                    labelText.setFill(Color.GREEN);
                else
                    labelText.setFill(Color.MAGENTA);
                
                tabHeader.currentURI = uri;
                wasUpdated = true;
            }
            
            if (jdeComponent.isShowingError() != showingError)
            {
                showingError = jdeComponent.isShowingError();

                if (showingError)
                    iconBox.setCenter(new Circle(8, Color.RED));
                else
                    iconBox.setCenter(iconCache.createImageViewFor(uri));
                wasUpdated = true;
            }

            URI[] newOutputs = jdeComponent.getGeneratedOutputs();
            if (newOutputs != currentOutputs)
            {
                currentOutputs = newOutputs;
                wasUpdated = true;
            }

            boolean hasFocusNow = false;
            Node n = getScene().getFocusOwner();
            while (n != null)
            {
                if (n == jdeComponent)
                {
                    hasFocusNow = true;
                    break;
                }
                n = n.getParent();
            }

            hasGainedFocus = !hasFocus && hasFocusNow;
            hasFocus = hasFocusNow;
                
            return wasUpdated;
        }

        public boolean setDropIndicatorShowing(boolean value)
        {
            if (value)
                tabHeader.setStyle("-fx-border-color: blue; -fx-border-radius: 4;");
            else
                tabHeader.setStyle("-fx-border-color: rgb(0,0,0,0);");

            isDropIndicatorShowing = value;
            return value;
        }

        public boolean isDropIndicatorShowing()
        {
            return isDropIndicatorShowing;
        }

        class CustomGraphic extends HBox
        {
            URI currentURI;
            double pressPoint;

            CustomGraphic()
            {
                super(10);

                setAlignment(Pos.BOTTOM_CENTER);

                setOnMousePressed((evt) -> 
                                  {
                                      pressPoint = evt.getX();
                                  });

                setOnMouseDragged(evt -> 
                                  {
                                      if ((Math.abs(evt.getX() - pressPoint) < 10) || (draggingTab != null))
                                          return;
                                  
                                      SnapshotParameters snapParams = new SnapshotParameters();
                                      snapParams.setTransform(Transform.scale(0.4, 0.4));
                                  
                                      ImageView im = new ImageView();
                                      im.setImage(getContent().snapshot(snapParams, null));
                                      dragImageView.setCenter(im);
                                  
                                      URITabPane.this.getChildren().add(dragImageView);
                                      draggingTab = URITab.this;
                                  
                                      double h1 = ((Region) draggingTab.getContent()).getHeight();
                                      dragImageView.relocate(evt.getX(), URITabPane.this.getHeight() - h1);
                                  });
            }
        }

        class TabContextMenu extends ContextMenu
        {
            TabContextMenu()
            {
                MenuItem moveToLeft = new MenuItem("Move Left <<");
                moveToLeft.setOnAction((evt) -> moveTabLeft(URITabPane.URITab.this)); 
                MenuItem moveToRight = new MenuItem("Move Right >>");
                moveToRight.setOnAction((evt) -> moveTabRight(URITabPane.URITab.this));
                MenuItem clearError = new MenuItem("Clear Error");
                clearError.setOnAction((evt) -> clearError());
                MenuItem markAsMain = new MenuItem("Mark as Main Project");
                markAsMain.setOnAction((evt) -> markAsMain(true));
                getItems().setAll(moveToLeft, moveToRight, new SeparatorMenuItem(), clearError, new SeparatorMenuItem(), markAsMain);
            }
        }
    }

    public void clearMainMarkers()
    {      
        ObservableList<Tab> tabs = getTabs();

        for (int i=tabs.size()-1; i>=0; i--)
        {
            URITab tt = (URITab) tabs.get(i);
            tt.markAsMain(false);
        }
    }

    public URITab getMainMarkedTab()
    {      
        ObservableList<Tab> tabs = getTabs();

        for (int i=tabs.size()-1; i>=0; i--)
        {
            URITab tt = (URITab) tabs.get(i);
            if (tt.markedAsMain())
                return tt;
        }
        
        return null;
    }

    protected void markedAsMain(URITab tab)
    {
    }

    protected void moveTabLeft(URITab tab)
    {
        System.out.println("Move tab left "+tab.jdeComponent.getURI());
    }

    protected void moveTabRight(URITab tab)
    {
        System.out.println("Move tab right "+tab.jdeComponent.getURI());
    }

    protected void tabRemoved(URITab tab, JDEComponent jde)
    {
    }

    public URI[] removeAll()
    {
        URI[] uris = getJDEComponentURIs();
        for (int i=0; i<uris.length; i++)
            removeURI(uris[i]);
        return uris;
    }

    public JDEComponent removeURI(URI uri)
    {
        if (uri == null)
            return null;

        String ss = URIResourceTree.uriToString(uri);        
        ObservableList<Tab> tabs = getTabs();

        for (int i=tabs.size()-1; i>=0; i--)
        {
            URITab tt = (URITab) tabs.get(i);
            JDEComponent jde = (JDEComponent) tt.getContent();
            if (URIResourceTree.uriToString(jde.getURI()).startsWith(ss))
            {
                tabs.remove(i);
                tabRemoved(tt, jde);
                return jde;
            }
        }

        return null;
    }
    
    public Tab showTab(URI uri)
    {
        Tab tt = getTabByURI(uri);
        if (tt == null)
            return null;
        ((Node) tt.getContent()).requestFocus();
        getSelectionModel().select(tt);
        return tt;
    }

    public JDEComponent addNewTab(JDEComponent jde)
    {
        URITab tab = new URITab(jde);
        getTabs().add(tab);
        getSelectionModel().select(getTabs().size()-1);

        refreshTab(false, true, true, tab, jde);
        return jde;
    } 

    public URITab getSelectedTab()
    {
        return (URITab) getSelectionModel().getSelectedItem();
    }

    public URITab getFocussedTab()
    {
        URITab selected = getSelectedTab();
        if (selected == null)
            return null;
        if (selected.hasFocus())
            return selected;
        return null;
    }

    public JDEComponent getSelectedJDEComponent()
    {
        URITab tt = getSelectedTab();
        if (tt == null)
            return null;
        return (JDEComponent) tt.getContent();
    }

    public String getSelectedTabTitle()
    {
        JDEComponent jde = getSelectedJDEComponent();
        if (jde == null)
            return null;
        return jde.getFullTitle();
    }
}
