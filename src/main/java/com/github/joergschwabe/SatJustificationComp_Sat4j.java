package com.github.joergschwabe;

import java.util.Set;

import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Proof;
import org.liveontologies.puli.pinpointing.InterruptMonitor;
import org.liveontologies.puli.pinpointing.MinimalSubsetEnumerator;
import org.liveontologies.puli.pinpointing.MinimalSubsetsFromProofs;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.specs.ISolver;

/**
 * 
 * @author Jörg Schwabe
 *
 * @param <C>
 *            the type of conclusions used in inferences
 * @param <I>
 *            the type of inferences used in the proof
 * @param <A>
 *            the type of axioms used by the inferences
 */
public class SatJustificationComp_Sat4j<C, I extends Inference<? extends C>, A>
		extends MinimalSubsetsFromProofs<C, I, A> implements InterruptMonitor {

	private static final SatJustificationComp_Sat4j.Factory<?, ?, ?> FACTORY_ = new Factory<Object, Inference<?>, Object>();

	@SuppressWarnings("unchecked")
	public static <C, I extends Inference<? extends C>, A> MinimalSubsetsFromProofs.Factory<C, I, A> getFactory() {
		return (Factory<C, I, A>) FACTORY_;
	}

	protected SatJustificationComp_Sat4j(final Proof<? extends I> proof,
			final InferenceJustifier<? super I, ? extends Set<? extends A>> justifier,
			final InterruptMonitor monitor) {
		super(proof, justifier, monitor);
	}

	// Object query, Proof<? extends I> proof,
	// InferenceJustifier<? super I, ? extends Set<? extends A>> justifier,
	// InterruptMonitor interruptMonitor, ISolver solver

	@Override
	public boolean isInterrupted() {
		return super.isInterrupted();
	}

	
	protected MinimalSubsetEnumerator<A> newEnumerator(final Object query, ISolver solver) {
		return new Sat4jJustificationEnumerator<C, I, A>(query, getProof(),
				getInferenceJustifier(), this, solver);
	}
	
	@Override
	public MinimalSubsetEnumerator<A> newEnumerator(final Object query) {
		return newEnumerator(query, SolverFactory.newDefault());
	}

	/**
	 * The factory.
	 * 
	 * @author Jörg Schwabe
	 *
	 * @param <C>
	 *            the type of conclusions used in inferences
	 * @param <I>
	 *            the type of inferences used in the proof
	 * @param <A>
	 *            the type of axioms used by the inferences
	 */
	private static class Factory<C, I extends Inference<? extends C>, A>
			implements MinimalSubsetsFromProofs.Factory<C, I, A> {

		@Override
		public MinimalSubsetEnumerator.Factory<C, A> create(
				final Proof<? extends I> proof,
				final InferenceJustifier<? super I, ? extends Set<? extends A>> justifier,
				final InterruptMonitor monitor) {
			return new SatJustificationComp_Sat4j<C, I, A>(proof, justifier,
					monitor);
		}

	}

}
