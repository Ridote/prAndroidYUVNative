#include <string.h>
#include <jni.h>
#include <pthread.h>
#include <omp.h>
#include <android/log.h>

#define MAX_NUM_THREADS 16

    typedef struct paramST {  // data structure holding all operands needed by a worker thread
        unsigned char * data;
        int * pixels;
        int width;
        int height;
        int my_id;
        int nthr;
    } paramST;

    typedef struct paramSTConv {  // data structure holding all operands needed by a worker thread
        unsigned char * data;
        int * pixels;
        int width;
        int height;
        int my_id;
        int nthr;
        const int * matrix;
        int divisor;
    } paramSTConv;

    // return RGB value from YUV
    static int convertYUVtoRGB(int y, int u, int v)
    {
        int r,g,b;

        r = y + (int)1.14f*v;
        g = y - (int)(0.395f*u +0.581f*v);
        b = y + (int)2.033f*u;
        r = r>255? 255 : r<0 ? 0 : r;
        g = g>255? 255 : g<0 ? 0 : g;
        b = b>255? 255 : b<0 ? 0 : b;
        return 0xff000000 | (b<<16) | (g<<8) | r; // one byte for alpha, one for blue, one for green, one for red
    }

//--------------------------------------------------------------------------------------------------
//                                              YUV 2 RGB
//--------------------------------------------------------------------------------------------------

//                                          YUV 2 RGB NATIVE
//--------------------------------------------------------------------------------------------------

    // process the whole image
    static void convertYUV420_NV21toRGB8888(const unsigned char * data, int * pixels, int width, int height)
    // pixels must have room for width*height ints, one per pixel
    {
         int size = width*height;
         int u, v, y1, y2, y3, y4;
         int i, k;

         // i traverses Y
         // k traverses U and V
         for(i=0, k=0; i < size; i+=2, k+=2) { // each 4x4 region of the bitmap has the same (u,v) but different y1,y2,y3,y4
             y1 = data[i  ]&0xff;
             y2 = data[i+1]&0xff;
             y3 = data[width+i  ]&0xff;
             y4 = data[width+i+1]&0xff;

             u = data[size+k  ]&0xff;
             v = data[size+k+1]&0xff;
             u = u-128;
             v = v-128;

             pixels[i  ] = convertYUVtoRGB(y1, u, v);
             pixels[i+1] = convertYUVtoRGB(y2, u, v);
             pixels[width+i  ] = convertYUVtoRGB(y3, u, v);
             pixels[width+i+1] = convertYUVtoRGB(y4, u, v);

             if (i!=0 && (i+2)%width==0)
                 i+=width;
         }
    }

    // Native function (convertYUV420_NV21toRGB8888)
    void Java_es_uma_muii_apdm_ImageProcessingNative_MainActivity_YUVtoRGBNative( JNIEnv* env, jobject thiz,
                                                                        jbyteArray data,
                                                                         jintArray result,
                                                                         jint width, jint height)
    {
        unsigned char *cData;
        int *cResult;

        cData = (*env)->GetByteArrayElements(env,data,NULL); // While arrays of objects must be accessed one entry at a time, arrays of primitives can be read and written directly as if they were declared in C.   http://developer.android.com/training/articles/perf-jni.html
        if (cData==NULL) __android_log_print(ANDROID_LOG_INFO, "HOOKnative", "Can't not get data array reference");
        else
        {
            cResult= (*env)->GetIntArrayElements(env,result,NULL);
            if (cResult==NULL) __android_log_print(ANDROID_LOG_INFO, "HOOKnative", "Can't not get result array reference");
            else
            {
                // operates on data
                convertYUV420_NV21toRGB8888(cData,cResult,width,height);
            }
        }
        if (cResult!=NULL) (*env)->ReleaseIntArrayElements(env,result,cResult,0); // care: errors in the type of the pointers are detected in runtime
        if (cData!=NULL) (*env)->ReleaseByteArrayElements(env, data, cData, 0); // release must be done even when the original array is not copied into cDATA

        // Log from native (need 'ndk {ldLibs "log"}' in app/build.gradle
        //__android_log_print(ANDROID_LOG_INFO, "HOOKnative", "Log from native function: %s", buf);

    }

