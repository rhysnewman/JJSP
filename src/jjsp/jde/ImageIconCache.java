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

import jjsp.util.*;
import jjsp.engine.*;

import java.awt.Graphics2D;
import java.awt.image.*;
import javax.swing.*;
import javax.swing.filechooser.*;
import javax.imageio.*;
import javafx.scene.image.*;

import javafx.application.*;
import javafx.scene.*;
import javafx.stage.*;
import javafx.scene.paint.*;
import javafx.collections.*;
import javafx.scene.layout.*;
import javafx.embed.swing.*;

public class ImageIconCache
{
    private File cacheDir;
    private HashMap imageIndex;

    private static final JFileChooser chooser = new JFileChooser();

    private static Image jjspImage, jjspImageSmall, jfImage, jfImageSmall;
    static
    {
        try
        {
            byte[] rawData1 = Utils.load("resources/JJSP.png");
            BufferedImage im = ImageIO.read(new ByteArrayInputStream(rawData1));
            im = new ImageGenerator().makeEdgesTransparent(im);
            ByteArrayOutputStream bb = new ByteArrayOutputStream();
            ImageIO.write(im, "png", bb);
            rawData1 = bb.toByteArray();

            jjspImage = new Image(new ByteArrayInputStream(rawData1));
            jjspImageSmall = new Image(new ByteArrayInputStream(rawData1), 20, 20, true, true);
        }
        catch (Exception e) 
        {
            e.printStackTrace();
            System.out.println("Warning - failed to load default JJSP image");
        }

        try
        {
            byte[] rawData2 = Utils.load("resources/jf.png");
            jfImage = new Image(new ByteArrayInputStream(rawData2));
            jfImageSmall = new Image(new ByteArrayInputStream(rawData2), 20, 20, true, true);
        }
        catch (Exception e) 
        {
            jfImage = jjspImage;
            jfImageSmall = jjspImageSmall;
        }
    }

    public ImageIconCache()
    {
        this(null);
    }

    public ImageIconCache(File cacheDir)
    {
        this.cacheDir = cacheDir;
        imageIndex = new HashMap();
        if (jjspImageSmall != null)
        {
            imageIndex.put("jjsp", jjspImageSmall);
            imageIndex.put("jet", jjspImageSmall);
        }
        if (jfImageSmall != null)
            imageIndex.put("jf", jfImageSmall);
    }

    public static Image getJJSPImage()
    {
        return jjspImage;
    }

    public Image getImageFor(File f)
    {
        try
        {
            return getImageFor(f.toURI());
        }
        catch (Exception e) {}
        return null;
    }

    public Image getImageFor(URL url)
    {
        try
        {
            return getImageFor(url.toURI());
        }
        catch (Exception e) {}
        return null;
    }

    public Image getImageFor(URI uri)
    {
        return getImageFor(uri, true);
    }

    private Image getFromLocalCache(String key, File srcFile)
    {
        if (cacheDir == null)
            return null;
        try
        {
            if ((srcFile != null) && srcFile.exists())
                return getImageForFileType(srcFile);

            try
            {
                InputStream in = getClass().getClassLoader().getResourceAsStream("resources/DefaultIcon_"+key+".png");
                if (in != null)
                    return new Image(in);
            }
            catch (Exception e) {}
                    

            File ff = new File(cacheDir, key+".png");
            if (!ff.exists())
            {
                if (key.startsWith("D_"))
                    return null;

                File testFile = new File(cacheDir, "IconTest."+key);
                testFile.createNewFile();
                return getImageForFileType(testFile);
            }
            
            return new Image(new FileInputStream(ff));
        }
        catch (Exception e) {}
        return null;
    }

    private void saveToLocalCache(String key, Image image)
    {
        if (cacheDir == null)
            return;

        try
        {
            File ff = new File(cacheDir, key+".png");
            ff.createNewFile();
            BufferedImage bim = SwingFXUtils.fromFXImage(image, null);
            ImageIO.write(bim, "PNG", ff);
        }
        catch (Exception e) {}
    }

