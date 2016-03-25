package es.uma.muii.apdm.ImageProcessingNative;


import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.TextView;
import java.io.IOException;
import es.uma.muii.apdm.ImageProcessingNative.Support.MySurfaceHolderCallback;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {

    Camera cam;                                 // Android camera object to interact with
    int camid = 0;                              // Android camera identifier to obtain camera object
    int rotation;                               // Rotation angle we have to apply to the camera frames
    boolean preview;                            // True if the preview of the camera frames is running
    MySurfaceHolderCallback mscb;               // Object to know if a SurfaceView is ready
    MyPreviewCallback myPreviewCallback;        // Object to implement/call the processing algorithms for each captured frame
    RadioButton[] actionRB;                     // Array of RadioButtons for the different processing algorithms
    int nActionRB = 6;                          // Number of RadioButtons
    CheckBox[] optionCB;                        // Array of CheckBoxes for different options
    int nOptionCB = 3;                          // Number of Checkboxes
    int[] procImage;                            // Buffer for processed image
    int[] procImage2;                           // Buffer for processed image after scaled and rotated
    Bitmap resultBitmap;                        // Bitmap to show on screen
    long fpsT0, fpsT1;                          // Variables to calculate the real FPS of the sequence of processed images
    public int lastformat;
    public int lastwidth;
    public int lastheight;

    public int[] matrix;                        // Convolution matrix
    public int divisor;                     // Divisor for convolution
    float[] histograma;                         // Store histogram values

    // YUBtoRGB parallel stuff
    YUVtoRGBParallel YUV2RGBpar;                // Object for parallel image processing (it holds the pool of worked threads (executor))
    YUVtoGrayParallel YUV2GRAYpar;
    YUVtoConvolutionParallel YUV2CONVpar;
    int nThreads;                               // number of parallel working threads

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setRadioButtons();                                              // Setup of all RadioButtons
        setCheckBoxes();                                                // Setup of all CheckBoxes

        matrix = new int[]{-1,-1,-1,-1,8,-1,-1,-1,-1};                  // Setup default matrix values
        divisor = 1;                                                    // Setup default divisor value

        mscb = new MySurfaceHolderCallback();
        SurfaceView sv = (SurfaceView) findViewById(R.id.surfaceView);  // Surface that shows the camera preview
        sv.getHolder().addCallback(mscb);                               // Add callback to know if surface holder is ready

        SurfaceHolder surf2 = ((SurfaceView) findViewById(R.id.surfaceView2)).getHolder();  // A second surface that shows processed frames
        MySurfaceHolderCallback myshc2 = new MySurfaceHolderCallback();
        surf2.addCallback(myshc2);                                     // Add callback to know if surface holder is ready
        myPreviewCallback = new MyPreviewCallback(surf2, myshc2);      // Create the object that carries out the frame processing
    }

    @Override
    protected void onResume() {
        super.onResume();
        nThreads = Runtime.getRuntime().availableProcessors();
        YUV2RGBpar = new YUVtoRGBParallel(nThreads);  // Initialize parallel implementation
        YUV2GRAYpar = new YUVtoGrayParallel(nThreads);
        YUV2CONVpar = new YUVtoConvolutionParallel(nThreads);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPreview(null);          // When the activity is paused we stop the camera preview and image processing
        YUV2RGBpar.shutdown();      // Shutdown the pool of worked threads
        YUV2GRAYpar.shutdown();
        YUV2CONVpar.shutdown();
    }

    /* Method called when "STARTPREVIEW" button is pressed */
    public void startPreview(View v) {
        if (!preview && getCheckedActionRB() > 0) {
            preview = true;
            while (!mscb.surfaceready) {}               // wait for the surface to be ready (for camera preview)
            try {
                cam = Camera.open(camid);                   // open the selected camera
            } catch (RuntimeException e) {
                preview = false;
                Log.d("HOOK", "Can't access camera "+camid);
                return;
            }
            SurfaceView sv = (SurfaceView) findViewById(R.id.surfaceView);
            try {
                cam.setPreviewDisplay(sv.getHolder());  // tell the camera where to show the frames (SurfaceView)
            } catch (IOException e) {
                e.printStackTrace();
                preview = false;
                Log.d("HOOK", "Can't access camera " + camid);
                return;
            }
            rotation = Support.setCameraDisplayOrientation(this, camid);    // calculate the rotation that should be applied to the camera frames
            cam.setDisplayOrientation(rotation);                    // apply this rotation to the camera frames
            Camera.Parameters parameters = cam.getParameters();
            lastformat = parameters.getPreviewFormat();
            lastwidth = parameters.getPreviewSize().width;  // the surface where the preview is visible may not coincide with these dimensions; the image is scaled automatically by the preview
            lastheight = parameters.getPreviewSize().height;
            Camera.Size s = parameters.getPreviewSize();
            cam.addCallbackBuffer(new byte[3 * s.width * s.height / 2]);  // create a reusable buffer for the data passed to onPreviewFrame call (in order to avoid GC)
            cam.setPreviewCallbackWithBuffer(myPreviewCallback);    // assign the callback called when a frame is shown by the camera preview (for frame processing)
            myPreviewCallback.reset();                              // reset statistics of the frame processing
            cam.startPreview();                                     // start camera preview
            fpsT0 = System.nanoTime();                              // store start time
        }
    }

    /* Method called when "STOPPREVIEW" button is pressed */
    public void stopPreview(View v) {
        if (preview && cam != null) {
            fpsT1 = System.nanoTime();                              // store stop time and write on log the resulting FPS
            Log.d("HOOK", "FPS: "+(1000000000.0f/(double)(fpsT1-fpsT0))*(double)myPreviewCallback.count);
            cam.stopPreview();                                      // stop camera preview
            cam.setPreviewCallback(null);                           // delete preview callback
            cam.release();                                          // release the camera
            TextView tv = (TextView) findViewById(R.id.textView);   // show frame processing mean execution time
            tv.setText(getResources().getString(R.string.mean) + " " + myPreviewCallback.getMean() + " " + getResources().getString(R.string.timeunit));
            myPreviewCallback.reset();                              // reset statistics of the frame processing
        }
        preview = false;
    }

    /* New Intent to setup new convolution matrix values */
    public void setMatrix(View v)
    {
        Intent intent = new Intent(this, MatrixActivity.class);
        intent.putExtra("matrix", matrix); // Pass actual matrix value
        intent.putExtra("divisor", divisor); // Pass actual divisor value
        startActivityForResult(intent, 1);
    }

    /* This class implements the callback called every time a camera frame is displayed
     * It's duty is to process the frame and display it on the second surfacewie */
    private final class MyPreviewCallback implements android.hardware.Camera.PreviewCallback
    {
        public long count=0;
        public long time=0;
        SurfaceHolder surf2 ;
        MySurfaceHolderCallback myshc2;
        public proccesImageOnBackground myProccesImageOnBackground;

        MyPreviewCallback(SurfaceHolder _surf, MySurfaceHolderCallback _mshcb) {
            surf2 = _surf;
            myshc2 = _mshcb;
        }

        /* reset the stats */
        public void reset() {
            if (myProccesImageOnBackground != null)
                myProccesImageOnBackground.cancel(true);
            count = 0;
            time = 0;
        }

        /* get the image processing mean time */
        public float getMean() {
            if (count == 0)
                return 0;
            return ((float)time/(float)count)/1000000.0f;
        }

        /*Called as preview frames are displayed. This callback is invoked on the thread that opens the camera.
         More info here: http://developer.android.com/reference/android/hardware/Camera.PreviewCallback.html.
         The argument "data" stores the image that is being previewed */
        public void onPreviewFrame (byte[] data, Camera camera) {
            if (lastformat == android.graphics.ImageFormat.NV21) // should be true: YCrCb format;
            {
                if (procImage == null)
                    procImage = new int[lastwidth * lastheight];  // global array to store the processed image
                myProccesImageOnBackground = (proccesImageOnBackground)new proccesImageOnBackground(data, procImage, this).execute(((CheckBox)findViewById(R.id.checkBox7)).isChecked());
            }
        }
    }

    // Class that implements the image processing in a background thread
    // AsyncTask <doInBackground input type, onProgressUpdate input type, doInBackground output and onPostExecute input type>
    private class proccesImageOnBackground extends AsyncTask<Boolean, Void, Void> {
        private byte[] data;
        public int[] procImage;
        MyPreviewCallback cb;
        boolean renderHistogram = false; //flag to render histogram

        proccesImageOnBackground (byte[] _data, int[] _procImage, MyPreviewCallback _cb) {
            data = _data;
            procImage = _procImage;
            cb = _cb;
        }

        @Override
        protected Void doInBackground (Boolean... booleans) {
            //Log.d("HOOK", "doInBackground ...");
            Boolean histogramaBoolean = booleans[0];
            long t0=0,t1=0;
            switch (getCheckedActionRB()) {                 // select the image processing algorithm
                case 1:                                     // Convert to RGB
                    if (!isCheck(0)) {                      // not parallel
                        if (!isCheck(1)) {                  // not native (not parallel)
                            t0 = System.nanoTime();
                            YUVtoRGB.convertYUV420_NV21toRGB8888(data, procImage, lastwidth, lastheight);
                            t1 = System.nanoTime();
                        } else {                             // native (not parallel)
                            t0 = System.nanoTime();
                            YUVtoRGBNative(data, procImage, lastwidth, lastheight);
                            t1 = System.nanoTime();
                        }
                    } else {                                // parallel version
                        if (!isCheck(1)) {                  // not native (parallel)
                            t0 = System.nanoTime();
                            YUV2RGBpar.convertYUV420_NV21toRGB8888_parallel(data, procImage, lastwidth, lastheight);
                            t1 = System.nanoTime();
                        } else {                            // native parallel
                            if (!isCheck(2)) {              // native parallel pthread
                                t0 = System.nanoTime();
                                YUVtoRGBNativeParallel(data, procImage, lastwidth, lastheight, nThreads);
                                t1 = System.nanoTime();
                            } else {                        // native parallel OMP
                                t0 = System.nanoTime();
                                YUVtoRGBNativeParallelOMP(data, procImage, lastwidth, lastheight, nThreads);
                                t1 = System.nanoTime();
                            }
                        }
                    }
                    break;
                case 2:                                     // Convert to RGB
                    if (!isCheck(0)) {                      // not parallel
                        if (!isCheck(1)) {                  // not native (not parallel)
                            t0 = System.nanoTime();
                            YUVtoGray.convertYUV420_NV21toGray8888(data, procImage, lastwidth, lastheight);
                            t1 = System.nanoTime();
                        } else {                             // native (not parallel)
                            t0 = System.nanoTime();
                            YUVtoGrayNative(data, procImage, lastwidth, lastheight);
                            t1 = System.nanoTime();
                        }
                        // if histogram is checked calculate histogram and activate flag
                        if(histogramaBoolean) {
                            histograma = generarHistograma(data, lastwidth, lastheight);
                            renderHistogram = true;
                        }
                    } else {                                // parallel version
                        if (!isCheck(1)) {                  // not native (parallel)
                            t0 = System.nanoTime();
                            YUV2GRAYpar.convertYUV420_NV21toGray8888_parallel(data, procImage, lastwidth, lastheight);
                            t1 = System.nanoTime();
                        } else {                            // native parallel
                            if (!isCheck(2)) {              // native parallel pthread
                                t0 = System.nanoTime();
                                YUVtoGrayNativeParallel(data, procImage, lastwidth, lastheight, nThreads);
                                t1 = System.nanoTime();
                            } else {                        // native parallel OMP
                                t0 = System.nanoTime();
                                YUVtoGrayNativeParallelOMP(data, procImage, lastwidth, lastheight, nThreads);
                                t1 = System.nanoTime();
                            }
                       }
                        // if histogram is checked calculate histogram and activate flag
                        if(histogramaBoolean) {
                            histograma = generarHistograma(data, lastwidth, lastheight);
                            renderHistogram = true;
                        }
                    }
                    break;
                case 3:                                     // Convert to RGB
                    if (!isCheck(0)) {                      // not parallel
                        if (!isCheck(1)) {                  // not native (not parallel)
                            t0 = System.nanoTime();
                            YUVtoConvolution.convertYUV420_NV21toConvolution(data, procImage, lastwidth, lastheight, matrix, divisor);
                            t1 = System.nanoTime();
                        } else {                             // native (not parallel)
                            t0 = System.nanoTime();
                            YUVtoConvolutionNative(data, procImage, lastwidth, lastheight, matrix, divisor);
                            t1 = System.nanoTime();
                        }
                    } else {                                // parallel version
                        if (!isCheck(1)) {                  // not native (parallel)
                            t0 = System.nanoTime();
                            YUV2CONVpar.convertYUV420_NV21toConvolution8888_parallel(data, procImage, lastwidth, lastheight, matrix, divisor);
                            t1 = System.nanoTime();
                        } else {                            // native parallel
                            if (!isCheck(2)) {              // native parallel pthread
                                t0 = System.nanoTime();
                                YUVtoConvolutionNativeParallel(data, procImage, lastwidth, lastheight, nThreads, matrix, divisor);
                                t1 = System.nanoTime();
                            } else {
                                // native parallel OMP
                                t0 = System.nanoTime();
                                YUVtoConvolutionNativeParallelOMP(data, procImage, lastwidth, lastheight, nThreads, matrix, divisor);
                                t1 = System.nanoTime();
                            }
                        }
                    }
                    break;
            }
            cb.count ++;                   // increment number of processed images
            cb.time += t1-t0;              // accumulate elapsed time
            return null;
        }

        @Override
        protected void onPostExecute(Void voids) {
            //Log.d("HOOK", "onPostExecute ...");
            if ((cb.surf2!=null)&&(cb.myshc2.surfaceready))
            {
                Canvas canv = cb.surf2.lockCanvas(); // we have access to surf because we are an inner class of MainActivity, which has a member called surf (pp. 246 of thinking in java 4th)
                if (canv != null) {
                    Paint p = new Paint();
                    canv.drawColor(android.graphics.Color.WHITE);
                    // all coordinates in the canvas are float but have pixel units (the size of the canvas is as specified in the layout of the activity)
                    // Y-axis goes from top to bottom; X-axis goes from left to right.
                    int canvW = canv.getWidth();    // get the size of the canvas for the new RGB image
                    int canvH = canv.getHeight();
                    if (procImage2 == null)
                        procImage2 = new int[canvH*canvW];  // create global array to store transformed RGB image
                    // downscale and rotate RGB image (procImage --> procImage2)
                    Support.downscaleAndRotateImage(procImage, procImage2, lastwidth, lastheight, canvW, canvH, rotation);
                    if (resultBitmap == null)
                        // create global Bitmap (to show on surf2) from procImage2
                        resultBitmap = Bitmap.createBitmap(canvW, canvH, android.graphics.Bitmap.Config.ARGB_8888);
                    // copy transformed RGB image (procImage2) on bitmap
                    resultBitmap.setPixels(procImage2, 0, canvW, 0, 0, canvW, canvH);
                    // draw the bitmap
                    canv.drawBitmap(resultBitmap,0,0,p);
                    p.setColor(android.graphics.Color.YELLOW);
                    p.setTextSize((float) 48.0); // these are points, as in html size in points
                    p.setStyle(android.graphics.Paint.Style.FILL_AND_STROKE);
                    // draw the number of processed images
                    canv.drawText(String.valueOf(cb.count), 10, 50, p);

                    //draw the histogram if necessary
                    if (renderHistogram) {
                        //Fijamos trazo y color para pintar
                        p.setColor(android.graphics.Color.RED);
                        p.setStrokeWidth(1);
                        p.setAlpha(150);
                        //Transformation of coordinate system (arriba izquierda es 0,0), tranformamos el 0.0 a abajo izquierda
                        for(int i = 0; i < 256; i++) {
                            canv.drawLine(i, canv.getHeight(), i, canv.getHeight() - histograma[i], p);
                        }
                    }

                    cb.surf2.unlockCanvasAndPost(canv); // This queue the drawing for the next refresh event of surf2 to take it;
                    // Actually, it allows a thread different from the one that refresh the canvas on screen to draw on surf2
                    // The actual drawing is not done here at this moment; the refreshing is done whenever the activity wants
                }
            }
            cam.addCallbackBuffer(data);    // return the data buffer for then next onPreviewFrame call (no GC)
        }

        @Override
        protected void onCancelled(Void voids) {

        }
    }


    /*** NDK related methods ***/

    // Methods to call native functions
    public native void YUVtoRGBNative(byte[] data, int[] result, int width, int height);
    public native void YUVtoRGBNativeParallel(byte[] data, int[] result, int width, int height, int nthr);
    public native void YUVtoRGBNativeParallelOMP(byte[] data, int[] result, int width, int height, int nthr);

    public native void YUVtoGrayNative(byte[] data, int[] result, int width, int height);
    public native void YUVtoGrayNativeParallel(byte[] data, int[] result, int width, int height, int nthr);
    public native void YUVtoGrayNativeParallelOMP(byte[] data, int[] result, int width, int height, int nthr);

    public native void YUVtoConvolutionNative(byte[] data, int[] result, int width, int height, int[] matrix, int divisor);
    public native void YUVtoConvolutionNativeParallel(byte[] data, int[] result, int width, int height, int nthr, int[] matrix, int divisor);
    public native void YUVtoConvolutionNativeParallelOMP(byte[] data, int[] result, int width, int height, int nthr, int[] matrix, int divisor);

    // Load native library when this class is loaded by the loader class
    static {
        System.loadLibrary("processimg");
    }


    /***  GUI related methods  ***/

    /* Method to setup all RadioButtons. The RadioButtons are used to select one of the available frame processing algorithm */
    private void setRadioButtons() {

        /* Listener for all RadioButtons. Allows only one RadioButton to be checked */
        final View.OnClickListener actionRBlistener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (int i=0; i<nActionRB; i++) {
                    if (actionRB[i].getId() != v.getId()) {
                        actionRB[i].setChecked(false);
                    }
                }
            }
        };

        actionRB = new RadioButton[nActionRB];
        int i = 0;
        RadioButton rb = (RadioButton) findViewById(R.id.radioButton1);
        rb.setText(getResources().getString(R.string.rgb)); // set the text of the RadioButton
        rb.setEnabled(true);                                    // enable or disable the button
        rb.setOnClickListener(actionRBlistener);                // set the listener
        actionRB[i++] = rb;                                     // store reference to the RadioButton
        rb = (RadioButton) findViewById(R.id.radioButton2);
        rb.setText(getResources().getString(R.string.gray));
        rb.setEnabled(true);
        rb.setOnClickListener(actionRBlistener);
        actionRB[i++] = rb;
        rb = (RadioButton) findViewById(R.id.radioButton3);
        rb.setText(getResources().getString(R.string.convolution));
        rb.setEnabled(true);
        rb.setOnClickListener(actionRBlistener);
        actionRB[i++] = rb;
        rb = (RadioButton) findViewById(R.id.radioButton4);
        rb.setText(getResources().getString(R.string.notused));
        rb.setEnabled(false);
        rb.setOnClickListener(actionRBlistener);
        actionRB[i++] = rb;
        rb = (RadioButton) findViewById(R.id.radioButton5);
        rb.setText(getResources().getString(R.string.notused));
        rb.setEnabled(false);
        rb.setOnClickListener(actionRBlistener);
        actionRB[i++] = rb;
        rb = (RadioButton) findViewById(R.id.radioButton6);
        rb.setText(getResources().getString(R.string.notused));
        rb.setEnabled(false);
        rb.setOnClickListener(actionRBlistener);
        actionRB[i++] = rb;
    }

    /* Method to setup all CheckBoxes */
    private void setCheckBoxes() {
        /* Listener for checkboxes 1 and 2. Checkbox 3 can be checked only if both 1 and 2 are checked */
        final View.OnClickListener actionCBlistener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (optionCB[0].isChecked() && optionCB[1].isChecked()) {
                    optionCB[2].setEnabled(true);
                } else {
                    optionCB[2].setEnabled(false);
                    optionCB[2].setChecked(false);
                }
            }
        };

        optionCB = new CheckBox[nOptionCB];
        int i = 0;
        CheckBox cb = (CheckBox) findViewById(R.id.checkBox1);
        cb.setText(getResources().getString(R.string.parallel));    // set the text of the CheckBox
        cb.setEnabled(true);                                        // enable or disable the check box
        cb.setOnClickListener(actionCBlistener);                    // set the listener
        optionCB[i++] = cb;                                         // store reference to the CheckBox
        cb = (CheckBox) findViewById(R.id.checkBox2);
        cb.setText(getResources().getString(R.string.natv));
        cb.setEnabled(true);
        cb.setOnClickListener(actionCBlistener);
        optionCB[i++] = cb;
        cb = (CheckBox) findViewById(R.id.checkBox3);
        cb.setText(getResources().getString(R.string.omp));
        cb.setEnabled(false);
        optionCB[i++] = cb;
    }

    /* Method to get the index of the RadioButton checked. 0 if none is checked */
    private int getCheckedActionRB() {
        for (int i=0; i<nActionRB; i++) {
            if (actionRB[i].isChecked())
                return i+1;
        }
        return 0;
    }

    private boolean isCheck(int nCheckBox) {
        if (nCheckBox >= 0 && nCheckBox < nOptionCB)
            return optionCB[nCheckBox].isChecked();
        return false;
    }

    /* Get values from MatrixActivity and update */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //Esto es el callback para procesar el resultado devuelto por el intent de la matriz
        if (requestCode == 1) {
            if(resultCode == Activity.RESULT_OK){
                //Guardamos la nueva matriz y el nuevo divisor
                divisor = (int) data.getExtras().get("divisor");
                matrix = (int[]) data.getExtras().get("matrix");
            }
            if (resultCode == Activity.RESULT_CANCELED) {
                //Write your code if there's no result
            }
        }
    }

    public static float[] generarHistograma(byte[] data, int width, int height) {
        float[] salida = new float[256];
        float max = 0, min = 0;
        int luminancia;
        for(int i = 0; i < width*height; i++) {
            luminancia = data[i]&0xff;
            salida[luminancia] = salida[luminancia] + 1;
        }

        min = max = salida[0];
        for(int i = 1; i < 256; i++) {
            if (salida[i] > max) {
                max = salida[i];
            } else if (salida[i] < min) {
                min = salida[i];
            }
        }

        //Normalizamos a 0-100
        for(int i = 0; i < 256; i++) {
            salida[i] = ((salida[i]-min) / (max-min)) * 100;
        }
        return salida;
    }
}