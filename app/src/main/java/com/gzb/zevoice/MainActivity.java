package com.gzb.zevoice;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import android.content.SharedPreferences;
import android.media.PlaybackParams;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.media.AudioRecord;
import android.media.AudioFormat;
import android.media.AudioAttributes;
//import android.media.AudioManager;
import android.media.MediaRecorder.AudioSource;
import android.media.AudioTrack;
//import android.media.AudioTrack.Builder;
import android.graphics.Color;


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
//import android.support.v7.app.*;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.View;

import android.content.Intent;


import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.ActivityResult;
import android.app.Activity;
import androidx.documentfile.provider.DocumentFile;

import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private int bufferSize;
    private boolean isRecording = false;
    private static final int RECORDINGDURATION=5000;
    private static final int RECORDINGON = 1;
    private static final int RECORDINGOFF = 2;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION = 201;
    private static final int PCMBUFFER = 1;

    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private Button pminus;
    private Button pplus;
    private Button sminus;
    private Button splus;
    private TextView tpitch;
    private TextView tspeed;
    private TextView mRecording;

    private Button startButton;
    private Button stopButton;
    private Button playButton;
    private static final int BUFFER_SIZE_FACTOR = 6;
    private Handler mHandler;

    private final AtomicBoolean recordingInProgress = new AtomicBoolean(false);
    private final AtomicBoolean letAppRun = new AtomicBoolean(false);
    private AudioRecord recorder = null;
    private Thread recordingThread = null;
    private Uri baseDocumentTreeUri;
    private Context context;
    private ActivityResultLauncher<Intent> launcher;
    String fName;
    private volatile float pitch;
    private volatile float speed;


    // Requesting permission to RECORD_AUDIO
    private boolean permissionToRecordAccepted;
    private boolean permissionToWriteAccepted;
    private String[] permissions;

    private SoundPool soundPool;

    public AudioFeeder audioFeeder ;

    //-------------------------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pminus = (Button) findViewById(R.id.pminus);
        pplus = (Button) findViewById(R.id.pplus);
        sminus = (Button) findViewById(R.id.sminus);
        splus = (Button) findViewById(R.id.splus);
        mRecording = (TextView) findViewById(R.id.trecording);
        tspeed = (TextView) findViewById(R.id.tspeed);
        tpitch = (TextView) findViewById(R.id.tpitch);
        startButton = (Button) findViewById(R.id.btnStart);
        stopButton = (Button) findViewById(R.id.btnStop);


        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT)*BUFFER_SIZE_FACTOR;
        pitch=1.5f;
        speed=1.5f;
        tspeed.setText(String.format("%.1f",speed));
        tpitch.setText(String.format("%.1f",pitch));

        //bufferSize=32767*1024;
        Log.d("MAIN", "Buffersize=" + String.valueOf(bufferSize));
        //-------------------------------------------------------------------------------------------------------------

        // Requesting permission to RECORD_AUDIO
        permissionToRecordAccepted = false;
        //permissionToWriteAccepted = false;
        context=context;

        //String[] permissionWriteExternalStorage = {Manifest.permission.WRITE_EXTERNAL_STORAGE};


        //---------------------------------------------------------------------------------------------------------------
        //-------------------------------------------------------------------------------------------------------------
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.d("MAIN", "Request perm Record audio");
            String[] permissionRecordAudio = {Manifest.permission.RECORD_AUDIO};
            ActivityCompat.requestPermissions(this, permissionRecordAudio, REQUEST_RECORD_AUDIO_PERMISSION);
            //return;
        }
        if (!permissionToRecordAccepted) {
            Log.d("MAIN", "Record not allowed");
            //finish();
        }

        //-------------------------------------------------------------------------------------------------------------
        Log.d("MAIN", "audiorecord Build");
        audioRecord = new AudioRecord.Builder()
                .setAudioSource(AudioSource.MIC)
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .build();
        Log.d("MAIN", "audiorecord Built");

        Log.d("MAIN", "audiotrack Build");
        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        Log.d("MAIN", "audiotrack Built");

        AudioAttributes audioAttributes = new AudioAttributes
                .Builder()
                .setUsage( AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(  AudioAttributes .CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool
                .Builder()
                .setMaxStreams(3)
                .setAudioAttributes( audioAttributes)
                .build();

        //audioFeeder = new AudioFeeder(audioTrack);
        //Thread tAudioFeeder = new Thread(audioFeeder,"AudioFeeder");
        //tAudioFeeder.start();

        mHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message msg){
                switch(msg.what) {
                    case RECORDINGON :
                        mRecording.setTextColor(Color.GREEN);
                        mRecording.setBackgroundColor(Color.GREEN);
                        mRecording.setText("Recording");
                        break;
                    case RECORDINGOFF :
                        mRecording.setTextColor(Color.RED);
                        mRecording.setBackgroundColor(Color.RED);
                        mRecording.setText("Not Recording");
                        break;
                    default:
                        Log.d("Main"," handler unknown message : " + (String)msg.obj );
                }
            }
        };

        Log.d("MAIN", "listener Build");
        pplus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pitch+=0.1f;
                tpitch.setText(String.format("%.1f",pitch));
            }
        });
        pminus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pitch-=0.1f;
                tpitch.setText(String.format("%.1f",pitch));
            }
        });
        splus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                speed+=0.1f;
                tspeed.setText(String.format("%.1f",speed));
            }
        });
        sminus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                speed-=0.1f;
                tspeed.setText(String.format("%.1f",speed));
            }
        });

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("RECORD", "start Button");
                startRecording();
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("RECORD", "stop Button");
                stopRecording();
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
            }
        });

        Log.d("LISTENER", "Built");
     }

    //-------------------------------------------------------------------------------------
    private void startRecording() {
        Log.d("MAIN", "startRecordingWithFile()");
        String[] permissionRecordAudio = {Manifest.permission.RECORD_AUDIO};
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.d("MAIN", "Request perm Record audio");
            ActivityCompat.requestPermissions(this, permissionRecordAudio, REQUEST_RECORD_AUDIO_PERMISSION);
            return;
        }
        //recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLE_RATE,
        //        CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
        audioRecord.startRecording();
        //recordingInProgress.set(true);
        letAppRun.set(true);
        //recordingThread = new Thread(new RecordingRunnable(), "Recording Thread");
        recordingThread = new Thread(new RecordingInMemoryRunnable(mHandler), "Recording Thread");
        recordingThread.start();
    }

    //-------------------------------------------------------------------------------------
    private void stopRecording() {
        Log.d("MAIN", "stopRecordingWithFile()");
        if (null == audioRecord) {
            return;
        }
        recordingInProgress.set(false);
        letAppRun.set(false);
        audioRecord.stop();
        //audioRecord.release();
        //audioRecord = null;
        //recordingThread = null;
    }

    //-------------------------------------------------------------------------------------
    private void playRecordedInMemory(ArrayList<ByteBuffer> audioBuffers) {
        Log.d("MAIN", "playRecordingOnly() " + " pitch=" + Float.toString(pitch) + " speed=" + Float.toString(speed));
        byte[] audioBuffer = new byte[bufferSize];
        PlaybackParams pbp = audioTrack.getPlaybackParams();

        pbp.allowDefaults();
        pbp.setPitch(pitch);
        pbp.setSpeed(speed);
        audioTrack.setPlaybackParams(pbp);
        audioTrack.play();

        int totalSize=0;
        for (ByteBuffer ab : audioBuffers) {
            int read=ab.capacity();
            Log.d("MAIN","got audioBuffer, capacity=" + read);
            audioTrack.write(ab, read, AudioTrack.WRITE_BLOCKING);
            Log.d("MAIN","got audioBuffer written ");
            totalSize+=(int)read/2;
        }
        //Log.d("MAIN","playRecordedInMemory() going to Sleep headPosition= "+ audioTrack.getPlaybackHeadPosition() );
        //SystemClock.sleep(1000);
        //Log.d("MAIN","playRecordedInMemory() waking up headPosition=" + audioTrack.getPlaybackHeadPosition());
        //SystemClock.sleep(1000);
        //Log.d("MAIN","playRecordedInMemory() waking up headPosition=" + audioTrack.getPlaybackHeadPosition());
        //audioTrack.play();
        while( audioTrack.getPlaybackHeadPosition() < totalSize) {
            Log.d("MAIN","playRecordedInMemory() waking up headPosition=" + audioTrack.getPlaybackHeadPosition() + " toPlay=" + totalSize);
            SystemClock.sleep(50);
        }
        audioTrack.pause();
        audioTrack.flush();

        audioTrack.stop();
        //audioTrack.release();
    }

    //======================================================================================================================
    private class AudioFeeder implements Runnable {

        Handler handler;
        private AudioTrack audioTrack;

        public AudioFeeder(AudioTrack audioTrack) {
            this.audioTrack=audioTrack;
        }
        public Handler getHandler() {
             return(handler);
        }
        @Override
        public void run() {
             Looper.prepare();
             handler = new Handler(Looper.myLooper()) {
                 @Override
                 public void handleMessage(Message msg) {
                     switch (msg.what) {
                         case PCMBUFFER:
                             Log.d("AudioFeeder", "PCMBUFFER");
                             int msgLength = msg.arg1;
                             //byte[] buffer = new byte[msg.obj.toString().length()];
                             synchronized ("PCM") {
                                 Log.d("AudioFeeder", "PCMBUFFER writing");
                                 audioTrack.write((byte[]) msg.obj, 0, msgLength, AudioTrack.WRITE_BLOCKING);
                                 Log.d("AudioFeeder", "PCMBUFFER end of writing");
                             }
                             break;
                         default:
                             Log.d("AudioFeeder", "Unknown what");
                             break;
                     }
                 }
             };
             Looper.loop();
        }
    }

    //======================================================================================================================
    private class RecordingInMemoryRunnable implements Runnable {

        private Handler handler;
        public RecordingInMemoryRunnable(Handler handler) {
            this.handler=handler;
        }
        @Override
        public void run() {
            while (letAppRun.get()) {
                handler.obtainMessage(RECORDINGON).sendToTarget();
                ArrayList<ByteBuffer> audioBuffers = new ArrayList<ByteBuffer>();
                int loop=0;
                long start=System.currentTimeMillis();
                while ((System.currentTimeMillis() - start) < RECORDINGDURATION ) {
                    Log.d("MAIN", "adding buffer to audioBuffers size=" + bufferSize);
                    ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
                    int result = audioRecord.read(buffer, bufferSize);
                    audioBuffers.add(buffer);
                    loop++;
                }
                Log.d("MAIN", "reached max , loop="+loop
                        + "getNotificationMarkerPosition()=" + audioRecord.getNotificationMarkerPosition()
                        + "getPositionNotificationPeriod= " + audioRecord.getPositionNotificationPeriod()
                        +  "getBufferSizeInFrames= " + audioRecord.getBufferSizeInFrames());
                handler.obtainMessage(RECORDINGOFF).sendToTarget();;
                playRecordedInMemory(audioBuffers);
              }
        }
    }

}
