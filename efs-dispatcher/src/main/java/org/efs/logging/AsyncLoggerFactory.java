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

package org.efs.logging;

import com.google.common.base.Strings;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for creating {@link AsyncLogger} instances for
 * a given class or name. This logger is based upon a nested
 * logger which is used to perform the logging asynchronously.
 * <p>
 * {@code AsyncLogger}s are cached based on given
 * {@link #getLogger(Class) class} (using {@link Class#getName()}
 * as key) or {@link #getLogger(String) name} (using {@code name}
 * as key). This means if there are two calls to
 * {@code getLogger} with an equivalent {@code Class} or
 * {@code String}, then the same {@code AsyncLogger} instance is
 * returned for that class or string.
 * </p>
 * <p>
 * This class contains static methods only and cannot be
 * instantiated.
 * </p>
 *
 * @see AsyncLogger
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class AsyncLoggerFactory
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * Null logger class is reported as {@value}.
     */
    public static final String NULL_CLASS = "class is null";

    /**
     * Invalid logger name is reported as {@value}.
     */
    public static final String INVALID_NAME =
        "name is either null, an empty string, or blank";

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Associate unique logger name with its logger instance.
     * If logger is created with a {@code Class} instance, then
     * {@link Class#getName()} is used as cache key.
     */
    private static final Map<String, AsyncLogger> sLoggerCache =
        new ConcurrentHashMap<>();

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Private constructor to prevent instantiation.
     */
    private AsyncLoggerFactory()
    {}

    //
    // end of Constructors.
    //-----------------------------------------------------------

    /**
     * Returns {@link AsyncLogger asynchronous logger} for given
     * class. Logger is cached for each unique class. If this
     * method is called twice with the same class, then the
     * same {@code AsyncLogger} is returned for each method call.
     * <p>
     * Logger is cached using {@link Class#getName()} as key.
     * </p>
     * @param clazz create logger for this class.
     * @return new asynchronous logger.
     * @throws NullPointerException
     * if {@code clazz} is {@code null}.
     */
    public static Logger getLogger(final Class<?> clazz)
    {
        Objects.requireNonNull(clazz, NULL_CLASS);

        final String key = clazz.getName();

        return (
            sLoggerCache.computeIfAbsent(
                key,
                t ->
                    new AsyncLogger(
                        LoggerFactory.getLogger(clazz))));
    } // end of getLogger(Class)

    /**
     * Returns {@link AsyncLogger asynchronous logger} for given
     * name. Logger is cached for each unique name. If this
     * method is called twice with the same name, then the
     * same {@code AsyncLogger} is returned for each method call.
     * <p>
     * Logger is cached using {@code name} as key.
     * </p>
     * @param name create logger for this name.
     * @return new asynchronous logger.
     * @throws IllegalArgumentException
     * if {@code name} is either {@code null} or an empty string.
     */
    public static Logger getLogger(final String name)
    {
        if (Strings.isNullOrEmpty(name) || name.isBlank())
        {
            throw (new IllegalArgumentException(INVALID_NAME));
        }

        return (
            sLoggerCache.computeIfAbsent(
                name,
                t ->
                    new AsyncLogger(
                        LoggerFactory.getLogger(name))));
    } // end of getLogger(String)
} // end of class AsyncLoggerFactory
