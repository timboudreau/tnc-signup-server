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
import com.mastfrog.giulius.DeploymentMode;
import com.mastfrog.settings.Settings;
import static com.mastfrog.signup.server.SignupServer.DEFAULT_REVOCATION_TOKEN_STAMP_SUBTRACT;
import static com.mastfrog.signup.server.SignupServer.GUICE_BINDING_ATOMIC_MOVES;
import static com.mastfrog.signup.server.SignupServer.GUICE_BINDING_LAUNCH_TIMESTAMP;
import static com.mastfrog.signup.server.SignupServer.GUICE_BINDING_STORAGE_DIR;
import static com.mastfrog.signup.server.SignupServer.GUICE_BINDING_TEMP_FOLDER;
import static com.mastfrog.signup.server.SignupServer.SETTINGS_KEY_REVOCATION_TOKEN_STAMP_SUBTRACT;
import com.mastfrog.signup.server.VisitorCookie;
import com.mastfrog.signup.server.token.TokenGenerator;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.RandomStrings;
import com.mastfrog.util.time.TimeUtil;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
public class Signups implements Iterable<Path> {

    private final Path fld;
    private final AtomicLong index = new AtomicLong();
    private final ObjectMapper mapper;
    private final Random random;
    private final RandomStrings strings;
    private final TokenGenerator tokConfig;
    private final long subtract;
    private static final int VER = 1;
    private static final char DELIM = '$';
    private final Path tmp;
    private boolean atomicMove;
    private final Path store;

    @Inject
    Signups(@Named(GUICE_BINDING_LAUNCH_TIMESTAMP) long launch, @Named(GUICE_BINDING_STORAGE_DIR) java.nio.file.Path store,
            ObjectMapper mapper, Random random, RandomStrings strings, TokenGenerator tokConfig, Settings settings,
            DeploymentMode mode, @Named(GUICE_BINDING_TEMP_FOLDER) Path tmp, @Named(GUICE_BINDING_ATOMIC_MOVES) boolean atomicMoves) throws IOException {
        this.store = store;
        java.nio.file.Path sess = store.resolve("sessions/" + launch);
        if (!Files.exists(sess)) {
            Files.createDirectories(sess);
        }
        assert Files.exists(sess) && Files.isDirectory(sess);
        this.fld = sess;
        this.mapper = mapper;
        this.random = random;
        this.strings = strings;
        this.tokConfig = tokConfig;
        this.subtract = settings.getLong(SETTINGS_KEY_REVOCATION_TOKEN_STAMP_SUBTRACT,
                DEFAULT_REVOCATION_TOKEN_STAMP_SUBTRACT);
        atomicMove = atomicMoves;
        this.tmp = tmp;
    }

    public Path add(SignupInfo info, VisitorCookie vk, HttpEvent evt) throws IOException {
        CharSequence ua = evt.header(Headers.USER_AGENT);
        long now = System.currentTimeMillis();
        Signup signup = new Signup(info, now, vk, ua == null ? "none" : ua.toString(),
                false, false, newRevocationToken(now));

        String name = TimeUtil.toSortableStringFormat(TimeUtil.fromUnixTimestamp(now)) 
                + "-" + index.getAndIncrement() + ".signup";

        return saveFile(name, signup);
    }

    private Path saveFile(String name, Signup signup) throws IOException {
        Path nue = fld.resolve(name);
        if (atomicMove) {
            Path tempNue = tmp.resolve(name);
            saveTo(tempNue, signup);
            Files.move(tempNue, nue, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
        } else {
            saveTo(nue, signup);
        }
        return nue;
    }

    private void saveTo(Path nue, Signup signup) throws IOException {
        try (OutputStream out = Files.newOutputStream(nue, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            mapper.writeValue(out, signup);
        }
    }

    private String newRevocationToken(long now) {
        long prime = tokConfig.randomPrime();
        long val = random.nextLong();
        String prefix = Long.toString((now * prime * val) - subtract, 36);
        return prefix + DELIM + strings.get(20) + DELIM + VER;
    }

    @Override
    public Iterator<Path> iterator() {
        try {
            Stream<Path> paths = Files.find(store, 4, (pth, attrs) -> {
                return pth.toString().endsWith(".signup") || attrs.isDirectory();
            }).filter(pth -> {
                return !Files.isDirectory(pth);
            });
            return paths.iterator();
        } catch (IOException ex) {
            return Exceptions.chuck(ex);
        }
    }

    public Iterator<Path> iterator(Predicate<Signup> pred) {
        try {
            Stream<Path> paths = Files.find(store, 4, (pth, attrs) -> {
                return pth.toString().endsWith(".signup") || attrs.isDirectory();
            }).filter(pth -> {
                return !Files.isDirectory(pth);
            }).filter(pth -> {
                try {
                    if (Files.size(pth) == 0) {
                        return false;
                    }
                    return pred.test(mapper.readValue(Files.readAllBytes(pth), Signup.class));
                } catch (IOException ex) {
                    return Exceptions.chuck(ex);
                }
            });
            return paths.iterator();
        } catch (IOException ex) {
            return Exceptions.chuck(ex);
        }
    }

}
