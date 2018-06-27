package com.mastfrog.signup.server.token;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import javax.inject.Inject;
import javax.inject.Singleton;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static com.mastfrog.signup.server.token.TokensConfig.TOKEN_BYTES_LENGTH;

/**
 * <p>
 * A token consists of a base-64 encoded string encoding 36 bytes of data, including timestamp information, per-system
 * random elements, per-instance random elements, and a random number which must be divisible by at least two of the
 * prime numbers the system is configured with, after subtracting a shared value, the sequence number and the last byte
 * of per-instance random data from the value. The timestamp is an offset, modulo 15 seconds, which can be recovered by
 * adding the base timestamp to the 6-byte value sent. The timestamp should be in the past, and if in the future, by no
 * more than a configurable skew.
 * <p>
 * All this is just a fast way to reject obviously fake tokens with minimal work - it does not consititute a meaningful
 * security measure, since a valid token is obtainable if you have logged in.
 *
 * @author Tim Boudreau
 */
@Singleton
public class Tokens {

    private static final long LONG_MASK = 0x00000000ffffffffL;
    private final TokensConfig config;

    @Inject
    public Tokens(TokensConfig config) {
        this.config = config;
    }

    /**
     * Extract the timestamp from the token. Note that a new token's timestamp may be up to 15 seconds in the past - we
     * store it as 6 bytes, taking the current timestamp, subtracting a base timestamp of <i>Sat, 19 Aug 2017 02:26:24
     * -04:00<i>, and dividing the result by 15000.
     *
     * @param tok The token
     * @return The timestamp as millis epoch, or 0 if the token is invalid.
     */
    public long extractTimestamp(String tok) {
        try {
            byte[] bytes = Base64.getDecoder().decode( tok );
            return extractTimestamp( bytes );
        } catch ( IllegalArgumentException ex ) {
            return 0;
        }
    }

    public long extractTimestamp(byte[] bytes) {
        if ( bytes.length < TOKEN_BYTES_LENGTH ) {
            return 0;
        }
        long timeOffset = extractOffset( bytes );
        return ( timeOffset * config.timeOffsetDivisor() ) + config.baseTimestamp();
    }

    static long extractOffset(byte[] bytes) {
        byte[] b = new byte[8];
        System.arraycopy( bytes, 28, b, 0, 6 );
        b[6] = 0;
        b[7] = 0;
        long result = ByteBuffer.wrap( b ).order( LITTLE_ENDIAN ).getLong();
//        logHex( "EXTRACTED as " + result, bytes, 28, 6 );
        return result;
    }

    /**
     * Determine if a token is legal.
     *
     * @param tok The token
     * @return True if it is valid according to our rules
     */
    public boolean isValid(String tok) {
        try {
            byte[] bytes = Base64.getDecoder().decode( tok );
            if ( bytes.length < TOKEN_BYTES_LENGTH ) {
                return false;
            }
            ByteBuffer buf = ByteBuffer.wrap( bytes ).order( ByteOrder.LITTLE_ENDIAN );
            long multiplierLsdw = buf.getInt( 0 ) & LONG_MASK;
            int seq = buf.getInt( 4 );
            ByteBuffer ihBytes = ByteBuffer.wrap( new byte[]{0, bytes[8], bytes[9], bytes[10]} ).order(
                    ByteOrder.LITTLE_ENDIAN );
            int ih = ihBytes.getInt( 0 );
            long multiplierMsdw = buf.getInt( 11 ) & LONG_MASK;

//            long timeOffset = extractOffset( bytes );
            long timestamp = extractTimestamp( bytes );
            int thirdRand = (int) bytes[27] & 0xFF;
            long multiplied = multiplierLsdw | ( multiplierMsdw << 32 );
            multiplied -= config.primesAdd() + thirdRand + seq;
            int divCount = config.divisibleCount( multiplied );
            long skew = timestamp - config.currentTimeMillis();
            boolean timestampTooOld = timestamp < config.baseTimestamp();
            boolean divisibleBy91 = ih % 91 == 0;
            boolean skewOk = skew < config.maxDateFutureSkew();
            boolean containsOurPrimes = divCount >= 2;
            return divisibleBy91 && skewOk && containsOurPrimes && !timestampTooOld;
        } catch ( IllegalArgumentException ex ) { // Invalid base64
            return false;
        }
    }
}
