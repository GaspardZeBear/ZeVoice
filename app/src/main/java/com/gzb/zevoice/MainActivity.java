package com.gzb.zevoice;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
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

public class MainActivity extends AppCompatActivity {

    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private int bufferSize;
    private boolean isRecording = false;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION = 201;
    private static final int PCMBUFFER = 1;

    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private Button mSample;
    private Button mOn;
    private Button mOff;
    private Button pitch10;
    private Button pitch05;
    private Button pitch15;
    private Button pitch20;
    private Button speed10;
    private Button speed05;
    private Button speed15;
    private Button speed20;
    private Button startButton;
    private Button stopButton;
    private Button playButton;
    private static final int BUFFER_SIZE_FACTOR = 6;

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
        mOn = (Button) findViewById(R.id.on);
        mOff = (Button) findViewById(R.id.off);
        pitch10 = (Button) findViewById(R.id.pitch10);
        pitch05 = (Button) findViewById(R.id.pitch05);
        pitch15 = (Button) findViewById(R.id.pitch15);
        pitch20 = (Button) findViewById(R.id.pitch20);
        speed10 = (Button) findViewById(R.id.speed10);
        speed05 = (Button) findViewById(R.id.speed05);
        speed15 = (Button) findViewById(R.id.speed15);
        speed20 = (Button) findViewById(R.id.speed20);
        startButton = (Button) findViewById(R.id.btnStart);
        stopButton = (Button) findViewById(R.id.btnStop);
        playButton = (Button) findViewById(R.id.btnPlay);

        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT)*BUFFER_SIZE_FACTOR;
        pitch=1.5f;
        speed=1.5f;
        //bufferSize=32767*1024;
        Log.d("MAIN", "Buffersize=" + String.valueOf(bufferSize));
        //-------------------------------------------------------------------------------------------------------------

        // Requesting permission to RECORD_AUDIO
        permissionToRecordAccepted = false;
        permissionToWriteAccepted = false;
        context=context;

        String[] permissionWriteExternalStorage = {Manifest.permission.WRITE_EXTERNAL_STORAGE};


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

        audioFeeder = new AudioFeeder(audioTrack);
        Thread tAudioFeeder = new Thread(audioFeeder,"AudioFeeder");
        tAudioFeeder.start();


        Log.d("MAIN", "listener Build");
        pitch05.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pitch=0.5f;
            }
        });
        pitch10.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pitch=1.0f;
            }
        });
        pitch15.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pitch=1.5f;
            }
        });
        pitch20.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pitch=2.0f;
            }
        });
        speed05.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                speed=0.5f;
            }
        });
        speed10.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                speed=1.0f;
            }
        });
        speed15.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                speed=1.5f;
            }
        });
        speed20.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                speed=2.0f;
            }
        });
        mOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecordingAndPlaying();
            }
        });
        mOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecordingAndPlaying();
            }
        });
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("RECORD", "start Button");
                startRecordingWithFile();
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("RECORD", "stop Button");
                stopRecordingWithFile();
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
            }
        });

        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("MAIN", "play Button");
                //playRecording();
            }
        });
        Log.d("LISTENER", "Built");
        launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), (result) -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    baseDocumentTreeUri = Objects.requireNonNull(result.getData()).getData();
                    final int takeFlags = (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    Log.d("MAIN","launcher " + baseDocumentTreeUri);
                    // take persistable Uri Permission for future use
                    //context.getContentResolver().takePersistableUriPermission(result.getData().getData(), takeFlags);
                    getContentResolver().takePersistableUriPermission(baseDocumentTreeUri, takeFlags);
                    //SharedPreferences preferences = context.getSharedPreferences("com.example.geofriend.fileutility", Context.MODE_PRIVATE);
                    //preferences.edit().putString("filestorageuri", result.getData().getData().toString()).apply();
                } else {
                    Log.e("FileUtility", "Some Error Occurred : " + result);
                }
        });
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        launcher.launch(intent);
    }

    //--------------------------------------------------------------------------------------------------------------
    public void launchBaseDirectoryPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        launcher.launch(intent);
        return;
    }

    //-------------------------------------------------------------------------------------
    private void startRecordingAndPlaying() {
        Log.d("MAIN", "startRecordingAndPlaying()");
        audioRecord.startRecording();
        PlaybackParams pbp = audioTrack.getPlaybackParams();
        pbp.allowDefaults();
        pbp.setPitch(pitch);
        pbp.setSpeed(speed);
        audioTrack.setPlaybackParams(pbp);
        audioTrack.play();
        isRecording = true;

        new Thread(() -> {
            byte[] audioBuffer = new byte[bufferSize];
            Log.d("MAIN", "recording thread start");
            while (isRecording) {
                int read = audioRecord.read(audioBuffer, 0, audioBuffer.length);
                if (read > 0) {
                    //audioTrack.write(audioBuffer, 0, read, AudioTrack.WRITE_BLOCKING);
                    audioFeeder.getHandler().obtainMessage(MainActivity.PCMBUFFER, read, -1, audioBuffer).sendToTarget();
                }
            }
            Log.d("MAIN", "recording thread end");
        }).start();
    }

    //-------------------------------------------------------------------------------------
    private void stopRecordingAndPlaying() {
        if (isRecording) {
            isRecording = false;
            audioRecord.stop();
            audioTrack.stop();
            //audioRecord.release();
            //audioTrack.release();
        }
    }

    //-------------------------------------------------------------------------------------
    private void startRecordingWithFile() {
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
        recordingThread = new Thread(new RecordingRunnable(), "Recording Thread");
        recordingThread.start();
    }

    //-------------------------------------------------------------------------------------
    private void stopRecordingWithFile() {
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
    private void playRecordingWithFile(File file) {
        Log.d("MAIN", "playRecordingOnly() " + " pitch=" + Float.toString(pitch) + " speed=" + Float.toString(speed));
        byte[] audioBuffer = new byte[bufferSize];
        PlaybackParams pbp = audioTrack.getPlaybackParams();
        pbp.allowDefaults();
        pbp.setPitch(pitch);
        pbp.setSpeed(speed);
        audioTrack.setPlaybackParams(pbp);
        audioTrack.play();

        try {
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
            int read;
            while ( (read = bis.read(audioBuffer,0,bufferSize)) != -1) {
                audioTrack.write(audioBuffer, 0, read, AudioTrack.WRITE_BLOCKING);
                //audioFeeder.getHandler().obtainMessage(MainActivity.PCMBUFFER, read, -1, audioBuffer).sendToTarget();
            }
            bis.close();
            //SystemClock.sleep(2000);
            audioTrack.flush();
            audioTrack.pause();
            audioTrack.stop();
        } catch (IOException e) {
            Log.d("MAIN","IOException while playRecording");
        }
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
    private class RecordingRunnable implements Runnable {

        //-------------------------------------------------------------------------------------
        // Records to a file then plays it
        public int counter;

        public RecordingRunnable() {
            startWatchdog(2000);
        }

        public void startWatchdog(int interval) {
            new Thread(() -> {
                while (letAppRun.get() ) {
                    recordingInProgress.set(false);
                    Log.d("Watchdog", "recordingInProgress " + recordingInProgress + " counter " + counter);
                    synchronized ("WATCHDOG") {
                        SystemClock.sleep(interval);
                    }
                    recordingInProgress.set(true);
                    SystemClock.sleep(interval);
                    Log.d("Watchdog", "recordingInProgress " + recordingInProgress);
                }
            }).start();
        }

        @Override
        public void run() {
            counter=0;
            while (letAppRun.get() ) {
                recordingInProgress.set(true);
                counter++;
                if (counter > 2 ) {
                    counter=0;
                }
                synchronized ("WATCHDOG") {}

                File root = android.os.Environment.getExternalStorageDirectory();
                File dir = new File (root.getAbsolutePath() + "/Documents");
                dir.mkdirs();
                File file = new File(dir, "/ZeVoice/ZeVoice.pcm");

                //String fSuffix="/Documents/ZeVoice.pcm";
                //File dir=Environment.getExternalStorageDirectory();
                //final File file = new File(dir, fSuffix);
                //String fName="ZeVoice;";
                //final DocumentFile dFile = DocumentFile.fromTreeUri(context,baseDocumentTreeUri).createFile("audio/pcm",fName);
                //fName = dir + fSuffix;
                Log.d("MAIN", "Recording File : " + file.getName() );
                final ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
                try {
                    //final FileOutputStream outStream = new FileOutputStream(dFile.getName());
                    final FileOutputStream outStream = new FileOutputStream(file);
                    while (recordingInProgress.get()) {
                        int result = audioRecord.read(buffer, bufferSize);
                        outStream.write(buffer.array(), 0, bufferSize);
                        buffer.clear();
                    }
                    outStream.flush();
                    outStream.close();
                    Log.d("MAIN", "Stopped by watchdog, closing");
                } catch (IOException e) {
                    Log.d("MAIN", "Exception");
                    throw new RuntimeException("Writing of recorded audio failed", e);
                }
                SystemClock.sleep(10);
                //recordingInProgress.set(false);
                playRecordingWithFile(file);
                //recordingInProgress.set(true);
            }
        }

        //-------------------------------------------------------------------------------------
        private String getBufferReadFailureReason(int errorCode) {
            switch (errorCode) {
                case AudioRecord.ERROR_INVALID_OPERATION:
                    return "ERROR_INVALID_OPERATION";
                case AudioRecord.ERROR_BAD_VALUE:
                    return "ERROR_BAD_VALUE";
                case AudioRecord.ERROR_DEAD_OBJECT:
                    return "ERROR_DEAD_OBJECT";
                case AudioRecord.ERROR:
                    return "ERROR";
                default:
                    return "Unknown (" + errorCode + ")";
            }
        }
    }

    //-------------------------------------------------------------------------------------
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //Log.d("MAIN", "got RequestCode " + String.valueOf(requestCode) + " grant[0] "+ String.valueOf(grantResults[0]));
        switch (requestCode) {
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
            case REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION:
                permissionToWriteAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
    }

}
