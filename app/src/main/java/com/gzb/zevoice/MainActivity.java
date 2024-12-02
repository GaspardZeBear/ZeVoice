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
import android.media.audiofx.EnvironmentalReverb;
import android.media.audiofx.PresetReverb;
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
    // abs sample value over SILENCE are not SILENCE !
    private static final int SILENCE=10000;
    private static final int CONSECUTIVE_SILENCE_COUNT_MAX=2;
    private static final int PLAYERSLEEP=100;
    private static final int RECORDINGDURATION=2500;
    private static final int RECORDINGON = 1;
    private static final int RECORDINGOFF = 2;
    private static final int RECORDINGWILLSTOP = 3;
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
    private Button durationminus;
    private Button durationplus;
    private Button durationminusminus;
    private Button durationplusplus;
    private Button silenceminus;
    private Button silenceplus;
    private TextView tduration;
    private TextView tsilence;
    private TextView tspeed;
    private TextView mRecording;

    private Button startButton;
    private Button stopButton;
    private Button playButton;
    private static final int BUFFER_SIZE_FACTOR = 6;
    private Handler mHandler;

    private final AtomicBoolean recordingInProgress = new AtomicBoolean(false);
    private final AtomicBoolean letAppRun = new AtomicBoolean(false);
    private final AtomicBoolean playNow = new AtomicBoolean(false);
    private AudioRecord recorder = null;
    private Thread recordingThread = null;
    private Uri baseDocumentTreeUri;
    private Context context;
    private ActivityResultLauncher<Intent> launcher;
    String fName;
    private volatile float pitch=1.5f;
    private volatile float speed=1.0f;
    private int silence=SILENCE;
    private int duration=RECORDINGDURATION;


    // Requesting permission to RECORD_AUDIO
    private boolean permissionToRecordAccepted;
    private boolean permissionToWriteAccepted;
    private String[] permissions;

    private SoundPool soundPool;

    public AudioFeeder audioFeeder ;
    public ByteBuffer mySound;

    //-------------------------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pminus = (Button) findViewById(R.id.pminus);
        pplus = (Button) findViewById(R.id.pplus);
        sminus = (Button) findViewById(R.id.sminus);
        splus = (Button) findViewById(R.id.splus);
        durationminus = (Button) findViewById(R.id.durationMinus);
        durationplusplus = (Button) findViewById(R.id.durationPlusPlus);
        durationminusminus = (Button) findViewById(R.id.durationMinusMinus);
        durationplus = (Button) findViewById(R.id.durationPlus);
        silenceminus = (Button) findViewById(R.id.silenceMinus);
        silenceplus = (Button) findViewById(R.id.silencePlus);
        mRecording = (TextView) findViewById(R.id.trecording);
        tspeed = (TextView) findViewById(R.id.tspeed);
        tpitch = (TextView) findViewById(R.id.tpitch);
        tduration = (TextView) findViewById(R.id.tduration);
        tsilence = (TextView) findViewById(R.id.tsilence);
        startButton = (Button) findViewById(R.id.btnStart);
        stopButton = (Button) findViewById(R.id.btnStop);
        playButton = (Button) findViewById(R.id.btnPlay);


        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT)*BUFFER_SIZE_FACTOR;
        //pitch=1.5f;
        //speed=1.5f;
        tspeed.setText(String.format("%.1f",speed));
        tpitch.setText(String.format("%.1f",pitch));
        tsilence.setText(String.format("%d",silence));
        tduration.setText(String.format("%d",duration));

        //bufferSize=32767*1024;
        Log.d("MAIN", "Buffersize=" + String.valueOf(bufferSize));
        //-------------------------------------------------------------------------------------------------------------

        // Requesting permission to RECORD_AUDIO
        permissionToRecordAccepted = false;
        //permissionToWriteAccepted = false;
        context=context;

        //String[] permissionWriteExternalStorage = {Manifest.permission.WRITE_EXTERNAL_STORAGE};

        ByteBuffer mySound = ByteBuffer.allocateDirect(bufferSize);
        for (short i = 0; i < mySound.capacity()/2 ; i++) {
            short sin0=(short) ( (Math.sin((double)i) + Math.sin((double)(i+1000)) ));
            mySound.putShort((short)i);
        }

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
                        //mRecording.setText("Recording");
                        //long left=(long)(msg.obj)/1000;
                        mRecording.setText(String.valueOf(msg.obj));
                        break;
                    case RECORDINGOFF :
                        mRecording.setTextColor(Color.RED);
                        mRecording.setBackgroundColor(Color.RED);
                        mRecording.setText("Not Recording");
                        break;
                    case RECORDINGWILLSTOP :
                        mRecording.setTextColor(Color.RED);
                        mRecording.setBackgroundColor(Color.YELLOW);
                        mRecording.setText("Will stop");
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
                if ( speed <= 2) {
                    speed += 0.1f;
                }
                tspeed.setText(String.format("%.1f",speed));
            }
        });
        sminus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ( speed > 0.5) {
                    speed -= 0.1f;
                }
                tspeed.setText(String.format("%.1f",speed));
            }
        });

        silenceplus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                silence+=1000;
                tsilence.setText(String.format("%d",silence));
            }
        });
        silenceminus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                silence-=1000;
                tsilence.setText(String.format("%d",silence));
            }
        });

        durationplus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                duration+=100;
                tduration.setText(String.format("%d",duration));
            }
        });
        durationplusplus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                duration+=1000;
                tduration.setText(String.format("%d",duration));
            }
        });
        durationminus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                duration-=100;
                tduration.setText(String.format("%d",duration));
            }
        });
        durationminusminus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                duration-=1000;
                tduration.setText(String.format("%d",duration));
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
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playButton.setEnabled(true);
                playButton.setEnabled(false);
                playNow.set(true);
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
        audioRecord.startRecording();
        letAppRun.set(true);
        recordingThread = new Thread(new RecordingInMemoryRunnable(mHandler), "Recording Thread");
        recordingThread.start();
    }

    //-------------------------------------------------------------------------------------
    private void stopRecording() {
        Log.d("MAIN", "stopRecording()");
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
        EnvironmentalReverb reverb=new EnvironmentalReverb(1,audioTrack.getAudioSessionId());
        //PresetReverb reverb=new PresetReverb(1,0);
        reverb.setRoomLevel((short)EnvironmentalReverb.PARAM_ROOM_LEVEL);
        reverb.setEnabled(true);
        //audioTrack.attachAuxEffect(reverb.getId());
        audioTrack.setAuxEffectSendLevel(1.0f);
        audioTrack.setVolume(0.8f);
        audioTrack.attachAuxEffect(reverb.getId());

        audioTrack.play();

        int totalSize=0;
        ByteBuffer lastBB=null;
        for (ByteBuffer ab : audioBuffers) {
            //int read=ab.capacity();
            int read=ab.capacity();
            Log.d("MAIN","got audioBuffer "
                    + " capacity=" + ab.capacity()
                    + " position=" + ab.position()
                    + " bufferSizeInFrames=" + audioRecord.getBufferSizeInFrames());
            int capacity=audioTrack.write(ab, read, AudioTrack.WRITE_BLOCKING);
            Log.d("MAIN","after write capacity=" + capacity);
            totalSize+=(int)read/2;
            //lastBB=ab.duplicate();
        }

        int numSamples=totalSize;
        Log.d("MAIN","playRecordedInMemory() numSamples=" + numSamples);
        audioTrack.setNotificationMarkerPosition(numSamples);  // Set the marker to the end.
        //audioTrack.setNotificationMarkerPosition(1024);
        audioTrack.setPlaybackPositionUpdateListener(
                new AudioTrack.OnPlaybackPositionUpdateListener() {
                    @Override
                    public void onPeriodicNotification(AudioTrack track) {}
                    @Override
                    public void onMarkerReached(AudioTrack track) {
                        Log.d("MAIN","playRecordedInMemory() onMarkerReached underrun=" + track.getUnderrunCount());
                        //track.stop();
                    }
                });

        //while( audioTrack.getPlaybackHeadPosition() < totalSize) {
        //    Log.d("MAIN","playRecordedInMemory() waking up headPosition=" + audioTrack.getPlaybackHeadPosition() + " toPlay=" + totalSize);
        //    SystemClock.sleep(PLAYERSLEEP);
        //}


        //SystemClock.sleep(5000);
        Log.d("MAIN","playRecordedInMemory() last waking up headPosition=" + audioTrack.getPlaybackHeadPosition() + " toPlay=" + totalSize);
        //audioTrack.pause();
        //audioTrack.flush();
        Log.d("MAIN","playRecordedInMemory() paused and flushed");
        audioTrack.stop();
        //audioTrack.release();
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
                handler.obtainMessage(RECORDINGON,duration).sendToTarget();
                audioRecord.startRecording();
                ArrayList<ByteBuffer> audioBuffers = new ArrayList<ByteBuffer>();
                int loop=0;
                int notDiscarded=0;
                int consecutiveSilenceCount=0;
                long start=System.currentTimeMillis();
                String exitReason="";
                //while ((System.currentTimeMillis() - start) < duration || consecutiveSilenceCount > CONSECUTIVE_SILENCE_COUNT_MAX) {
                while (true) {
                    if ( (System.currentTimeMillis() - start) > duration ) {
                        exitReason="Duration";
                        break;
                    }
                    if ( consecutiveSilenceCount > CONSECUTIVE_SILENCE_COUNT_MAX)  {
                        exitReason="Silence";
                        break;
                    }
                    if ( playNow.get() )  {

                        exitReason="Playnow";
                        break;
                    }
                    ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
                    int result = audioRecord.read(buffer, bufferSize);

                    if ( (System.currentTimeMillis() - start) > duration - 1000 ) {
                        handler.obtainMessage(RECORDINGWILLSTOP).sendToTarget();
                    } else {
                        handler.obtainMessage(RECORDINGON,duration - (System.currentTimeMillis() - start)).sendToTarget();
                    }
                    if ( !isSilence(buffer) ) {
                        Log.d("MAIN", "adding buffer to audioBuffers size=" + bufferSize + " result=" + result);
                        buffer.rewind();
                        //buffer.position(result);
                        audioBuffers.add(buffer);
                        notDiscarded++;
                        consecutiveSilenceCount=0;
                    } else {
                        //Log.d("MAIN", "discarding silence buffer " + bufferSize);
                        consecutiveSilenceCount++;
                    }
                    loop++;
                }
                playNow.set(false);
                handler.obtainMessage(RECORDINGOFF).sendToTarget();;
                audioRecord.stop();
                ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
                int result = audioRecord.read(buffer, bufferSize);
                //buffer.position(result);
                Log.d("MAIN", "adding last buffer to audioBuffers size=" + bufferSize + " result=" + result);
                //buffer.position(bufferSize);
                audioBuffers.add(buffer);
                //audioRecord.stop();
                for (int i0=0;i0<1;i0++) {
                    Log.d("MAIN","inserting dummy " + i0);
                    ByteBuffer dummyBuffer = ByteBuffer.allocateDirect(bufferSize);
                    for (short i = 0; i < dummyBuffer.capacity()/2 ; i++) {
                        dummyBuffer.putShort( (short)0);
                    }
                    dummyBuffer.rewind();
                    //mySound.rewind();
                    audioBuffers.add(dummyBuffer);
                }

                Log.d("MAIN", "reached end of recording " + exitReason
                        + " loop="+loop
                        + " kept Buffers=" + notDiscarded
                        + " getBufferSizeInFrames= " + audioRecord.getBufferSizeInFrames());

                playRecordedInMemory(audioBuffers);
              }
        }

        //-------------------------------------------------------------------------------------
        private boolean isSilence(ByteBuffer buffer) {
            int gtSilence = 0;
            buffer.rewind();
            for (int i = 0; i < bufferSize; i += 2) {
                short s = buffer.getShort();
                if (Math.abs(s) > silence ) {
                    gtSilence++;
                }
            }
            if (gtSilence > 0) {
                return(false);
            }
            return(true);
            //Log.d("MAIN", "Abs(Sample Value) > " + threshold + " ="+ gtSilence);

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


}
