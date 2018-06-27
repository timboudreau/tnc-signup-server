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

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.annotations.HttpCall;
import static com.mastfrog.acteur.headers.Headers.CACHE_CONTROL;
import static com.mastfrog.acteur.headers.Headers.EXPIRES;
import static com.mastfrog.acteur.headers.Headers.LOCATION;
import static com.mastfrog.acteur.headers.Method.GET;
import static com.mastfrog.acteur.headers.Method.HEAD;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.resources.ResourcesPage;
import com.mastfrog.acteur.resources.StaticResources;
import static com.mastfrog.acteur.resources.markup.MarkupFiles.SETTINGS_KEY_USE_DYN_FILE_RESOURCES;
import com.mastfrog.acteur.resources.markup.MarkupFilesModule;
import com.mastfrog.acteur.server.PathFactory;
import com.mastfrog.acteur.server.ServerBuilder;
import static com.mastfrog.acteur.server.ServerModule.MAX_CONTENT_LENGTH;
import static com.mastfrog.acteur.server.ServerModule.PORT;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_CORS_ENABLED;
import static com.mastfrog.acteur.server.ServerModule.SETTINGS_KEY_URLS_HOST_NAME;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.bunyan.LoggingModule;
import static com.mastfrog.bunyan.LoggingModule.SETTINGS_KEY_LOG_FILE;
import com.mastfrog.crypto.CryptoConfig;
import static com.mastfrog.crypto.Features.MAC;
import com.mastfrog.crypto.MacConfig;
import com.mastfrog.crypto.PortableCrypto;
import com.mastfrog.giulius.DeploymentMode;
import static com.mastfrog.giulius.SettingsBindings.*;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.jackson.DurationSerializationMode;
import com.mastfrog.jackson.JacksonModule;
import com.mastfrog.jackson.TimeSerializationMode;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.util.ConfigurationError;
import com.mastfrog.util.Strings;
import com.mastfrog.util.strings.RandomStrings;
import static io.netty.handler.codec.http.HttpResponseStatus.TEMPORARY_REDIRECT;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author Tim Boudreau
 */
public class SignupServer extends AbstractModule {

    public static final String SETTINGS_KEY_SS_PASS = "password";
    private static final String DEFAULT_PASSWORD = "changeit23";
    public static final String SETTINGS_KEY_STORAGE_DIR = "storage";
    public static final String SETTINGS_KEY_POSSIBLE_SIGNUPS = "categories";
    public static final String DEFAULT_POSSIBLE_SIGNUPS = "invest,community,employment,retail";
    public static final String GUICE_BINDING_STORAGE_DIR = SETTINGS_KEY_STORAGE_DIR;
    public static final String GUICE_BINDING_HIT_COUNTER = "counter";
    public static final String GUICE_BINDING_LAUNCH_TIMESTAMP = "launch";
    public static final String GUICE_BINDING_POSSIBLE_SIGNUPS = SETTINGS_KEY_POSSIBLE_SIGNUPS;
    public static final String SETTINGS_KEY_CACHE_MINUTES = "token.cache.minutes";

    private final Settings settings;
    private final Random rnd;
    private final ReentrantScope scope;

    SignupServer(Settings settings, ReentrantScope scope) throws NoSuchAlgorithmException {
        this.settings = settings;
        rnd = new Random(SecureRandom.getInstanceStrong().nextLong());
        this.scope = scope;
    }

    @Override
    protected void configure() {
        String pass = settings.getString(SETTINGS_KEY_SS_PASS, DEFAULT_PASSWORD);
        bind(Random.class).toInstance(rnd);
        bind(PortableCrypto.class).toInstance(new PortableCrypto(rnd, pass, CryptoConfig.AES128, MacConfig.HMAC256, MAC));
        bind(RandomStrings.class).toInstance(new RandomStrings(rnd));
        bind(AtomicLong.class).annotatedWith(Names.named(GUICE_BINDING_HIT_COUNTER)).toInstance(new AtomicLong());
        bind(Long.class).annotatedWith(Names.named(GUICE_BINDING_LAUNCH_TIMESTAMP)).toInstance(System.currentTimeMillis());
        if (!settings.getBoolean("dont.bind.storage.dir", false)) {
            String fld = settings.getString(SETTINGS_KEY_STORAGE_DIR);
            bind(Path.class).annotatedWith(Names.named(GUICE_BINDING_STORAGE_DIR)).toInstance(Paths.get(fld));
        }
        bind(ConfigSanityCheck.class).asEagerSingleton();
        Set<String> possibleSignups = new HashSet<>();
        for (CharSequence seq : Strings.splitUniqueNoEmpty(',', settings.getString(pass, DEFAULT_POSSIBLE_SIGNUPS))) {
            possibleSignups.add(seq.toString());
        }
        bind(new TypeLiteral<Set<String>>() {
        }).annotatedWith(Names.named(GUICE_BINDING_POSSIBLE_SIGNUPS))
                .toInstance(possibleSignups);
        install(new MarkupFilesModule(SignupServer.class, scope));
//        bind(StaticResources.class).to(DynamicFileResources.class);
//        bind(StaticResources.class).to(FileResources.class);
    }

