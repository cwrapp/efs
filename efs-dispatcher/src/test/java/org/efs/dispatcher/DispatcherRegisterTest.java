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

package org.efs.dispatcher;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.assertThat;
import org.efs.dispatcher.EfsDispatcher.DispatcherType;
import org.efs.dispatcher.config.ThreadType;
import org.efs.event.IEfsEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class DispatcherRegisterTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String DISPATCHER_NAME =
        "test-dispatcher-100";
    private static final String AGENT_NAME = "test-agent-";
    private static final String NAME_METHOD = "name";
    private static final String EQUALS_METHOD = "equals";
    private static final String HASH_METHOD = "hashCode";
    private static final String TO_STRING_METHOD = "toString";

    //-----------------------------------------------------------
    // Statics.
    //

    private static int sAgentIndex = 0;

    //-----------------------------------------------------------
    // Locals.
    //

    private String  mAgentName;
    private IEfsAgent mAgent;
    private IEfsEvent mEvent;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeAll
    public static void setUpClass()
    {
        final int numThreads = 1;
        final ThreadType threadType = ThreadType.BLOCKING;
        final int priority = 3;
        final DispatcherType dispatcherType = DispatcherType.EFS;
        final int eventQueueCapacity = 128;
        final int runQueueCapacity = 32;
        final int maxEvents = eventQueueCapacity;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(DISPATCHER_NAME);

        builder.numThreads(numThreads)
               .threadType(threadType)
               .priority(priority)
               .dispatcherType(dispatcherType)
               .eventQueueCapacity(eventQueueCapacity)
               .runQueueCapacity(runQueueCapacity)
               .maxEvents(maxEvents)
               .build();
    } // end of setUpClass()

    @AfterAll
    public static void tearDownClass()
    {
        EfsDispatcher.stopDispatchers();
    } // end of tearDownClass()

    @BeforeEach
    public void setUp()
    {
        mAgentName = AGENT_NAME + sAgentIndex++;
        mAgent = createProxyAgent(mAgentName);
        mEvent = createProxyEvent();

    } // end of setUp()

    @AfterEach
    public void tearDown()
    {
    } // end of tearDown()

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    public void agentRegistrationTest()
    {
        assertThat(EfsDispatcher.isRegistered(mAgent)).isFalse();

        // This method call should successfully de-register an
        // unregistered agent.
        EfsDispatcher.deregister(mAgent);

        assertThat(EfsDispatcher.isRegistered(mAgent)).isFalse();
    } // end of agentRegistrationTest()

    @Test
    public void unregisteredAgentDispatchConsumer()
    {
        final Consumer<IEfsEvent> consumer = e -> {};
        final String message =
            "efs agent " + mAgentName + " not registered";

        assertThat(EfsDispatcher.isRegistered(mAgent)).isFalse();

        try
        {
            EfsDispatcher.dispatch(consumer, mEvent, mAgent);
        }
        catch (IllegalStateException statex)
        {
            assertThat(statex).hasMessage(message);
        }
    } // end of unregisteredAgentDispatchConsumer()

    @Test
    public void unregisteredAgentDispatchTask()
    {
        final Runnable task = () -> {};
        final String message =
            "efs agent " + mAgentName + " not registered";

        assertThat(EfsDispatcher.isRegistered(mAgent)).isFalse();

        try
        {
            EfsDispatcher.dispatch(task, mAgent);
        }
        catch (IllegalStateException statex)
        {
            assertThat(statex).hasMessage(message);
        }
    } // end of unregisteredAgentDispatchTask()

    @Test
    public void consumerException()
    {
        final Consumer<IEfsEvent> consumer =
            e ->
            {
                throw (new RuntimeException("oops!"));
            };

        EfsDispatcher.register(mAgent, DISPATCHER_NAME);
        EfsDispatcher.dispatch(consumer, mEvent, mAgent);
    } // end of consumerException()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private IEfsAgent createProxyAgent(final String agentName)
    {
        final ClassLoader loader =
            (IEfsAgent.class).getClassLoader();
        final Class<?>[] interfaces =
            new Class<?>[] { IEfsAgent.class };
        final InvocationHandler handler =
            new InvocationHandler()
            {
                @Override
                public Object invoke(final Object proxy, Method method,
                                     final Object[] args)
                    throws Throwable
                {
                    final String mname = method.getName();
                    final int pcount = method.getParameterCount();
                    final Object retval;

                    if (NAME_METHOD.equals(mname) &&
                        pcount == 0)
                    {
                        retval = agentName;
                    }
                    else if (EQUALS_METHOD.equals(mname) &&
                             pcount == 1)
                    {
                        retval = (proxy == args[0]);
                    }
                    else if (HASH_METHOD.equals(mname) &&
                             pcount == 0)
                    {
                        retval = System.identityHashCode(proxy);
                    }
                    else if (TO_STRING_METHOD.equals(mname) &&
                             pcount == 0)
                    {
                        retval =
                            "ProxyIEfsAgent[" + agentName + "]";
                    }
                    // For all other methods return a reasonable
                    // default value.
                    else
                    {
                        final Class<?> rt =
                            method.getReturnType();

                        if (!rt.isPrimitive())
                        {
                            retval = null;
                        }
                        else if (rt == boolean.class)
                        {
                            retval = false;
                        }
                        else if (rt == byte.class)
                        {
                            retval = (byte) 0;
                        }
                        else if (rt == short.class)
                        {
                            retval = (short) 0;
                        }
                        else if (rt == int.class)
                        {
                            retval = 0;
                        }
                        else if (rt == long.class)
                        {
                            retval = 0L;
                        }
                        else if (rt == float.class)
                        {
                            retval = 0f;
                        }
                        else if (rt == double.class)
                        {
                            retval = 0d;
                        }
                        // That leaves char.
                        else
                        {
                            retval = '\0';
                        }
                    }

                    return (retval);
                }
            };

        return (
            (IEfsAgent) Proxy.newProxyInstance(
                loader, interfaces, handler));
    } // end of createProxyAgent(String)

    private IEfsEvent createProxyEvent()
    {
        final ClassLoader loader =
            IEfsEvent.class.getClassLoader();
        final Class<?>[] interfaces =
            new Class<?>[] { IEfsEvent.class };
        final InvocationHandler handler =
            new InvocationHandler()
            {
                @Override
                public Object invoke(final Object proxy,
                                     final Method method,
                                     final Object[] args)
                    throws Throwable
                {
                    final String mname = method.getName();
                    final int pcount = method.getParameterCount();
                    final Object retval;

                    if (EQUALS_METHOD.equals(mname) &&
                        pcount == 1)
                    {
                        retval = args[0];
                    }
                    else if (HASH_METHOD.equals(mname) &&
                             pcount == 0)
                    {
                        retval = System.identityHashCode(proxy);
                    }
                    else if (TO_STRING_METHOD.equals(mname) &&
                             pcount == 0)
                    {
                        retval = "ProxyIEfsEvent";
                    }
                    else
                    {
                        final Class<?> rt =
                            method.getReturnType();

                        if (!rt.isPrimitive())
                        {
                            retval = null;
                        }
                        else if (rt == boolean.class)
                        {
                            retval = false;
                        }
                        else if (rt == byte.class)
                        {
                            retval = (byte) 0;
                        }
                        else if (rt == short.class)
                        {
                            retval = (short) 0;
                        }
                        else if (rt == int.class)
                        {
                            retval = 0;
                        }
                        else if (rt == long.class)
                        {
                            retval = 0L;
                        }
                        else if (rt == float.class)
                        {
                            retval = 0f;
                        }
                        else if (rt == double.class)
                        {
                            retval = 0d;
                        }
                        // That leaves char.
                        else
                        {
                            retval = '\0';
                        }
                    }

                    return (retval);
                }
            };

        return (
            (IEfsEvent) Proxy.newProxyInstance(
                loader, interfaces, handler));
    } // end of createProxyEvent()
} // end of class DispatcherRegisterTest