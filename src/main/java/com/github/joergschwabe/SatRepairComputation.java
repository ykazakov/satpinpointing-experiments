package com.github.joergschwabe;

import java.util.HashSet;
import java.util.Set;

import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceDerivabilityChecker;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Producer;
import org.liveontologies.puli.Proof;
import org.liveontologies.puli.Proofs;
import org.liveontologies.puli.pinpointing.InterruptMonitor;
import org.liveontologies.puli.pinpointing.MinimalSubsetEnumerator;
import org.liveontologies.puli.pinpointing.MinimalSubsetsFromProofs;
import org.liveontologies.puli.pinpointing.PriorityComparator;

import com.google.common.base.Preconditions;

/**
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of conclusions used in inferences
 * @param <I>
 *            the type of inferences used in the proof
 * @param <A>
 *            the type of axioms used by the inferences
 */
public class SatRepairComputation<C, I extends Inference<? extends C>, A>
		extends MinimalSubsetsFromProofs<C, I, A> {

	/**
	 * Creates a factory for computing repairs
	 * 
	 * @param <C>
	 *            the type of conclusions used in inferences
	 * @param <I>
	 *            the type of inferences used in the proof
	 * @param <A>
	 *            the type of axioms used by the inferences
	 * @param satFactory
	 *            the factory for creation of a {@link SatAdapter} used for
	 *            computation of justification
	 * @return a factory for computing of justifications from proofs using a
	 *         specific type of a sat solver
	 */
	public static <C, I extends Inference<? extends C>, A> MinimalSubsetsFromProofs.Factory<C, I, A> getFactory(
			SatAdapter.Factory satFactory) {
		return new SatRepairComputation.Factory<>(satFactory);
	}

	private final SatAdapter.Factory satFactory_;

	public SatRepairComputation(Proof<? extends I> proof,
			InferenceJustifier<? super I, ? extends Set<? extends A>> justifier,
			InterruptMonitor monitor, SatAdapter.Factory satFactory) {
		super(proof, justifier, monitor);
		this.satFactory_ = Preconditions.checkNotNull(satFactory);
	}

	@Override
	public MinimalSubsetEnumerator<A> newEnumerator(final Object query) {
		return new Enumerator(query, satFactory_.create());
	}

	class Enumerator implements MinimalSubsetEnumerator<A>,
			Producer<Inference<? extends Integer>> {

		private final IdProvider<A> axiomIds_;

		private final Proof<? extends Inference<? extends Integer>> integerProof_;
		private final int goal_;
		private final InferenceDerivabilityChecker<Integer, Inference<? extends Integer>> checker_;

		private final SatAdapter solver_;

		int cycleCount_ = 0;

		Enumerator(Object query, SatAdapter solver) {
			Preconditions.checkNotNull(query);
			this.solver_ = Preconditions.checkNotNull(solver);
			IdSupplier idSup = new IdSupplier();
			this.axiomIds_ = new IdProvider<>(idSup);
			IdProvider<Object> conclusionIds = new IdProvider<>(idSup);
			this.integerProof_ = IntegerProofTranslator.translate(getProof(),
					getInferenceJustifier(), query, conclusionIds, axiomIds_);
			this.goal_ = conclusionIds.getId(query);
			this.checker_ = new InferenceDerivabilityChecker<>(integerProof_);
			initEncoding();
			solver.setInterruptMonitor(getInterruptMonitor());
		}

		void initEncoding() {
			solver_.reset();
			// add positive clause for the goal
			solver_.newClause();
			solver_.addLiteral(goal_);
			solver_.addClause();
			// add clauses for each inference and their premises
			Proofs.unfoldRecursively(
					Proofs.removeAssertedInferences(integerProof_), goal_,
					this);
		}

		@Override
		public void produce(Inference<? extends Integer> inf) {
			solver_.newClause();
			solver_.addLiteral(-inf.getConclusion());
			for (int premise : inf.getPremises()) {
				solver_.addLiteral(premise);
			}
			solver_.addClause();
		}

		@Override
		public void enumerate(Listener<A> listener) {
			Preconditions.checkNotNull(listener);
			while (solver_.findModel()) {
				// block exactly the true axioms
				for (int id : axiomIds_.getIds()) {
					if (solver_.isTrue(id)) {
						checker_.block(id);
					} else {
						checker_.unblock(id);
					}
				}
				// new repair found
				Set<A> repair = new HashSet<>();
				// create a blocking clause
				solver_.newClause();
				// try to add true axioms unless it causes the entailment
				for (int id : axiomIds_.getIds()) {
					if (!solver_.isTrue(id)) {
						continue;
					}
					checker_.unblock(id);
					if (checker_.isDerivable(goal_)) {
						// axiom causes entailment => belongs to
						// a repair
						repair.add(axiomIds_.getValue(id));
						solver_.addLiteral(-id);
						checker_.block(id);
					}
				}
				solver_.addClause();
				listener.newMinimalSubset(repair);
			}
		}

		@Override
		public void enumerate(Listener<A> listener,
				PriorityComparator<? super Set<A>, ?> priorityComparator) {
			throw new UnsupportedOperationException("Priority not supported");
		}

	}

	/**
	 * @author Yevgeny Kazakov
	 *
	 * @param <C>
	 *            the type of conclusions used in inferences
	 * @param <I>
	 *            the type of inferences used in the proof
	 * @param <A>
	 *            the type of axioms used by the inferences
	 */
	static class Factory<C, I extends Inference<? extends C>, A>
			implements MinimalSubsetsFromProofs.Factory<C, I, A> {

		private final SatAdapter.Factory satFactory_;

		Factory(SatAdapter.Factory satFactory) {
			this.satFactory_ = satFactory;
		}

		@Override
		public MinimalSubsetEnumerator.Factory<C, A> create(
				Proof<? extends I> proof,
				InferenceJustifier<? super I, ? extends Set<? extends A>> justifier,
				InterruptMonitor monitor) {
			return new SatRepairComputation<>(proof, justifier, monitor,
					satFactory_);
		}

	}

}
