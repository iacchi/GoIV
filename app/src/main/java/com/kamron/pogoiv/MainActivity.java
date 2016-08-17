package com.kamron.pogoiv;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity {
    private static final int OVERLAY_PERMISSION_REQ_CODE = 1234;
    private static final int WRITE_STORAGE_REQ_CODE = 1236;
    private static final int SCREEN_CAPTURE_REQ_CODE = 1235;

    private MediaProjection mProjection;
    private ImageReader mImageReader;
    private FileObserver screenShotObserver;

    private DisplayMetrics displayMetrics;
    private DisplayMetrics rawDisplayMetrics;
    private TessBaseAPI tesseract;
    private boolean tessInitiated = false;
    private boolean batterySaver = false;

    private boolean readyForNewScreenshot = true;

    private String pokemonName;
    private double estimatedPokemonLevel;
    private int pokemonCP;
    private int pokemonHP;
    private boolean pokeFlyRunning = false;
    private int trainerLevel;
    private final double[] CpM = {0.0939999967813492, 0.135137432089339, 0.166397869586945, 0.192650913155325, 0.215732470154762, 0.236572651424822, 0.255720049142838, 0.273530372106572, 0.290249884128571, 0.306057381389863
            , 0.321087598800659, 0.335445031996451, 0.349212676286697, 0.362457736609939, 0.375235587358475, 0.387592407713878, 0.399567276239395, 0.4111935532161, 0.422500014305115, 0.432926420512509, 0.443107545375824
            , 0.453059948165049, 0.46279838681221, 0.472336085311278, 0.481684952974319, 0.490855807179549, 0.499858438968658, 0.5087017489616, 0.517393946647644, 0.525942516110322, 0.534354329109192, 0.542635753803599
            , 0.550792694091797, 0.558830584490385, 0.566754519939423, 0.57456912814537, 0.582278907299042, 0.589887907888945, 0.597400009632111, 0.604823648665171, 0.61215728521347, 0.619404107958234, 0.626567125320435
            , 0.633649178748576, 0.6406529545784, 0.647580971386554, 0.654435634613037, 0.661219265805859, 0.667934000492096, 0.674581885647492, 0.681164920330048, 0.687684901255373, 0.694143652915955, 0.700542901033063
            , 0.706884205341339, 0.713169074873823, 0.719399094581604, 0.725575586915154, 0.731700003147125, 0.734741038550429, 0.737769484519958, 0.740785579737136, 0.743789434432983, 0.746781197247765, 0.749761044979095
            , 0.752729099732281, 0.75568550825119, 0.758630370209851, 0.761563837528229, 0.76448604959218, 0.767397165298462, 0.770297293677362, 0.773186504840851, 0.776064947064992, 0.778932750225067, 0.781790050767666
            , 0.784636974334717, 0.787473608513275, 0.790300011634827};

    private int areaX1;
    private int areaY1;
    private int areaX2;
    private int areaY2;
    private int statusBarHeight;
    private int arcCenter;
    private int arcInitialY;
    private int radius;

    @TargetApi(23)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        TextView tvVersionNumber = (TextView) findViewById(R.id.version_number);
        try {
            tvVersionNumber.setText(getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        TextView goIvInfo = (TextView) findViewById(R.id.goiv_info);
        goIvInfo.setMovementMethod(LinkMovementMethod.getInstance());

        final SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        trainerLevel = sharedPref.getInt("level", 1);
        batterySaver = sharedPref.getBoolean("batterySaver",false);
        ((EditText) findViewById(R.id.trainerLevel)).setText(String.valueOf(trainerLevel));

        final CheckBox CheckBox_BatterySaver = (CheckBox) findViewById(R.id.checkbox_batterySaver);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            CheckBox_BatterySaver.setChecked(true);
            CheckBox_BatterySaver.setEnabled(false);
            batterySaver = true;
        }
        else{
            CheckBox_BatterySaver.setChecked(batterySaver);
        }

        File newFile = new File(getExternalFilesDir(null) + "/tessdata/eng.traineddata");
        if (!newFile.exists()) {
            copyAssetFolder(getAssets(), "tessdata", getExternalFilesDir(null) + "/tessdata");
            tesseract = new TessBaseAPI();
            tesseract.init(getExternalFilesDir(null) + "", "eng");
            tesseract.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789/♀♂");
            tessInitiated = true;
        } else {
            tesseract = new TessBaseAPI();
            tesseract.init(getExternalFilesDir(null) + "", "eng");
            tesseract.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789/♀♂");
            tessInitiated = true;
        }

        Button launch = (Button) findViewById(R.id.start);
        launch.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (((Button) v).getText().toString().equals("Grant Permissions")) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(MainActivity.this)) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
                        }
                        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_STORAGE_REQ_CODE);
                        }
                } else if (((Button) v).getText().toString().equals("Start")) {
                    batterySaver = CheckBox_BatterySaver.isChecked();
                    Rect rectangle = new Rect();
                    Window window = getWindow();
                    window.getDecorView().getWindowVisibleDisplayFrame(rectangle);
                    statusBarHeight = rectangle.top;

                    arcCenter = (int) ((displayMetrics.widthPixels * 0.5));
                    arcInitialY = (int) Math.floor(displayMetrics.heightPixels / 2.803943); // - pointerHeight - statusBarHeight; // 913 - pointerHeight - statusBarHeight; //(int)Math.round(displayMetrics.heightPixels / 6.0952381) * -1; //dpToPx(113) * -1; //(int)Math.round(displayMetrics.heightPixels / 6.0952381) * -1; //-420;
                    if (displayMetrics.heightPixels == 2392) {
                        arcInitialY--;
                    } else if (displayMetrics.heightPixels == 1920) {
                        arcInitialY++;
                    }

                    radius = (int) Math.round(displayMetrics.heightPixels / 4.3760683); //dpToPx(157); //(int)Math.round(displayMetrics.heightPixels / 4.37606838); //(int)Math.round(displayMetrics.widthPixels / 2.46153846); //585;
                    if (displayMetrics.heightPixels == 1776 || displayMetrics.heightPixels == 960) {
                        radius++;
                    }

                    trainerLevel = Integer.parseInt(((EditText) findViewById(R.id.trainerLevel)).getText().toString());
                    if (trainerLevel > 0 && trainerLevel <= 40) {
                        sharedPref.edit().putInt("level", trainerLevel).apply();
                        sharedPref.edit().putBoolean("batterySaver",batterySaver).apply();
                        File newFile = new File(getExternalFilesDir(null) + "/tessdata/eng.traineddata");

                        if (!tessInitiated) {
                            if (!newFile.exists()) {
                                copyAssetFolder(getAssets(), "tessdata", getExternalFilesDir(null) + "/tessdata");
                                tesseract = new TessBaseAPI();
                                tesseract.init(getExternalFilesDir(null) + "", "eng");
                                tesseract.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789/♀♂");
                                tessInitiated = true;
                            } else {
                                tesseract = new TessBaseAPI();
                                tesseract.init(getExternalFilesDir(null) + "", "eng");
                                tesseract.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789/♀♂");
                                tessInitiated = true;
                            }
                        }
                        if(batterySaver){
                            startScreenshotService();
                        }
                        else {
                            startScreenService();
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Invalid Trainer Level!", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    stopService(new Intent(MainActivity.this, pokefly.class));
                    if (mProjection != null) {
                        mProjection.stop();
                        mProjection = null;
                        mImageReader = null;
                    }
                    else if(screenShotObserver != null){
                        screenShotObserver.stopWatching();
                        screenShotObserver = null;
                    }
                    pokeFlyRunning = false;
                    ((Button) v).setText("Start");
                }
            }
        });

        checkPermissions(launch);


        displayMetrics = this.getResources().getDisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        rawDisplayMetrics = new DisplayMetrics();
        Display disp = windowManager.getDefaultDisplay();
        disp.getRealMetrics(rawDisplayMetrics);


        areaX1 = Math.round(displayMetrics.widthPixels / 24);  // these values used to get "white" left of "power up"
        areaY1 = (int) Math.round(displayMetrics.heightPixels / 1.24271845);
        areaX2 = (int) Math.round(displayMetrics.widthPixels / 1.15942029);  // these values used to get greenish color in transfer button
        areaY2 = (int) Math.round(displayMetrics.heightPixels / 1.11062907);

        LocalBroadcastManager.getInstance(this).registerReceiver(resetScreenshot, new IntentFilter("reset-screenshot"));
        LocalBroadcastManager.getInstance(this).registerReceiver(takeScreenshot, new IntentFilter("screenshot"));
    }

    /**
     * startPokeFly
     * Starts the PokeFly background service which contains overlay logic
     */
    private void startPokeyFly(){
        ((Button) findViewById(R.id.start)).setText("Stop");
        Intent PokeFly = new Intent(MainActivity.this, pokefly.class);
        PokeFly.putExtra("trainerLevel", trainerLevel);
        PokeFly.putExtra("statusBarHeight", statusBarHeight);
        PokeFly.putExtra("batterySaver",batterySaver);
        startService(PokeFly);

        pokeFlyRunning = true;

        openPokemonGoApp();
    }

    /**
     * checkPermissions
     * Checks to see if all runtime permissions are granted,
     * if not change button text to Grant Permissions.
     * @param launch The start button to change the text of
     */
    private void checkPermissions(Button launch) {
        //Check Permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            //Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            //       Uri.parse("package:" + getPackageName()));
            //startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
            launch.setText("Grant Permissions");
            //startScreenService();
        } else if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            launch.setText("Grant Permissions");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (pokeFlyRunning) {
            stopService(new Intent(MainActivity.this, pokefly.class));
            pokeFlyRunning = false;
        }
        if (mProjection != null) {
            mProjection.stop();
        }
        tesseract.stop();
        tesseract.end();
        mProjection = null;
        mImageReader = null;

        LocalBroadcastManager.getInstance(this).unregisterReceiver(resetScreenshot);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(takeScreenshot);
    }


    @TargetApi(23)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (!Settings.canDrawOverlays(this)) {
                // SYSTEM_ALERT_WINDOW permission not granted...
                ((Button) findViewById(R.id.start)).setText("Grant Permissions");
            } else if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                ((Button) findViewById(R.id.start)).setText("Start");
            }
        } else if (requestCode == SCREEN_CAPTURE_REQ_CODE) {
            if (resultCode == RESULT_OK) {
                MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                mProjection = projectionManager.getMediaProjection(resultCode, data);
                mImageReader = ImageReader.newInstance(rawDisplayMetrics.widthPixels, rawDisplayMetrics.heightPixels, PixelFormat.RGBA_8888, 2);
                mProjection.createVirtualDisplay("screen-mirror", rawDisplayMetrics.widthPixels, rawDisplayMetrics.heightPixels, rawDisplayMetrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, mImageReader.getSurface(), null, null);

                startPokeyFly();
                //showNotification();
                final Handler handler = new Handler();
                final Timer timer = new Timer();
                TimerTask doAsynchronousTask = new TimerTask() {
                    @Override
                    public void run() {
                        handler.post(new Runnable() {
                            @SuppressWarnings("unchecked")
                            public void run() {
                                try {
                                    if (pokeFlyRunning) {
                                        scanPokemonScreen();
                                    } else {
                                        timer.cancel();
                                    }
                                } catch (Exception e) {
                                    // TODO Auto-generated catch block
                                }
                            }
                        });
                    }
                };
                timer.schedule(doAsynchronousTask, 0, 750);
            } else {
                ((Button) findViewById(R.id.start)).setText("Start");
            }
        }
    }

    /**
     * openPokemonGoApp
     * Runs a launch intent for Pokemon GO
     */
    private void openPokemonGoApp() {
        Intent i = getPackageManager().getLaunchIntentForPackage("com.nianticlabs.pokemongo");
        if (i != null)
            startActivity(i);
    }

    @TargetApi(23)
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == WRITE_STORAGE_REQ_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (Settings.canDrawOverlays(this) && ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    // SYSTEM_ALERT_WINDOW permission not granted...
                    ((Button) findViewById(R.id.start)).setText("Start");
                }
            }
        }
    }

    /**
     * takeScreenshot
     * Called by intent from pokefly, captures the screen and runs it through scanPokemon
     */
    private void takeScreenshot() {
        Image image = null;
        try {
            image = mImageReader.acquireLatestImage();
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, "Error Scanning! Please try again later!", Toast.LENGTH_SHORT).show();
        }

        if (image != null) {
            final Image.Plane[] planes = image.getPlanes();
            final ByteBuffer buffer = planes[0].getBuffer();
            int offset = 0;
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * displayMetrics.widthPixels;
            // create bitmap
            try {
                image.close();
                Bitmap bmp = Bitmap.createBitmap(displayMetrics.widthPixels + rowPadding / pixelStride, displayMetrics.heightPixels, Bitmap.Config.ARGB_8888);
                bmp.copyPixelsFromBuffer(buffer);
                scanPokemon(bmp);
                //SaveImage(bmp,"Search");
            } catch (Exception e) {
                image.close();
            }


        }
    }

    /**
     * scanPokemon
     * Performs OCR on an image of a pokemon and sends the pulled info to PokeFly to display.
     * @param pokemonImage The image of the pokemon
     */
    private void scanPokemon(Bitmap pokemonImage) {
        estimatedPokemonLevel = trainerLevel + 1.5;

        for (double estPokemonLevel = estimatedPokemonLevel; estPokemonLevel >= 1.0; estPokemonLevel -= 0.5) {
            double angleInDegrees = (CpM[(int) (estPokemonLevel * 2 - 2)] - 0.094) * 202.037116 / CpM[trainerLevel * 2 - 2];
            if (angleInDegrees > 1.0 && trainerLevel < 30) {
                angleInDegrees -= 0.5;
            }
            else if(trainerLevel >= 30){
                angleInDegrees += 0.5;
            }

            double angleInRadians = (angleInDegrees + 180) * Math.PI / 180.0;
            int x = (int) (arcCenter + (radius * Math.cos(angleInRadians)));
            int y = (int) (arcInitialY + (radius * Math.sin(angleInRadians)));
            //System.out.println("X: " + x + ", Y: " + y);
            if (pokemonImage.getPixel(x, y) == Color.rgb(255, 255, 255)) {
                estimatedPokemonLevel = estPokemonLevel;
                break;
            }
        }

        Bitmap name = Bitmap.createBitmap(pokemonImage, displayMetrics.widthPixels / 4, (int) Math.round(displayMetrics.heightPixels / 2.22608696), (int) Math.round(displayMetrics.widthPixels / 2.057), (int) Math.round(displayMetrics.heightPixels / 18.2857143));
        name = replaceColors(name, 68, 105, 108, Color.WHITE, 200);
        tesseract.setImage(name);
        //System.out.println(tesseract.getUTF8Text());
        pokemonName = tesseract.getUTF8Text().replace(" ", "").replace("1", "l").replace("0", "o").replace("Sparky", "Jolteon").replace("Rainer", "Vaporeon").replace("Pyro", "Flareon");
        //SaveImage(name, "name");
        Bitmap hp = Bitmap.createBitmap(pokemonImage, (int) Math.round(displayMetrics.widthPixels / 2.8), (int) Math.round(displayMetrics.heightPixels / 1.8962963), (int) Math.round(displayMetrics.widthPixels / 3.5), (int) Math.round(displayMetrics.heightPixels / 34.13333333));
        hp = replaceColors(hp, 55, 66, 61, Color.WHITE, 200);
        tesseract.setImage(hp);
        //System.out.println(tesseract.getUTF8Text());
        pokemonHP = Integer.parseInt(tesseract.getUTF8Text().split("/")[1].replace("Z", "2").replace("O", "0").replace("l", "1").replaceAll("[^0-9]", ""));
        //SaveImage(hp, "hp");
        Bitmap cp = Bitmap.createBitmap(pokemonImage, (int) Math.round(displayMetrics.widthPixels / 3.0), (int) Math.round(displayMetrics.heightPixels / 15.5151515), (int) Math.round(displayMetrics.widthPixels / 3.84), (int) Math.round(displayMetrics.heightPixels / 21.333333333));
        cp = replaceColors(cp, 255, 255, 255, Color.BLACK, 1);
        tesseract.setImage(cp);
        String cpText = tesseract.getUTF8Text().replace("O", "0").replace("l", "1").replace("S", "3").replaceAll("[^0-9]", "");
        if (cpText.length() > 4) {
            cpText = cpText.substring(cpText.length() - 4, cpText.length() - 1);
        }
        //System.out.println(cpText);
        pokemonCP = Integer.parseInt(cpText);
        if (pokemonCP > 4500) {
            cpText = cpText.substring(1);
            pokemonCP = Integer.parseInt(cpText);
        }
        //SaveImage(cp, "cp");
        //System.out.println("Name: " + pokemonName);
        //System.out.println("HP: " + pokemonHP);
        //System.out.println("CP: " + pokemonCP);
        name.recycle();
        cp.recycle();
        hp.recycle();

        Intent info = new Intent("pokemon-info");
        info.putExtra("name", pokemonName);
        info.putExtra("hp", pokemonHP);
        info.putExtra("cp", pokemonCP);
        info.putExtra("level", estimatedPokemonLevel);
        LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(info);
    }

    /**
     * scanPokemonScreen
     * Scans the device screen to check area1 for the white and area2 for the transfer button.
     * If both exist then the user is on the pokemon screen.
     */
    private void scanPokemonScreen() {
        //System.out.println("Checking...");
        Image image = mImageReader.acquireLatestImage();
        if (image != null) {
            final Image.Plane[] planes = image.getPlanes();
            final ByteBuffer buffer = planes[0].getBuffer();
            int offset = 0;
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * rawDisplayMetrics.widthPixels;
            // create bitmap
            image.close();
            Bitmap bmp = Bitmap.createBitmap(rawDisplayMetrics.widthPixels + rowPadding / pixelStride, displayMetrics.heightPixels, Bitmap.Config.ARGB_8888); //+ rowPadding / pixelStride
            bmp.copyPixelsFromBuffer(buffer);
            Intent showIVButton = new Intent("display-ivButton");
            if (bmp.getPixel(areaX1, areaY1) == Color.rgb(250, 250, 250) && bmp.getPixel(areaX2, areaY2) == Color.rgb(28, 135, 150)) {
                showIVButton.putExtra("show", true);
            } else {
                showIVButton.putExtra("show", false);
            }
            bmp.recycle();
            LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(showIVButton);
            //SaveImage(bmp,"everything");
        }
    }

    /**
     * SaveImage
     * Used to save the image the screen capture is captuing, used for debugging.
     *
     * @param finalBitmap The bitmap to save
     * @param name        The name of the file to save it as
     */
    private void SaveImage(Bitmap finalBitmap, String name) {

        String root = Environment.getExternalStorageDirectory().toString();
        File myDir = new File(root + "/saved_images");
        myDir.mkdirs();
        String fname = "Image-" + name + ".jpg";
        File file = new File(myDir, fname);
        if (file.exists()) file.delete();
        try {
            FileOutputStream out = new FileOutputStream(file);
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * startScreenService
     * Starts the screen capture.
     */
    private void startScreenService() {
        ((Button)findViewById(R.id.start)).setText("Accept Screen Capture");
        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQ_CODE);
    }

    /**
     * startScreenshotService
     * Starts the screenshot service, which checks for a new screenshot to scan
     */
    private void startScreenshotService(){
        final String screenshotPath = Environment.getExternalStorageDirectory() + File.separator + Environment.DIRECTORY_PICTURES + File.separator + "Screenshots";
        final Uri uri = MediaStore.Files.getContentUri("external");
        screenShotObserver = new FileObserver(screenshotPath) {
            @Override
            public void onEvent(int event, String file) {
                if (event == FileObserver.CLOSE_NOWRITE || event == FileObserver.CLOSE_WRITE) {
                    if (readyForNewScreenshot && file != null) {
                        readyForNewScreenshot = false;
                        scanPokemon(BitmapFactory.decodeFile(screenshotPath + File.separator + file));
                        getContentResolver().delete(uri, MediaStore.Files.FileColumns.DATA + "=?", new String[]{screenshotPath + File.separator + file});
                    }
                }
            }
        };
        screenShotObserver.startWatching();
        startPokeyFly();
    }

    /**
     * takeScreenshot
     * IV Button was pressed, take screenshot and send back pokemon info.
     */
    private final BroadcastReceiver takeScreenshot = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (readyForNewScreenshot) {
                takeScreenshot();
                readyForNewScreenshot = false;
            }
        }
    };

    /**
     * resetScreenshot
     * Used to notify a new request for screenshot can be made. Needed to prevent multiple
     * intents for some devices.
     */
    private final BroadcastReceiver resetScreenshot = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            readyForNewScreenshot = true;
        }
    };

    /**
     * replaceColors
     * Replaces colors in a bitmap that are not the same as a specific color.
     *
     * @param myBitmap     The bitmap to check the colors for.
     * @param keepCr       The red color to keep
     * @param keepCg       The green color to keep
     * @param keepCb       The blue color to keep
     * @param replaceColor The color to replace mismatched colors with
     * @param similarity   The similarity buffer
     * @return Bitmap with replaced colors
     */
    private Bitmap replaceColors(Bitmap myBitmap, int keepCr, int keepCg, int keepCb, int replaceColor, int similarity) {
        int[] allpixels = new int[myBitmap.getHeight() * myBitmap.getWidth()];
        myBitmap.getPixels(allpixels, 0, myBitmap.getWidth(), 0, 0, myBitmap.getWidth(), myBitmap.getHeight());

        for (int i = 0; i < allpixels.length; i++) {
            int r = Color.red(allpixels[i]);
            int g = Color.green(allpixels[i]);
            int b = Color.blue(allpixels[i]);
            double d = Math.sqrt(Math.pow(keepCr - r, 2) + Math.pow(keepCg - g, 2) + Math.pow(keepCb - b, 2));
            if (d > similarity) {
                allpixels[i] = replaceColor;
            }
        }

        myBitmap.setPixels(allpixels, 0, myBitmap.getWidth(), 0, 0, myBitmap.getWidth(), myBitmap.getHeight());
        return myBitmap;
    }

    private static boolean copyAssetFolder(AssetManager assetManager,
                                           String fromAssetPath, String toPath) {
        try {
            String[] files = assetManager.list(fromAssetPath);
            new File(toPath).mkdirs();
            boolean res = true;
            for (String file : files)
                if (file.contains("."))
                    res &= copyAsset(assetManager,
                            fromAssetPath + "/" + file,
                            toPath + "/" + file);
                else
                    res &= copyAssetFolder(assetManager,
                            fromAssetPath + "/" + file,
                            toPath + "/" + file);
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean copyAsset(AssetManager assetManager,
                                     String fromAssetPath, String toPath) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open(fromAssetPath);
            new File(toPath).createNewFile();
            out = new FileOutputStream(toPath);
            copyFile(in, out);
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

}
