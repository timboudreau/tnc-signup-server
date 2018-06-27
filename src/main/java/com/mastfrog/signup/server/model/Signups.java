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
package com.mastfrog.signup.server.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.signup.server.SignupServer.GUICE_BINDING_LAUNCH_TIMESTAMP;
import static com.mastfrog.signup.server.SignupServer.GUICE_BINDING_STORAGE_DIR;
import com.mastfrog.signup.server.VisitorCookie;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
public class Signups {

    private final Path fld;
    private final AtomicLong index = new AtomicLong();
    private final ObjectMapper mapper;

    @Inject
    Signups(@Named(GUICE_BINDING_LAUNCH_TIMESTAMP) long launch, @Named(GUICE_BINDING_STORAGE_DIR) java.nio.file.Path store, ObjectMapper mapper) throws IOException {
        java.nio.file.Path sess = store.resolve("sessions/" + launch);
        if (!Files.exists(sess)) {
            Files.createDirectories(sess);
        }
        assert Files.exists(sess) && Files.isDirectory(sess);
        this.fld = sess;
        this.mapper = mapper;
    }

    public Path add(SignupInfo info, VisitorCookie vk, HttpEvent evt) throws IOException {
        CharSequence ua = evt.header(Headers.USER_AGENT);
        long now = System.currentTimeMillis();
        Signup signup = new Signup(info, now, vk, ua == null ? "none" : ua.toString(), false, false);

        Path nue = fld.resolve(now + "-" + index.getAndIncrement() + ".signup");
        try (OutputStream out = Files.newOutputStream(nue, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            mapper.writeValue(out, signup);
        }
        return nue;
    }
}
