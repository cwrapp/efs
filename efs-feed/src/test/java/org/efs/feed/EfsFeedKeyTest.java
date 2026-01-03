//
// Copyright 2025 Charles W. Rapp
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package org.efs.feed;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import org.junit.jupiter.api.Test;

/**
 * Tests {@code EfsFeedKey} class.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class EfsFeedKeyTest
{
//---------------------------------------------------------------
// Member data.
//

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    public void getKeyNullClass()
    {
        final Class<SampleEvent> ec = null;
        final String subject = "/abc/def";

        try
        {
            EfsFeedKey.getKey(ec, subject);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex).hasMessage("ec is null");
        }
    } // end of getKeyNullClass()

    @Test
    public void getKeyNullSubject()
    {
        final Class<SampleEvent> ec = SampleEvent.class;
        final String subject = null;

        try
        {
            EfsFeedKey.getKey(ec, subject);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage(
                    "subject is either null or an empty string");
        }
    } // end of getKeyNullSubject()

    @Test
    public void getKeyEmptySubject()
    {
        final Class<SampleEvent> ec = SampleEvent.class;
        final String subject = "";

        try
        {
            EfsFeedKey.getKey(ec, subject);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage(
                    "subject is either null or an empty string");
        }
    } // end of getKeyEmptySubject()

    @Test
    public void getKeySuccess()
    {
        final Class<SampleEvent> ec = SampleEvent.class;
        final String subject = "/abc/def";
        final String className = ec.getName();
        final String text =
            String.format(EfsFeedKey.KEY_FORMAT,
                          className,
                          EfsFeedKey.KEY_IFS,
                          subject);
        final EfsFeedKey key = EfsFeedKey.getKey(ec, subject);

        assertThat(key).isNotNull();
        assertThat(key.eventClass()).isEqualTo(ec);
        assertThat(key.eventClassName()).isEqualTo(className);
        assertThat(key.subject()).isEqualTo(subject);
        assertThat(key.toString()).isEqualTo(text);
        assertThat(key).isEqualTo(key);
    } // end of getKeySuccess()

    @Test
    public void feedKeyEqualsTrue()
    {
        final Class<SampleEvent> ec = SampleEvent.class;
        final String subject = "/abc/def";
        final EfsFeedKey key0 = EfsFeedKey.getKey(ec, subject);
        final EfsFeedKey key1 = EfsFeedKey.getKey(ec, subject);

        assertThat(key0).isEqualTo(key1);
        assertThat(key0).isSameAs(key1);
        assertThat(key0.hashCode()).isEqualTo(key1.hashCode());
    } // end of feedKeyEqualsTrue()

    @Test
    public void feedKeyEqualsFalse()
    {
        final Class<SampleEvent> ec = SampleEvent.class;
        final String subject0 = "/abc/def";
        final String subject1 = "/abc/deg";
        final EfsFeedKey key0 = EfsFeedKey.getKey(ec, subject0);
        final EfsFeedKey key1 = EfsFeedKey.getKey(ec, subject1);

        assertThat(key0).isNotEqualTo(key1);
        assertThat(key0).isNotSameAs(key1);
        assertThat(key0.hashCode()).isNotEqualTo(key1.hashCode());
    } // end of feedKeyEqualsFalse()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------
} // end of class EfsFeedKeyTest