package com.mastfrog.signup.server.token;

import com.mastfrog.giulius.DeploymentMode;
import com.mastfrog.settings.SettingsBuilder;
import static com.mastfrog.signup.server.token.TokensConfig.TOKEN_BYTES_LENGTH;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Base64;
import java.util.Random;
import javax.inject.Inject;


/**
 *
 * @author Tim Boudreau
 */
public class TokenGenerator {

    private final TokensConfig config;

    @Inject
    TokenGenerator(TokensConfig config) {
        this.config = config;
    }

    public long randomPrime() {
        return config.randomPrime();
    }

    private byte[] toThreeByteArray(int val) {
        byte[] result = new byte[4];
        ByteBuffer.wrap( result ).asIntBuffer().put( val );
        return new byte[]{result[3], result[2], result[1]};
    }

    private int leastSignificantDWord(long l) {
        return (int) l;
    }

    private int mostSignificantDWord(long l) {
        return (int) ( l >> 32 );
    }

    public String newToken() {
        byte[] result = new byte[TOKEN_BYTES_LENGTH];
        ByteBuffer parts = ByteBuffer.wrap( result ).order( ByteOrder.LITTLE_ENDIAN );
        IntBuffer randomInts = ByteBuffer.wrap( config.randomBytes( 16 )).asIntBuffer();
        int firstInt = randomInts.get();
        int secondInt = randomInts.get();
        int thirdInt = Math.abs(randomInts.get() % 256);
        long[] primes = config.pickPrimes( 2 );

        long multiplier = Math.abs( randomInts.get() );
//        long multiplier = Math.abs(config.randomLong());
        for ( int i = 0; i < primes.length; i++ ) {
            multiplier *= primes[i];
        }
        long counter = config.counter();
        multiplier += config.primesAdd() + counter + ( thirdInt & 0xFF );

        int multiplierLow = leastSignificantDWord( multiplier );
        // bytes 0-3, least significant dword of our random number multiplied by two of our
        // set of primes
        parts.putInt( multiplierLow );
        // bytes 4-7, a counter
        parts.putInt( (int) ( counter % 2147483647 ) );

        // bytes 8-11, a hash of the mac addresses of all network interfaces
        // on this machine.  The algorithm guarantees the result fits in 3 bytes and is
        // divisible by 91
        parts.put( toThreeByteArray( config.interfacesHash() ) );
        int multiplierHigh = mostSignificantDWord( multiplier );
        // bytes 12-16, the most significant dword of the random number multiplied by
        // two of our primes
        parts.putInt( multiplierHigh );
        // A random sequence of four bytes that identifies this process
        parts.putInt( config.processSpecificAsInt() );

        // 9 random bytes, one of which was incorporated into the final multiplier value
        // bytes 17-26
        parts.putInt( firstInt );
        parts.putInt( secondInt );
        parts.put( (byte) thirdInt );

        // Take the current timestamp, subtract our base timestamp, and divide by 15000
        long timeOffset = ( ( config.currentTimeMillis() - config.baseTimestamp() ) / config.timeOffsetDivisor() );
        // Write 6 bytes of it
        result[28] = (byte) timeOffset;
        result[29] = (byte) (timeOffset >> 8);
        result[30] = (byte) (timeOffset >> 16);
        result[31] = (byte) (timeOffset >> 24);
        result[32] = (byte) (timeOffset >> 32);
        result[33] = (byte) (timeOffset >> 40);

        Tokens.extractOffset( result);


        parts.position( 34 );
        // bytes 34-35, the process pid
        parts.putShort( config.pid() );
        return Base64.getEncoder().encodeToString( result );
    }

    public static void main(String[] args) throws IOException {
        TokenGenerator gen = new TokenGenerator(new TokensConfig(new SettingsBuilder().build(), DeploymentMode.DEVELOPMENT,new Random()) );
        for ( int i = 0; i < 5; i++ ) {
            System.out.println( gen.newToken() );
        }
    }
}
