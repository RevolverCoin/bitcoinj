package org.revolvercoin.crypto;

import com.hashengineering.crypto.Sha512Hash;
import org.bitcoinj.core.Sha256Hash;

import fr.cryptohash.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Set;



/**
 * Created by Hash Engineering on 4/24/14 for the X11 algorithm
 */
public class X11Evo {

    public static class Solution {
        public static void nextPermutation(int[] num) {
            int i = num.length - 2;
            for(; i >= 0 && num[i] >= num[i+1]; i--)
                ;

            if(i >= 0) {
                int j = i + 1;
                for(; j<num.length && num[i] < num[j]; j++)
                    ;
                exchange(num, i, j-1);
            }

            i ++ ;
            int k = num.length - 1;
            for(; i<k; i++, k--)
                exchange(num, i, k);
        }

        private static void exchange(int[] num, int i, int j) {
            int t = num[i];
            num[i] = num[j];
            num[j] = t;
        }
    }


    private static final Logger log = LoggerFactory.getLogger(X11Evo.class);
    private static boolean native_library_loaded = false;

    static {

        try {
            log.info("Loading x11 native library...");
            System.loadLibrary("x11");
            native_library_loaded = true;
            log.info("Loaded x11 successfully.");
        }
        catch(UnsatisfiedLinkError x)
        {
            log.info("Loading x11 failed: " + x.getMessage());
        }
        catch(Exception e)
        {
            native_library_loaded = false;
            log.info("Loading x11 failed: " + e.getMessage());
        }
    }

    public static byte[] x11Digest(byte[] input, int offset, int length, long time)
    {
        byte [] buf = new byte[length];
        for(int i = 0; i < length; ++i)
        {
            buf[i] = input[offset + i];
        }
        return x11Digest(buf, time);
    }

    public static byte[] x11Digest(byte[] input, long time) {
        //long start = System.currentTimeMillis();
        try {
            return native_library_loaded ? x11_native(input) : x11evo(input, time);
            /*long start = System.currentTimeMillis();
            byte [] result = x11_native(input);
            long end1 = System.currentTimeMillis();
            byte [] result2 = x11(input);
            long end2 = System.currentTimeMillis();
            log.info("x11: native {} / java {}", end1-start, end2-end1);
            return result;*/
        } catch (Exception e) {
            return null;
        }
        finally {
            //long time = System.currentTimeMillis()-start;
            //log.info("X11 Hash time: {} ms per block", time);
        }
    }

    static native byte [] x11_native(byte [] input);


    enum Algos {
            BLAKE,
            BMW,
            GROESTL,
            SKEIN,
            JH,
            KECCAK,
            LUFFA,
            CUBEHASH,
            SHAVITE,
            SIMD,
            ECHO
    };

    // 012345

    static long getTimeIndex(long current_time, long base_time){
        return (current_time - base_time) / (60 * 60 * 24);
    }
    static byte [] x11evo(byte header[], long time)
    {
        // get sequence
        long index = getTimeIndex(time,1462060800);
        if (index < 0) index = 0;

        // initial array
        int[] num = new int[Algos.values().length];
        for (int i=0; i<num.length; i++) {
            num[i] = i;
        }

        // do permutations
        for (int i=0; i<index;i++) {
            Solution.nextPermutation(num);
        }

        //Initialize
        BLAKE512 blake512 = new BLAKE512();
        BMW512 bmw = new BMW512();
        Groestl512 groestl = new Groestl512();
        Skein512 skein = new Skein512();
        JH512 jh = new JH512();
        Keccak512 keccak = new Keccak512();
        Luffa512 luffa = new Luffa512();
        CubeHash512 cubehash = new CubeHash512();
        SHAvite512 shavite = new SHAvite512();
        SIMD512 simd = new SIMD512();
        ECHO512 echo = new ECHO512();


        Sha512Hash hash = null;

        for (int i : num) {

            Algos algo = Algos.values()[i];

            byte[] prevHash = header;
            if (hash != null) {
                prevHash = hash.getBytes();
            }

            switch (algo) {
                case BLAKE:     hash = new Sha512Hash(blake512.digest(prevHash)); break;
                case BMW:       hash = new Sha512Hash(bmw.digest(prevHash)); break;
                case GROESTL:   hash = new Sha512Hash(groestl.digest(prevHash)); break;
                case SKEIN:     hash = new Sha512Hash(skein.digest(prevHash)); break;
                case JH:        hash = new Sha512Hash(jh.digest(prevHash)); break;
                case KECCAK:    hash = new Sha512Hash(keccak.digest(prevHash)); break;
                case LUFFA:     hash = new Sha512Hash(luffa.digest(prevHash)); break;
                case CUBEHASH:  hash = new Sha512Hash(cubehash.digest(prevHash)); break;
                case SHAVITE:   hash = new Sha512Hash(shavite.digest(prevHash)); break;
                case SIMD:      hash = new Sha512Hash(simd.digest(prevHash)); break;
                case ECHO:      hash = new Sha512Hash(echo.digest(prevHash)); break;
            }
        }

        if (hash == null)
            return null;

        return hash.trim256().getBytes();
    }
}