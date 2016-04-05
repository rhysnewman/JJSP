package jjsp.engine;

import java.io.*;
import java.util.*;
import java.awt.*;
import java.awt.image.*;

import javax.imageio.*;

import jjsp.util.*;

public class ImageGenerator
{
    public java.awt.Color toAWTColor(String spec)
    {
        javafx.scene.paint.Color fxColour = javafx.scene.paint.Color.web(spec);
        return new java.awt.Color((float)fxColour.getRed(), (float)fxColour.getGreen(), (float)fxColour.getBlue(), (float)fxColour.getOpacity());
    }
    
    public javafx.scene.paint.Color fromAWTColor(java.awt.Color awtColor)
    {
        return new javafx.scene.paint.Color(awtColor.getRed() / 255.0, awtColor.getGreen() / 255.0, awtColor.getBlue() / 255.0, awtColor.getAlpha() / 255.0);
    }

    public byte[] getImageBytes(BufferedImage im) throws Exception
    {   
        return getImageBytes(im, "PNG");
    }

    public byte[] getImageBytes(BufferedImage im, String format) throws Exception
    {     
        if ((format.equalsIgnoreCase("jpg") || format.equalsIgnoreCase("jpeg")) && (im.getAlphaRaster() != null))
            im = noAlpha(im);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ImageIO.write(im, format, bout);
        bout.close();

        return bout.toByteArray();
    }

    public BufferedImage imageFromBytes(byte[] raw) throws Exception
    {
        return ImageIO.read(new ByteArrayInputStream(raw));
    }

    public BufferedImage plain(int width, int height, String colSpec)
    {
        BufferedImage im = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int rgb = toAWTColor(colSpec).getRGB();

        for (int i=0; i<im.getWidth(); i++)
            for (int j=0; j<im.getHeight(); j++)
                im.setRGB(i, j, rgb);

        return im;
    }   

    public BufferedImage gradient(int width, int height, String colSpec1, String colSpec2, double power)
    {
        BufferedImage im = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        java.awt.Color c1 = toAWTColor(colSpec1);
        java.awt.Color c2 = toAWTColor(colSpec2);

        for (int i=0; i<im.getWidth(); i++)
            for (int j=0; j<im.getHeight(); j++)
            {
                double factor = (1.0*i/im.getWidth());
                if (power != 1)
                    factor = Math.pow(factor, power);

                int r = (int) (c1.getRed()* factor + (1 - factor) * c2.getRed());
                int b = (int) (c1.getBlue()* factor + (1 - factor) * c2.getBlue());
                int g = (int) (c1.getGreen()* factor + (1 - factor) * c2.getGreen());
                int a = (int) (c1.getAlpha()* factor + (1 - factor) * c2.getAlpha());

                int rgb = (a << 24) | (r << 16) | (g << 8) | b;
                im.setRGB(i, j, rgb);
            }

        return im;
    }

    public BufferedImage waves(int width, int height, String colSpec1, String colSpec2, int wavelength, int angle)
    {
        BufferedImage im = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        java.awt.Color c1 = toAWTColor(colSpec1);
        java.awt.Color c2 = toAWTColor(colSpec2);

        double theta = (angle/360.0 * Math.PI * 2) + Math.PI/2;
        for (int i=0; i<im.getWidth(); i++)
            for (int j=0; j<im.getHeight(); j++)
            {
                double arg = Math.sin(theta) * j + Math.cos(theta) * i;
                double factor = 0.5*Math.cos(arg/wavelength*Math.PI*2) + 0.5;

                int r = (int) (c1.getRed()* factor + (1 - factor) * c2.getRed());
                int b = (int) (c1.getBlue()* factor + (1 - factor) * c2.getBlue());
                int g = (int) (c1.getGreen()* factor + (1 - factor) * c2.getGreen());
                int a = (int) (c1.getAlpha()* factor + (1 - factor) * c2.getAlpha());

                int rgb = (a << 24) | (r << 16) | (g << 8) | b;
                im.setRGB(i, j, rgb);
            }

        return im;
    }

