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
    private AudioTrack audioTrack1;
    private AudioTrack[] audioTracks;
    private int bufferSize;
    private boolean isRecording = false;
    // abs sample value over SILENCE are not SILENCE !
    public static final int SILENCE = 10000;
    private static final int CONSECUTIVE_SILENCE_COUNT_MAX = 2;
    private static final int PLAYERS = 4;
    private static final int RECORDINGDURATION = 2500;
    private static final int RECORDINGON = 1;
    private static final int RECORDINGOFF = 2;
    private static final int RECORDINGWILLSTOP = 3;
    private static final int BUTTONPLAYON = 4;
    private static final int BUTTONPLAYOFF = 5;
    private static final int BUTTONSTARTON = 6;
    private static final int BUTTONSTARTOFF = 7;
    private static final int BUTTONBREAKOFF = 9;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION = 201;
    private static final int PCMBUFFER = 1;

    private static final int MINDURATION = 500;
    private static final int MAXDURATION = 15000;
    public static final int SAMPLE_RATE = 44100;
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
    private Button breakButton;

    private Button[] pButtons;
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
    private volatile float pitch = 1.5f;
    private volatile float speed = 1.0f;
    private int silence = SILENCE;
    private int duration = RECORDINGDURATION;


    private boolean[] pActive = {true, false, false, true};
    // Requesting permission to RECORD_AUDIO
    private boolean permissionToRecordAccepted;
    private boolean permissionToWriteAccepted;
    private String[] permissions;

    private SoundPool soundPool;

    public AudioFeeder audioFeeder;
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
        breakButton = (Button) findViewById(R.id.btnBreak);

        pButtons = new Button[]{
                (Button) findViewById(R.id.p1),
                (Button) findViewById(R.id.p2),
                (Button) findViewById(R.id.p3),
                (Button) findViewById(R.id.p4)
        };

        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT) * BUFFER_SIZE_FACTOR;
        //pitch=1.5f;
        //speed=1.5f;
        tspeed.setText(String.format("%.1f", speed));
        tpitch.setText(String.format("%.1f", pitch));
        tsilence.setText(String.format("%d", silence));
        tduration.setText(String.format("%d", duration));

        //bufferSize=32767*1024;
        Log.d("MAIN", "Buffersize=" + String.valueOf(bufferSize));
        //-------------------------------------------------------------------------------------------------------------

        // Requesting permission to RECORD_AUDIO
        permissionToRecordAccepted = false;
        //permissionToWriteAccepted = false;
        context = context;

        //String[] permissionWriteExternalStorage = {Manifest.permission.WRITE_EXTERNAL_STORAGE};

        ByteBuffer mySound = ByteBuffer.allocateDirect(bufferSize);
        for (short i = 0; i < mySound.capacity() / 2; i++) {
            short sin0 = (short) ((Math.sin((double) i) + Math.sin((double) (i + 1000))));
            mySound.putShort((short) i);
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
        audioTracks = new AudioTrack[PLAYERS];
        for (int i = 0; i < audioTracks.length; i++) {
            audioTracks[i] = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(3 * bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
        }
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
                .setBufferSizeInBytes(3 * bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        audioTrack1 = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(3 * bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        Log.d("MAIN", "audiotrack Built");

        //audioFeeder = new AudioFeeder(audioTrack);
        //Thread tAudioFeeder = new Thread(audioFeeder,"AudioFeeder");
        //tAudioFeeder.start();

        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case RECORDINGON:
                        mRecording.setTextColor(Color.GREEN);
                        mRecording.setBackgroundColor(Color.GREEN);
                        //mRecording.setText("Recording");
                        //long left=(long)(msg.obj)/1000;
                        mRecording.setText(String.valueOf(msg.obj));
                        break;
                    case RECORDINGOFF:
                        mRecording.setTextColor(Color.RED);
                        mRecording.setBackgroundColor(Color.RED);
                        mRecording.setText("Not Recording");
                        break;
                    case RECORDINGWILLSTOP:
                        mRecording.setTextColor(Color.RED);
                        mRecording.setBackgroundColor(Color.YELLOW);
                        mRecording.setText("Will stop");
                        break;
                    case BUTTONPLAYON:
                        playButton.setEnabled(true);
                        break;
                    case BUTTONPLAYOFF:
                        playButton.setEnabled(false);
                        break;
                    case BUTTONSTARTON:
                        startButton.setEnabled(true);
                        break;
                    case BUTTONSTARTOFF:
                        startButton.setEnabled(false);
                        break;
                    case BUTTONBREAKOFF:
                        breakButton.setActivated(false);
                        break;
                    default:
                        Log.d("Main", " handler unknown message : " + (String) msg.obj);
                }
            }
        };

        Log.d("MAIN", "listener Build");
        pplus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pitch += 0.1f;
                tpitch.setText(String.format("%.1f", pitch));
            }
        });
        pminus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pitch -= 0.1f;
                tpitch.setText(String.format("%.1f", pitch));
            }
        });
        splus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (speed <= 2) {
                    speed += 0.1f;
                }
                tspeed.setText(String.format("%.1f", speed));
            }
        });
        sminus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (speed > 0.5) {
                    speed -= 0.1f;
                }
                tspeed.setText(String.format("%.1f", speed));
            }
        });

        silenceplus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                silence += 1000;
                tsilence.setText(String.format("%d", silence));
            }
        });
        silenceminus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                silence -= 1000;
                tsilence.setText(String.format("%d", silence));
            }
        });

        durationplus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                duration = modifyDuration(duration, 100);
                tduration.setText(String.format("%d", duration));
            }
        });
        durationplusplus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                duration = modifyDuration(duration, 1000);
                tduration.setText(String.format("%d", duration));
            }
        });
        durationminus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                duration = modifyDuration(duration, -100);
                tduration.setText(String.format("%d", duration));
            }
        });
        durationminusminus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                duration = modifyDuration(duration, -1000);
                tduration.setText(String.format("%d", duration));
            }
        });

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("RECORD", "start Button");
                startRecording();
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                playButton.setEnabled(false);
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("RECORD", "stop Button");
                stopRecording();
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                playButton.setEnabled(true);
            }
        });
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopButton.setEnabled(false);
                playButton.setEnabled(false);
                startButton.setEnabled(false);
                stopRecording();
                new Thread(() -> {
                    playRecordedInMemory(getFromFile());
                    mHandler.obtainMessage(BUTTONPLAYON).sendToTarget();
                    mHandler.obtainMessage(BUTTONSTARTON).sendToTarget();
                }).start();
            }
        });
        breakButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("RECORD", "break Button");
                breakButton.setActivated(true);
            }
        });
        for (int i = 0; i < pButtons.length; i++) {
            int idx = i;
            pButtons[i].setClickable(true);
            Log.d("BUTTON", " idx=" + idx + " setting OnClickListener");
            if (pButtons[idx].isActivated()) {
                pButtons[idx].setBackgroundColor(Color.GREEN);
            } else {
                pButtons[idx].setBackgroundColor(Color.GRAY);
            }
            //pActive[i]=pButtons[i].isActivated();
            pButtons[idx].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d("BUTTON", " idx=" + idx + " enabled=" + pButtons[idx].isEnabled() + " activated=" + pButtons[idx].isActivated());
                    pButtons[idx].setActivated(!pButtons[idx].isActivated());
                    pButtons[idx].invalidate();
                    if (pButtons[idx].isActivated()) {
                        pButtons[idx].setBackgroundColor(Color.GREEN);
                    } else {
                        pButtons[idx].setBackgroundColor(Color.GRAY);
                    }
                    Log.d("BUTTON", " idx=" + idx + " enabled=" + pButtons[idx].isEnabled() + " activated=" + pButtons[idx].isActivated());
                }
            });
        }

        Log.d("LISTENER", "Built");
       }


    //-------------------------------------------------------------------------------------
    private int modifyDuration(int duration, int offset) {
        if ((duration + offset) > MAXDURATION) {
            return (MAXDURATION);
        }
        if ((duration + offset) < MINDURATION) {
            return (MINDURATION);
        }
        return (duration + offset);
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
        Log.d("MAIN", "playRecordingOnly() audioBuffers.size=" + audioBuffers.size() + " pitch=" + Float.toString(pitch) + " speed=" + Float.toString(speed));
        float[] pitches = {2.0f, 1.5f, 1.0f, 0.7f};
        float[] volumes = {0.25f, 0.5f, 0.75f, 1f};
        for (int i = 0; i < audioTracks.length; i++) {
            Log.d("MAIN", "playRecordingOnly() player i=" + i);
            PlaybackParams pbp = audioTrack.getPlaybackParams();
            pbp.allowDefaults();
            pbp.setPitch((float) (pitch * pitches[i]));
            pbp.setSpeed(speed);
            audioTracks[i].setVolume(volumes[i]);
            audioTracks[i].setPlaybackParams(pbp);
            audioTracks[i].play();
        }

        for (ByteBuffer ab : audioBuffers) {
            int read = 0;
            int readWithEffect = 0;
            int readCapacity;
            ByteBuffer abWithEffect;
            abWithEffect = new Delay(0,ab).applyEffect();
            readWithEffect = abWithEffect.capacity();
            //read = ab.capacity();
            for (int i = 0; i < audioTracks.length; i++) {
                if (!pButtons[i].isActivated()) {
                    continue;
                }
                //ab.rewind();
                abWithEffect.rewind();
                audioTracks[i].write(abWithEffect, readWithEffect, AudioTrack.WRITE_BLOCKING);
                abWithEffect.rewind();
                audioTracks[i].write(abWithEffect, readWithEffect, AudioTrack.WRITE_BLOCKING);
            }
        }

        for (AudioTrack at : audioTracks) {
            at.stop();
        }
        Log.d("MAIN", "playRecordedInMemory() over");
    }

    //-------------------------------------------------------------------------------------
    public void recordToFile(ArrayList<ByteBuffer> audioBuffers) {
        //final File file = new File(Environment.getExternalStorageDirectory(), "recording.pcm");
        final File file = new File(getFilesDir(), "recording.pcm");
        Log.d("MAIN", "Writing File : " + getFilesDir() +"/recording.pcm");
        try (final FileOutputStream outStream = new FileOutputStream(file)) {
            for (ByteBuffer ab : audioBuffers) {
                ab.rewind();
                byte[] toBytes=new byte[ab.capacity()];
                ab.get(toBytes);
                //outStream.write(ab.array());
                outStream.write(toBytes);
            }
        } catch (IOException e) {
            Log.d("MAIN","Exception");
            throw new RuntimeException("Writing of recorded audio failed", e);
        }
    }

    //-------------------------------------------------------------------------------------
    public ArrayList<ByteBuffer> getFromFile() {
        ArrayList<ByteBuffer> arbb=new ArrayList<ByteBuffer>();
        final File file = new File(getFilesDir(), "recording.pcm");
        Log.d("MAIN", "Reading from File : " + getFilesDir() +"/recording.pcm");
        try {
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
            byte[] buffer = new byte[bufferSize];
            int read;
            int idx=0;
            while ( (read = bis.read(buffer,0,bufferSize)) != -1) {
                if (read > 0) {
                  Log.d("MAIN", "Reading from File : got " + read + " bytes");
                  byte[] cloned=new byte[bufferSize];
                  cloned=buffer.clone();
                  ByteBuffer ar=ByteBuffer.wrap(cloned);
                  ar.rewind();
                  ar.rewind();
                  boolean b=arbb.add(ar);
                  idx++;
                }
            }
        } catch (IOException e) {
            Log.d("MAIN","Exception");
            throw new RuntimeException("Writing of recorded audio failed", e);
        }

        Log.d("MAIN","Read audiobuffer : " + arbb.size());
        for (int i=0;i<arbb.size();i++) {
            ByteBuffer bb=arbb.get(i);
            bb.rewind();
            Log.d("MAIN"," bb capacity=" + bb.capacity() + " position=" + bb.position());
            bb.rewind();
        }
        return(arbb);
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
                    //Log.d("Break"," button " + breakButton.isActivated());
                    if ( breakButton.isActivated() ) {
                        exitReason="Break";
                        handler.obtainMessage(BUTTONBREAKOFF).sendToTarget();
                        break;
                    }
                    if ( (System.currentTimeMillis() - start) > duration ) {
                        exitReason="Duration";
                        break;
                    }
                    if ( consecutiveSilenceCount > CONSECUTIVE_SILENCE_COUNT_MAX)  {
                        exitReason="Silence";
                        break;
                    }
                    //if ( playNow.get() )  {

                    //    exitReason="Playnow";
                    //    break;
                    //}
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

                recordToFile(audioBuffers);
                playRecordedInMemory(audioBuffers);
                //playRecordedInMemory(getFromFile());
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
