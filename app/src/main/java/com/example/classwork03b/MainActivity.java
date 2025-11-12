package com.example.classwork03b;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// this import is needed to pull in properties from local.properties
// reference build.gradle.kts (the defaultConfig object) to see how properties are loaded into Gradle
import com.example.classwork03b.BuildConfig;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.AnnotateImageResponse;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;

public class MainActivity extends AppCompatActivity {

    /**
     * Stores the absolute path to the current photo, if any.
     */
    String currentPhotoPath;

    // Used to uniquely identify the "session of using the camera" to capture an image
    int REQUEST_IMAGE_CAPTURE = 1000;

    File currentFile;

    String apiKey;

    private final ExecutorService pool = Executors.newFixedThreadPool(3);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // get the api key from the BuildConfig class
        apiKey = BuildConfig.API_KEY;
    }


    /**
     * Returns a File object for saving the full-size photo.
     * @return File
     * @throws IOException
     * <a href="https://developer.android.com/media/camera/camera-deprecated/photobasics#TaskPath">...</a>
     */
    private File createImageFile() throws IOException {

        // Create the filename first
        // The Locale.US is optional, sets the timezone for the date
        String timeStamp = new SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
        ).format(new Date());
        String imageFileName = "IMG_" + timeStamp + ".png";

        // Seems like you have to create a File object for the parent directory of the photo
        // that will be returned from the camera
        File imageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                imageDir      /* directory */
        );

        // save the absolute path of the image file (just in case, I'm not sure it's needed)
        currentPhotoPath = image.getAbsolutePath();

        return image;
    }

    public String[] getVisionAPIDescriptions(BatchAnnotateImagesResponse response) {
        List<String> descriptions = new ArrayList<>();

        // response.getResponses() returns a List of AnnotateImageResponse objects
        for (AnnotateImageResponse annotation : response.getResponses()) {

            if (annotation == null || annotation.getLabelAnnotations() == null) {
                continue;
            }

            annotation.getLabelAnnotations().forEach(label -> {

                if (label.getDescription() != null && !label.getDescription().isEmpty() && !descriptions.contains(label.getDescription())) {
                    descriptions.add(label.getDescription());
                }

                if (descriptions.size() == 3) {
                    return;
                }

            });
        }

        return descriptions.toArray(new String[0]);
    }

    void myVisionTester(Bitmap bitmap) throws IOException
    {

        if (bitmap == null) {
            Log.v("takePicture", "The bitmap is null and cannot be used with Google Vision API.");
            return;
        }

        Log.v("takePicture", "made it to myVisionTester function.");

            //1. ENCODE image.
//        Bitmap bitmap = BitmapFactory.decodeFile(path);

        Log.v("takePicture", "decoded the image into a Bitmap object");

        ByteArrayOutputStream bout = new ByteArrayOutputStream();

        try {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bout);
        } catch (Exception e) {
            Log.e("takePicture", "failed to compress the bitmap into a byte array: " + e.getMessage(), e);
            e.printStackTrace();
        }



        Log.v("takePicture", "compressed the bitmap into a byte array (variable name 'bout').");

        Image myimage = new Image();
        myimage.encodeContent(bout.toByteArray());

        Log.v("takePicture", "converted the bitmap into an Image object.");

        Log.v("takePicture", "made it to creating AnnotateImageRequest.");


        //2. PREPARE AnnotateImageRequest
        AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();
        annotateImageRequest.setImage(myimage);
        Feature f = new Feature();
        f.setType("LABEL_DETECTION");
        f.setMaxResults(5);
        List<Feature> lf = new ArrayList<Feature>();
        lf.add(f);
        annotateImageRequest.setFeatures(lf);

        Log.v("takePicture", "made it to creating the Vision.Builder object...");

        //3.BUILD the Vision
        HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
        GsonFactory jsonFactory = GsonFactory.getDefaultInstance();
        Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);

        Log.v("takePicture", "made to create the Vision object with the API key...");

        // This app now reads the API key from Gradle Scripts/local.properties, which prevents
        // committing the key accidentally to version control
        builder.setVisionRequestInitializer(new VisionRequestInitializer(this.apiKey));
        Vision vision = builder.build();

        Log.v("takePicture", "made to creating the BatchAnnotateImagesRequest...");

        //4. CALL Vision.Images.Annotate
        // To understand the JSON that is returned, look at the documentation for the
        // AnnotateImageResponse object here:
        // https://cloud.google.com/vision/docs/reference/rest/v1/AnnotateImageResponse
        // Each object in the "labelAnnotations" array is an EntityAnnotation object:
        // https://cloud.google.com/vision/docs/reference/rest/v1/AnnotateImageResponse#EntityAnnotation
        BatchAnnotateImagesRequest batchAnnotateImagesRequest = new BatchAnnotateImagesRequest();
        List<AnnotateImageRequest> list = new ArrayList<AnnotateImageRequest>();
        list.add(annotateImageRequest);
        batchAnnotateImagesRequest.setRequests(list);
        Vision.Images.Annotate task = vision.images().annotate(batchAnnotateImagesRequest);
        Log.v("takePicture", "About to execute the vision task; please wait up to 60 seconds for a response.");
        BatchAnnotateImagesResponse response = task.execute();
        Log.v("takePicture", response.toPrettyString());

        // get a list of descriptions
        String[] descriptions = getVisionAPIDescriptions(response);

        // Set the text in the TextView
        TextView tv = findViewById(R.id.tv1);
        if (descriptions.length > 0) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tv.setText(String.join(", ", descriptions));
                }
            });
        }

    }

    /**
     * This method waits for the picture to be returned from the camera and then updates
     * the imageview. Without using this, the application will be checking for the photo
     * before it exists yet.
     * @param requestCode The integer request code originally supplied to
     *                    startActivityForResult(), allowing you to identify who this
     *                    result came from.
     * @param resultCode The integer result code returned by the child activity
     *                   through its setResult().
     * @param data An Intent, which can return result data to the caller
     *               (various data can be attached to Intent "extras").
     *
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Log.v("takePicture", "The camera activity has been returned.");

            File recentPhoto = new File(currentPhotoPath);

            if (!recentPhoto.isFile()) {
                Log.v("onActivityResult", "The file for the newest photo does NOT exist.");
                return;
            }

            // classify the image
                ImageView iv = findViewById(R.id.iv1);
                Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath);
                iv.setImageBitmap(bitmap);

                Log.v("takePicture", "Starting vision test...");

                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Log.v("takePicture", "Entering vision test try block...");
                            myVisionTester(bitmap);
                            Log.v("takePicture", "The vision test has completed.");
                        } catch (IOException e) {
                            Log.v("takePicture", "The vision test failed; why?");
                            e.printStackTrace();
                        }
                    }
                });

                thread.start();



        }

        // reset the absolute path for the next photo
        currentPhotoPath = "";

        // increment the REQUEST_IMAGE_CAPTURE by 1
        REQUEST_IMAGE_CAPTURE++;
    }

    /**
     * Handles the "onClick" event for the "Capture Image" button.
     * @param view
     * AndroidManifest.xml has XML added to it to enable saving images, etc. This is required.
     */
    public void onCaptureImage(View view) {

        // There are two types of Intent objects: explicit (when you specify the class),
        // and implicit, when you are asking for whether an app can meet the need without having
        // to know the class
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // Check to see if there is an app that can handle this intent. If not, then return.
        // There is a warning here:
        // Consider adding a <queries> declaration to your manifest when calling this method
        // Why? Never did it and this works.
        ComponentName componentName = takePictureIntent.resolveActivity(getPackageManager());

        // Stop here if componentName is null; this means that no activity from any other app
        // matches our requested Intent type
        if (componentName == null) {
            Log.v("takePicture", "No app found to take the picture.");
            return;
        }

        Log.v("takePicture", "An app was found to take the picture!");

        // Create the File where the photo should go
        File photoFile;

        try {
            // This will always be not null unless an error occurs
            photoFile = createImageFile();

            // the ".fileprovider" used here has to be defined first in AndroidManifest.xml
            Uri photoURI = FileProvider.getUriForFile(this,
                    "com.example.classwork03b.fileprovider", // "com.example.android.fileprovider",
                    photoFile);

            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);

        } catch (IOException ex) {

            Log.v("takePicture", "Error occurred creating the image file.");
        }

    }
}