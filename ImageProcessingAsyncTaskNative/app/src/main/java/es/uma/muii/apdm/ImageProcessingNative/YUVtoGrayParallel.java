package es.uma.muii.apdm.ImageProcessingNative;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
 * Created by movinf005 on 18/11/15.
 */
public class YUVtoGrayParallel {
    private ExecutorService exSrv;
    private int nThreads;

    YUVtoGrayParallel(int _nthreads) {
        nThreads = _nthreads;
        exSrv = Executors.newFixedThreadPool(nThreads);
    }

    public void shutdown() {
        exSrv.shutdown();
    }

    private static void convertYUV420_NV21toGray8888(byte [] data, int [] pixels, int width, int height, int my_id, int nThreads)
    // pixels must have room for width*height ints, one per pixel. See https://en.wikipedia.org/wiki/YUV
    {
        int y1, y2, y3, y4;
        int myEnd, myOff;

        int myInitRow = (int)(((float)my_id/(float)nThreads)*((float)height/2.0f));
        int nextThreadRow = (int)(((float)(my_id+1)/(float)nThreads)*((float)height/2.0f));
        myOff = 2*width*myInitRow;
        myEnd = 2*width*nextThreadRow;

        //Log.d("HOOK", "convertYUV420_NV21toRGB8888: id "+my_id+" nth "+nThreads+" " + width + "x" + height + " initrow "+myInitRow+" nextrow "+nextThreadRow+" my_off " + myOff + " my_end " + myEnd);
        for(int i=myOff; i < myEnd; i+=2) { // each 4x4 region of the bitmap has the same (u,v) but different y1,y2,y3,y4
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

    private final class myJavaWorker implements Callable<Void> {
        private byte [] data;
        private int [] pixels;
        private int width;
        private int height;
        private int id;
        private int nth;

        public myJavaWorker(byte [] _data, int [] _pixels, int _width, int _height, int _th_id, int _nth) {
            data = _data;
            pixels = _pixels;
            width = _width;
            height = _height;
            id = _th_id;
            nth = _nth;
        }

        public Void call(){
            convertYUV420_NV21toGray8888(data, pixels, width, height, id, nth);
            return null;
        }

    }

    public void convertYUV420_NV21toGray8888_parallel(byte [] data, int [] pixels, int width, int height) {
        List<Callable<Void>> todoTasks;
        todoTasks = new ArrayList<Callable<Void>>(nThreads);

        for (int i=0; i < nThreads; i++) {
            todoTasks.add(i, new myJavaWorker(data, pixels, width, height, i, nThreads));
        }
        try {
            exSrv.invokeAll(todoTasks);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