//                                          YUV 2 RGB OMP
//--------------------------------------------------------------------------------------------------
    static void convertYUV420_NV21toRGB8888_OMP(const unsigned char * data, int * pixels, int width, int height)
    // pixels must have room for width*height ints, one per pixel
    {
        int size = width*height;
        int u, v, y1, y2, y3, y4;
        int i, k, y, yrow, ycol;

        // "y" traverses the set of four "Y"-pixels that share components U and V.
        #pragma omp parallel for schedule(guided) private(yrow, ycol, i, k, u, v, y1, y2, y3, y4)
        for (y=0; y<size/4; y++) {
            //__android_log_print(ANDROID_LOG_INFO, "HOOKnative", "num threads %d", omp_get_num_threads());
            yrow = (int)(y/(width/2));              // row of the first "y" set
            ycol = y % (width/2);                   // col of the first "y" set
            i = 2*(yrow*width + ycol);              // offset in array "data" of first "Y" of "y" set
            k = size + yrow*width + 2*ycol;         // offset in array "data" of the "U" component of "y" set

            y1 = data[i  ]&0xff;
            y2 = data[i+1]&0xff;
            y3 = data[width+i  ]&0xff;
            y4 = data[width+i+1]&0xff;

            u = data[k  ]&0xff;
            v = data[k+1]&0xff;
            u = u-128;
            v = v-128;

            pixels[i  ] = convertYUVtoRGB(y1, u, v);
            pixels[i+1] = convertYUVtoRGB(y2, u, v);
            pixels[width+i  ] = convertYUVtoRGB(y3, u, v);
            pixels[width+i+1] = convertYUVtoRGB(y4, u, v);
        }
    }

    // OMP function (convertYUV420_NV21toRGB8888_OMP)
    void Java_es_uma_muii_apdm_ImageProcessingNative_MainActivity_YUVtoRGBNativeParallelOMP( JNIEnv* env, jobject thiz,
                                                                                     jbyteArray data,
                                                                                     jintArray result,
                                                                                     jint width, jint height, jint nthreads)
    {
        unsigned char *cData;
        int *cResult;

        cData = (*env)->GetByteArrayElements(env,data,NULL); // While arrays of objects must be accessed one entry at a time, arrays of primitives can be read and written directly as if they were declared in C.   http://developer.android.com/training/articles/perf-jni.html
        if (cData==NULL) __android_log_print(ANDROID_LOG_INFO, "HOOKnative", "Can't get data array reference");
        else
        {
            cResult= (*env)->GetIntArrayElements(env,result,NULL);
            if (cResult==NULL) __android_log_print(ANDROID_LOG_INFO, "HOOKnative", "Can't get result array reference");
            else
            {
                omp_set_num_threads(nthreads);
                // operates on data
                convertYUV420_NV21toRGB8888_OMP(cData,cResult,width,height);
            }
        }
        if (cResult!=NULL) (*env)->ReleaseIntArrayElements(env,result,cResult,0); // care: errors in the type of the pointers are detected at runtime
        if (cData!=NULL) (*env)->ReleaseByteArrayElements(env, data, cData, 0); // release must be done even when the original array is not copied into cDATA
    }