    public static void main(String[] args) throws IOException, InterruptedException, NoSuchAlgorithmException {
        final ReentrantScope scope = new ReentrantScope();
        Settings settings = new SettingsBuilder("signup-server")
                .add(PORT, 7382)
                .add(SETTINGS_KEY_POSSIBLE_SIGNUPS, DEFAULT_POSSIBLE_SIGNUPS)
                .add(SETTINGS_KEY_STORAGE_DIR, "/tmp/signup-store")
                .add(SETTINGS_KEY_LOG_FILE, "/tmp/signup-server.log")
                .add(SETTINGS_KEY_USE_DYN_FILE_RESOURCES, false)
                .add(SETTINGS_KEY_URLS_HOST_NAME, "truenorthcultivation.com")
                .add(MAX_CONTENT_LENGTH, 2400)
                .add(SETTINGS_KEY_CORS_ENABLED, "false")
                .add("application.name", "Signup Server 1.0")
                .add(LoggingModule.SETTINGS_KEY_LOG_TO_CONSOLE, true)
                .addDefaultLocations()
                .parseCommandLineArguments(args)
                .build();

        new ServerBuilder("signup-server", scope)
                .add(settings)
                .enableOnlyBindingsFor(INT, LONG, STRING, BOOLEAN)
                .disableCORS()
//                .withType(VisitorCookie.class)
                .add(new SignupServer(settings, scope))
                .add(new LoggingModule().bindLogger("signup").bindLogger("tokens"))
                .add(new JacksonModule().withJavaTimeSerializationMode(TimeSerializationMode.TIME_AS_ISO_STRING, DurationSerializationMode.DURATION_AS_STRING))
                .build().start().await();
    }

    @HttpCall(order = Integer.MAX_VALUE - 1)
    @Methods({HEAD, GET})
    public static final class RP extends ResourcesPage {

        @Inject
        public RP(ActeurFactory af, StaticResources r, Settings settings) {
            super(af, r, settings);
        }
    }

    @HttpCall(order=1)
    @Methods({HEAD, GET})
    public static final class RootResource extends Acteur {

        @Inject
        RootResource(PathFactory factory, HttpEvent evt) {
            if (evt.path().size() == 0) {
                URI uri = factory.constructURI("/index.html");
                add(LOCATION, uri);
                add(CACHE_CONTROL, CacheControl.PUBLIC_MUST_REVALIDATE_MAX_AGE_1_DAY);
                add(EXPIRES, ZonedDateTime.now().plus(Duration.ofDays(1)));
                reply(TEMPORARY_REDIRECT);
            } else {
                reject();
            }
        }
    }

    static final class ConfigSanityCheck {

        @Inject
        ConfigSanityCheck(Settings settings, DeploymentMode mode, @Named(GUICE_BINDING_LAUNCH_TIMESTAMP) long launch) throws IOException {
            if (mode == DeploymentMode.PRODUCTION) {
                String pass = settings.getString(SETTINGS_KEY_SS_PASS, DEFAULT_PASSWORD);
                if (DEFAULT_PASSWORD.equals(pass)) {
                    throw new ConfigurationError("Using default password in production mode.F");
                }
            }
            String fld = settings.getString(SETTINGS_KEY_STORAGE_DIR, "/tmp/signup-store");
            assert fld != null;
            Path pth = Paths.get(fld);
            if (!Files.exists(pth)) {
                Files.createDirectories(pth);
            } else if (!Files.isDirectory(pth)) {
                throw new ConfigurationError("Exists but not a folder: " + pth);
            }
            Path sess = pth.resolve("sessions/" + launch);
            if (!Files.exists(sess)) {
                Files.createDirectories(sess);
            } else if (!Files.isDirectory(sess)) {
                throw new ConfigurationError("Exists but not a folder: " + sess);
            }
        }
    }
}
