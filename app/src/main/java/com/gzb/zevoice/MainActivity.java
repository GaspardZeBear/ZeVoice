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
import android.os.ParcelFileDescriptor;
import android.view.View;

import android.content.Intent;


import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.contract.ActivityResultContracts;
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

    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private Button mSample;
    private Button mOn;
    private Button mOff;
    private Button x10;
    private Button x05;
    private Button x15;
    private Button startButton;
    private Button stopButton;
    private Button playButton;
    private static final int BUFFER_SIZE_FACTOR = 2;

    private final AtomicBoolean recordingInProgress = new AtomicBoolean(false);
    private AudioRecord recorder = null;
    private Thread recordingThread = null;
    private Uri baseDocumentTreeUri;
    private Context context;
    private ActivityResultLauncher<Intent> launcher;
    String fName;


    // Requesting permission to RECORD_AUDIO
    private boolean permissionToRecordAccepted;
    private boolean permissionToWriteAccepted;
    private String[] permissions;

    //-------------------------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSample = (Button) findViewById(R.id.sample);
        mOn = (Button) findViewById(R.id.on);
        mOff = (Button) findViewById(R.id.off);
        x10 = (Button) findViewById(R.id.x10);
        x05 = (Button) findViewById(R.id.x05);
        x15 = (Button) findViewById(R.id.x15);
        startButton = (Button) findViewById(R.id.btnStart);
        stopButton = (Button) findViewById(R.id.btnStop);
        playButton = (Button) findViewById(R.id.btnPlay);

        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        //bufferSize=32767*1024;
        Log.d("MAIN", "Buffersize=" + String.valueOf(bufferSize));
        //-------------------------------------------------------------------------------------------------------------

        // Requesting permission to RECORD_AUDIO
        permissionToRecordAccepted = false;
        permissionToWriteAccepted = false;

        String[] permissionWriteExternalStorage = {Manifest.permission.WRITE_EXTERNAL_STORAGE};


        //---------------------------------------------------------------------------------------------------------------
        //-------------------------------------------------------------------------------------------------------------
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.d("MAIN", "Request perm Record audio");
            String[] permissionRecordAudio = {Manifest.permission.RECORD_AUDIO};
            ActivityCompat.requestPermissions(this, permissionRecordAudio, REQUEST_RECORD_AUDIO_PERMISSION);
            //return;
        }
        //if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
        //    Log.d("MAIN", "Request perm Write external storage");
        //    ActivityCompat.requestPermissions(this, permissionWriteExternalStorage, REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION);
        //return;
        // }

        if (!permissionToRecordAccepted) {
            Log.d("MAIN", "Record not allowed");
            //finish();
        }
        //if (!permissionToWriteAccepted) {
        //    Log.d("MAIN", "Write not allowed");
        //finish();
        //}

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

        Log.d("MAIN", "listener Build");
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
                startRecordingOnly();
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("RECORD", "stop Button");
                stopRecordingOnly();
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
        //pbp.setPitch(1.0f);
        //pbp.setSpeed(1.0f);

        audioTrack.setPlaybackParams(pbp);
        audioTrack.play();
        isRecording = true;

        new Thread(() -> {
            byte[] audioBuffer = new byte[bufferSize];
            Log.d("MAIN", "recording thread start");
            while (isRecording) {
                int read = audioRecord.read(audioBuffer, 0, audioBuffer.length);
                Log.d("MAIN", "got data len=" + String.valueOf(read));
                if (read > 0) {
                    Log.d("MAIN", "writing");
                    //Log.v("AUDIORECORD","writing");
                    audioTrack.write(audioBuffer, 0, read, AudioTrack.WRITE_BLOCKING);
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
    private void startRecordingOnly() {
        Log.d("MAIN", "startRecordingOnly()");
        String[] permissionRecordAudio = {Manifest.permission.RECORD_AUDIO};
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.d("MAIN", "Request perm Record audio");
            ActivityCompat.requestPermissions(this, permissionRecordAudio, REQUEST_RECORD_AUDIO_PERMISSION);
            return;
        }
        //recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLE_RATE,
        //        CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
        audioRecord.startRecording();
        recordingInProgress.set(true);
        recordingThread = new Thread(new RecordingRunnable(), "Recording Thread");
        recordingThread.start();
    }

    //-------------------------------------------------------------------------------------
    private void stopRecordingOnly() {
        Log.d("MAIN", "stopRecordingOnly()");
        if (null == audioRecord) {
            return;
        }
        recordingInProgress.set(false);
        audioRecord.stop();
        audioRecord.release();
        audioRecord = null;
        recordingThread = null;
    }

    //-------------------------------------------------------------------------------------
    private void playRecording(String fName) {
        Log.d("MAIN", "playRecordingOnly()");
        byte[] audioBuffer = new byte[bufferSize];
        PlaybackParams pbp = audioTrack.getPlaybackParams();
        pbp.allowDefaults();
        pbp.setPitch(2.0f);
        //pbp.setSpeed(1.5f);
        audioTrack.setPlaybackParams(pbp);
        audioTrack.play();
        try {
            BufferedInputStream bis =
                    new BufferedInputStream(new FileInputStream(new File(fName)));
            int read;
            while ( (read = bis.read(audioBuffer,0,bufferSize)) != -1) {
                //Log.d("MAIN", "got data len=" + String.valueOf(read));
                if (read > 0) {
                    audioTrack.write(audioBuffer, 0, read, AudioTrack.WRITE_BLOCKING);
                }
            }
        } catch (IOException e) {
            Log.d("MAIN","IOException while playRecording");
        }
    }


    //======================================================================================================================
    private class RecordingRunnable implements Runnable {

        //-------------------------------------------------------------------------------------
        @Override
        public void run() {
            Log.d("MAIN"," ExtStorage  " + Environment.getExternalStorageDirectory());
            final File file = new File(Environment.getExternalStorageDirectory(), "/Documents/ZeVoice.pcm");
            fName=Environment.getExternalStorageDirectory()+"/Documents/ZeVoice.pcm";
            Log.d("MAIN", "Recording File : " + fName);
            final ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
            try (final FileOutputStream outStream = new FileOutputStream(file)) {
                while (recordingInProgress.get()) {
                    int result = audioRecord.read(buffer, bufferSize);
                    if (result < 0) {
                        throw new RuntimeException("Reading of audio buffer failed: " +
                                getBufferReadFailureReason(result));
                    }
                    //Log.d("MAIN", "Writing Buffer");
                    outStream.write(buffer.array(), 0, bufferSize);
                    buffer.clear();
                }
            } catch (IOException e) {
                Log.d("MAIN", "Exception");
                throw new RuntimeException("Writing of recorded audio failed", e);
            }
            playRecording(fName);
        }

        //@override
        //-------------------------------------------------------------------------------------
        public void runOK() {
            Log.d("MAIN"," ExtStorage  " + Environment.getExternalStorageDirectory());
            final File file = new File(Environment.getExternalStorageDirectory(), "/Documents/ZeVoice.pcm");
            String fName=Environment.getExternalStorageDirectory()+"/Documents/ZeVoice.pcm";
            Log.d("MAIN", "Recording File : " + fName);
            final ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
            try (final FileOutputStream outStream = new FileOutputStream(file)) {
                while (recordingInProgress.get()) {
                    int result = audioRecord.read(buffer, bufferSize);
                    if (result < 0) {
                        throw new RuntimeException("Reading of audio buffer failed: " +
                                getBufferReadFailureReason(result));
                    }
                    Log.d("MAIN", "Writing Buffer");
                    outStream.write(buffer.array(), 0, bufferSize);
                    buffer.clear();
                }
            } catch (IOException e) {
                Log.d("MAIN", "Exception");
                throw new RuntimeException("Writing of recorded audio failed", e);
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
