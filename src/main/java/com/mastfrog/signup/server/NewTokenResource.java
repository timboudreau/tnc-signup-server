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
package com.mastfrog.signup.server;

import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.annotations.HttpCall;
import com.mastfrog.acteur.annotations.Precursors;
import static com.mastfrog.acteur.headers.Headers.CACHE_CONTROL;
import static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE;
import static com.mastfrog.acteur.headers.Method.POST;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.bunyan.Log;
import com.mastfrog.bunyan.Logger;
import com.mastfrog.bunyan.type.Info;
import com.mastfrog.settings.Settings;
import com.mastfrog.signup.server.token.TokenCache;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Random;

/**
 *
 * @author Tim Boudreau
 */
@HttpCall
@Path("api/token")
@Methods(POST)
@Precursors(VisitorCookieHelper.class)
public class NewTokenResource extends Acteur {


    @Inject
    NewTokenResource(TokenCache gen, Random rnd, Settings settings, @Named("signup") Logger tokenLog, VisitorCookie cookie, HttpEvent evt) {
        String tok = gen.newToken();
        add(CACHE_CONTROL, CacheControl.PRIVATE_NO_CACHE_NO_STORE);
        add(CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.withCharset(StandardCharsets.US_ASCII));
        try (Log<Info> ilog = tokenLog.info("newtoken")) {
            if (settings.getBoolean("delay", true)) {
                long delay = 300 + rnd.nextInt(2500);
                response().delayedBy(Duration.ofMillis(delay));
            }
            ilog.add("tok", tok).add("visitor", cookie);
            if (evt.urlParameter("ix") != null) {
                ilog.add("index", evt.urlParameter("ix"));
            }
        }
        ok(tok);
    }
}
