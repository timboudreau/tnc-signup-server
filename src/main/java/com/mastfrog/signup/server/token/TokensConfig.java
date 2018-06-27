package com.mastfrog.signup.server.token;

import com.mastfrog.giulius.DeploymentMode;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.ConfigurationError;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.time.TimeUtil;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Configuration for auth token pre-validation, used to avoid a server round trip for obvious garbage. This needs to be
 * configured with the same values as the javascript configuration for the auth server - specifically, the list of
 * primes, the offset to add and the base timestamp must match for validation to work.
 *
 * @author Tim Boudreau
 */
@Singleton
final class TokensConfig {

    private static final long DEFAULT_BASE_TS = 1503123984836L;
    private static final long DEFAULT_TIME_OFFSET_DIVISOR = 15000;
    private static final long DEFAULT_PRIMES_ADD = 23829901;
    private static final long DEFAULT_MAX_DATE_FUTURE_SKEW = 60 * 1000 * 10; // ten minutes
    private static final String DEFAULT_PRIMES = "16657,17291,22259,36919,39191";

    public static final String SETTINGS_KEY_BASE_TS = "token.base.timestamp";
    public static final String SETTINGS_KEY_PRIMES_ADD = "token.primes.offset";
    public static final String SETTINGS_KEY_MAX_DATE_FUTURE_SKEW = "token.max.data.future.skew";
    public static final String SETTINGS_KEY_TOKEN_TIME_OFFSET_DIVISOR = "token.time.offset.divisor";
    public static final String SETTINGS_KEY_TOKEN_PRIMES_LIST = "token.primes";

    private final AtomicLong counter = new AtomicLong( 1 );
    private final long baseTimestamp;
    private final long timeOffsetDivisor;
    private final long primesAdd;
    private final long maxDateFutureSkew;
    private final long[] primes;
    private final Random random;
    private final byte[] processSpecific;
    private final int networkInterfacesHash;
    private final int pid;
    private final long fixedTimestamp;
    private final long creationTimestamp = System.currentTimeMillis();
    public static final int TOKEN_BYTES_LENGTH = 36;

    @Inject
    TokensConfig(Settings settings, DeploymentMode mode, Random random) throws SocketException {
        baseTimestamp = settings.getLong( SETTINGS_KEY_BASE_TS, DEFAULT_BASE_TS );
        timeOffsetDivisor = settings.getLong( SETTINGS_KEY_TOKEN_TIME_OFFSET_DIVISOR, DEFAULT_TIME_OFFSET_DIVISOR );
        primesAdd = settings.getLong( SETTINGS_KEY_PRIMES_ADD, DEFAULT_PRIMES_ADD );
        maxDateFutureSkew = settings.getLong( SETTINGS_KEY_MAX_DATE_FUTURE_SKEW, DEFAULT_MAX_DATE_FUTURE_SKEW );
        fixedTimestamp = settings.getLong( "tokens.fixed.timestamp", 0 ); // for tests
        if ( mode.isProduction() && fixedTimestamp != 0 ) {
            throw new ConfigurationError( "Fixed timestamp for tokens set to " + TimeUtil.toHttpHeaderFormat(
                    TimeUtil.fromUnixTimestamp( fixedTimestamp ) ) + " - this is for tests, but started in " + mode );
        }
        this.random = random;
        processSpecific = new byte[4];
        networkInterfacesHash = _interfacesHash();
        random.nextBytes( processSpecific );
        pid = Integer.parseInt( ManagementFactory.getRuntimeMXBean().getName().split( "@" )[0] );
        String primeList = settings.getString( SETTINGS_KEY_TOKEN_PRIMES_LIST, DEFAULT_PRIMES );
        if ( mode == DeploymentMode.PRODUCTION && DEFAULT_PRIMES.equals( primeList ) ) {
            throw new ConfigurationError( "Will not use default primes in production mode." );
        }
        String[] ps = primeList.split( "," );
        primes = new long[ps.length];
        for ( int i = 0; i < ps.length; i++ ) {
            try {
                primes[i] = Long.parseLong( ps[i].trim() );
            } catch ( NumberFormatException ex ) {
                throw new ConfigurationError( "Not a valid long at index " + i + " '" + ps[i] + " in primes list "
                        + primeList );
            }
        }
    }

