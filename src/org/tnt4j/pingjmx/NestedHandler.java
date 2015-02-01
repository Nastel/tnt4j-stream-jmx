package org.tnt4j.pingjmx;

public interface NestedHandler<V, K> {
	/**
	 * Add a sample listener, which will be invoked when
	 * each sample starts and ends.
	 *  
	 * @param listener sample listener instance
	 * 
	 */
	V addListener(K listener);

	/**
	 * Remove sample listener.
	 *  
	 * @param listener sample listener instance
	 * 
	 */
	V removeListener(K listener);
}
