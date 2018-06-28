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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastfrog.util.strings.RandomStrings;
import com.mastfrog.util.time.TimeUtil;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 *
 * @author Tim Boudreau
 */
public final class VisitorCookie {

    public final String rs;
    public final String addr;
    public final long launch;
    public final long counter;
    public final long tsOff;

    @JsonCreator
    public VisitorCookie(@JsonProperty("rs") String rs,
            @JsonProperty("addr") String addr,
            @JsonProperty("launch") long launch,
            @JsonProperty("counter") long counter,
            @JsonProperty("tsOff") long tsOff) {
        this.rs = rs;
        this.addr = addr;
        this.launch = launch;
        this.counter = counter;
        this.tsOff = tsOff;
    }

    VisitorCookie(RandomStrings rs, long counter, long launch, SocketAddress addr) {
        this.rs = rs.get(7);
        this.counter = counter;
        this.launch = launch;
        this.tsOff = System.currentTimeMillis() - launch;
        if (addr instanceof InetSocketAddress) {
            InetSocketAddress isa = (InetSocketAddress) addr;
            this.addr = isa.getHostString();
        } else {
            this.addr = addr.toString();
        }
    }

    VisitorCookie(String s) {
        String[] parts = s.split(";");
        if (parts.length != 5) {
            throw new IllegalArgumentException("Wrong number of parts in '" + s + "'");
        }
        launch = Long.parseLong(parts[0], 36);
        counter = Long.parseLong(parts[1], 36);
        tsOff = Long.parseLong(parts[2], 36);
        rs = parts[3];
        addr = parts[4];
    }

    @JsonIgnore
    public ZonedDateTime issued() {
        return TimeUtil.fromUnixTimestamp(launch + tsOff);
    }

    public String toString() {
        return Long.toString(launch, 36) + ";" + Long.toString(counter) + ";" + Long.toString(tsOff, 36) + ";" + rs + ";" + addr;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 11 * hash + Objects.hashCode(this.rs);
        hash = 11 * hash + Objects.hashCode(this.addr);
        hash = 11 * hash + (int) (this.launch ^ (this.launch >>> 32));
        hash = 11 * hash + (int) (this.counter ^ (this.counter >>> 32));
        hash = 11 * hash + (int) (this.tsOff ^ (this.tsOff >>> 32));
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final VisitorCookie other = (VisitorCookie) obj;
        if (this.launch != other.launch) {
            return false;
        }
        if (this.counter != other.counter) {
            return false;
        }
        if (this.tsOff != other.tsOff) {
            return false;
        }
        if (!Objects.equals(this.rs, other.rs)) {
            return false;
        }
        if (!Objects.equals(this.addr, other.addr)) {
            return false;
        }
        return true;
    }
}
