package com.example.maxim.barcodescanner;


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.support.v4.app.ActivityCompat;
import android.hardware.camera2.CameraManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "BarcodeScanner";
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private CameraDevice cameraDevice;
    private Size imageDimantion;

    private CaptureRequest.Builder captureRequestBuilder;
    private CameraCaptureSession cameraCaptureSessions;

    private FrameLayout mainLayout;
    private static  ListView listView;
    private TextureView textureView;
    private Button button;

    private static Context appContext;

    private BarcodeDetector barcodeDetector;

    private static HashMap<String, Integer> productList = new HashMap<>();
    private static HashMap<String, Product> productInfoList = new HashMap<>();

    public static ProgressBar loading;
    public static TextView productsTitle;
    public static TextView total;

    public static void showProduct(Product product) {
        Product exist = productInfoList.get(product.code);
        if (exist == null && product.name != null) {
            MainActivity.productInfoList.put(product.code, product);
        }

        if (product.name == null) {
            Toast.makeText(appContext, "Product not found in Rozetka", Toast.LENGTH_LONG).show();
        }


        List<HashMap<String, String>> aList = new ArrayList<HashMap<String, String>>();
        for (Map.Entry<String, Integer> entry : productList.entrySet()) {
            HashMap<String, String> hm = new HashMap<String, String>();

            String bar_code = entry.getKey();
            Integer bar_count = entry.getValue();

            Product product2 = productInfoList.get(bar_code);

            try {
                hm.put("product_image", Integer.toString(R.drawable.product_unknown64));
                hm.put("product_title", product2.name);
                hm.put("product_price", String.format("Price %s grn", Float.toString(product2.price)));
                hm.put("product_count", String.format("Count %s", Integer.toString(bar_count)));
                aList.add(hm);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        String[] from = {"product_image", "product_title", "product_price", "product_count"};
        int[] to = {R.id.product_image, R.id.product_title, R.id.product_price, R.id.product_count};

        SimpleAdapter simpleAdapter = new SimpleAdapter(appContext, aList, R.layout.listview_activity, from, to);
        MainActivity.listView.setAdapter(simpleAdapter);

        MainActivity.productsTitle.setText(String.format("Products (%d)", MainActivity.productInfoList.size()));
        MainActivity.total.setText(String.format("Total %.2f grn", MainActivity.calcTotal()));
    }

    public static float calcTotal() {
        float total = 0;

        for (Map.Entry<String, Integer> entry : productList.entrySet()) {
            HashMap<String, String> hm = new HashMap<String, String>();

            String bar_code = entry.getKey();
            Integer bar_count = entry.getValue();

            Product product2 = productInfoList.get(bar_code);
            if (product2 != null)
                total += (float) product2.price * bar_count;
        }

        return total;
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            Log.e(TAG, "texture: size avaliable");
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            Log.e(TAG, "texture: size changed");
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            Log.e(TAG, "texture: destroyed");
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            Log.e(TAG, "texture: updated");
            if (barcodeDetector == null) {
                return;
            }

            Bitmap myBitmap = Bitmap.createBitmap(textureView.getWidth(), textureView.getHeight(), Bitmap.Config.ARGB_8888);
            textureView.getBitmap(myBitmap);

            Frame frame = new Frame.Builder().setBitmap(myBitmap).build();
            SparseArray<Barcode> barcodes = barcodeDetector.detect(frame);

            if (barcodes.size() > 0) {
                Barcode barcode = barcodes.valueAt(0);
                String code = barcode.rawValue;

                Integer count = productList.get(code);
                if (count != null) {
                    productList.put(code, count + 1);
                } else {
                    productList.put(code, 1);
                }

                Log.e(TAG, String.format("code %s count %d", code, count));
                textureView.setVisibility(View.INVISIBLE);
                mainLayout.setVisibility(View.VISIBLE);

//                ArrayList<String> products = new ArrayList<>();
//                for(Map.Entry<String, Integer> entry : productList.entrySet()) {
//                    String bar_code = entry.getKey();
//                    Integer bar_count = entry.getValue();
//
//                    products.add(String.format("Product #%s - count %d", bar_code, bar_count));
//                }

                MainActivity.loading.setVisibility(View.VISIBLE);
                new ProductSearchTask().execute(code);

//                listView.setAdapter(new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_list_item_1, products));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        appContext = getApplicationContext();

        textureView = (TextureView) findViewById(R.id.texture);
        textureView.setSurfaceTextureListener(textureListener);

        button = (Button) findViewById(R.id.scan_btn);
        button.setOnClickListener(this);

        listView = (ListView) findViewById(R.id.product_list);
        mainLayout = (FrameLayout) findViewById(R.id.main_layout);

        loading = (ProgressBar) findViewById(R.id.loading_indicator);
        loading.setVisibility(View.INVISIBLE);

        productsTitle = (TextView) findViewById(R.id.products_title);
        total = (TextView) findViewById(R.id.total);

        barcodeDetector = new BarcodeDetector.Builder(getApplicationContext())
                .setBarcodeFormats(Barcode.UPC_A | Barcode.UPC_E | Barcode.EAN_13 | Barcode.EAN_8 | Barcode.CODE_39 | Barcode.CODE_93 | Barcode.CODE_128)
                .build();

        if (!barcodeDetector.isOperational()) {
            Toast.makeText(getApplicationContext(), "Cant process barcode", Toast.LENGTH_SHORT).show();
        }
    }

     @Override
     public void onClick(View v) {
        openCamera();
     }

     protected void updatePreview() {
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
         try {
             cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, null);
         } catch (CameraAccessException e) {
             e.printStackTrace();
         }
     }

     private void createCameraPreview() {
        Log.e(TAG, "create preview");
        SurfaceTexture texture = textureView.getSurfaceTexture();
        assert texture != null;

        texture.setDefaultBufferSize(imageDimantion.getWidth(), imageDimantion.getHeight());
        Surface surface = new Surface(texture);

        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(cameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    if (cameraDevice == null) {
                        return;
                    }
                    cameraCaptureSessions = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
     }

     private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
         @Override
         public void onOpened(CameraDevice camera) {
            Log.e(TAG, "state: opened");

            mainLayout.setVisibility(View.INVISIBLE);
            textureView.setVisibility(View.VISIBLE);

            cameraDevice = camera;
            createCameraPreview();
         }

         @Override
         public void onDisconnected(CameraDevice camera) {
             Log.e(TAG, "state: disconnected");
             cameraDevice.close();
         }

         @Override
         public void onError(CameraDevice camera, int error) {
             Log.e(TAG, "state: error");
             cameraDevice.close();
         }
     };

     protected void openCamera() {
         Log.e(TAG, "Camera is open");
         CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
         try {
            String cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            imageDimantion = map.getOutputSizes(SurfaceTexture.class)[0];

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        MainActivity.this,
                        new String[]{Manifest.permission.CAMERA},
                        REQUEST_CAMERA_PERMISSION);
                return;
            }

             manager.openCamera(cameraId, stateCallback, null);
         } catch (CameraAccessException e) {
             e.printStackTrace();
         }
     }

     protected void closeCamera() {
         if (cameraDevice == null) {
             return;
         }

         cameraDevice.close();
         cameraDevice = null;
     }
 }