    public BufferedImage blend(BufferedImage src1, BufferedImage src2, int factorPercent)
    {
        int width = src1.getWidth();
        int height = src1.getHeight();
 
        double factor = factorPercent / 100.0;
        BufferedImage im = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int i=0; i<im.getWidth(); i++)
            for (int j=0; j<im.getHeight(); j++)
            {
                int rgb1 = src1.getRGB(i, j);
                int rgb2 = src2.getRGB(i, j);

                int a1 = 0xFF & (rgb1 >> 24);
                int r1 = 0xFF & (rgb1 >> 16);
                int g1 = 0xFF & (rgb1 >> 8);
                int b1 = 0xFF & rgb1;

                int a2 = 0xFF & (rgb2 >> 24);
                int r2 = 0xFF & (rgb2 >> 16);
                int g2 = 0xFF & (rgb2 >> 8);
                int b2 = 0xFF & rgb2;

                int rr = (int) (r1* factor + (1 - factor) * r2);
                int bb = (int) (b1* factor + (1 - factor) * b2);
                int gg = (int) (g1* factor + (1 - factor) * g2);
                int aa = (int) (a1* factor + (1 - factor) * a2);

                int rgb = (aa << 24) | (rr << 16) | (gg << 8) | bb;
                im.setRGB(i, j, rgb);
            }

