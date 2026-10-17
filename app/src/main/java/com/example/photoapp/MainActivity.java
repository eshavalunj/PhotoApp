package com.example.photoapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    ImageView[] slots;
    VideoView backgroundVideo;
    Button btnCamera, btnGallery, btnShare;

    ArrayList<Bitmap> images = new ArrayList<>();
    int currentIndex = 0;

    ActivityResultLauncher<Void> cameraLauncher;
    ActivityResultLauncher<String> galleryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1️⃣ Find the VideoView
        backgroundVideo = findViewById(R.id.backgroundVideo);

// 2️⃣ Create the Uri for the video
        Uri uri = Uri.parse(
                "android.resource://" + getPackageName() + "/" + R.raw.splash_video
        );

// 3️⃣ Set video
        backgroundVideo.setVideoURI(uri);

// 4️⃣ Prepare video
        backgroundVideo.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            mp.setVolume(0f, 0f);

            mp.setVideoScalingMode(
                    MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            );

            mp.setOnVideoSizeChangedListener((mediaPlayer, width, height) -> {
                backgroundVideo.post(() -> {
                    FrameLayout.LayoutParams params =
                            (FrameLayout.LayoutParams) backgroundVideo.getLayoutParams();
                    params.width = FrameLayout.LayoutParams.MATCH_PARENT;
                    params.height = FrameLayout.LayoutParams.MATCH_PARENT;
                    backgroundVideo.setLayoutParams(params);
                });
            });
        });

// 5️⃣ Start video
        backgroundVideo.start();



        slots = new ImageView[]{
                findViewById(R.id.img1),
                findViewById(R.id.img2),
                findViewById(R.id.img3),
                findViewById(R.id.img4)
        };

        btnCamera = findViewById(R.id.btnCamera);
        btnGallery = findViewById(R.id.btnGallery);
        btnShare = findViewById(R.id.btnShare);

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                bitmap -> {
                    if (bitmap != null) handleImage(bitmap);
                });

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri1 -> {
                    try {
                        Bitmap bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), uri1);
                        handleImage(bmp);
                    } catch (Exception ignored) {}
                });

        btnCamera.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA}, 1);
            } else {
                reset();
                shootNext();
            }
        });

        btnGallery.setOnClickListener(v -> galleryLauncher.launch("image/*"));
        btnShare.setOnClickListener(v -> shareStrip());
    }

    private void shootNext() {
        if (currentIndex < 4) {
            new Handler().postDelayed(() -> cameraLauncher.launch(null), 1200);
        }
    }

    private void handleImage(Bitmap bmp) {
        if (currentIndex >= 4) return;

        Bitmap vintage = applyVintage(bmp);
        slots[currentIndex].setBackgroundResource(R.drawable.film_border);
        slots[currentIndex].setImageBitmap(vintage);
        images.add(vintage);
        currentIndex++;

        if (currentIndex < 4) shootNext();
        else btnShare.setEnabled(true);
    }

    private void reset() {
        currentIndex = 0;
        images.clear();
        for (ImageView iv : slots) iv.setImageDrawable(null);
        btnShare.setEnabled(false);
    }

    private Bitmap applyVintage(Bitmap src) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint();

        ColorMatrix m = new ColorMatrix();
        m.setSaturation(0);
        m.postConcat(new ColorMatrix(new float[]{
                1.15f,0,0,0,-18,
                0,1.15f,0,0,-18,
                0,0,1.15f,0,-18,
                0,0,0,1,0
        }));

        p.setColorFilter(new ColorMatrixColorFilter(m));
        c.drawBitmap(src, 0, 0, p);
        return out;
    }

    private void shareStrip() {
        try {
            // 1️⃣ Define FINAL output size (high quality)
            int targetWidth = 1080;
            int singleHeight = 1350; // portrait look
            int totalHeight = singleHeight * images.size();

            Bitmap finalStrip = Bitmap.createBitmap(
                    targetWidth,
                    totalHeight,
                    Bitmap.Config.ARGB_8888
            );

            Canvas canvas = new Canvas(finalStrip);
            canvas.drawColor(Color.WHITE); // clean background

            int top = 0;

            for (Bitmap src : images) {
                // 2️⃣ Scale each image properly
                Bitmap scaled = Bitmap.createScaledBitmap(
                        src,
                        targetWidth,
                        singleHeight,
                        true
                );

                canvas.drawBitmap(scaled, 0, top, null);
                top += singleHeight;
            }

            // 3️⃣ Save clean PNG
            File dir = new File(getCacheDir(), "share");
            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, "vintage_strip.png");

            FileOutputStream fos = new FileOutputStream(file);
            finalStrip.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();

            // 4️⃣ Share securely
            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    file
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share your strip"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
