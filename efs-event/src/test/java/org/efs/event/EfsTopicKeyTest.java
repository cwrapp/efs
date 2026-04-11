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

package org.efs.event;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import org.junit.jupiter.api.Test;

/**
 * Tests {@code EfsTopicKey} class.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class EfsTopicKeyTest
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
        final String topic = "/abc/def";

        try
        {
            EfsTopicKey.getKey(ec, topic);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex).hasMessage("ec is null");
        }
    } // end of getKeyNullClass()

    @Test
    public void getKeyNullTopic()
    {
        final Class<SampleEvent> ec = SampleEvent.class;
        final String topic = null;

        try
        {
            EfsTopicKey.getKey(ec, topic);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage(EfsTopicKey.INVALID_TOPIC);
        }
    } // end of getKeyNullTopic()

    @Test
    public void getKeyEmptyTopic()
    {
        final Class<SampleEvent> ec = SampleEvent.class;
        final String topic = "";

        try
        {
            EfsTopicKey.getKey(ec, topic);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage(EfsTopicKey.INVALID_TOPIC);
        }
    } // end of getKeyEmptyTopic()

    @Test
    public void getKeyBlankTopic()
    {
        final Class<SampleEvent> ec = SampleEvent.class;
        final String topic = " ";

        try
        {
            EfsTopicKey.getKey(ec, topic);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage(EfsTopicKey.INVALID_TOPIC);
        }
    } // end of getKeyBlankTopic()

    @Test
    public void getKeySuccess()
    {
        final Class<SampleEvent> ec = SampleEvent.class;
        final String topic = "/abc/def";
        final String className = ec.getName();
        final String text =
            String.format(EfsTopicKey.KEY_FORMAT,
                          className,
                          EfsTopicKey.KEY_IFS,
                          topic);
        final EfsTopicKey key = EfsTopicKey.getKey(ec, topic);

        assertThat(key).isNotNull();
        assertThat(key.eventClass()).isEqualTo(ec);
        assertThat(key.eventClassName()).isEqualTo(className);
        assertThat(key.topic()).isEqualTo(topic);
        assertThat(key.toString()).isEqualTo(text);
        assertThat(key).isEqualTo(key);
    } // end of getKeySuccess()

    @Test
    public void topicKeyEqualsTrue()
    {
        final Class<SampleEvent> ec = SampleEvent.class;
        final String topic = "/abc/def";
        final EfsTopicKey key0 = EfsTopicKey.getKey(ec, topic);
        final EfsTopicKey key1 = EfsTopicKey.getKey(ec, topic);

        assertThat(key0).isEqualTo(key1);
        assertThat(key0).isSameAs(key1);
        assertThat(key0.hashCode()).isEqualTo(key1.hashCode());
    } // end of topicKeyEqualsTrue()

    @Test
    public void topicKeyEqualsFalse()
    {
        final Class<SampleEvent> ec = SampleEvent.class;
        final String topic0 = "/abc/def";
        final String topic1 = "/abc/deg";
        final EfsTopicKey key0 = EfsTopicKey.getKey(ec, topic0);
        final EfsTopicKey key1 = EfsTopicKey.getKey(ec, topic1);

        assertThat(key0).isNotEqualTo(key1);
        assertThat(key0).isNotSameAs(key1);
        assertThat(key0.hashCode()).isNotEqualTo(key1.hashCode());
    } // end of topicKeyEqualsFalse()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------
} // end of class EfsFeedKeyTest