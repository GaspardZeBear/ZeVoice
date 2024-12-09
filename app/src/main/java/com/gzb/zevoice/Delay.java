package com.gzb.zevoice;

import static com.gzb.zevoice.MainActivity.*;

import android.util.Log;

import java.nio.ByteBuffer;


public class Delay {
    ByteBuffer arInit;
    int delayMs;
    int SAMPLE_RATE=44100;
    int SILENCE=10000;
    public Delay(int delayMs, ByteBuffer arInit) {
        this.arInit=arInit;
        this.delayMs=delayMs;
    }
    //-------------------------------------------------------------------------------------
    public void testDummyBuffer() {
        ByteBuffer bb0=ByteBuffer.allocateDirect(100);
        ByteBuffer bb1=ByteBuffer.allocateDirect(100);
        for (int i=0;i<bb0.capacity();i+=2) {
            bb0.putShort((short)(128*i));
        }
        bb0.rewind();
        arInit=bb0;
        delayMs=3;
        bb1=applyEffect();

        for (int i=0;i<bb0.capacity();i+=2) {
            //MyLog.d("TEST"," i=" + i + " bb0=" + bb0.getShort(i) + " bb1=" + bb1.getShort(i));
        }
    }

    //-------------------------------------------------------------------------------------
    private void copyBuffer(String tag, ByteBuffer src, ByteBuffer dest, int start, int count, float factor) {
        // hre we have to convert samples to bytes !!!!!
        int srcIdx=2*start;
        int srcIdxMax=srcIdx+2*count;
        if ( srcIdxMax > src.capacity() ) {
            srcIdxMax=src.capacity();
        }
        //MyLog.d("Effect","srcIdx="+ srcIdx + " srcIdxMax=" + srcIdxMax );
        int notZero=0;
        int zero=0;
        while ( srcIdx < srcIdxMax) {
            if ( (dest.capacity() - dest.position()) < 2 ) {
                //MyLog.d("Effect"," Break !!! dest.capacity()="+ dest.capacity() + " dest.position()=" + dest.position() );
                break;
            }
            if ( src.getShort(srcIdx) != 0 ) {
                notZero++;
            } else {
                zero++;
            }
            dest.putShort((short) (factor * src.getShort(srcIdx)));
            srcIdx+=2;
        }
    }

    //-------------------------------------------------------------------------------------
    //public ByteBuffer applyEffect(int delayMs, ByteBuffer arInit) {
    public ByteBuffer applyEffect() {
        //MyLog.d("MAIN", "applyEffect delay=" + delayMs);
        if (delayMs  <2 || delayMs > SAMPLE_RATE/100 ) {
            ////MyLog.d("MAIN", "applyEffect delayMs out of range");
            return(arInit);
        }
        int samplesGap=delayMs*SAMPLE_RATE/1000;

        // just for internal testing !
        // force to 100 ms !
        //samplesGap=4410;
        if ( delayMs < 10 ) {
            samplesGap=delayMs;
        }
        statsOnByteBuffer("Initial buffer",arInit);
        displayBuffer("DISPLAY ARINIT",arInit,0, arInit.capacity());
        // Samples the initial buffer (1 out of 2)
        // after insertion of the duplicated sample, size will then be equal to the initial size
        // Arf !!!!!!
        ByteBuffer ar=ByteBuffer.allocateDirect(arInit.capacity());
        for (int i=0; i<ar.capacity();i+=4) {
            ar.putShort((short)(arInit.getShort(i)));
        }
        //statsOnByteBuffer("Reduced buffer",ar);
        displayBuffer("DISPLAY AR",ar,0, ar.capacity());

        ByteBuffer arn = ByteBuffer.allocateDirect(arInit.capacity());
        int sampleTotal=ar.capacity()/2;
        //MyLog.d("MAIN", "applyEffect samplesGap=" + samplesGap + " arn.capacity=" + arn.capacity() + " sampleTotal=" + sampleTotal);
        // here we manipulate samples : shorts, not bytes !!!!!
        copyBuffer("Init",ar,arn,0,samplesGap,1.0f);
        for (int sampleIdx = samplesGap; sampleIdx < sampleTotal; sampleIdx+=samplesGap) {
            int copyIdx;
            copyIdx=(sampleIdx - samplesGap);
            copyBuffer("Echo",ar,arn,copyIdx,samplesGap,1.0f);
            copyIdx=sampleIdx;
            copyBuffer("Current",ar,arn,copyIdx,samplesGap,1.0f);
        }
        copyBuffer("Last",ar,arn,sampleTotal-samplesGap,samplesGap,1.0f);
        //checkBuffer(arn,samplesGap,1.0f);
        //statsOnByteBuffer("ARN stats",arn);
        arn.rewind();
        displayBuffer("DISPLAY ARN",arn,0,arn.capacity());
        return(arn);

        //ByteBuffer ar2=ByteBuffer.allocateDirect(arInit.capacity());
        //copyBuffer("AR2",ar,ar2,0,ar2.capacity(),1.0f);
        //ar2.position(ar2.capacity()/2);
        //copyBuffer("AR2",ar,ar2,0,ar2.capacity(),1.0f);
        //ar2.rewind();
        //displayBuffer("AR2", ar2,0,ar2.capacity());
        //displayBuffer("AR2 + xxx", ar2,ar2.capacity()/2,ar2.capacity());
        //return(ar2);
    }

    //-------------------------------------------------------------------------------------
    private void displayBuffer(String tag, ByteBuffer bb, int start, int count) {
        bb.rewind();
        //MyLog.d("DELAY",tag+" displayBuffer" );
        for (int i=start; i<(start+count);i+=2) {
            if ( Math.abs(bb.getShort(i)) > 0 ) {
            //System.out.printf("DELAY tag=%s i=%d val=%d\n",tag ,i ,bb.getShort(i));
            }
        }
        bb.rewind();
    }

    //-------------------------------------------------------------------------------------
    private void checkBuffer(ByteBuffer bb, int offset,float decay) {
        int errors=0;
        for (int i=0; i<bb.capacity()/2;i+=2) {
            if (bb.getShort(i) != (short)(decay*bb.getShort(i+offset))) {
                errors++;
            }
        }
        //MyLog.d("MAIN", "checkBuffers errors=" + errors);
    }

    //-------------------------------------------------------------------------------------
    public void statsOnByteBuffer(String tag,ByteBuffer bb) {
        int loud=0;
        //MyLog.d("MAIN"," statsOnByteBuffer " + tag + "bb capacity=" + bb.capacity() + " position=" + bb.position());
        for (int i=0;i<bb.capacity()/2;i++) {
            short val=bb.getShort(2*i);
            if (Math.abs((int)val) > SILENCE ) {
                loud++;
            }
        }
        bb.rewind();
        //MyLog.d("MAIN", "statsOnByteBuffer " + tag + " loud="+loud);
    }

}
