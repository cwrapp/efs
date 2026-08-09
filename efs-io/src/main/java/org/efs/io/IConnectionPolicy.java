//
// Copyright 2026 Charles W. Rapp
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

package org.efs.io;

import org.efs.dispatcher.IEfsAgent;
import org.efs.io.EfsFile.AccessMode;

/**
 * Defines control policy for agents attempting to connect to
 * an {@link EfsFile efs event file} using a specified
 * {@link EfsFile.AccessMode access mode}. If
 * {@link #isAllowed(IEfsAgent, AccessMode)} returns {@code true}
 * then agent is allowed to connect using access mode; otherwise
 * the connect attempt is rejected.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@FunctionalInterface
public interface IConnectionPolicy
{
//---------------------------------------------------------------
// Member methods.
//

    /**
     * Returns {@code true} if agent is allowed to connect to
     * an {@link EfsFile efs event file} using the specified
     * access mode; otherwise returns {@code false}.
     * @param agent agent attempted to connect to event file.
     * @param accessMode event file access mode.
     * @return {@code true} if {@code agent}, {@code accessMode}
     * is allowed to connect and {@code false} otherwise.
     */
    boolean isAllowed(IEfsAgent agent,
                      AccessMode accessMode);
} // end of interface IConnectionPolicy

