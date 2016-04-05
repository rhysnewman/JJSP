package jjsp.util;

import java.awt.*;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.util.*;

import java.awt.image.*;
import javax.swing.*;
import javax.imageio.*;

import javax.script.*;
import javax.swing.text.html.*;

import javafx.application.*;
import javafx.event.*;
import javafx.scene.*;
import javafx.beans.*;
import javafx.beans.value.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.input.*;
import javafx.scene.image.*;
import javafx.scene.web.*;

import javafx.geometry.*;
import javafx.util.*;
import javafx.stage.*;
import javafx.collections.*;

import jjsp.engine.*;
import jjsp.util.*;
import jjsp.http.*;


public class IconUtils
{
    private static class ImageHeader
    {
        int width, height, numColours, planes, bitsPerPixel, offset, dataSize;
    }

    public static Image readImageFromICO(byte[] icoData)
    {
        return readImageFromICO(icoData, 16);
    }

    public static Image readImageFromICO(byte[] icoData, int idealWidth)
    {
        Image[] images = readImagesFromICO(icoData);
        if ((images == null) || (images.length == 0))
            return null;

        Image largest = images[0];
        for (int i=1; i<images.length; i++)
        {
            if (images[i].getWidth() == idealWidth)
                return images[i];
            
            if (images[i].getWidth() > largest.getWidth())
                largest = images[i];
        }

        if (largest.getWidth() == 0)
            return null;

        int idealHeight = (int) (largest.getHeight()*idealWidth/largest.getWidth());
        
        BufferedImage bim = javafx.embed.swing.SwingFXUtils.fromFXImage(largest, null);
        BufferedImage bim2 = new BufferedImage(idealWidth, idealHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = bim2.createGraphics();
        g2.drawImage(bim, 0, 0, idealWidth, idealHeight, null);
        g2.dispose();

        return javafx.embed.swing.SwingFXUtils.toFXImage(bim2, null);
    }

    public static Image readLargestImageFromICO(byte[] icoData)
    {
        Image[] images = readImagesFromICO(icoData);
        if ((images == null) || (images.length == 0))
            return null;

        Image largest = images[0];
        for (int i=1; i<images.length; i++)
        {
            if (images[i].getWidth() > largest.getWidth())
                largest = images[i];
        }

        if (largest.getWidth() == 0)
            return null;
        
        BufferedImage bim = javafx.embed.swing.SwingFXUtils.fromFXImage(largest, null);
        return javafx.embed.swing.SwingFXUtils.toFXImage(bim, null);
    }

    public static Image[] readImagesFromICO(byte[] icoData) 
    {
        ByteBuffer bb = ByteBuffer.wrap(icoData);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        if (bb.getShort() != 0)
            return null;
        if (bb.getShort() != 1)
            return null;

        int number = bb.getShort();
        if (number <= 0)
            return null;
        
        int maxSize = 0;
        ArrayList buf = new ArrayList();
        BufferedImage result = null;
        
        ImageHeader[] hdrs = new ImageHeader[number];
        for (int i=0; i<number; i++)
        {
            hdrs[i] = new ImageHeader();

            hdrs[i].width = 0xFF & bb.get();
            if (hdrs[i].width == 0)
                hdrs[i].width = 256;
            hdrs[i].height = 0xFF & bb.get();
            if (hdrs[i].height == 0)
                hdrs[i].height = 256;
            hdrs[i].numColours = 0xFF & bb.get();
            bb.get(); // a reserved byte
            hdrs[i].planes = bb.getShort();
            hdrs[i].bitsPerPixel = bb.getShort();

            hdrs[i].dataSize = bb.getInt();
            hdrs[i].offset = bb.getInt();
        }

        for (int pos=0; pos<number; pos++) 
        {
            ImageHeader hdr = hdrs[pos];
            short type = bb.getShort();

            BufferedImage im = null;
            bb.position(hdr.offset);
            if (bb.getInt() == 1196314761) // 89 + "PNG" as int
            {
                try
                {
                    im = ImageIO.read(new ByteArrayInputStream(icoData, hdr.offset, hdr.dataSize));
                }
                catch (Exception e) {}
            }
            else
            {
                bb.position(hdr.offset);
                int bmpheaderlength = bb.getInt();
                //Adjust for optional colour mask
                bb.position(hdr.offset + 16);
                if (bb.getInt() == 3||bb.getInt() == 6)
                    bmpheaderlength = bmpheaderlength + 16; 
                //Get color table if necessary
                byte[] aaa = new byte[1]; 
                IndexColorModel cm = new IndexColorModel(1,1,aaa,aaa,aaa);
                int numColors = 0;
                if (hdr.bitsPerPixel == 1 || hdr.bitsPerPixel == 2 || hdr.bitsPerPixel == 4 || hdr.bitsPerPixel == 8)
                {
                    bb.position(hdr.offset + 32);
                    numColors = bb.getInt();
                    if (numColors == 0)
                        numColors = (int) Math.pow(2,hdr.bitsPerPixel);
                    bb.position(hdr.offset + bmpheaderlength);
                    byte[] b = new byte[numColors];
                    byte[] g = new byte[numColors];
                    byte[] r = new byte[numColors];
                    for (int i=0; i<numColors; i++)
                    {
                        b[i] = bb.get();
                        g[i] = bb.get();
                        r[i] = bb.get();
                        bb.get();
                    }
                    cm = new IndexColorModel(8, numColors, r, g, b);
                }

                if (hdr.bitsPerPixel == 32)
                {
                    bb.position(hdr.offset + bmpheaderlength);
                    im = new BufferedImage(hdr.width, hdr.height, BufferedImage.TYPE_INT_ARGB);
                    for (int i=0; i<hdr.height; i++)
                        for (int j=0; j<hdr.width; j++)
                            im.setRGB(j, hdr.height-i-1, bb.getInt());
                }
                else if (hdr.bitsPerPixel == 24)
                {
                    bb.position(hdr.offset + bmpheaderlength);

                    im = new BufferedImage(hdr.width, hdr.height, BufferedImage.TYPE_INT_ARGB);
                    for (int i=0; i<hdr.height; i++)
                        for (int j=0; j<hdr.width; j++)
                        {
                            int bgrb = bb.getInt();
                            int ones  = 0b11111111;
                            int b = bgrb & (ones << 24);
                            int g = bgrb & (ones << 16);
                            int r = bgrb & (ones << 8);
                            int argb = (b >>> 24) | (r << 8) | (g >>> 8) | (ones << 24);

                            bb.position(bb.position()-1);
                            im.setRGB(j, hdr.height-i-1, argb);
                        }
                }

                else if (hdr.bitsPerPixel == 16)
                {
                    bb.position(hdr.offset +  bmpheaderlength);

                    im = new BufferedImage(hdr.width, hdr.height, BufferedImage.TYPE_INT_ARGB);
                    for (int i=0; i<hdr.height; i++)
                        for (int j=0; j<hdr.width; j++)
                        {
                            byte ar = bb.get();
                            byte gb = bb.get();
                            int a = ar & 0b11110000;
                            int r = ar & 0b00001111;
                            int g = gb & 0b11110000;
                            int b = gb & 0b00001111;
                            int argb = 0 | (a << 24 ) | (r << 20 ) | (g << 8 ) | (b << 4 ) ;
                            im.setRGB(j, hdr.height-i-1, argb);
                        }
                }
                else if (hdr.bitsPerPixel == 8)
                {
                    bb.position(hdr.offset + bmpheaderlength + numColors*4);
                    //get pixel array
                    byte[] pixels = new byte[hdr.width*hdr.height];
                    for (int i=0;i<hdr.height;i++)
                        for (int j=0; j<hdr.width; j++)
                            pixels[(hdr.width * hdr.height) - ((i + 1) * hdr.width) + j] = bb.get();
                    im = new BufferedImage(hdr.width, hdr.height, BufferedImage.TYPE_INT_ARGB);
                    for (int i=0; i<hdr.height; i++)
                        for (int j=0; j<hdr.width; j++)
                            im.setRGB(j, i, cm.getRGB( pixels[(i*hdr.width)+j] ));
                    setAlpha(hdr, bb, im);
                }

                else if (hdr.bitsPerPixel == 4)
                {
                    bb.position(hdr.offset + bmpheaderlength + numColors*4);
                    byte[] pixels = new byte[hdr.width*hdr.height];
                    for (int i=0; i<hdr.height; i++)
                    {
                        for (int j=0; j<hdr.width; j+=2)
                        {
                            byte twopixels = bb.get();
                            pixels[(hdr.width * hdr.height) - ((i + 1) * hdr.width) + j] = (byte) ((twopixels & 0b11110000) >> 4);
                            pixels[(hdr.width * hdr.height) - ((i + 1) * hdr.width) + j + 1] = (byte) (twopixels & 0b00001111);
                        }
                    }
                    im = new BufferedImage(hdr.width, hdr.height, BufferedImage.TYPE_INT_ARGB);
                    for (int i=0; i<hdr.height; i++)
                        for (int j=0; j<hdr.width; j++)
                            im.setRGB(j, i, cm.getRGB( pixels[(i*hdr.width)+j] ));
                    setAlpha(hdr, bb, im);

                }
                else if (hdr.bitsPerPixel == 2)
                {
                    bb.position(hdr.offset + bmpheaderlength + numColors*4);
                    byte[] pixels = new byte[hdr.width*hdr.height];
                    for (int i=0;i<hdr.height;i++)
                        for (int j=0; j<hdr.width; j+=4) //size is always a multiple of 4
                        {
                            byte fourpixels = bb.get();
                            pixels[(hdr.width * hdr.height) - ((i + 1) * hdr.width) + j] = (byte) ((fourpixels & 11000000) >> 6);
                            pixels[(hdr.width * hdr.height) - ((i + 1) * hdr.width) + j + 1] = (byte) ((fourpixels & 00110000) >> 4);
                            pixels[(hdr.width * hdr.height) - ((i + 1) * hdr.width) + j + 2] = (byte) ((fourpixels & 00001100) >> 2);
                            pixels[(hdr.width * hdr.height) - ((i + 1) * hdr.width) + j + 3] = (byte) (fourpixels & 00000011);
                        }
                    im = new BufferedImage(hdr.width, hdr.height, BufferedImage.TYPE_INT_ARGB);
                    for (int i=0; i<hdr.height; i++)
                        for (int j=0; j<hdr.width; j++)
                            im.setRGB(j, i, cm.getRGB( pixels[(i*hdr.width)+j] ));
                    setAlpha(hdr, bb, im);
                }
                else if (hdr.bitsPerPixel == 1)
                {
                    bb.position(hdr.offset + bmpheaderlength);
                    //get bits
                    BitSet bs = new BitSet();
                    bs.valueOf(bb);
                    im = new BufferedImage(hdr.width, hdr.height, BufferedImage.TYPE_BYTE_BINARY, cm);
                    for (int i=0; i<hdr.height; i++)
                        for (int j=0; j<hdr.width; j++)
                            im.setRGB(j, hdr.height-i-1, bs.get(i*hdr.width + j)? 1 : 0);
                }
                else
                {
                    try
                    {
                        im = ImageIO.read(new ByteArrayInputStream(icoData, hdr.offset, hdr.dataSize));
                    }
                    catch (Exception e) {}
                }
            }
            
            bb.position(hdr.offset + hdr.dataSize);
            if (im != null)
                buf.add(javafx.embed.swing.SwingFXUtils.toFXImage(im, null));
        }
        
        Image[] images = new Image[buf.size()];
        buf.toArray(images);
        
        return images;
    }

    private static WritableRaster GetRaster(ImageHeader hdr, byte[] pixels)
    {
        DataBuffer db = new DataBufferByte(pixels,hdr.width*hdr.height);
        int[] bm = {(byte)0xffffffff};
        SampleModel sm = new SinglePixelPackedSampleModel(DataBuffer.TYPE_BYTE, hdr.width,hdr.height, bm);
        WritableRaster raster = Raster.createWritableRaster(sm, db, null);
        return raster;
    }

    private static void setAlpha(ImageHeader hdr, ByteBuffer bb, BufferedImage im)
    {
        WritableRaster alpha = im.getAlphaRaster();
        boolean isPadded = false;
        if ((hdr.width/8) % 4 != 0)
            isPadded = true;
        BitSet bs = new BitSet();
        bs = bs.valueOf(bb); //
        BitSet reverse = new BitSet();
        int l = bs.size() / 8;
        for (int i=0;i<l;i++)
            for (int j=0;j<8;j++)
                reverse.set(i*8 + j, bs.get(i*8 + 7 - j));
        int paddingOffset = 0;
        for (int i=0;i<hdr.height;i++)
        {
            for (int j = 0; j < hdr.width; j++) 
                alpha.setSample(j, hdr.height - i - 1, 0, reverse.get(i * hdr.width + j + paddingOffset) ? 0 : 0xffffffff);
            if (isPadded == true)
                paddingOffset += 16;
        }
    }

    public static class Test extends Application
    {
        public void start(final Stage primaryStage) throws Exception
        {
            String fileName = getParameters().getRaw().get(0);
            Image im = readImageFromICO(Utils.load(new File(fileName)));

            BorderPane bp = new BorderPane();
            bp.setCenter(new ImageView(im));

            Scene scene = new Scene(bp, Color.WHITE);
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(300);
            primaryStage.setMinHeight(300);

            primaryStage.show();
            Platform.setImplicitExit(true);
        }
    }

    public static void main(String[] args) throws Exception
    {
        Application.launch(Test.class, args);
    }
}
