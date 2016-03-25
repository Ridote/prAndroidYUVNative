package es.uma.muii.apdm.ImageProcessingNative;

/**
 * Created by movinf005 on 18/11/15.
 */
public class YUVtoGray {
    public static void convertYUV420_NV21toGray8888(byte [] data, int[] pixels, int width, int height)
    // pixels must have room for width*height ints, one per pixel. See https://en.wikipedia.org/wiki/YUV
    {
        int size = width*height;
        int y1, y2, y3, y4;

        // i percorre os Y and the final pixels
        // k percorre os pixles U e V
        for(int i=0; i < size; i+=2) { // each 4x4 region of the bitmap has the same (u,v) but different y1,y2,y3,y4
            y1 = data[i  ]&0xff;
            y2 = data[i+1]&0xff;
            y3 = data[width+i  ]&0xff;
            y4 = data[width+i+1]&0xff;

            pixels[i  ] = 0xff000000 | (y1<<16) | (y1<<8) | y1;
            pixels[i+1] = 0xff000000 | (y2<<16) | (y2<<8) | y2;
            pixels[width+i  ] = 0xff000000 | (y3<<16) | (y3<<8) | y3;
            pixels[width+i+1] = 0xff000000 | (y4<<16) | (y4<<8) | y4;

            if (i!=0 && (i+2)%width==0)
                i+=width;
        }

    }
}
