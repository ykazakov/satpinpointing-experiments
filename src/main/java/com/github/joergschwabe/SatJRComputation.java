package com.github.joergschwabe;

import java.util.HashSet;
import java.util.Set;

import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceDerivabilityChecker;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Proof;
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
public abstract class SatJRComputation<C, I extends Inference<? extends C>, A>
		extends MinimalSubsetsFromProofs<C, I, A> {

	/**
	 * Creates a factory for computing justifications
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
	public static <C, I extends Inference<? extends C>, A> MinimalSubsetsFromProofs.Factory<C, I, A> getJustificationFactory(
			SatAdapter.Factory satFactory) {
		return new SatJRComputation.JustificationFactory<>(satFactory);
	}

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
	public static <C, I extends Inference<? extends C>, A> MinimalSubsetsFromProofs.Factory<C, I, A> getRepairFactory(
			SatAdapter.Factory satFactory) {
		return new SatJRComputation.RepairFactory<>(satFactory);
	}

	private final SatAdapter.Factory satFactory_;

	public SatJRComputation(Proof<? extends I> proof,
			InferenceJustifier<? super I, ? extends Set<? extends A>> justifier,
			InterruptMonitor monitor, SatAdapter.Factory satFactory) {
		super(proof, justifier, monitor);
		this.satFactory_ = Preconditions.checkNotNull(satFactory);
	}

	public MinimalSubsetEnumerator<A> newJustificationEnumerator(
			final Object query) {
		return new JustificationEnumerator(query, satFactory_.create());
	}

	public MinimalSubsetEnumerator<A> newRepairEnumerator(final Object query) {
		return new RepairEnumerator(query, satFactory_.create());
	}

	class JustificationEnumerator extends Enumerator
			implements MinimalSubsetEnumerator<A> {

		JustificationEnumerator(Object query, SatAdapter solver) {
			super(query, solver);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void enumerate(MinimalSubsetEnumerator.Listener<A> listener) {
			enumerate(listener,
					(MinimalSubsetEnumerator.Listener<A>) MinimalSubsetEnumerator.Listener.DUMMY);
		}

	}

	class RepairEnumerator extends Enumerator
			implements MinimalSubsetEnumerator<A> {

		RepairEnumerator(Object query, SatAdapter solver) {
			super(query, solver);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void enumerate(MinimalSubsetEnumerator.Listener<A> listener) {
			enumerate(
					(MinimalSubsetEnumerator.Listener<A>) MinimalSubsetEnumerator.Listener.DUMMY,
					listener);
		}

	}

	class Enumerator {

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
			solver.reset();
			solver.setInterruptMonitor(getInterruptMonitor());
		}

		public void enumerate(
				MinimalSubsetEnumerator.Listener<A> justificationListener,
				MinimalSubsetEnumerator.Listener<A> repairListener) {
			Preconditions.checkNotNull(justificationListener);
			Preconditions.checkNotNull(repairListener);
			while (solver_.findModel()) {
				// unblock exactly the satisfying axioms
				for (int id : axiomIds_.getIds()) {
					if (solver_.isTrue(id)) {
						checker_.unblock(id);
					} else {
						checker_.block(id);
					}
				}
				if (checker_.isDerivable(goal_)) {
					// a new justification can be extracted
					Set<A> justification = new HashSet<>();
					// create and add a blocking clause for the justificaiton
					solver_.newClause();
					// try to remove true axioms unless it breaks the entailment
					for (int id : axiomIds_.getIds()) {
						if (!solver_.isTrue(id)) {
							continue;
						}
						checker_.block(id);
						if (!checker_.isDerivable(goal_)) {
							// axiom cannot be removed => belongs to
							// a justification
							justification.add(axiomIds_.getValue(id));
							solver_.addLiteral(-id);
							checker_.unblock(id);
						}
					}
					solver_.addClause();
					justificationListener.newMinimalSubset(justification);
				} else {
					// a new repair can be extracted
					Set<A> repair = new HashSet<>();
					// create and add a blocking clause for the repair
					solver_.newClause();
					// try to add false axioms unless it causes the entailment
					for (int id : axiomIds_.getIds()) {
						if (solver_.isTrue(id)) {
							continue;
						}
						checker_.unblock(id);
						if (checker_.isDerivable(goal_)) {
							// axiom causes entailment => belongs to
							// a repair
							repair.add(axiomIds_.getValue(id));
							solver_.addLiteral(id);
							checker_.block(id);
						}
					}
					solver_.addClause();
					repairListener.newMinimalSubset(repair);
				}
			}
		}

		public void enumerate(MinimalSubsetEnumerator.Listener<A> listener,
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
	static class SatJRJustificaitonComputation<C, I extends Inference<? extends C>, A>
			extends SatJRComputation<C, I, A> {

		public SatJRJustificaitonComputation(Proof<? extends I> proof,
				InferenceJustifier<? super I, ? extends Set<? extends A>> justifier,
				InterruptMonitor monitor, SatAdapter.Factory satFactory) {
			super(proof, justifier, monitor, satFactory);
		}

		@Override
		public MinimalSubsetEnumerator<A> newEnumerator(C query) {
			return newJustificationEnumerator(query);
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
	static class SatJRRepairComputation<C, I extends Inference<? extends C>, A>
			extends SatJRComputation<C, I, A> {

		public SatJRRepairComputation(Proof<? extends I> proof,
				InferenceJustifier<? super I, ? extends Set<? extends A>> justifier,
				InterruptMonitor monitor, SatAdapter.Factory satFactory) {
			super(proof, justifier, monitor, satFactory);
		}

		@Override
		public MinimalSubsetEnumerator<A> newEnumerator(C query) {
			return newRepairEnumerator(query);
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
	static class JustificationFactory<C, I extends Inference<? extends C>, A>
			implements MinimalSubsetsFromProofs.Factory<C, I, A> {

		private final SatAdapter.Factory satFactory_;

		JustificationFactory(SatAdapter.Factory satFactory) {
			this.satFactory_ = satFactory;
		}

		@Override
		public MinimalSubsetEnumerator.Factory<C, A> create(
				Proof<? extends I> proof,
				InferenceJustifier<? super I, ? extends Set<? extends A>> justifier,
				InterruptMonitor monitor) {
			return new SatJRJustificaitonComputation<C, I, A>(proof, justifier,
					monitor, satFactory_);
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
	static class RepairFactory<C, I extends Inference<? extends C>, A>
			implements MinimalSubsetsFromProofs.Factory<C, I, A> {

		private final SatAdapter.Factory satFactory_;

		RepairFactory(SatAdapter.Factory satFactory) {
			this.satFactory_ = satFactory;
		}

		@Override
		public MinimalSubsetEnumerator.Factory<C, A> create(
				Proof<? extends I> proof,
				InferenceJustifier<? super I, ? extends Set<? extends A>> justifier,
				InterruptMonitor monitor) {
			return new SatJRRepairComputation<C, I, A>(proof, justifier,
					monitor, satFactory_);
		}

	}

}
