package com.github.joergschwabe;

/*-
 * #%L
 * Axiom Pinpointing Experiments
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2017 - 2019 Live Ontologies Project
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.liveontologies.puli.AssertedConclusionInference;
import org.liveontologies.puli.BaseProof;
import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Inferences;
import org.liveontologies.puli.ModifiableProof;
import org.liveontologies.puli.Producer;
import org.liveontologies.puli.Proof;
import org.liveontologies.puli.Proofs;

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
public class IntegerProofTranslator<C, I extends Inference<? extends C>, A>
		implements Producer<I> {

	private final IdProvider<C> conclusionIds_;
	private final IdProvider<A> axiomIds_;
	private final InferenceJustifier<? super I, ? extends Set<? extends A>> justifier_;
	private final ModifiableProof<Inference<? extends Integer>> result_ = new BaseProof<>();;

	IntegerProofTranslator(Proof<? extends I> proof,
			InferenceJustifier<? super I, ? extends Set<? extends A>> justifier,
			Object goal, IdProvider<C> conclusionIds, IdProvider<A> axiomIds) {
		this.conclusionIds_ = conclusionIds;
		this.axiomIds_ = axiomIds;
		this.justifier_ = justifier;
		Proofs.unfoldRecursively(proof, goal, this);
		for (int axiomId : axiomIds_.getIds()) {
			result_.produce(new AssertedConclusionInference<Integer>(axiomId));
		}
	}

	public static <C, I extends Inference<? extends C>, A> Proof<Inference<? extends Integer>> translate(
			Proof<? extends I> proof,
			InferenceJustifier<? super I, ? extends Set<? extends A>> justifier,
			Object goal, IdProvider<C> conclusionIds, IdProvider<A> axiomIds) {
		return new IntegerProofTranslator<C, I, A>(proof, justifier, goal,
				conclusionIds, axiomIds).result_;
	}

	@Override
	public void produce(I inference) {
		// translation to integer inferences
		C conclusion = inference.getConclusion();
		List<?> premises = inference.getPremises();
		Set<? extends A> justifications = justifier_
				.getJustification(inference);
		List<Integer> translatedPremises = new ArrayList<Integer>(
				premises.size() + justifications.size());
		for (C premise : inference.getPremises()) {
			translatedPremises.add(conclusionIds_.getId(premise));
		}
		for (A axiom : justifier_.getJustification(inference)) {
			translatedPremises.add(axiomIds_.getId(axiom));
		}
		int translatedConclusion = conclusionIds_.getId(conclusion);
		Inference<Integer> translatedInference = Inferences.create(
				"Integer Translation", translatedConclusion,
				translatedPremises);
		result_.produce(translatedInference);
	}

}