//                                          YUV 2 RGB PTHREAD
//--------------------------------------------------------------------------------------------------

    // Process a chunk of the image
     void *convertYUV420_NV21toRGB8888Chunk(void *args)
    // pixels must have room for width*height ints, one per pixel. See https://en.wikipedia.org/wiki/YUV
    {
        paramST *param = (paramST *)args;

        unsigned char * data = param->data;
        int * pixels = param->pixels;
        int width = param->width;
        int height = param->height;
        int my_id = param->my_id;
        int nThreads = param->nthr;

        int size = width*height;
        int u=0, v=0, y1, y2, y3, y4;
        int myEnd, myOff;
        int i, k;

        int myInitRow = (int)(((float)my_id/(float)nThreads)*((float)height/2.0f));
        int nextThreadRow = (int)(((float)(my_id+1)/(float)nThreads)*((float)height/2.0f));
        myOff = 2*width*myInitRow;
        myEnd = 2*width*nextThreadRow;
        int myUVOff = size+myInitRow*width;
        for(i=myOff, k=myUVOff; i < myEnd; i+=2, k+=2) { // each 4x4 region of the bitmap has the same (u,v) but different y1,y2,y3,y4
            y1 = data[i  ]&0xff;
            y2 = data[i+1]&0xff;
            y3 = data[width+i  ]&0xff;
            y4 = data[width+i+1]&0xff;

            u = data[k  ]&0xff;
            v = data[k+1]&0xff;
            u = u-128;
            v = v-128;

            pixels[i  ] = convertYUVtoRGB(y1, u, v);
            pixels[i+1] = convertYUVtoRGB(y2, u, v);
            pixels[width+i  ] = convertYUVtoRGB(y3, u, v);
            pixels[width+i+1] = convertYUVtoRGB(y4, u, v);

            if (i!=0 && (i+2)%width==0)
                i+=width;
        }
        pthread_exit(NULL);
    }

    // Process the whole image in parallel using nthr pthreads
    static void convertYUV420_NV21toRGB8888Parallel(const unsigned char * data, int * pixels, int width, int height, int nthr)
    {
        pthread_t th[MAX_NUM_THREADS];
        paramST params[MAX_NUM_THREADS];
        int status;
        int my_nthr = nthr;
        int i;

        if (my_nthr > MAX_NUM_THREADS)
            my_nthr = MAX_NUM_THREADS;
        for (i=0; i < my_nthr; i++) {
            params[i].data = data;
            params[i].pixels = pixels;
            params[i].width = width;
            params[i].height = height;
            params[i].my_id = i;
            params[i].nthr = my_nthr;
            pthread_create(&(th[i]), NULL, convertYUV420_NV21toRGB8888Chunk, (void *)&(params[i]));
        }
        for (i=0; i < my_nthr; i++) {
            pthread_join(th[i], (void*)&status);
        }
    }


    // Pthreads function (convertYUV420_NV21toRGB8888Parallel)
    void Java_es_uma_muii_apdm_ImageProcessingNative_MainActivity_YUVtoRGBNativeParallel( JNIEnv* env, jobject thiz,
                                                       jbyteArray data,
                                                       jintArray result,
                                                       jint width, jint height, jint nthreads)
    {
        unsigned char *cData;
        int *cResult;

        cData = (*env)->GetByteArrayElements(env,data,NULL); // While arrays of objects must be accessed one entry at a time, arrays of primitives can be read and written directly as if they were declared in C.   http://developer.android.com/training/articles/perf-jni.html
        if (cData==NULL) __android_log_print(ANDROID_LOG_INFO, "HOOKnative", "Can't get data array reference");
        else
        {
            cResult= (*env)->GetIntArrayElements(env,result,NULL);
            if (cResult==NULL) __android_log_print(ANDROID_LOG_INFO, "HOOKnative", "Can't get result array reference");
            else
            {
                // operates on data
                convertYUV420_NV21toRGB8888Parallel(cData,cResult,width,height,nthreads);
            }
        }
        if (cResult!=NULL) (*env)->ReleaseIntArrayElements(env,result,cResult,0); // care: errors in the type of the pointers are detected at runtime
        if (cData!=NULL) (*env)->ReleaseByteArrayElements(env, data, cData, 0); // release must be done even when the original array is not copied into cDATA
    }

//--------------------------------------------------------------------------------------------------
//                                              YUV 2 GRAY
//--------------------------------------------------------------------------------------------------

