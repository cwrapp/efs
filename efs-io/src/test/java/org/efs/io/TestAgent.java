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

import com.googlecode.cqengine.query.Query;
import jakarta.annotation.Nullable;
import java.util.concurrent.CountDownLatch;
import org.efs.io.EfsFile.AccessMode;
import org.efs.io.EfsFileConnection.Retrieval;
import org.efs.io.RetrievalCompleteEvent.CompletionType;

/**
 * Test agent
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class TestAgent
    extends AbstractTestAgent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    private CountDownLatch mContinueSignal;
    private CountDownLatch mDoneSignal;
    private boolean mIsCompleted;
    private CompletionType mCompletionType;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    public TestAgent(final String agentName,
                     final AccessMode mode,
                     final EfsFile<TradeEvent> tradeFile)
    {
        super (agentName, mode, tradeFile);
    } // end of TestAgent(String, AccessMode, EfsFile<>)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    public boolean isCompleted()
    {
        return (mIsCompleted);
    } // end of isCompleted()

    @Nullable
    public CompletionType completionType()
    {
        return (mCompletionType);
    } // end of completionType()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    public void setContinueSignal(final CountDownLatch signal)
    {
        mContinueSignal = signal;
    } // end of setContinueSignal(CountDownLatch)

    public void setDoneSignal(final CountDownLatch signal)
    {
        mDoneSignal = signal;
    } // end of setDoneSignal(CountDownLatch)

    //
    // end of Set Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Event Handlers.
    //

    public void onEvent(final EfsRow<TradeEvent> trade)
    {
        // Need to hold up on the first event to provide time for
        // retrieval request to be canceled.
        try
        {
            mContinueSignal.await();
        }
        catch (InterruptedException interruptn)
        {}
    } // end of onEvent(TradeEvent)

    public void onDone(final RetrievalCompleteEvent<TradeEvent> event)
    {
        mIsCompleted = true;
        mCompletionType = event.completionType();

        if (mDoneSignal != null)
        {
            mDoneSignal.countDown();
        }
    } // end of onDone(RetrievalCompleteEvent<>)

    //
    // end of Event Handlers.
    //-----------------------------------------------------------

    @SuppressWarnings ("unchecked")
    public Retrieval<TradeEvent> retrieve(final EfsInterval interval,
                                          final Query query)
    {
        return (mTradeConnection.retrieve(interval,
                                          query,
                                          this::onEvent,
                                          this::onDone));
    } // end of retrieve(EfsInterval, Query)
} // end of class TestAgent
