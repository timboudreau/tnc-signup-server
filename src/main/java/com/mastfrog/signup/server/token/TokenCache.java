/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.signup.server.token;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.mastfrog.settings.Settings;
import static com.mastfrog.signup.server.SignupServer.SETTINGS_KEY_CACHE_MINUTES;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
public class TokenCache {

    private final Cache<String, String> cache;
    private final Cache<String, String> used;
    private final TokenGenerator gen;
    private final Tokens tokens;
    private final Duration tokenMaxAge;

    @Inject
    TokenCache(TokenGenerator gen, Settings settings, Tokens tokens) {
        tokenMaxAge = Duration.ofMinutes(settings.getInt(SETTINGS_KEY_CACHE_MINUTES, 10));
        this.cache = CacheBuilder.newBuilder().concurrencyLevel(5)
                .expireAfterAccess(tokenMaxAge.toMillis(), TimeUnit.MILLISECONDS).build();
        this.used = CacheBuilder.newBuilder().concurrencyLevel(5)
                .expireAfterAccess(Duration.ofDays(2).toMillis(), TimeUnit.MILLISECONDS).build();
        this.gen = gen;
        this.tokens = tokens;
    }

    public String newToken() {
        String result = gen.newToken();
        cache.put(result, result);
        return result;
    }
    
    public void onTokenUsed(String token) {
        used.put(token, token);
    }

    public boolean isUsed(String token) {
        return used.getIfPresent(token) != null;
    }

    public boolean isValid(String token) {
        boolean result = this.tokens.isValid(token);
        if (result) {
            long issued = tokens.extractTimestamp(token);
            Duration age = Duration.ofMillis(System.currentTimeMillis() - issued);
            result = age.toMillis() <= tokenMaxAge.toMillis();
            if (result) {
                result = cache.getIfPresent(token) != null;
            }
        }
        return result;
    }
}
