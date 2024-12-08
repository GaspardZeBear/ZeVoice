package com.gzb.zevoice;

import static com.gzb.zevoice.MainActivity.*;

import android.util.Log;

import java.nio.ByteBuffer;

public class Delay {
    ByteBuffer arInit;
    int delayMs;
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
                Log.d("TEST"," i=" + i + " bb0=" + bb0.getShort(i) + " bb1=" + bb1.getShort(i));
            }
    }

    //-------------------------------------------------------------------------------------
    private void copyBuffer(String tag, ByteBuffer src, ByteBuffer dest, int start, int count, float factor) {
        Log.d("Effect", tag + " start=" + start
                + " count=" + count
                + " src.capacity=" + src.capacity()
                + " src.position()=" + src.position()
                + " dest.capacity()=" + dest.capacity()
                + " dest.position()=" + dest.position());
        //if ((src.capacity() - src.position()) < 2 * count) {
        //    Log.d("Effect", tag + " src cannot get count elements");
        //    return;
        //}
        //if ((dest.capacity() - dest.position()) < 2 * count) {
        //    Log.d("Effect", tag + " dest cannot accept elements");
        //    return;
        //}

        // hre we have to convert samples to bytes !!!!!
        int srcIdx=2*start;
        int srcIdxMax=srcIdx+2*count;
        if ( srcIdxMax > src.capacity() ) {
            srcIdxMax=src.capacity();
        }
        Log.d("Effect","srcIdx="+ srcIdx + " srcIdxMax=" + srcIdxMax );
        while ( srcIdx < srcIdxMax) {
            if ( (dest.capacity() - dest.position()) < 2 ) {
                Log.d("Effect"," Break !!! dest.capacity()="+ dest.capacity() + " dest.position()=" + dest.position() );
                break;
            }
            //Log.d("Effect", tag + " srcIdx=" + srcIdx + " src=" + src.getShort(srcIdx));
            dest.putShort((short) (factor * src.getShort(srcIdx)));
            srcIdx+=2;
        }
        Log.d("Effect", tag + " After copy dest.position=" + dest.position());
    }

    //-------------------------------------------------------------------------------------
    //public ByteBuffer applyEffect(int delayMs, ByteBuffer arInit) {
    public ByteBuffer applyEffect() {
        Log.d("MAIN", "applyEffect delay=" + delayMs);
        if (delayMs  <2 || delayMs > SAMPLE_RATE/100 ) {
            //Log.d("MAIN", "applyEffect delayMs out of range");
            return(arInit);
        }
        int samplesGap=delayMs*SAMPLE_RATE/1000;

        // just for internal testing !
        // force to 100 ms !
        samplesGap=4410;
        if ( delayMs < 10 ) {
            samplesGap=delayMs;
        }
        statsOnByteBuffer("Initial buffer",arInit);
        // Samples the initial buffer (1 out of 2)
        // after insertion of the duplicated sample, size will then be equal to the initial size
        // Arf !!!!!!
        ByteBuffer ar=ByteBuffer.allocateDirect(arInit.capacity()/2);
        for (int i=0; i<ar.capacity();i+=4) {
            ar.putShort((short)(arInit.getShort(i)));
        }
        statsOnByteBuffer("Reduced buffer",ar);

        ByteBuffer arn = ByteBuffer.allocateDirect(arInit.capacity());

        int sampleTotal=ar.capacity()/2;
        Log.d("MAIN", "applyEffect samplesGap=" + samplesGap + " arn.capacity=" + arn.capacity() + " sampleTotal=" + sampleTotal);
        // here we manipulate samples : shorts, not bytes !!!!!
        copyBuffer("Init",ar,arn,0,samplesGap,0.5f);
        for (int sampleIdx = samplesGap; sampleIdx < sampleTotal; sampleIdx+=samplesGap) {
            int copyIdx;
            copyIdx=(sampleIdx - samplesGap);
            copyBuffer("Echo",ar,arn,copyIdx,samplesGap,0.5f);
            copyIdx=sampleIdx;
            copyBuffer("Current",ar,arn,copyIdx,samplesGap,0.5f);
        }
        copyBuffer("Last",ar,arn,sampleTotal-samplesGap,samplesGap,0.5f);
        checkBuffer(arn,samplesGap);
        statsOnByteBuffer("ARN stats",arn);
        arn.rewind();
        //return(arn);

        ByteBuffer ar2=ByteBuffer.allocateDirect(arInit.capacity());
        copyBuffer("x2",ar,ar2,0,ar.capacity()/2,1.0f);
        copyBuffer("x2",ar,ar2,0,ar.capacity()/2,1.0f);
        ar2.rewind();
        return(ar2);
    }


    //-------------------------------------------------------------------------------------
    private void checkBuffer(ByteBuffer bb, int offset) {
        int errors=0;
        for (int i=0; i<bb.capacity()/2;i+=2) {
            if (bb.getShort(i) != bb.getShort(i+offset)) {
                errors++;
            }
        }
        Log.d("MAIN", "checkBuffers errors=" + errors);
    }

    //-------------------------------------------------------------------------------------
    public void statsOnByteBuffer(String tag,ByteBuffer bb) {
        int loud=0;
        Log.d("MAIN"," statsOnByteBuffer " + tag + "bb capacity=" + bb.capacity() + " position=" + bb.position());
        for (int i=0;i<bb.capacity()/2;i++) {
            short val=bb.getShort(2*i);
            if (Math.abs((int)val) > SILENCE ) {
                loud++;
            }
        }
        bb.rewind();
        Log.d("MAIN", "statsOnByteBuffer " + tag + " loud="+loud);
    }

}
