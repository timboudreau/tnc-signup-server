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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastfrog.signup.server.VisitorCookie;
import java.util.Objects;

/**
 *
 * @author Tim Boudreau
 */
public class Signup implements Comparable<Signup> {

    public final SignupInfo info;
    public final long when;
    public final VisitorCookie cookie;
    public final String userAgent;
    public final boolean emailed;

    @JsonCreator
    public Signup(@JsonProperty("info") SignupInfo info,
            @JsonProperty("when") long when,
            @JsonProperty("cookie") VisitorCookie cookie,
            @JsonProperty("agent") String userAgent,
            @JsonProperty("emailed") boolean emailed,
            @JsonProperty("validated") boolean validated) {
        this.info = info;
        this.when = when;
        this.cookie = cookie;
        this.userAgent = userAgent;
        this.emailed = emailed;
    }

    @Override
    public String toString() {
        return "Signup{" + "info=" + info + ", when=" + when + ", cookie="
                + cookie + ", userAgent=" + userAgent + ", emailed="
                + emailed + '}';
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.info);
        hash = 67 * hash + (int) (this.when ^ (this.when >>> 32));
        hash = 67 * hash + Objects.hashCode(this.cookie);
        hash = 67 * hash + Objects.hashCode(this.userAgent);
        hash = 67 * hash + (this.emailed ? 1 : 0);
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
        final Signup other = (Signup) obj;
        if (this.when != other.when) {
            return false;
        }
        if (this.emailed != other.emailed) {
            return false;
        }
        if (!Objects.equals(this.userAgent, other.userAgent)) {
            return false;
        }
        if (!Objects.equals(this.info, other.info)) {
            return false;
        }
        if (!Objects.equals(this.cookie, other.cookie)) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(Signup o) {
        return info.emailAddress.compareTo(o.info.emailAddress);
    }
}
