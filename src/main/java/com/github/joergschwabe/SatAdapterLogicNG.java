package com.github.joergschwabe;

import org.liveontologies.puli.pinpointing.InterruptMonitor;
import org.logicng.datastructures.Assignment;
import org.logicng.datastructures.Tristate;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.handlers.SATHandler;
import org.logicng.solvers.MiniSat;
import org.logicng.solvers.SATSolver;

/**
 * A {@link SatAdapter} backed up by an instance of a LogicNG solver
 * 
 * @author Yevgeny Kazakov
 *
 * @see <a href="http://google.com">https://github.com/logic-ng/LogicNG</a>
 *
 */
public class SatAdapterLogicNG implements SatAdapter {

	/**
	 * An instance of LogicNG solver used for searching for models
	 */
	private final SATSolver solver_;

	private final FormulaFactory ff_;
	private Formula clause_;
	private Assignment model_;
	private SATHandler interruptHandler_;

	public SatAdapterLogicNG(SATSolver solver) {
		this.solver_ = solver;
		this.ff_ = solver.factory();
	}

	@Override
	public void newClause() {
		clause_ = ff_.falsum();
	}

	@Override
	public void addLiteral(int l) {
		String name = "" + (l > 0 ? l : -l);
		Formula lit = ff_.literal(name, l > 0);
		clause_ = ff_.or(clause_, lit);
	}

	@Override
	public void addClause() {
		solver_.add(clause_);
	}

	@Override
	public boolean findModel() {
		if (solver_.sat(interruptHandler_) == Tristate.TRUE) {
			model_ = solver_.model();
			return true;
		}
		// else
		return false;
	}

	@Override
	public boolean isTrue(int atom) {
		return model_.evaluateLit(ff_.literal("" + atom, true));
	}

	@Override
	public void setInterruptMonitor(final InterruptMonitor monitor) {
		interruptHandler_ = new SATHandler() {

			@Override
			public void started() {
			}

			@Override
			public boolean aborted() {
				return false;
			}

			@Override
			public void finishedSolving() {
			}

			@Override
			public boolean detectedConflict() {
				return !monitor.isInterrupted();
			}
		};
	}

	@Override
	public void reset() {
		solver_.reset();
		clause_ = null;
		model_ = null;
		interruptHandler_ = null;
	}

	/* predefined factories for sat solvers */

	public enum FACTORY implements SatAdapter.Factory {
		GLUCOSE, MINI_CARD, MINI_SAT;

		@Override
		public SatAdapter create() {
			MiniSat solver;
			FormulaFactory ff = new FormulaFactory();
			switch (this) {
			case GLUCOSE:
				solver = MiniSat.glucose(ff);
				break;
			case MINI_CARD:
				solver = MiniSat.miniCard(ff);
				break;
			case MINI_SAT:
				solver = MiniSat.miniSat(ff);
				break;
			default:
				throw new RuntimeException(
						"Unknown SAT Solver Factory: " + this);
			}
			return new SatAdapterLogicNG(solver);
		}
	}

}
