package com.github.joergschwabe;

import java.util.Collection;
import java.util.Collections;
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
public class SatJustificationComputation<C, I extends Inference<? extends C>, A>
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
	public static <C, I extends Inference<? extends C>, A> MinimalSubsetsFromProofs.Factory<C, I, A> getFactory(
			SatAdapter.Factory satFactory) {
		return new SatJustificationComputation.Factory<>(satFactory);
	}

	private final SatAdapter.Factory satFactory_;

	public SatJustificationComputation(Proof<? extends I> proof,
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

		private final IdProvider<Inference<? extends Integer>> inferenceIds_;
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
			this.inferenceIds_ = new IdProvider<>(idSup);
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
			Set<Integer> unfolded = Proofs.unfoldRecursively(integerProof_,
					goal_, this);
			// add clauses for each used conclusion and its inferences
			for (int conclusion : unfolded) {
				solver_.newClause();
				solver_.addLiteral(-conclusion);
				for (Inference<? extends Integer> inf : integerProof_
						.getInferences(conclusion)) {
					solver_.addLiteral(inferenceIds_.getId(inf));
				}
				solver_.addClause();
			}
		}

		@Override
		public void produce(Inference<? extends Integer> inf) {
			int infId = inferenceIds_.getId(inf);
			for (int premise : inf.getPremises()) {
				solver_.newClause();
				solver_.addLiteral(-infId);
				solver_.addLiteral(premise);
				solver_.addClause();
			}
		}

		Proof<? extends Inference<? extends Integer>> getModelProof() {
			return new Proof<Inference<? extends Integer>>() {
				@Override
				public Collection<? extends Inference<? extends Integer>> getInferences(
						Object conclusion) {
					for (Inference<? extends Integer> inf : integerProof_
							.getInferences(conclusion)) {
						if (solver_.isTrue(inferenceIds_.getId(inf))) {
							return Collections.singleton(inf);
						}
					}
					// else
					return Collections.emptySet();
				}
			};
		}

		@Override
		public void enumerate(Listener<A> listener) {
			Preconditions.checkNotNull(listener);
			while (solver_.findModel()) {
				Proof<? extends Inference<? extends Integer>> modelProof = getModelProof();
				InferenceDerivabilityChecker<Integer, Inference<? extends Integer>> inducedChecker = new InferenceDerivabilityChecker<>(
						modelProof);
				if (inducedChecker.isDerivable(goal_)) {
					// unblock exactly the satisfying axioms
					for (int id : axiomIds_.getIds()) {
						if (solver_.isTrue(id)) {
							checker_.unblock(id);
						} else {
							checker_.block(id);
						}
					}
					// minimize justification w.r.t. to the original inferences
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
					listener.newMinimalSubset(justification);
				} else {
					// there must be a cycle in the proof => find it and block
					// it
					Set<Integer> visited = new HashSet<>();
					int next = goal_;
					Set<? extends Integer> nonDerivable = inducedChecker
							.getNonDerivableConclusions();
					// search for cycle
					while (visited.add(next)) {
						// take the first inference, it should exist since the
						// encoding is satisfiable
						Inference<? extends Integer> inf = modelProof
								.getInferences(next).iterator().next();
						for (Integer premise : inf.getPremises()) {
							if (nonDerivable.contains(premise)) {
								next = premise;
								break;
							}
						}
					}
					// record the cycle
					solver_.newClause();
					int cycleStart = next;
					do {
						Inference<? extends Integer> inf = modelProof
								.getInferences(next).iterator().next();
						solver_.addLiteral(-inferenceIds_.getId(inf));
						for (Integer premise : inf.getPremises()) {
							if (nonDerivable.contains(premise)) {
								next = premise;
								break;
							}
						}
					} while (next != cycleStart);
					solver_.addClause();
					// System.out.println("Cycle added: " + ++cycleCount_);
				}
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
			return new SatJustificationComputation<>(proof, justifier, monitor,
					satFactory_);
		}

	}

}
