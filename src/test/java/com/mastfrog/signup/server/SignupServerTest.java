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

import com.google.common.io.Files;
import static com.google.common.net.MediaType.JSON_UTF_8;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.mastfrog.acteur.annotations.GenericApplication;
import com.mastfrog.acteur.annotations.GenericApplicationModule;
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
import static com.mastfrog.signup.server.SignupServer.GUICE_BINDING_STORAGE_DIR;
import static com.mastfrog.signup.server.SignupServer.SETTINGS_KEY_POSSIBLE_SIGNUPS;
import static com.mastfrog.signup.server.SignupServer.SETTINGS_KEY_STORAGE_DIR;
import com.mastfrog.signup.server.SignupServerTest.TestSignupModule;
import com.mastfrog.signup.server.model.SignupInfo;
import com.mastfrog.util.Exceptions;
import static com.mastfrog.util.collections.CollectionUtils.setOf;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import static org.junit.Assert.assertNotNull;
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
    public void testSomeMethod(TestHarness harn, PortableCrypto crypto) throws Throwable {
        CallResult res = harn.post("/api/token")
                .addQueryPair("ix", "23")
//                .log()
                .setTimeout(TIMEOUT)
                .go()
                .await()
                .assertStatus(OK)
                .assertHasHeader(SET_COOKIE_B)
                .assertHasCookie("_v");
        String tokn = res.content();
        assertNotNull(tokn);
        System.out.println("TOKEN: " + tokn);

        String ck = res.getCookieValue("_v");

        VisitorCookie vc = new VisitorCookie(crypto.decrypt(ck));
        System.out.println("VISITOR COOKIE: " + vc);

        SignupInfo signup = new SignupInfo("foo@bar.com", setOf("stuff", "more-stuff"), tokn);

        String resp = harn.post("/api/signup")
                .setBody(signup, JSON_UTF_8)
                .addHeader(COOKIE_B, new Cookie[] {new DefaultCookie("_v", ck)})
//                .log()
                .setTimeout(TIMEOUT)
                .go()
                .await()
                .assertStatus(OK)
                .assertCookieValue("tnc-e", "[more-stuff,stuff]foo@bar.com")
                .content();

        System.out.println("RESP: " + resp);
    }

    static final class TestSignupModule extends AbstractModule {

        private final Settings settings;
        private final long ts;
        private final File dir;
        private final ReentrantScope scope = new ReentrantScope();
        TestSignupModule(Settings settings) throws IOException {
            ts = System.currentTimeMillis();
            dir = Files.createTempDir();
            this.settings = new SettingsBuilder()
                    .add(settings)
                    .add("dont.bind.storage.dir")
                    .add(SETTINGS_KEY_STORAGE_DIR, dir.getAbsolutePath())
                    .add(SETTINGS_KEY_POSSIBLE_SIGNUPS, DEFAULT_POSSIBLE_SIGNUPS)
                    .build();
            System.out.println("DIR IS " + dir);
        }

        @Override
        protected void configure() {
            try {
                install(new SignupServer(settings, scope));
                install(new LoggingModule().bindLogger("signup"));
                install(new GenericApplicationModule(scope, settings, GenericApplication.class));
                bind(Path.class).annotatedWith(Names.named(GUICE_BINDING_STORAGE_DIR)).toInstance(dir.toPath());
            } catch (Exception ex) {
                Exceptions.chuck(ex);
            }
        }

    }
}
