package com.github.joergschwabe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
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
import org.liveontologies.puli.pinpointing.PriorityComparator;
import org.logicng.io.parsers.ParserException;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.TimeoutException;

import com.google.common.base.Preconditions;

/**
 * @author Jörg Schwabe
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of conclusions used in inferences
 * @param <I>
 *            the type of inferences used in the proof
 * @param <A>
 *            the type of axioms used by the inferences
 */
class Sat4jJustificationEnumerator<C, I extends Inference<? extends C>, A>
		implements MinimalSubsetEnumerator<A>,
		Producer<Inference<? extends Integer>> {

	private final Collection<Inference<? extends Integer>> inferences_;
	private final InterruptMonitor interruptMonitor_;
	private final ISolver solver_;
	private final IdProvider<A, I> idProvider_ = new IdProvider<>();
	private final SatClauseHandlerSat4j<I, A> satClauseHandler_;
	private final CycleComputator cycleComputator_;
	private Listener<A> listener_;

	Sat4jJustificationEnumerator(Object query, Proof<? extends I> proof,
			InferenceJustifier<? super I, ? extends Set<? extends A>> justifier,
			InterruptMonitor interruptMonitor, ISolver solver) {
		this.interruptMonitor_ = interruptMonitor;
		this.solver_ = solver;		
		int queryId = idProvider_.getConclusionId(query);
		this.inferences_ = new ArrayList<Inference<? extends Integer>>();
		IntegerProofTranslator<C, I, A> proofTranslator = new IntegerProofTranslator<C, I, A>(
				proof, justifier);
		Proof<Inference<? extends Integer>> translatedProof = proofTranslator
				.getTranslatedProof(idProvider_, query);
		Proofs.unfoldRecursively(translatedProof, queryId, this);
		this.cycleComputator_ = new CycleComputator(translatedProof, queryId);
		this.satClauseHandler_ = new SatClauseHandlerSat4j<I, A>(idProvider_,
				new InferenceDerivabilityChecker<Object, Inference<?>>(
						proofTranslator.getTranslatedProofDiv(idProvider_)),
				queryId, solver_);
	}

	@Override
	public void enumerate(Listener<A> listener,
			PriorityComparator<? super Set<A>, ?> unused) {
		enumerate(listener);
	}

	@Override
	public void enumerate(Listener<A> listener) {
		Preconditions.checkNotNull(listener);
		this.listener_ = listener;
		solver_.reset();

		try {
			for (Inference<? extends Integer> inf : inferences_) {
				satClauseHandler_.addInfImplicationToSolver(inf);
			}
			satClauseHandler_.translateQuery();
			satClauseHandler_.addConclusionInferencesClauses();
		} catch (ContradictionException e) {
			return;
		}

		try {
			compute();

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void compute() throws ContradictionException, TimeoutException,
			ParserException, IOException {
		final ISolver solver = satClauseHandler_.getISolver();
		Thread interruptMonitorThread = new Thread(new Runnable() {

			@Override
			public void run() {
				for (;;) {
					if (interruptMonitor_.isInterrupted()) {
						solver.expireTimeout();
						Thread.currentThread().interrupt();
					}
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						return;
					}
				}
			}
		});
		interruptMonitorThread.start();
		try {
			Set<Integer> axiomSet;
			Set<Integer> conclusionSet;
			Set<Inference<? extends Integer>> inferenceSet;
			Set<Integer> justification_int;
			Set<A> justification;

			while (solver.isSatisfiable()) {
				int[] list = solver.model();

				axiomSet = satClauseHandler_.getPositiveOntologieAxioms(list);

				if (satClauseHandler_.isQueryDerivable(axiomSet)) {
					if (axiomSet.isEmpty()) {
						listener_.newMinimalSubset(new HashSet<A>());
						break;
					}

					justification_int = satClauseHandler_
							.computeJustification(axiomSet);

					try {
						satClauseHandler_
								.pushNegClauseToSolver(justification_int);
					} catch (ContradictionException e) {
						justification = satClauseHandler_
								.translateToAxioms(justification_int);

						listener_.newMinimalSubset(justification);

						break;
					}

					justification = satClauseHandler_
							.translateToAxioms(justification_int);

					listener_.newMinimalSubset(justification);
				} else {
					conclusionSet = satClauseHandler_
							.getPositiveConclusions(list);

					inferenceSet = satClauseHandler_
							.getPositiveInferences(list);

					Set<Inference<? extends Integer>> cycle = cycleComputator_
							.getCycle(conclusionSet, inferenceSet);

					satClauseHandler_.addCycleClause(cycle);
				}

				if (interruptMonitor_.isInterrupted()) {
					break;
				}
			}

		} finally {
			interruptMonitorThread.interrupt();
		}
	}

	@Override
	public void produce(Inference<? extends Integer> inference) {
		idProvider_.addConclusionInference(inference);
		inferences_.add(inference);
	}
}