        return im;
    }

    public BufferedImage copyImage(BufferedImage src)
    {
        return copyImage(src, src.getWidth(), src.getHeight());
    }

    private static BufferedImage copyImage(BufferedImage src, int width, int height)
    {
        BufferedImage im = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = im.createGraphics();
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        
        return im;
    }

    public BufferedImage blur(BufferedImage src, int maskSize)
    {
        BufferedImage im = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int i=0; i<im.getWidth(); i++)
            for (int j=0; j<im.getHeight(); j++)
            {
                int aveA = 0;
                int aveR = 0;
                int aveG = 0;
                int aveB = 0;
                int count = 0;

                for (int k=-maskSize; k<=maskSize; k++)
                    for (int l=-maskSize; l<=maskSize; l++)
                    {
                        try
                        {
                            int rgb = src.getRGB(i+k, j+l);
                            
                            int a = 0xFF & (rgb >> 24);
                            int r = 0xFF & (rgb >> 16);
                            int g = 0xFF & (rgb >> 8);
                            int b = 0xFF & rgb;
                            
                            aveA += a;
                            aveR += r;
                            aveG += g;
                            aveB += b;
                            count++;
                        }
                        catch (Exception e) {}
                    }

                aveA /= count;
                aveR /= count;
                aveG /= count;
                aveB /= count;
                
                int rgb = (aveA << 24) | (aveR << 16) | (aveG << 8) | aveB;
                im.setRGB(i, j, rgb);
            }

        return im;
    }
      
    public BufferedImage crop(BufferedImage src, int x, int y, int width, int height)
    {
        BufferedImage im = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = im.createGraphics();
        g2.drawImage(src, -x, -y, null);
        g2.dispose();
        
        return im;
    }

    public BufferedImage scaleByFactor(BufferedImage src, double factor)
    {
        int w = (int) (src.getWidth() * factor);
        int h = (int) (src.getHeight() * factor);
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = im.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        
        return im;
    }

    public BufferedImage scaleToWidth(BufferedImage src, int newWidth)
    {
        int newHeight = (int) (newWidth * 1.0 * src.getHeight() / src.getWidth());
        BufferedImage im = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = im.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, newWidth, newHeight, null);
        g2.dispose();
        
        return im;
    }

    public BufferedImage spliceAndStretch(BufferedImage src, int newWidth)
    {
        BufferedImage im = new BufferedImage(newWidth, src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = im.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int h = src.getHeight();
        int halfWidth = Math.min(newWidth/2, src.getWidth()/2);
        g2.drawImage(src, 0, 0, halfWidth, h, 0, 0, halfWidth, h, null);
        g2.drawImage(src, newWidth-halfWidth, 0, newWidth, h, halfWidth, 0, src.getWidth(), h, null);
        g2.dispose();
        
        int refCol = Math.max(0, halfWidth-2);
        for (int x=refCol; x<=newWidth-refCol; x++)
        {
            for (int y=0; y<h; y++)
                im.setRGB(x, y, src.getRGB(refCol, y));
        }

        return im;
    }

    public BufferedImage padToSize(BufferedImage src, int width, int height, String colSpec)
    {
        return padToSize(src, width, height, colSpec, false);
    }
    
    //American spelling - yuk
    public BufferedImage padToSizeCentered(BufferedImage src, int width, int height, String colSpec)
    {
        return padToSizeCentred(src, width, height, colSpec);
    }

    public BufferedImage padToSizeCentred(BufferedImage src, int width, int height, String colSpec)
    {
        return padToSize(src, width, height, colSpec, true);
    }

    private BufferedImage padToSize(BufferedImage src, int width, int height, String colSpec, boolean centre)
    {
        BufferedImage im = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        java.awt.Color bg = new java.awt.Color(255, 255, 255, 0);
        try
        {
            bg = toAWTColor(colSpec);
        }
        catch (Exception e) {}

        Graphics2D g2 = im.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(bg);
        g2.fillRect(0, 0, im.getWidth(), im.getHeight());
        /*int bgColour = bg.getRGB();
        for (int i=0; i<im.getWidth(); i++)
            for (int j=0; j<im.getHeight(); j++)
            im.setRGB(i, j, bgColour);*/

        double scale = Math.min(1, (1.0*height)/src.getHeight());
        scale = Math.min(scale, (1.0*width)/src.getWidth());

        int w = (int) (src.getWidth()*scale);
        int h = (int) (src.getHeight()*scale);

        if (centre)
            g2.drawImage(src, (im.getWidth() - w)/2, (im.getHeight() - h)/2, w, h, null);
        else
            g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        
        return im;
    }

    public BufferedImage scaleToHeight(BufferedImage src, int newHeight)
    {
        int newWidth = (int) (newHeight * 1.0 * src.getWidth() / src.getHeight());
        BufferedImage im = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = im.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, newWidth, newHeight, null);
        g2.dispose();
        
        return im;
    }

    public BufferedImage scale(BufferedImage src, int newWidth, int newHeight)
    {
        BufferedImage im = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = im.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, newWidth, newHeight, null);
        g2.dispose();
        
        return im;
    }

    public BufferedImage flipHorizontal(BufferedImage src)
    {
        BufferedImage im = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = im.createGraphics();
        g2.scale(-1, 1);
        g2.translate(-im.getWidth(), 0);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        
        return im;
    }

    public BufferedImage flipVertical(BufferedImage src)
    {
        BufferedImage im = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = im.createGraphics();
        g2.scale(1, -1);
        g2.translate(0, -im.getHeight());
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        
        return im;
    }

    public BufferedImage rotate(BufferedImage src, int degrees)
    {
        double theta = -Math.PI/180*degrees;
        int w = src.getWidth();
        int h = src.getHeight();
        int nw = (int) (Math.abs(Math.cos(theta))*w + Math.abs(Math.sin(theta))*h + 0.5);
        int nh = (int) (Math.abs(Math.sin(theta))*w + Math.abs(Math.cos(theta))*h + 0.5);
        BufferedImage im = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = im.createGraphics(); 
        g2.translate(nw/2, nh/2);
        g2.rotate(theta);
        g2.translate(-w/2, -h/2);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        
        return im;
    }

    public BufferedImage join(BufferedImage src1, BufferedImage src2)
    {
        int width = src1.getWidth() + src2.getWidth();
        int height = Math.max(src1.getHeight(), src2.getHeight());
        
        BufferedImage im = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = im.createGraphics();
        g2.drawImage(src1, 0, 0, null);
        g2.drawImage(src2, src1.getWidth(), 0, null);
        g2.dispose();

        return im;
    }

    public BufferedImage stack(BufferedImage src1, BufferedImage src2)
    {
        int width = Math.max(src1.getWidth(), src2.getWidth());
        int height = src1.getHeight() + src2.getHeight();
        
        BufferedImage im = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = im.createGraphics();
        g2.drawImage(src1, 0, 0, null);
        g2.drawImage(src2, 0, src1.getHeight(), null);
        g2.dispose();

        return im;
    }

    public BufferedImage overlayCentre(BufferedImage top, BufferedImage bottom)
    {
        BufferedImage result = copyImage(bottom);
        
        Graphics2D g2 = result.createGraphics();
        g2.drawImage(top, (bottom.getWidth() - top.getWidth())/2, (bottom.getHeight() - top.getHeight())/2, null);
        g2.dispose();

        return result;
    }

    public BufferedImage overlay(BufferedImage top, BufferedImage bottom, int xOffset, int yOffset)
    {
        BufferedImage result = copyImage(bottom);
        
        Graphics2D g2 = result.createGraphics();
        g2.drawImage(top, xOffset, yOffset, null);
        g2.dispose();

        return result;
    }

    public BufferedImage roundCorners(BufferedImage im, int radius)
    {
        int width = im.getWidth();
        int height = im.getHeight();
        im = copyImage(im, width, height);

        double r2 = (radius+1)*(radius+1);
        for (int x=0; x<radius; x++)
            for (int y=0; y<radius; y++)
            {
                double v1 = (x+0.5 - radius) * (x+0.5 - radius);
                v1 += (y+0.5- radius) * (y+0.5 - radius);

                if (v1 < radius*radius)
                    continue;
                v1 = Math.sqrt(v1) - radius;
                int alpha = (int) (255*(1 - Math.min(1, v1)));
                alpha <<= 24;

                int rgb1 = im.getRGB(x, y);
                im.setRGB(x, y, alpha | 0x00FFFFFF & rgb1);
                int rgb2 = im.getRGB(width - x - 1, y);
                im.setRGB(width - x - 1, y, alpha | 0x00FFFFFF & rgb2);
                int rgb3 = im.getRGB(width - x - 1, height - y - 1);
                im.setRGB(width - x - 1, height - y - 1, alpha | 0x00FFFFFF & rgb3);
                int rgb4 = im.getRGB(x, height - y - 1);
                im.setRGB(x, height - y - 1, alpha | 0x00FFFFFF & rgb4);
            }
        
        return im;
    }

    public BufferedImage makeEdgesTransparent(BufferedImage src)
    {
        BufferedImage result = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);

        int bg = src.getRGB(0, 0);

        for (int i=0; i<src.getWidth(); i++)
            for (int j=0; j<src.getHeight(); j++)
            {
                int rgb = src.getRGB(i, j);
                if ((0xFFFFFF & rgb) == (0xFFFFFF & bg))
                    result.setRGB(i, j, 0x00000000);
                else
                    result.setRGB(i, j, rgb);
            }
        
        return result;
    }

    public BufferedImage noAlpha(BufferedImage src)
    {
        BufferedImage im = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = im.createGraphics();
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        
        return im;
    }

    public java.awt.Color getAverageColour(BufferedImage src)
    {
        return getAverageColour(src, 0, src.getWidth(), 0, src.getHeight());
    }

    public java.awt.Color getAverageColour(BufferedImage src, int x1, int y1, int x2, int y2)
    {
        double rtot = 0;
        double gtot = 0;
        double btot = 0;

        x1 = Math.max(0, Math.min(src.getWidth(), x1));
        y1 = Math.max(0, Math.min(src.getHeight(), y1));
        x2 = Math.max(0, Math.min(src.getWidth(), x2));
        y2 = Math.max(0, Math.min(src.getHeight(), y2));

        x2 = Math.max(x1, x2);
        y2 = Math.max(y1, y2);
        
        int count = 0;
        for (int i=x1; i<x2; i++)
            for (int j=y1; j<y2; j++)
            {
                int rgb = src.getRGB(i, j);
                if ((rgb >> 24) == 0)
                    continue;

                count++;
                int r = 0xFF & (rgb >> 16);
                int g = 0xFF & (rgb >> 8);
                int b = 0xFF & (rgb);

                rtot += r;
                gtot += g;
                btot += b;
            }    

        if (count == 0)
            return new java.awt.Color(0);

        int rave = (int) (rtot/count);
        int gave = (int) (gtot/count);
        int bave = (int) (btot/count);

        return new Color(0xFF000000 | (rave << 16) | (gave << 8) | bave);
    }

    public BufferedImage shiftAverageColour(BufferedImage src, String newAverageColourSpec, int radius)
    {        
        java.awt.Color newAverage = toAWTColor(newAverageColourSpec);
        return shiftColour(src, getAverageColour(src), newAverage, radius);
    }

    public BufferedImage shiftColour(BufferedImage src, int colourRefX, int colourRefY, String newColourSpec, int radius)
    {
        java.awt.Color newColour = toAWTColor(newColourSpec);
        Color reference = new Color(src.getRGB(colourRefX, colourRefY));
        return shiftColour(src, reference, newColour, radius);
    }

    public BufferedImage shiftColour(BufferedImage src, String refSpec, String newColourSpec, int radius)
    {   
        return shiftColour(src, toAWTColor(refSpec), toAWTColor(newColourSpec), radius);
    }

    public BufferedImage shiftColour(BufferedImage src, java.awt.Color reference, java.awt.Color newColour, int radius)
    {    
        BufferedImage result = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);

        int refRGB = 0xFFFFFF & reference.getRGB();
        int rRef = 0xFF & (refRGB >> 16);
        int gRef = 0xFF & (refRGB >> 8);
        int bRef = 0xFF & (refRGB);

        int newRGB = 0xFFFFFF & newColour.getRGB();
        int newR = 0xFF & (newRGB >> 16);
        int newG = 0xFF & (newRGB >> 8);
        int newB = 0xFF & (newRGB);

        for (int i=0; i<src.getWidth(); i++)
            for (int j=0; j<src.getHeight(); j++)
            {
                int rgb = src.getRGB(i, j);
                int alpha = (rgb >> 24);
                if (alpha == 0)
                    continue;

                int r = 0xFF & (rgb >> 16);
                int g = 0xFF & (rgb >> 8);
                int b = 0xFF & (rgb);

                int dr = r - rRef;
                int db = g - gRef;
                int dg = b - bRef;
                
                double distance = Math.sqrt(dr*dr + dg*dg + db*db);
                if (distance < radius)
                {
                    r = Math.min(255, Math.max(0, newR + dr));
                    g = Math.min(255, Math.max(0, newG + dg));
                    b = Math.min(255, Math.max(0, newB + db));
                }
                
                rgb = (alpha << 24) | (r << 16) | (g << 8) | b;
                result.setRGB(i, j, rgb);
            }

        return result;
    }

    public BufferedImage replaceColour(BufferedImage src, java.awt.Color reference, java.awt.Color newColour, int radius)
    {    
        BufferedImage result = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);

        int refRGB = 0xFFFFFF & reference.getRGB();
        int rRef = 0xFF & (refRGB >> 16);
        int gRef = 0xFF & (refRGB >> 8);
        int bRef = 0xFF & (refRGB);

        int newRGB = 0xFFFFFF & newColour.getRGB();
        int newR = 0xFF & (newRGB >> 16);
        int newG = 0xFF & (newRGB >> 8);
        int newB = 0xFF & (newRGB);

        for (int i=0; i<src.getWidth(); i++)
            for (int j=0; j<src.getHeight(); j++)
            {
                int rgb = src.getRGB(i, j);
                int alpha = (rgb >> 24);
                if (alpha == 0)
                    continue;

                int r = 0xFF & (rgb >> 16);
                int g = 0xFF & (rgb >> 8);
                int b = 0xFF & (rgb);

                int dr = r - rRef;
                int db = g - gRef;
                int dg = b - bRef;
                
                double distance = Math.sqrt(dr*dr + dg*dg + db*db);
                if (distance < radius)
                {
                    r = Math.min(255, Math.max(0, newR));
                    g = Math.min(255, Math.max(0, newG));
                    b = Math.min(255, Math.max(0, newB));
                }
                
                rgb = (alpha << 24) | (r << 16) | (g << 8) | b;
                result.setRGB(i, j, rgb);
            }

        return result;
    }

    public BufferedImage replaceColour(BufferedImage src, String refSpec, String newColourSpec)
    {
        java.awt.Color originalColour = toAWTColor(refSpec);
        java.awt.Color newColour = toAWTColor(newColourSpec);
        
        BufferedImage result = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        
        int originalRGB = 0xFFFFFF & originalColour.getRGB();
        int newRGB = 0xFFFFFF & newColour.getRGB();

        for (int i=0; i<src.getWidth(); i++)
            for (int j=0; j<src.getHeight(); j++)
            {
                int rgb = src.getRGB(i, j);
                int noAlpha = 0xFFFFFFFF & rgb;
                if (noAlpha == originalRGB)
                    result.setRGB(i, j, (0xFF000000 & rgb) | newRGB);
                else
                    result.setRGB(i, j, rgb);
            }
        
        return result;
    }

    private int getBorderColor(double dist, int imageColor, int borderColor, int outerBorderEdge, int borderMiddle, int innerBorderEdge, boolean imageColourEdge)
    {
        if (dist < outerBorderEdge)
        {
            int alpha = (int) (255 * dist / outerBorderEdge);
            if (imageColourEdge)
                return (0xFF000000 & (alpha << 24)) | (0xFFFFFF & imageColor);
            else
                return (0xFF000000 & (alpha << 24)) | (0xFFFFFF & borderColor);
        }
        else if (dist < outerBorderEdge + borderMiddle)
            return borderColor;
        else if (dist < outerBorderEdge + borderMiddle + innerBorderEdge)
        {
            int a1 = 0xFF & (borderColor >> 24);
            int r1 = 0xFF & (borderColor >> 16);
            int g1 = 0xFF & (borderColor >> 8);
            int b1 = 0xFF & borderColor;
            
            int a2 = 0xFF & (imageColor >> 24);
            int r2 = 0xFF & (imageColor >> 16);
            int g2 = 0xFF & (imageColor >> 8);
            int b2 = 0xFF & imageColor;

            double factor = 1 - (dist - outerBorderEdge - borderMiddle) / innerBorderEdge;
            
            int aa = (int) (a1* factor + (1 - factor) * a2);
            int rr = (int) (r1* factor + (1 - factor) * r2);
            int bb = (int) (b1* factor + (1 - factor) * b2);
            int gg = (int) (g1* factor + (1 - factor) * g2);
            int a = 255;
            
            return (aa << 24) | (rr << 16) | (gg << 8) | bb;
        }
        else
            return imageColor;
    }

    public BufferedImage drawSimpleBorder(BufferedImage im, int radius, String bcSpec, int width)
    {
        return drawBorder(im, radius, bcSpec, 2, width, 2, false);
    }

    public BufferedImage drawBorder(BufferedImage im, int radius, String bcSpec, int outerBorderEdge, int borderMiddle, int innerBorderEdge, boolean imageColourEdge)
    {
        java.awt.Color bc = toAWTColor(bcSpec);

        int width = im.getWidth();
        int height = im.getHeight();
        im = copyImage(im, width, height);

        int brgb = bc.getRGB();
        int borderThickness = outerBorderEdge + borderMiddle + innerBorderEdge;
        
        for (int x=0; x<radius; x++)
            for (int y=0; y<radius; y++)
            {
                double dist = (1.0*x - radius) * (1.0*x - radius);
                dist += (1.0*y - radius) * (1.0*y - radius);
                dist = radius - Math.sqrt(dist);
                
                if (dist < 0)
                {
                    im.setRGB(x, y, 0);
                    im.setRGB(width - x - 1, y, 0);
                    im.setRGB(width - x - 1, height - y - 1, 0);
                    im.setRGB(x, height - y - 1, 0);
                    continue;
                }
                else if (dist > borderThickness)
                    continue;

                int x1 = x;
                int y1 = y;
                im.setRGB(x1, y1, getBorderColor(dist, im.getRGB(x1, y1), brgb, outerBorderEdge, borderMiddle, innerBorderEdge, imageColourEdge));

                x1 = width - x - 1;
                y1 = y;
                im.setRGB(x1, y1, getBorderColor(dist, im.getRGB(x1, y1), brgb, outerBorderEdge, borderMiddle, innerBorderEdge, imageColourEdge));

                x1 = width - x - 1;
                y1 = height - y - 1;
                im.setRGB(x1, y1, getBorderColor(dist, im.getRGB(x1, y1), brgb, outerBorderEdge, borderMiddle, innerBorderEdge, imageColourEdge));

                x1 = x;
                y1 = height - y - 1;
                im.setRGB(x1, y1, getBorderColor(dist, im.getRGB(x1, y1), brgb, outerBorderEdge, borderMiddle, innerBorderEdge, imageColourEdge));
            }

        for (int x=radius; x<width-radius; x++)
            for (int y=0; y<radius; y++)
            {
                if (y > borderThickness)
                    continue;
                
                int rgb = getBorderColor(y, im.getRGB(x, y), brgb, outerBorderEdge, borderMiddle, innerBorderEdge, imageColourEdge);
                im.setRGB(x, y, rgb);
                rgb = getBorderColor(y, im.getRGB(x, height - y - 1), brgb, outerBorderEdge, borderMiddle, innerBorderEdge, imageColourEdge);
                im.setRGB(x, height - y - 1, rgb);
            }

        for (int y=radius; y<height-radius; y++)
            for (int x=0; x<radius; x++)
            {
                if (x > borderThickness)
                    continue;
                
                int rgb = getBorderColor(x, im.getRGB(x, y), brgb, outerBorderEdge, borderMiddle, innerBorderEdge, imageColourEdge);
                im.setRGB(x, y, rgb);
                rgb = getBorderColor(x, im.getRGB(width - x - 1, y), brgb, outerBorderEdge, borderMiddle, innerBorderEdge, imageColourEdge);
                im.setRGB(width - x - 1, y, rgb);
            }

        return im;
    }
}
