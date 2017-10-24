package org.stt.persistence;

import org.stt.model.TimeTrackingItem;

import java.io.Closeable;
import java.util.Optional;

/**
 * <p>
 * Unless otherwise specified, the items returned <b>must</b> be ordered by
 * start time.
 * </p>
 * <p>
 * Note that only the last item returned should have no "end" time, all others
 * should be "closed"
 * </p>
 */
public interface ItemReader extends Closeable {
	/**
	 * Reads an item, if available.
	 * 
	 * @return An {@link Optional} of the {@link TimeTrackingItem} or absent if
	 *         none is available
	 */
	Optional<TimeTrackingItem> read();

    @Override
    void close();
}
