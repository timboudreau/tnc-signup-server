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
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.strings.Strings;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author Tim Boudreau
 */
public class SignupInfo {

    public final String emailAddress;
    public final Set<String> signedUpFor;
    public final String token;
    public final String name;

    @JsonCreator
    public SignupInfo(@JsonProperty(value = "emailAddress", required = true) String emailAddress,
            @JsonProperty(value = "signedUpFor", required = true) Set<String> signedUpFor,
            @JsonProperty(value = "token", required = true) String token,
            @JsonProperty(value= "name") String name) {
        this.emailAddress = notNull("emailAddress", emailAddress);
        this.signedUpFor = new TreeSet<>(notNull("signedUpFor", signedUpFor));
        this.token = token;
        this.name = name;
    }

    @Override
    public int hashCode() {
        return 13 * 5 + Objects.hashCode(this.emailAddress);
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
        final SignupInfo other = (SignupInfo) obj;
        return emailAddress.equals(other.emailAddress);
    }

    @Override
    public String toString() {
        return emailAddress + "[" + Strings.join(',', signedUpFor) + "]";
    }
}