//                                          YUV 2 GRAY NATIVE
//--------------------------------------------------------------------------------------------------
    // process the whole image
    static void convertYUV420_NV21toGray8888(const unsigned char * data, int * pixels, int width, int height)
    // pixels must have room for width*height ints, one per pixel
    {
        int size = width*height;
        int y1, y2, y3, y4;
        int i;

        // i traverses Y
        // k traverses U and V
        for(i=0; i < size; i+=2) { // each 4x4 region of the bitmap has the same (u,v) but different y1,y2,y3,y4
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

    // Native function (convertYUV420_NV21toGray8888)
    void Java_es_uma_muii_apdm_ImageProcessingNative_MainActivity_YUVtoGrayNative( JNIEnv* env, jobject thiz,
                                                                                  jbyteArray data,
                                                                                  jintArray result,
                                                                                  jint width, jint height)
    {
        unsigned char *cData;
        int *cResult;

        cData = (*env)->GetByteArrayElements(env,data,NULL); // While arrays of objects must be accessed one entry at a time, arrays of primitives can be read and written directly as if they were declared in C.   http://developer.android.com/training/articles/perf-jni.html
        if (cData==NULL) __android_log_print(ANDROID_LOG_INFO, "HOOKnative", "Can't not get data array reference");
        else
        {
            cResult= (*env)->GetIntArrayElements(env,result,NULL);
            if (cResult==NULL) __android_log_print(ANDROID_LOG_INFO, "HOOKnative", "Can't not get result array reference");
            else
            {
                // operates on data
                convertYUV420_NV21toGray8888(cData,cResult,width,height);
            }
        }
        if (cResult!=NULL) (*env)->ReleaseIntArrayElements(env,result,cResult,0); // care: errors in the type of the pointers are detected in runtime
        if (cData!=NULL) (*env)->ReleaseByteArrayElements(env, data, cData, 0); // release must be done even when the original array is not copied into cDATA

        // Log from native (need 'ndk {ldLibs "log"}' in app/build.gradle
        //__android_log_print(ANDROID_LOG_INFO, "HOOKnative", "Log from native function: %s", buf);

    }

//                                          YUV 2 GRAY OMP
//--------------------------------------------------------------------------------------------------
    // process the whole image
    static void convertYUV420_NV21toGray8888_OMP(const unsigned char * data, int * pixels, int width, int height)
    // pixels must have room for width*height ints, one per pixel
    {
        int size = width*height;
        int u, v, y1, y2, y3, y4;
        int i, y, yrow, ycol;

        // "y" traverses the set of four "Y"-pixels that share components U and V.
        #pragma omp parallel for schedule(guided) private(yrow, ycol, i, u, v, y1, y2, y3, y4)
        for (y=0; y<size/4; y++) {
            //__android_log_print(ANDROID_LOG_INFO, "HOOKnative", "num threads %d", omp_get_num_threads());
            yrow = (int)(y/(width/2));              // row of the first "y" set
            ycol = y % (width/2);                   // col of the first "y" set
            i = 2*(yrow*width + ycol);              // offset in array "data" of first "Y" of "y" set

            y1 = data[i  ]&0xff;
            y2 = data[i+1]&0xff;
            y3 = data[width+i  ]&0xff;
            y4 = data[width+i+1]&0xff;

            pixels[i  ] = 0xff000000 | (y1<<16) | (y1<<8) | y1;
            pixels[i+1] = 0xff000000 | (y2<<16) | (y2<<8) | y2;
            pixels[width+i  ] = 0xff000000 | (y3<<16) | (y3<<8) | y3;
            pixels[width+i+1] = 0xff000000 | (y4<<16) | (y4<<8) | y4;
        }
    }

    // OMP function (convertYUV420_NV21toGray8888_OMP)
    void Java_es_uma_muii_apdm_ImageProcessingNative_MainActivity_YUVtoGrayNativeParallelOMP( JNIEnv* env, jobject thiz,
                                                                                              jbyteArray data,
                                                                                              jintArray result,
                                                                                              jint width, jint height, jint nthreads)
    {
        unsigned char *cData;
        int *cResult;

        cData = (*env)->GetByteArrayElements(env,data,NULL); // While arrays of objects must be accessed one entry at a time, arrays of primitives can be read and written directly as if they were declared in C.   http://developer.android.com/training/articles/perf-jni.html
        if (cData==NULL) __android_log_print(ANDROID_LOG_INFO, "HOOKnative", "Can't get data array reference");
        else
        {
            cResult= (*env)->GetIntArrayElements(env,result,NULL);
            if (cResult==NULL) __android_log_print(ANDROID_LOG_INFO, "HOOKnative", "Can't get result array reference");
            else
            {
                omp_set_num_threads(nthreads);
                // operates on data
                convertYUV420_NV21toGray8888_OMP(cData,cResult,width,height);
            }
        }
        if (cResult!=NULL) (*env)->ReleaseIntArrayElements(env,result,cResult,0); // care: errors in the type of the pointers are detected at runtime
        if (cData!=NULL) (*env)->ReleaseByteArrayElements(env, data, cData, 0); // release must be done even when the original array is not copied into cDATA
    }
//                                          YUV 2 GRAY PTHREAD
        // Process a chunk of the image
        void *convertYUV420_NV21toGray8888Chunk(void *args)
        // pixels must have room for width*height ints, one per pixel. See https://en.wikipedia.org/wiki/YUV
        {
            paramST *param = (paramST *)args;

            unsigned char * data = param->data;
            int * pixels = param->pixels;
            int width = param->width;
            int height = param->height;
            int my_id = param->my_id;
            int nThreads = param->nthr;

            int size = width*height;
            int y1, y2, y3, y4;
            int myEnd, myOff;
            int i, k;

            int myInitRow = (int)(((float)my_id/(float)nThreads)*((float)height/2.0f));
            int nextThreadRow = (int)(((float)(my_id+1)/(float)nThreads)*((float)height/2.0f));
            myOff = 2*width*myInitRow;
            myEnd = 2*width*nextThreadRow;
            int myUVOff = size+myInitRow*width;
            for(i=myOff, k=myUVOff; i < myEnd; i+=2, k+=2) { // each 4x4 region of the bitmap has the same (u,v) but different y1,y2,y3,y4
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
            pthread_exit(NULL);
        }

        // Process the whole image in parallel using nthr pthreads
        static void convertYUV420_NV21toGray8888Parallel(const unsigned char * data, int * pixels, int width, int height, int nthr)
        {
            pthread_t th[MAX_NUM_THREADS];
            paramST params[MAX_NUM_THREADS];
            int status;
            int my_nthr = nthr;
            int i;

            if (my_nthr > MAX_NUM_THREADS)
                my_nthr = MAX_NUM_THREADS;
            for (i=0; i < my_nthr; i++) {
                params[i].data = data;
                params[i].pixels = pixels;
                params[i].width = width;
                params[i].height = height;
                params[i].my_id = i;
                params[i].nthr = my_nthr;
                pthread_create(&(th[i]), NULL, convertYUV420_NV21toGray8888Chunk, (void *)&(params[i]));
            }
            for (i=0; i < my_nthr; i++) {
                pthread_join(th[i], (void*)&status);
            }
        }


        // Pthreads function (convertYUV420_NV21toRGB8888Parallel)
        void Java_es_uma_muii_apdm_ImageProcessingNative_MainActivity_YUVtoGrayNativeParallel( JNIEnv* env, jobject thiz,
                                                                                              jbyteArray data,
                                                                                              jintArray result,
                                                                                              jint width, jint height, jint nthreads)
        {
            unsigned char *cData;
            int *cResult;

            cData = (*env)->GetByteArrayElements(env,data,NULL); // While arrays of objects must be accessed one entry at a time, arrays of primitives can be read and written directly as if they were declared in C.   http://developer.android.com/training/articles/perf-jni.html
            if (cData==NULL) __android_log_print(ANDROID_LOG_INFO, "HOOKnative", "Can't get data array reference");
            else
            {
                cResult= (*env)->GetIntArrayElements(env,result,NULL);
                if (cResult==NULL) __android_log_print(ANDROID_LOG_INFO, "HOOKnative", "Can't get result array reference");
                else
                {
                    // operates on data
                    convertYUV420_NV21toGray8888Parallel(cData,cResult,width,height,nthreads);
                }
            }
            if (cResult!=NULL) (*env)->ReleaseIntArrayElements(env,result,cResult,0); // care: errors in the type of the pointers are detected at runtime
            if (cData!=NULL) (*env)->ReleaseByteArrayElements(env, data, cData, 0); // release must be done even when the original array is not copied into cDATA
        }

//--------------------------------------------------------------------------------------------------
//--------------------------------------------------------------------------------------------------
//--------------------------------------------------------------------------------------------------
//                                              YUV 2 CONVOLUTION
//--------------------------------------------------------------------------------------------------



    // process the whole image
    static void convertYUV420_NV21toConvolution8888(const unsigned char * data, int * pixels, int width, int height, const int * matrix, const int divisor)
    // pixels must have room for width*height ints, one per pixel
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

    // Native function (convertYUV420_NV21toGray8888)
    void Java_es_uma_muii_apdm_ImageProcessingNative_MainActivity_YUVtoConvolutionNative( JNIEnv* env, jobject thiz,
                                                                                   jbyteArray data,
                                                                                   jintArray result,
                                                                                   jint width, jint height, jintArray matrix, jint divisor)
    {
        unsigned char *cData;
        int *cResult;
        int *cMatrix = (*env)->GetIntArrayElements(env, matrix, NULL);
        cData = (*env)->GetByteArrayElements(env,data,NULL); // While arrays of objects must be accessed one entry at a time, arrays of primitives can be read and written directly as if they were declared in C.   http://developer.android.com/training/articles/perf-jni.html

        if (cData==NULL || cMatrix == NULL) __android_log_print(ANDROID_LOG_INFO, "HOOKnative", "Can't not get data array reference or Convolution Matrix reference");
        else
        {
            cResult= (*env)->GetIntArrayElements(env,result,NULL);
            if (cResult==NULL) __android_log_print(ANDROID_LOG_INFO, "HOOKnative", "Can't not get result array reference");
            else
            {
                // operates on data
                convertYUV420_NV21toConvolution8888(cData,cResult,width,height, cMatrix, divisor);
            }
        }
        if (cResult!=NULL) (*env)->ReleaseIntArrayElements(env,result,cResult,0); // care: errors in the type of the pointers are detected in runtime
        if (cData!=NULL) (*env)->ReleaseByteArrayElements(env, data, cData, 0); // release must be done even when the original array is not copied into cDATA
        if (cMatrix!=NULL) (*env)->ReleaseIntArrayElements(env,matrix,cMatrix,0);
        // Log from native (need 'ndk {ldLibs "log"}' in app/build.gradle
        //__android_log_print(ANDROID_LOG_INFO, "HOOKnative", "Log from native function: %s", buf);

    }

    //                                          YUV 2 CONVOLUTION OMP
    //--------------------------------------------------------------------------------------------------

    // process the whole image
    static void convertYUV420_NV21toConvolution8888_OMP(const unsigned char * data, int * pixels, int width, int height, const int * matrix, int divisor)
    // pixels must have room for width*height ints, one per pixel
    {
        int pos = width+1;
        int prevrow, nextrow, aux, d,i,j;

        #pragma omp parallel for schedule(guided) private(i,pos,prevrow,nextrow,aux,d)
        for (j = 1; j < height -1; j++) {
            for(i = 1;  i < width - 1; i++) { // each 4x4 region of the bitmap has the same (u,v) but different y1,y2,y3,y4
                pos = j*width+i;
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
            }
        }

    }

    // OMP function (convertYUV420_NV21toConvolution8888_OMP)
    void Java_es_uma_muii_apdm_ImageProcessingNative_MainActivity_YUVtoConvolutionNativeParallelOMP( JNIEnv* env, jobject thiz,
                                                                                              jbyteArray data,
                                                                                              jintArray result,
                                                                                              jint width, jint height, jint nthreads, jintArray matrix, jint divisor)
    {
        unsigned char *cData;
        int *cResult;
        int *cMatrix = (*env)->GetIntArrayElements(env, matrix, NULL);
        cData = (*env)->GetByteArrayElements(env,data,NULL); // While arrays of objects must be accessed one entry at a time, arrays of primitives can be read and written directly as if they were declared in C.   http://developer.android.com/training/articles/perf-jni.html

        if (cData==NULL || cMatrix == NULL) __android_log_print(ANDROID_LOG_INFO, "HOOKnative", "Can't get data array reference");
        else
        {
            cResult= (*env)->GetIntArrayElements(env,result,NULL);
            if (cResult==NULL) __android_log_print(ANDROID_LOG_INFO, "HOOKnative", "Can't get result array reference");
            else
            {
                omp_set_num_threads(nthreads);
                // operates on data
                convertYUV420_NV21toConvolution8888_OMP(cData,cResult,width,height, cMatrix, divisor);
            }
        }
        if (cResult!=NULL) (*env)->ReleaseIntArrayElements(env,result,cResult,0); // care: errors in the type of the pointers are detected at runtime
        if (cData!=NULL) (*env)->ReleaseByteArrayElements(env, data, cData, 0); // release must be done even when the original array is not copied into cDATA
        if (cMatrix!=NULL) (*env)->ReleaseIntArrayElements(env,matrix,cMatrix,0);
    }

    //                                          YUV 2 CONVOLUTION PTHREAD
    //--------------------------------------------------------------------------------------------------

    // Process a chunk of the image
    void *convertYUV420_NV21toConvolution8888Chunk(void *args)
    // pixels must have room for width*height ints, one per pixel. See https://en.wikipedia.org/wiki/YUV
    {
        paramSTConv *param = (paramSTConv *)args;

        unsigned char * data = param->data;
        int * pixels = param->pixels;
        int width = param->width;
        int height = param->height;
        int my_id = param->my_id;
        int nThreads = param->nthr;
        const int * matrix = param->matrix;
        const int divisor = param->divisor;

        int myEnd, myOff,i;

        int myInitRow = (int)(((float)my_id/(float)nThreads)*((float)height/2.0f));
        int nextThreadRow = (int)(((float)(my_id+1)/(float)nThreads)*((float)height/2.0f));
        myOff = 2*width*myInitRow;
        myEnd = 2*width*nextThreadRow;
        int prevrow, nextrow, aux, d;

        for(i=myOff; i < myEnd; i+=1) {
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

        pthread_exit(NULL);
    }

    // Process the whole image in parallel using nthr pthreads
    static void convertYUV420_NV21toConvolution8888Parallel(const unsigned char * data, int * pixels, int width, int height, int nthr, const int * matrix, const int divisor)
    {
        pthread_t th[MAX_NUM_THREADS];
        paramSTConv params[MAX_NUM_THREADS];
        int status;
        int my_nthr = nthr;
        int i;

        if (my_nthr > MAX_NUM_THREADS)
            my_nthr = MAX_NUM_THREADS;
        for (i=0; i < my_nthr; i++) {
            params[i].data = data;
            params[i].pixels = pixels;
            params[i].width = width;
            params[i].height = height;
            params[i].my_id = i;
            params[i].nthr = my_nthr;
            params[i].matrix = matrix;
            params[i].divisor = divisor;
            pthread_create(&(th[i]), NULL, convertYUV420_NV21toConvolution8888Chunk, (void *)&(params[i]));
        }
        for (i=0; i < my_nthr; i++) {
            pthread_join(th[i], (void*)&status);
        }
    }


    // Pthreads function (convertYUV420_NV21toRGB8888Parallel)
    void Java_es_uma_muii_apdm_ImageProcessingNative_MainActivity_YUVtoConvolutionNativeParallel( JNIEnv* env, jobject thiz,
                                                                                           jbyteArray data,
                                                                                           jintArray result,
                                                                                           jint width, jint height, jint nthreads, jintArray matrix, jint divisor)
    {
        unsigned char *cData;
        int *cResult;
        int *cMatrix = (*env)->GetIntArrayElements(env, matrix, NULL);
        cData = (*env)->GetByteArrayElements(env,data,NULL); // While arrays of objects must be accessed one entry at a time, arrays of primitives can be read and written directly as if they were declared in C.   http://developer.android.com/training/articles/perf-jni.html

        if (cData==NULL || cMatrix == NULL) __android_log_print(ANDROID_LOG_INFO, "HOOKnative", "Can't get data array reference");
        else
        {
            cResult= (*env)->GetIntArrayElements(env,result,NULL);
            if (cResult==NULL) __android_log_print(ANDROID_LOG_INFO, "HOOKnative", "Can't get result array reference");
            else
            {
                // operates on data
                convertYUV420_NV21toConvolution8888Parallel(cData,cResult,width,height,nthreads,cMatrix, divisor);
            }
        }
        if (cResult!=NULL) (*env)->ReleaseIntArrayElements(env,result,cResult,0); // care: errors in the type of the pointers are detected at runtime
        if (cData!=NULL) (*env)->ReleaseByteArrayElements(env, data, cData, 0); // release must be done even when the original array is not copied into cDATA
        if (cMatrix!=NULL) (*env)->ReleaseIntArrayElements(env,matrix,cMatrix,0);
    }