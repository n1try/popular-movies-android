package com.github.n1try.popularmovies.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

public class ImageUtils {
    // https://gist.github.com/bodyflex/a6ab15cbba9c82a5065d
    public static int getAverageColor(Bitmap bitmap) {
        int R = 0;
        int G = 0;
        int B = 0;
        int height = bitmap.getHeight();
        int width = bitmap.getWidth();
        int n = 0;
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int i = 0; i < pixels.length; i += 1) {
            int color = pixels[i];
            R += Color.red(color);
            G += Color.green(color);
            B += Color.blue(color);
            n++;
        }
        return Color.rgb(R / n, G / n, B / n);
    }

    /* http://tech.chitgoks.com/2010/07/27/check-if-color-is-dark-or-light-using-java/ */
    public static int getBrightness(int color) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return (int) Math.sqrt(red * red * .241 + green * green * .691 + blue * blue * .068);
    }
}
