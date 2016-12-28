package com.junyenhuang.btcontroller;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.RelativeLayout;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class BarcodeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        FloatingActionButton fab = (FloatingActionButton)findViewById(R.id.fab1);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(BarcodeActivity.this,
                        Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(BarcodeActivity.this,
                            new String[]{Manifest.permission.CAMERA}, 1);
                } else {
                    IntentIntegrator integrator = new IntentIntegrator(BarcodeActivity.this);
                    integrator.initiateScan();
                }
            }
        });

        Animation anim = new AlphaAnimation(0.0f, 1.0f);
        anim.setDuration(500); //blinking time
        anim.setStartOffset(20);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        RelativeLayout hintText = (RelativeLayout) findViewById(R.id.hint_layout);
        hintText.startAnimation(anim);

        String mac = getSharedPreferences("DEVICES", MODE_PRIVATE).getString("MAC", "");
        if(!mac.isEmpty()) {
            Intent intent = new Intent(BarcodeActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    IntentIntegrator integrator = new IntentIntegrator(BarcodeActivity.this);
                    integrator.initiateScan();
                } else {
                    //finish();
                }
                return;
            }
        }
    }

    @Override
    public void onActivityResult(int req, int result, Intent intent) {
        try {
            IntentResult scanResult = IntentIntegrator.parseActivityResult(req, result, intent);
            if (scanResult != null) {
                String re = scanResult.getContents();
                if (!re.isEmpty()) {
                    getSharedPreferences("DEVICES", MODE_PRIVATE).edit()
                            .putString("MAC", re).apply();
                    Intent intent1 = new Intent(BarcodeActivity.this, MainActivity.class);
                    startActivity(intent1);
                    finish();
                }
            }
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        } finally {
            finish();
        }
    }
}
