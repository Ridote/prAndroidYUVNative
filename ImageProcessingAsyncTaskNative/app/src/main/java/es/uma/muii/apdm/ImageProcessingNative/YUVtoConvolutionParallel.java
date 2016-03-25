package es.uma.muii.apdm.ImageProcessingNative;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by movinf005 on 18/11/15.
 */
public class YUVtoConvolutionParallel {
    private ExecutorService exSrv;
    private int nThreads;

    YUVtoConvolutionParallel(int _nthreads) {
        nThreads = _nthreads;
        exSrv = Executors.newFixedThreadPool(nThreads);
    }

    public void shutdown() {
        exSrv.shutdown();
    }

    private static void convertYUV420_NV21toConvolution8888(byte [] data, int [] pixels, int width, int height, int[] matrix, int divisor, int my_id, int nThreads)
    // pixels must have room for width*height ints, one per pixel. See https://en.wikipedia.org/wiki/YUV
    {
        int myEnd, myOff;

        int myInitRow = (int)(((float)my_id/(float)nThreads)*((float)height/2.0f));
        int nextThreadRow = (int)(((float)(my_id+1)/(float)nThreads)*((float)height/2.0f));
        myOff = 2*width*myInitRow;
        myEnd = 2*width*nextThreadRow;


        int prevrow, nextrow, aux, d;


        for(int i=myOff; i < myEnd; i+=1) {
            prevrow = i-width;
            nextrow = i+width;
            aux = matrix[0]*(((int)data[prevrow-1])&0xff);
            aux += matrix[1]*(((int)data[prevrow])&0xff);
            aux += matrix[2]*(((int)data[prevrow+1])&0xff);
            aux += matrix[3]*(((int)data[i-1])&0xff);
            aux += matrix[4]*(((int)data[i])&0xff);
            aux += matrix[5]*(((int)data[i+1])&0xff);
            aux += matrix[6]*(((int)data[nextrow-1])&0xff);
            aux += matrix[7]*(((int)data[nextrow])&0xff);
            aux += matrix[8]*(((int)data[nextrow+1])&0xff);
            d = (aux>255 ? 255 : aux < 0 ? 0 : aux);
            pixels[i] = (0xff000000 | (d << 16) | (d << 8) | d)/divisor;
        }
    }

    private final class myJavaWorker implements Callable<Void> {
        private byte [] data;
        private int [] pixels;
        private int width;
        private int height;
        private int id;
        private int nth;
        private int[] matrix;
        private int divisor;

        public myJavaWorker(byte [] _data, int [] _pixels, int _width, int _height, int[] matrix, int divisor, int _th_id, int _nth) {
            data = _data;
            pixels = _pixels;
            width = _width;
            height = _height;
            id = _th_id;
            nth = _nth;
            this.divisor = divisor;
            this.matrix = matrix;
        }

        public Void call(){
            convertYUV420_NV21toConvolution8888(data, pixels, width, height, matrix, divisor, id, nth);
            return null;
        }

    }

    public void convertYUV420_NV21toConvolution8888_parallel(byte [] data, int [] pixels, int width, int height, int[] matrix, int divisor) {
        List<Callable<Void>> todoTasks;
        todoTasks = new ArrayList<Callable<Void>>(nThreads);

        for (int i=0; i < nThreads; i++) {
            todoTasks.add(i, new myJavaWorker(data, pixels, width, height, matrix, divisor, i, nThreads));
        }
        try {
            exSrv.invokeAll(todoTasks);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
