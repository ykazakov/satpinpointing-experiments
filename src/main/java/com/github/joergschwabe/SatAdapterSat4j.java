package com.github.joergschwabe;

import org.liveontologies.puli.pinpointing.InterruptMonitor;
import org.sat4j.core.VecInt;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.ISolverService;
import org.sat4j.specs.IVecInt;
import org.sat4j.specs.TimeoutException;
import org.sat4j.tools.SearchListenerAdapter;

import com.google.common.base.Preconditions;

/**
 * A {@link SatAdapter} backed up by an instance of a Sat4j solver
 * 
 * @author Yevgeny Kazakov
 * 
 * @see <a href="http://google.com">https://www.sat4j.org</a>
 *
 */
public class SatAdapterSat4j implements SatAdapter {

	/**
	 * An instance of Sat4j solver used for searching for models
	 */
	private final ISolver solver_;

	/**
	 * holds the last clause constructed by this sat solver
	 */
	private IVecInt clause_;

	/**
	 * {@code true} if a trivial contradiction has been detected during addition
	 * of clauses
	 */
	private boolean trivialContradictionDetected_ = false;

	/**
	 * Creates a {@link SatAdapter} backed by a given Sat4j solver
	 * 
	 * @param solver
	 *            a Sat4j solver used for searching for models
	 */
	public SatAdapterSat4j(ISolver solver) {
		this.solver_ = Preconditions.checkNotNull(solver);
	}

	@Override
	public void newClause() {
		clause_ = new VecInt();
	}

	@Override
	public void addLiteral(int l) {
		clause_.push(l);
	}

	@Override
	public void addClause() {
		try {
			solver_.addClause(clause_);
		} catch (ContradictionException e) {
			trivialContradictionDetected_ = true;
		}
	}

	@Override
	public boolean findModel() {
		if (trivialContradictionDetected_) {
			return false;
		}
		try {
			return solver_.isSatisfiable();
		} catch (TimeoutException e) {
			return false;
		}
	}

	@Override
	public boolean isTrue(int atom) {
		return atom <= solver_.realNumberOfVariables() && solver_.model(atom);
	}

	@Override
	public void setInterruptMonitor(final InterruptMonitor monitor) {
		solver_.setSearchListener(new SearchListenerAdapter<ISolverService>() {
			private static final long serialVersionUID = 1L;

			@Override
			public void beginLoop() {
				if (monitor.isInterrupted()) {
					solver_.expireTimeout();
				}
			}

		});

	}

	@Override
	public void reset() {
		solver_.reset();
		clause_ = null;
		trivialContradictionDetected_ = false;
	}

	/* predefined factories for sat solvers */

	public enum FACTORY implements SatAdapter.Factory {
		BEST_HT, BEST_WL, DEFAULT, GLUCOSE, GLUCOSE21, GREEDY, LIGHT, MINI_LEARNING_HEAP, MINI_SAT_HEAP, SAT;

		@Override
		public SatAdapter create() {
			ISolver solver;
			switch (this) {
			case BEST_HT:
				solver = SolverFactory.newBestHT();
				break;
			case BEST_WL:
				solver = SolverFactory.newBestWL();
				break;
			case DEFAULT:
				solver = SolverFactory.newDefault();
				break;
			case GLUCOSE:
				solver = SolverFactory.newGlucose();
				break;
			case GLUCOSE21:
				solver = SolverFactory.newGlucose21();
				break;
			case GREEDY:
				solver = SolverFactory.newGreedySolver();
				break;
			case LIGHT:
				solver = SolverFactory.newLight();
				break;
			case MINI_LEARNING_HEAP:
				solver = SolverFactory.newMiniLearningHeap();
				break;
			case MINI_SAT_HEAP:
				solver = SolverFactory.newMiniSATHeap();
				break;
			case SAT:
				solver = SolverFactory.newSAT();
				break;
			default:
				throw new RuntimeException(
						"Unknown SAT Solver Factory: " + this);
			}
			return new SatAdapterSat4j(solver);
		}
	}

}
