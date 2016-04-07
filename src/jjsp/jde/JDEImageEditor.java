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
import javafx.scene.text.*;
import javafx.stage.*;
import javafx.scene.image.*;
import javafx.scene.paint.*;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.application.*;

import javafx.geometry.*;

import jjsp.util.*;

public class JDEImageEditor extends JDEComponent
{
    protected Label summary;
    protected Slider slider;
    protected ImageView imageView;

    public JDEImageEditor(URI uri)
    {
        super(uri);
        
        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setCache(true);

        StackPane stack = new StackPane();
        stack.setBackground(new Background(new BackgroundFill(Color.web("#E0E0E0"), CornerRadii.EMPTY, Insets.EMPTY)));
        StackPane.setAlignment(imageView,Pos.CENTER);
        stack.getChildren().add(imageView); 

        slider = new Slider(-2, 2, 0);
        slider.setMinHeight(50);
        //slider.setOrientation(Orientation.VERTICAL);
        slider.valueProperty().addListener((evt) -> updateImageScale());


        ScrollPane sp = new ScrollPane(stack);
        sp.setStyle("-fx-font-size: 14px;"); //Sets the scroll bar size
        sp.viewportBoundsProperty().addListener((e, o, n) -> 
                                                {
                                                    stack.setPrefWidth(sp.getViewportBounds().getWidth());
                                                    stack.setPrefHeight(sp.getViewportBounds().getHeight());
                                                });
        
        setCenter(sp);

        summary = new Label("");
        summary.setFont(new Font("Arial", 24));
        BorderPane.setMargin(summary, new Insets(12,12,12,12));
        BorderPane.setAlignment(summary, Pos.CENTER);
        BorderPane bp = new BorderPane();
        bp.setCenter(slider);
        bp.setLeft(summary);

        setTop(bp);
        loadImage(uri);
    }

    private void updateImageScale()
    {
        Image im = imageView.getImage();
        if (im == null)
            return;

        double val = slider.getValue();
        double factor = Math.exp(val);
        double imageWidth = im.getWidth();
        imageView.setFitWidth(factor*getWidth() - 20);
    }

    protected FileChooser getFileChooser()
    {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("PNG Images", "*.png"));
        return fileChooser;
    }

    public Menu[] createMenus()
    {
        MenuItem saveAs = new MenuItem("Save to Local File");
        saveAs.setOnAction((event) -> 
                           { 
                               if (!isDisplayed()) 
                                   return;  
                               File file = getFileChooser().showSaveDialog(getScene().getWindow());
                               if (file != null)
                                   saveTo(file.toURI());
                           });

        Menu actions = new Menu("Image Actions");
        actions.getItems().addAll(saveAs);

        return new Menu[]{actions};
    }

    public boolean saveTo(URI target)
    {
        if (target == null)
            return false;
            
        try
        {
            java.awt.image.BufferedImage bim = javafx.embed.swing.SwingFXUtils.fromFXImage(imageView.getImage(), null);
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(bim, "PNG", bout);
            byte[] rawBytes = bout.toByteArray();
            
            if (target.getScheme().equals("file"))
            {
                OutputStream out = new FileOutputStream(new File(target));
                out.write(rawBytes);
                out.close();
            }
            else
            {
                HttpURLConnection conn = (HttpURLConnection) target.toURL().openConnection();
                conn.setDoOutput(true);

                OutputStream out = conn.getOutputStream();
                out.write(rawBytes);
                out.flush();
                out.close();
                
                if (conn.getResponseCode() != 200)
                    throw new IOException(conn.getResponseCode()+": "+conn.getResponseMessage());
            }
                
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public boolean loadImage(URI uri)
    {
        try
        {
            byte[] rawImageData = Utils.load(uri);

            componentURI = uri;
            Image im = new Image(new ByteArrayInputStream(rawImageData));
            imageView.setImage(im);
            Platform.runLater(() -> updateImageScale());

            summary.setText("Raw File Size: "+rawImageData.length+" bytes, "+im.getWidth()+" by "+im.getHeight()+" pixels");
            return true;
        }
        catch (Exception e) {}
        return false;
    }

    public void reloadContent()
    {
        loadImage(componentURI);
    }
}
