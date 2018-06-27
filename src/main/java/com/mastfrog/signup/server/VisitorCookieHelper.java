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

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import static com.mastfrog.acteur.headers.Headers.COOKIE_B;
import static com.mastfrog.acteur.headers.Headers.SET_COOKIE_B;
import com.mastfrog.crypto.PortableCrypto;
import static com.mastfrog.signup.server.SignupServer.GUICE_BINDING_HIT_COUNTER;
import static com.mastfrog.signup.server.SignupServer.GUICE_BINDING_LAUNCH_TIMESTAMP;
import com.mastfrog.util.strings.RandomStrings;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author Tim Boudreau
 */
public class VisitorCookieHelper extends Acteur {

    @Inject
    VisitorCookieHelper(PortableCrypto crypto, HttpEvent evt, RandomStrings strings, @Named(GUICE_BINDING_LAUNCH_TIMESTAMP) long launch,
            @Named(GUICE_BINDING_HIT_COUNTER) AtomicLong counter) {
        Cookie ck = findCookie("_v", evt);
        String cookieValue;
        VisitorCookie vc;
        if (ck != null) {
            cookieValue = crypto.decrypt(ck.value());
            vc = new VisitorCookie(cookieValue);
        } else {
            vc = new VisitorCookie(strings, counter.getAndIncrement(), launch, evt.remoteAddress());
            cookieValue = vc.toString();
            String withMac = crypto.encryptToString(cookieValue);
            DefaultCookie newCookie = new DefaultCookie("_v", withMac);
            newCookie.setMaxAge(60 * 60 * 24 * 14);
            newCookie.setSecure(true);
            newCookie.setHttpOnly(true);
            newCookie.setPath("/api");
            add(SET_COOKIE_B, newCookie);
        }
        next(vc);
    }

    private Cookie findCookie(String name, HttpEvent evt) {
        Cookie[] cookies = evt.header(COOKIE_B);
        if (cookies != null) {
            for (Cookie ck : cookies) {
                if (name.equals(ck.name())) {
                    return ck;
                }
            }
        }
        return null;
    }

}
