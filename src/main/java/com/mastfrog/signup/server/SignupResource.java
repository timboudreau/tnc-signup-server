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
import com.mastfrog.acteur.annotations.HttpCall;
import com.mastfrog.acteur.annotations.Precursors;
import com.mastfrog.acteur.errors.Err;
import static com.mastfrog.acteur.headers.Headers.CACHE_CONTROL;
import static com.mastfrog.acteur.headers.Headers.SET_COOKIE_B;
import static com.mastfrog.acteur.headers.Method.POST;
import com.mastfrog.acteur.preconditions.InjectRequestBodyAs;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.PathRegex;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.bunyan.Logger;
import static com.mastfrog.signup.server.SignupServer.GUICE_BINDING_POSSIBLE_SIGNUPS;
import com.mastfrog.signup.server.model.SignupInfo;
import com.mastfrog.signup.server.model.Signups;
import com.mastfrog.signup.server.token.TokenCache;
import com.mastfrog.util.Strings;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import static io.netty.handler.codec.rtsp.RtspResponseStatuses.PAYMENT_REQUIRED;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.netbeans.validation.api.Problems;
import org.netbeans.validation.api.builtin.stringvalidation.StringValidators;

/**
 *
 * @author Tim Boudreau
 */
@HttpCall(order = 2, scopeTypes = VisitorCookie.class)
@Methods(POST)
@PathRegex("^api\\/signup$")
@Precursors(VisitorCookieHelper.class)
@InjectRequestBodyAs(SignupInfo.class)
public class SignupResource extends Acteur {

    @Inject
    SignupResource(HttpEvent evt, SignupInfo info, Signups signups, @Named("signup") Logger signupLog, TokenCache tokens, VisitorCookie cookie, @Named(GUICE_BINDING_POSSIBLE_SIGNUPS) Set<String> possibilities) throws IOException {
        Problems problems = new Problems();
        StringValidators.EMAIL_ADDRESS.validate(problems, "address", info.emailAddress);
        if (problems.hasFatal()) {
            reply(Err.badRequest(problems.getLeadProblem().getMessage()));
            return;
        }
        if (info.signedUpFor.isEmpty()) {
            reply(Err.badRequest("Nothing selected to sign up for."));
            return;
        }
        if (!tokens.isValid(info.token)) {
            reply(new Err(PAYMENT_REQUIRED, "Invalid token"));
            return;
        }
        if (!possibilities.containsAll(info.signedUpFor)) {
            Set<String> unknowns = new TreeSet<>(info.signedUpFor);
            unknowns.removeAll(possibilities);
            reply(Err.badRequest("Unknown categories: " + Strings.join(',', unknowns)));
        }
        Path file = signups.add(info, cookie, evt);
        signupLog.info("signup")
                .add("info", info)
                .add("visitor", cookie)
                .add("file", file.toString())
                .close();
        DefaultCookie ck = new DefaultCookie("tnc_e", "[" + Strings.join(",", info.signedUpFor) + "]" + info.emailAddress);
        ck.setMaxAge(60 * 60 * 24 * 800);
        ck.setHttpOnly(false);
        ck.setPath("/");
        add(CACHE_CONTROL, CacheControl.PRIVATE_NO_CACHE_NO_STORE);
        add(SET_COOKIE_B, ck);
        ok("Signed up " + info.emailAddress);
    }
}
