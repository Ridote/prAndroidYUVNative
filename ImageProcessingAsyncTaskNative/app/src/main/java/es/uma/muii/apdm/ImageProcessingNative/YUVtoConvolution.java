package es.uma.muii.apdm.ImageProcessingNative;

/**
 * Created by movinf005 on 18/11/15.
 */
public class YUVtoConvolution {

    public static void convertYUV420_NV21toConvolution(byte [] data, int [] pixels, int width, int height, int[] matrix, int divisor)
    // pixels must have room for width*height ints, one per pixel. See https://en.wikipedia.org/wiki/YUV
    {
        int pos = width+1;
        int prevrow, nextrow, aux, d,i,j;


        for (j = 1; j < height -1; j++) {
            for(i = 1;  i < width - 1; i++) { // each 4x4 region of the bitmap has the same (u,v) but different y1,y2,y3,y4
                prevrow = pos-width;
                nextrow = pos+width;
                aux = matrix[0]*(((int)data[prevrow-1])&0xff);
                aux += matrix[1]*(((int)data[prevrow])&0xff);
                aux += matrix[2]*(((int)data[prevrow+1])&0xff);
                aux += matrix[3]*(((int)data[pos-1])&0xff);
                aux += matrix[4]*(((int)data[pos])&0xff);
                aux += matrix[5]*(((int)data[pos+1])&0xff);
                aux += matrix[6]*(((int)data[nextrow-1])&0xff);
                aux += matrix[7]*(((int)data[nextrow])&0xff);
                aux += matrix[8]*(((int)data[nextrow+1])&0xff);
                d = (aux>255 ? 255 : aux < 0 ? 0 : aux);
                pixels[pos] = (0xff000000 | (d << 16) | (d << 8) | d)/divisor;
                pos++;
            }
            pos+=2;
        }
    }
}
