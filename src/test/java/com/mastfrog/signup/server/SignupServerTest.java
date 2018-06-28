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

import com.fasterxml.jackson.databind.ObjectMapper;
import static com.google.common.net.MediaType.JSON_UTF_8;
import com.google.inject.AbstractModule;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.mastfrog.acteur.annotations.GenericApplication;
import com.mastfrog.acteur.annotations.GenericApplicationModule;
import static com.mastfrog.acteur.headers.Headers.CONTENT_DISPOSITION;
import static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE;
import static com.mastfrog.acteur.headers.Headers.COOKIE_B;
import static com.mastfrog.acteur.headers.Headers.SET_COOKIE_B;
import com.mastfrog.bunyan.LoggingModule;
import com.mastfrog.crypto.PortableCrypto;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarness.CallResult;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import static com.mastfrog.signup.server.SignupServer.DEFAULT_POSSIBLE_SIGNUPS;
import static com.mastfrog.signup.server.SignupServer.GUICE_BINDING_ATOMIC_MOVES;
import static com.mastfrog.signup.server.SignupServer.GUICE_BINDING_STORAGE_DIR;
import static com.mastfrog.signup.server.SignupServer.SETTINGS_KEY_ADMIN_NAME;
import static com.mastfrog.signup.server.SignupServer.SETTINGS_KEY_ADMIN_PASSWORD;
import static com.mastfrog.signup.server.SignupServer.SETTINGS_KEY_POSSIBLE_SIGNUPS;
import static com.mastfrog.signup.server.SignupServer.SETTINGS_KEY_STORAGE_DIR;
import com.mastfrog.signup.server.SignupServerTest.TestSignupModule;
import com.mastfrog.signup.server.model.Signup;
import com.mastfrog.signup.server.model.SignupInfo;
import com.mastfrog.util.Exceptions;
import static com.mastfrog.util.collections.CollectionUtils.setOf;
import static io.netty.handler.codec.http.HttpResponseStatus.CONFLICT;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith(TestSignupModule.class)
public class SignupServerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(4);

    static {
//        System.setProperty("acteur.debug", "true");
    }

    @Test
    public void testSomeMethod(TestHarness harn, PortableCrypto crypto, @Named(GUICE_BINDING_STORAGE_DIR) java.nio.file.Path store, ObjectMapper mapper) throws Throwable {
        CallResult res = harn.post("/api/token")
                .addQueryPair("ix", "23")
                .setTimeout(TIMEOUT)
                .go()
                .await()
                .assertStatus(OK)
                .assertHasHeader(SET_COOKIE_B)
                .assertHasCookie("_v");
        String tokn = res.content();
        assertNotNull(tokn);
        String ck = res.getCookieValue("_v");
        VisitorCookie vc = new VisitorCookie(crypto.decrypt(ck));
        SignupInfo signup = new SignupInfo("foo@bar.com", setOf("community", "invest"), tokn, "Boo Goo");
        String resp = harn.post("/api/signup")
                .setBody(signup, JSON_UTF_8)
                .addHeader(COOKIE_B, new Cookie[]{new DefaultCookie("_v", ck)})
                .setTimeout(TIMEOUT)
                .go()
                .await()
                .assertStatus(OK)
                .assertCookieValue("tnc-e", "[community,invest]foo@bar.com")
                .content();

        System.out.println("RESP: " + resp);

        harn.post("/api/signup")
                .setBody(signup, JSON_UTF_8)
                .addHeader(COOKIE_B, new Cookie[]{new DefaultCookie("_v", ck)})
                .setTimeout(TIMEOUT)
                .go()
                .await()
                .assertStatus(CONFLICT);

        Stream<Path> str = java.nio.file.Files.find(store, 100, new BiPredicate<Path, BasicFileAttributes>() {
            @Override
            public boolean test(Path t, BasicFileAttributes u) {
                System.out.println("TEST " + t);
                return Files.isDirectory(t) || t.toString().endsWith(".signup");
            }
        }, FileVisitOption.FOLLOW_LINKS).filter(pth -> {
            return !Files.isDirectory(pth);
        });

        Optional<Path> pth = str.findFirst();
        assertTrue("Searching " + store, pth.isPresent());
        Signup up;
        try (InputStream in = java.nio.file.Files.newInputStream(pth.get(), StandardOpenOption.READ)) {
            up = mapper.readValue(in, Signup.class);
        }
        assertEquals("foo@bar.com", up.info.emailAddress);
        assertEquals(setOf("community", "invest"), up.info.signedUpFor);
        assertEquals(up.cookie, vc);
        assertEquals(up.info.token, tokn);
        assertNotNull(up.revocationToken);

//        harn.get("api/admin/ist").basicAuthentication("x", "y")
//                .log()
//                .go()
//                .await()
//                .assertStatus(HttpResponseStatus.UNAUTHORIZED)
//                .assertHasHeader(WWW_AUTHENTICATE);
        String tok2 = harn.post("/api/token")
                .addQueryPair("ix", "2")
                .setTimeout(TIMEOUT)
                .go()
                .await()
                .assertStatus(OK)
                .assertHasHeader(SET_COOKIE_B)
                .assertHasCookie("_v").content();

        SignupInfo signup2 = new SignupInfo("moo@food.com", setOf("invest"), tok2, "Moo Goo");

        String resp2 = harn.post("/api/signup")
                .setBody(signup2, JSON_UTF_8)
                .addHeader(COOKIE_B, new Cookie[]{new DefaultCookie("_v", ck)})
                .setTimeout(TIMEOUT)
                .go()
                .await()
                .assertStatus(OK)
                .assertCookieValue("tnc-e", "[community,invest]foo@bar.com")
                .content();

        System.out.println("Resp2 " + resp2);

        String all = harn.get("api/admin/list")
                .basicAuthentication("foo", "bar")
                .setTimeout(TIMEOUT)
                .go()
                .await()
                .assertStatus(OK)
                .content();

        System.out.println("ALL: " + all);
        Signup[] signups = mapper.readValue(all, Signup[].class);
        assertNotNull(signups);
        assertEquals(2, signups.length);
        int ct = 0;
        for (Signup s : signups) {
            switch (s.info.emailAddress) {
                case "foo@bar.com":
                case "moo@food.com":
                    ct++;
                    break;
                default:
                    fail("Unknown item: " + s);
            }
        }
        assertEquals(2, ct);

        harn.get("api/admin/sheet")
                .basicAuthentication("foo", "bar")
                .setTimeout(TIMEOUT)
                .go()
                .await()
                .assertStatus(OK)
                .assertHasHeader(CONTENT_TYPE)
                .assertHasHeader(CONTENT_DISPOSITION);

    }

    static final class TestSignupModule extends AbstractModule {

        private final Settings settings;
        private final long ts;
        private final File dir;
        private final ReentrantScope scope = new ReentrantScope();

        TestSignupModule(Settings settings) throws IOException {
            ts = System.currentTimeMillis();
            dir = com.google.common.io.Files.createTempDir();
            this.settings = new SettingsBuilder()
                    .add(settings)
                    .add("dont.bind.storage.dir")
                    .add(SETTINGS_KEY_STORAGE_DIR, dir.getAbsolutePath())
                    .add(SETTINGS_KEY_POSSIBLE_SIGNUPS, DEFAULT_POSSIBLE_SIGNUPS)
                    .build();
        }

        @Override
        protected void configure() {
            try {
                bind(String.class).annotatedWith(Names.named(SETTINGS_KEY_ADMIN_NAME)).toInstance("foo");
                bind(String.class).annotatedWith(Names.named(SETTINGS_KEY_ADMIN_PASSWORD)).toInstance("bar");
                install(new SignupServer(settings, scope));
                install(new LoggingModule().bindLogger("signup").bindLogger("admin").bindLogger("token"));
                install(new GenericApplicationModule(scope, settings, GenericApplication.class));
                bind(Path.class).annotatedWith(Names.named(GUICE_BINDING_STORAGE_DIR)).toInstance(dir.toPath());
                Path tmp = dir.toPath().resolve("tmp");
                java.nio.file.Files.createDirectories(tmp);
                boolean ato = SignupServer.isAtomicMoveSupported(tmp);
                bind(Boolean.class).annotatedWith(Names.named(GUICE_BINDING_ATOMIC_MOVES)).toInstance(ato);
            } catch (Exception ex) {
                Exceptions.chuck(ex);
            }
        }
    }
}
