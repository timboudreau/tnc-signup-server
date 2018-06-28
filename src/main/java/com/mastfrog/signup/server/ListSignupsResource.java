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

import static com.google.common.net.MediaType.JSON_UTF_8;
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.ResponseWriter;
import com.mastfrog.acteur.annotations.HttpCall;
import static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE;
import static com.mastfrog.acteur.headers.Method.GET;
import static com.mastfrog.acteur.headers.Method.HEAD;
import com.mastfrog.acteur.preconditions.Authenticated;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.PathRegex;
import com.mastfrog.signup.server.model.Signups;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.HttpMethod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.function.BiPredicate;

/**
 *
 * @author Tim Boudreau
 */
@HttpCall
@Methods({GET, HEAD})
@PathRegex("^api\\/admin\\/list$")
@Authenticated
public class ListSignupsResource extends Acteur {

    @Inject
    ListSignupsResource(HttpEvent evt) {
        setChunked(true);
        add(CONTENT_TYPE, JSON_UTF_8);
        if (HttpMethod.GET.name().equals(evt.method().name())) {
            setResponseWriter(RW.class);
        }
        ok();
    }

    private static final class RW extends ResponseWriter implements BiPredicate<Path, BasicFileAttributes> {

        private Iterator<Path> paths;
        private final ByteBufAllocator alloc;

        @Inject
        RW(Signups signups, ByteBufAllocator alloc) throws IOException {
            paths = signups.iterator();
            this.alloc = alloc;
        }

        @Override
        public Status write(Event<?> evt, Output out, int iteration) throws Exception {
            Path pth = paths.hasNext() ? paths.next() : null;
            if (pth == null) {
                if (iteration == 0) {
                    out.write(new byte[]{'[', ']'});
                } else {
                    out.write(new byte[]{']'});
                }
                return Status.DONE;
            }
            int len = (int) Files.size(pth);
            ByteBuf buf = alloc.ioBuffer(len + 1);
            if (iteration == 0) {
                buf.writeByte('[');
            } else {
                buf.writeByte(',');
            }
            buf.writeBytes(Files.readAllBytes(pth));
            out.write(buf);
            return Status.NOT_DONE;
        }

        @Override
        public boolean test(Path t, BasicFileAttributes u) {
            boolean result = (t.toString().endsWith(".signup") && !u.isDirectory()) || u.isDirectory();
            return result;
        }
    }
}