    public Image getImageFor(URI uri, boolean loadIfAbsent)
    {
        try
        {
            File exampleFile = null;
            String key = uri.getPath();

            int dot = key.lastIndexOf(".");
            int slash = key.lastIndexOf("/");
            if ((dot >= 0) && ((slash < 0) || (dot > slash)))
            {
                if (key.endsWith(".ico"))
                {
                    if (uri.getScheme().equals("file"))
                        exampleFile = new File(uri);
                }
                else
                    key = key.substring(dot+1).toLowerCase();
            }
            else 
            {
                if (uri.getScheme().equals("file"))
                {
                    if (uri.toString().endsWith(":/") || uri.toString().endsWith(":"))
                        key = "_ROOT_DIR_";
                    else
                    {
                        try
                        {
                            if (new File(uri).isDirectory())
                                key = "_DIR_";
                        }
                        catch (Exception e) {}
                    }
                    exampleFile = new File(uri);
                }
                else
                {
                    int port = uri.getPort();
                    if (port > 0)
                        key = ("D_"+uri.getHost()+"__"+uri.getPort()).replace(".", "_");
                    else
                        key = ("D_"+uri.getHost()).replace(".", "_");
                }
            }
            
            Image result = (Image) imageIndex.get(key);
            if (result != null)
                return result;
            
            result = getFromLocalCache(key, exampleFile);
            if (result != null)
            {
                imageIndex.put(key, result);
                return result;
            }

            if (!loadIfAbsent)
                return null;

            result = getImageForWebResource(uri);
            if (result != null)
            {
                imageIndex.put(key, result);
                saveToLocalCache(key, result);
            }
            
            return result;
        }
        catch (Exception e) {}

        return null;
    }

    private Image getImageForWebResource(URI uri)
    {
        try
        {
            URL favIcon = uri.resolve("/favicon.ico").toURL();
            Image result = IconUtils.readImageFromICO(Utils.load(favIcon));
            if (result != null)
                return result;
        }
        catch (Exception e) {}
        
        try
        {
            URL googleFav = new URL("http://www.google.com/s2/favicons?domain="+uri.getHost());
            BufferedImage im = ImageIO.read(googleFav);
            if (im != null)
                return javafx.embed.swing.SwingFXUtils.toFXImage(im, null);
        }
        catch (Exception e) {}

        return null;
    }

    class DelayedImageView extends ImageView
    {
        DelayedImageView(URI uri)
        {
            new Thread(()->{Image result = getImageFor(uri); if (result != null) Platform.runLater(()-> setImage(result));}).start();
        }
    }

    public ImageView createImageViewFor(String uriString)
    {
        try
        {
            return createImageViewFor(new URI(uriString));
        }
        catch (Exception e) 
        {
            return new ImageView();
        }
    }

    public ImageView createImageViewFor(URI uri)
    {
        Image im = getImageFor(uri, false);
        if (im != null)
            return new ImageView(im);
        return new DelayedImageView(uri);
    }

    private static BufferedImage getBufferedImageForFileType(File tgt)
    {
        ImageIcon icn = null;
        try
        {
            icn = (ImageIcon) FileSystemView.getFileSystemView().getSystemIcon(tgt);
        }
        catch (Exception e){}
        
        if (icn == null)
            icn = (ImageIcon) chooser.getUI().getFileView(chooser).getIcon(tgt);
        java.awt.Image img = icn.getImage();
            
        BufferedImage bimage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = bimage.createGraphics();
        g2.drawImage(img, 0, 0, null);
        g2.dispose();

        return bimage;
    }

    public static Image getImageForFileType(File tgt)
    {
        try
        {
            return javafx.embed.swing.SwingFXUtils.toFXImage(getBufferedImageForFileType(tgt), null);
        }
        catch (Exception e) {}

        return null;
    }
    
    public static void main(String[] args) throws Exception
    {
        File resDir = new File("src/resources");
        if (!resDir.exists())
            resDir = new File("resources");
        
        File[] icons = new File("jjspcache/.jde/icons").listFiles();
        for (int i=0; i<icons.length; i++)
        {
            String name = icons[i].getName();
            BufferedImage bufferedImage = getBufferedImageForFileType(icons[i]);
            
            int dot = name.lastIndexOf(".");
            String outName = "DefaultIcon_"+name.substring(dot+1)+".png";
            System.out.println("Found "+name+" - saving Image as "+outName);
            
            if (!resDir.exists())
                System.out.println("Would save "+outName+" into resources dir...");
            else
            {
                File outFile = new File(resDir, outName);
                outFile.createNewFile();
                ImageIO.write(bufferedImage, "PNG", outFile);
                System.out.println("Saved "+outName+" into "+resDir);
            }
        }

        BufferedImage bufferedImage = getBufferedImageForFileType(new File(System.getProperty("user.dir")));
        File outFile = new File(resDir, "DefaultIcon__DIR_.png");
        outFile.createNewFile();
        ImageIO.write(bufferedImage, "PNG", outFile);
        System.out.println("Saved DefaultIcon_dir.png into "+resDir);
    }
}
