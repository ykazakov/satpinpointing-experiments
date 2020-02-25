package com.github.joergschwabe;

import org.liveontologies.puli.pinpointing.InterruptMonitor;

/**
 * A simple interface for propositional incremental SAT solvers used in
 * computations
 * 
 * @author Yevgeny Kazakov
 */
public interface SatAdapter {

	/**
	 * Creates of a new empty clause
	 */
	void newClause();

	/**
	 * Add a given literal to the last clause created by {@link #newClause()}
	 * 
	 * @param l
	 *            a literal to be added, which is either of the form {@code a}
	 *            (positive literal) or {@code -a} (negative literal) where
	 *            {@code a} is the positive identifier of a propositional
	 *            variable
	 */
	void addLiteral(int l);

	/**
	 * Adds the last created clause to this sat solver.
	 */
	void addClause();

	/**
	 * Searches for a propositional model satisfying all clauses added to this
	 * sat solver
	 * 
	 * @return {@code true} if the model is found and {@code false} otherwise; a
	 *         model may not be found if the set of clauses is unsatisfiable of
	 *         the search process has been interrupted.
	 * 
	 * @see #setInterruptMonitor(InterruptMonitor)
	 */
	public boolean findModel();

	/**
	 * Checks if a given atom is true in the last model found by
	 * {@link #findModel()}
	 * 
	 * @param atom
	 * @return {@code true} if the atom is true in the model and {@code false}
	 *         otherwise
	 */
	public boolean isTrue(int atom);

	/**
	 * Use a given {@link InterruptMonitor} to decide if the search for a model
	 * in {@link #findModel()} needs to aborted. If
	 * {@link InterruptMonitor#isInterrupted()} returns {@code true}, the search
	 * should be aborted at the next earliest possibility
	 * 
	 * @param monitor
	 *            the {@link InterruptMonitor} used to decide if to abort the
	 *            search for a model
	 */
	void setInterruptMonitor(final InterruptMonitor monitor);

	/**
	 * Resets this solver. The previously added clauses and model should be
	 * disregarded.
	 */
	void reset();

	public interface Factory {
		/**
		 * @return a new {@code SatSolver}
		 */
		SatAdapter create();

	}

}
