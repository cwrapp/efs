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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.Util;

/**
 * Utility class for creating {@link AsyncLogger} instances for
 * a given class or name. This logger is based upon a nested
 * logger which is used to perform the logging asynchronously.
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
     * Returns a new {@link AsyncLogger asynchronous logger} for
     * the calling object's class.
     * @return new asynchronous logger.
     */
    public static Logger getLogger()
    {
        return (getLogger(Util.getCallingClass()));
    } // end of getLogger()

    /**
     * Returns a new {@link AsyncLogger asynchronous logger} for
     * the given class.
     * @param clazz create logger for this class.
     * @return new asynchronous logger.
     */
    public static Logger getLogger(final Class<?> clazz)
    {
        return (new AsyncLogger(LoggerFactory.getLogger(clazz)));
    } // end of getLogger(Class)

    /**
     * Returns a new {@link AsyncLogger asynchronous logger} for
     * the given name.
     * @param name create logger for this name.
     * @return new asynchronous logger.
     */
    public static Logger getLogger(final String name)
    {
        return (new AsyncLogger(LoggerFactory.getLogger(name)));
    } // end of getLogger(String)
} // end of class AsyncLoggerFactory