    public ZonedDateTime currentDateTime() {
        return TimeUtil.fromUnixTimestamp( currentTimeMillis() );
    }

    public Date currentDate() {
        return new Date( currentTimeMillis() );
    }

    public long currentTimeMillis() {
        return fixedTimestamp != 0 ? fixedTimestamp + ( System.currentTimeMillis() - creationTimestamp ) : System
                .currentTimeMillis();
    }

    public int randomInt() {
        return random.nextInt();
    }

    public long randomLong() {
        return random.nextLong();
    }

    public short pid() {
        return (short) pid;
    }

    public int interfacesHash() {
        return networkInterfacesHash;
    }

    private static int _interfacesHash() throws SocketException {
        int result = 91;
        for ( NetworkInterface iface : CollectionUtils.toIterable( NetworkInterface.getNetworkInterfaces() ) ) {
            if (iface == null) {
                // OSX w/ docker can do this
                continue;
            }
            if (iface.isLoopback() || iface.isVirtual()) {
                continue;
            }
            try {
                byte[] hwaddress = iface.getHardwareAddress();
                if ( hwaddress == null ) {
                    continue;
                }
                for ( int i = 0; i < hwaddress.length; i++ ) {
                    int val = hwaddress[i] & 0xFF;
                    result += 91 * val;
                }
            } catch ( SocketException ex ) {
                new IOException("Could not determine hardware address for interface "
                        + iface.getDisplayName() + " (" + iface.getName() + ")").printStackTrace();
            }
        }
        return result;
    }

    public long counter() {
        return counter.incrementAndGet();
    }

    byte[] processSpecific() {
        return processSpecific;
    }

    int processSpecificAsInt() {
        return ByteBuffer.wrap( processSpecific() ).asIntBuffer().get();
    }

    public long[] pickPrimes(int count) {
        if ( count > primes.length ) {
            throw new IllegalArgumentException( "Asked for " + count + " primes but only have " + primes.length );
        }
        if ( count <= 0 ) {
            throw new IllegalArgumentException( "Count must be > 0" );
        }
        long[] pickFrom = new long[primes.length];
        System.arraycopy( primes, 0, pickFrom, 0, primes.length );
        shuffle( pickFrom );
        long[] result = new long[count];
        for ( int i = 0; i < count; i++ ) {
            result[i] = pickFrom[i];
        }
        return result;
    }

    public byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        random.nextBytes( bytes );
        return bytes;
    }

    private int[] randomIntArray(int length, int max) {
        byte[] b = new byte[length * 4];
        random.nextBytes( b );
        IntBuffer ib = ByteBuffer.wrap( b ).asIntBuffer();
        int[] ints = new int[length];
        for ( int i = 0; i < length; i++ ) {
            ints[i] = Math.abs( ib.get() % max );
        }
        return ints;
    }

    private long[] shuffle(long[] array) {
        // fisher yates shuffle
        int[] ints = randomIntArray( array.length, array.length );
        for ( int i = array.length - 1; i > 0; i -= 1 ) {
            int j = ints[i] % array.length;
            long temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
        return array;
    }

    public long baseTimestamp() {
        return baseTimestamp;
    }

    public long timeOffsetDivisor() {
        return timeOffsetDivisor;
    }

    public long primesAdd() {
        return primesAdd;
    }

    public long maxDateFutureSkew() {
        return maxDateFutureSkew;
    }

    public long[] primes() {
        return primes;
    }

    public int divisibleCount(long num) {
        int result = 0;
        for ( int i = 0; i < primes.length; i++ ) {
            if ( num % primes[i] == 0 ) {
                result++;
            }
        }
        return result;
    }
}
