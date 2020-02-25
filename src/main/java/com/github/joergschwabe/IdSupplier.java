package com.github.joergschwabe;

/**
 * Obtaining fresh integer identifiers
 * 
 * @author Yevgeny Kazakov
 *
 */
public class IdSupplier {

	private int nextId_ = 1;

	/**
	 * @return the next fresh integer identifier; it is guaranteed that this
	 *         identifier was not returned by the previous calls of the method
	 */
	public int getNextId() {
		return nextId_++;
	}

}
