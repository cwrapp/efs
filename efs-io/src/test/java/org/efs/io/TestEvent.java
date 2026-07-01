//

package org.efs.io;

import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.List;
import org.efs.event.IEfsEvent;

/**
 * Test event class for CQAttributeGenerator tests.
 * Implements IEfsEvent and provides various getter methods
 * with different CQAttribute annotations.
 */
public final class TestEvent
    implements IEfsEvent
{
//---------------------------------------------------------------
// Member data.
//
    //-----------------------------------------------------------
    // Locals.
    //

    private final long mId;
    private final String mName;
    private final String mAddress;
    private final String mText;
    private final List<String> mTags;
    private final List<Integer> mValues;
    private final Instant mTimestamp;
    private final boolean mFlag;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    public TestEvent(final long id,
                     final String name,
                     final String address,
                     final String text,
                     final List<String> tags,
                     final Instant timestamp,
                     final boolean flag)
    {
        mId = id;
        mName = name;
        mAddress = address;
        mText = text;
        mTags = tags;
        mValues = null;
        mTimestamp = timestamp;
        mFlag = flag;
    } // end of TestEvent(...)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    @Override
    public String toString()
    {
        return String.format("TestEvent[id=%d, name=%s, tags=%s, timestamp=%s]",
                             mId, mName, mTags, mTimestamp);
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns unique event identifier.
     * @return unique event identifier.
     */
    @CQAttribute (attribute = CQAttributeType.SIMPLE,
                  index = CQIndexType.UNIQUE_INDEX)
    public long getId()
    {
        return mId;
    } // end of getId()

    /**
     * Returns event name.
     * @return event name.
     */
    @CQAttribute (attribute = CQAttributeType.SIMPLE_NULLABLE,
                  index = CQIndexType.HASH_INDEX)
    @Nullable
    public String getName()
    {
        return mName;
    } // end of getName()

    @CQAttribute (attribute = CQAttributeType.SIMPLE,
                  index = CQIndexType.REVERSED_RADIX_INDEX)
    public String getAddress()
    {
        return (mAddress);
    } // end of getAddress()

    @CQAttribute (attribute = CQAttributeType.SIMPLE_NULLABLE,
                  index = CQIndexType.SUFFIX_RADIX_INDEX)
    @Nullable
    public String getText()
    {
        return (mText);
    } // end of getText()

    /**
     * Returns tag names list.
     * @return tag names.
     */
    @CQAttribute (attribute = CQAttributeType.MULTIVALUE,
                  index = CQIndexType.NO_INDEX)
    public List<String> getTags()
    {
        return mTags;
    } // end of getTags()

    /**
     * Returns tag values.
     * @return tag values.
     */
    @CQAttribute (attribute = CQAttributeType.MULTIVALUE_NULLABLE,
                  nullValues = true,
                  index = CQIndexType.NO_INDEX)
    public List<Integer> getValues()
    {
        return (mValues);
    } // end of getValues()

    /**
     * Returns event timestamp.
     * @return event timestamp.
     */
    @CQAttribute (attribute = CQAttributeType.SIMPLE,
                  index = CQIndexType.NAVIGABLE_INDEX)
    public Instant getTimestamp()
    {
        return mTimestamp;
    } // end of getTimestamp()

    @CQAttribute (attribute = CQAttributeType.SIMPLE,
                  index = CQIndexType.NO_INDEX)
    public boolean isFlag()
    {
        return (mFlag);
    } // end of isFlag()

    /**
     * Getter method WITHOUT CQAttribute annotation.
     * This should NOT be included in generated attributes.
     * @return unannotated.
     */
    public String getUnannotated()
    {
        return ("unannotated");
    } // end of getUnannotated()

    //
    // end of Get Methods.
    //-------------------------------------------------------
} // end of class TestEvent
// end of class TestEvent
