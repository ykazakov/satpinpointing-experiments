
package com.github.joergschwabe;

import java.io.IOException;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.liveontologies.puli.Inference;
import org.liveontologies.puli.Proof;
import org.logicng.io.parsers.ParserException;
import org.sat4j.specs.ContradictionException;

/**
 * @author Jörg Schwabe
 *
 * @param <I> the type of the inferences returned by the proof
 *
 * @param <A> the type of the axioms
 */
public class CycleComputator {

	/**
	 * the set of inferences from which the proofs are formed
	 */
	private final Proof<Inference<? extends Integer>> proof;

	/**
	 * the current positions of iterators over conclusions
	 */
	private Deque<Iterator<Integer>> conclusionStack = new LinkedList<Iterator<Integer>>();

	/**
	 * contains all visited conclusions
	 */
	private final Deque<Integer> visited = new LinkedList<Integer>();

	/**
	 * the inferences of considered path
	 */
	private final List<Inference<? extends Integer>> inferencePath = new LinkedList<Inference<? extends Integer>>();

	/**
	 * the objects of considered path
	 */
	private final List<Integer> conclusionPath = new LinkedList<Integer>();

	/**
	 * the current considered stronly connected component
	 */
	private Set<Integer> scc = new HashSet<Integer>();

	/**
	 * contains all true inferences in model
	 */
	private Set<Inference<? extends Integer>> inferenceSet;

	/**
	 * contains all stronly connected components
	 */
	private final List<List<Integer>> sccs;
	
	public CycleComputator(final Proof<Inference<? extends Integer>> proof, int queryId) {
		this.proof = proof;
		sccs = StronglyConnectedComponentsComputation.computeComponents(proof, queryId).getComponents();
	}

	public Set<Inference<? extends Integer>> getCycle(Set<Integer> conclusionSet, Set<Inference<? extends Integer>> inferenceSet) throws IOException, ParserException, ContradictionException {
		this.inferenceSet = inferenceSet;
		for(List<Integer> scc : sccs) {
			this.scc = new HashSet<Integer>(scc);
			this.scc.retainAll(conclusionSet);
			if(this.scc.size() > 1) {
				for(Integer concl : this.scc) {
					clear();
					addObject(concl);
					Set<Inference<? extends Integer>> cycle = findCycle();
					if(cycle != null) {
						return cycle;
					}
				}
			}
		}
		return null;
	}

	private void clear() {
		conclusionStack.clear();
		inferencePath.clear();
		visited.clear();
		conclusionPath.clear();
	}

	private Set<Inference<? extends Integer>> findCycle()
			throws IOException, ParserException, ContradictionException {
		for(;;) {

			Iterator<? extends Integer> conclIter = conclusionStack.peek();
			if(conclIter == null) {
				return null;
			}

			if(conclIter.hasNext()) {
				Integer premise = conclIter.next();
				
				if(visited.contains(premise)) {
					List<Inference<? extends Integer>> cycle = inferencePath.subList(conclusionPath.indexOf(premise), inferencePath.size());
					return new HashSet<Inference<? extends Integer>>(cycle);
				}

				addObject(premise);

				continue;
			}
			conclusionStack.pop();
			visited.pop();
			conclusionPath.remove(conclusionPath.size()-1);
			inferencePath.remove(inferencePath.size()-1);
		}

	}

	private void addObject(Integer object) {		
		Deque<Inference<? extends Integer>> infDeq = new LinkedList<Inference<? extends Integer>>(this.proof.getInferences(object));
		infDeq.retainAll(inferenceSet);
		if(infDeq.isEmpty()) {
			return;
		}
		Inference<? extends Integer> inference = infDeq.pop();
		conclusionStack.push(getPremises(inference).iterator());
		visited.push(object);
		conclusionPath.add(object);
		inferencePath.add(inference);
	}

	private Set<Integer> getPremises(Inference<? extends Integer> nextInf) {
		Set<Integer> premises = new HashSet<Integer>(nextInf.getPremises());
		premises.retainAll(scc);
		return premises;
	}
	

}