/*-
 * #%L
 * Proof Utility Library
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2017 Live Ontologies Project
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
package com.github.joergschwabe.input;

import com.github.joergschwabe.ProofAndJustifierBuilder;

public abstract class Exponential
		extends BaseEnumeratorTestInput<String, Integer> {

	private static ProofAndJustifierBuilder<String, Integer> getBuilder() {

		final ProofAndJustifierBuilder<String, Integer> builder = new ProofAndJustifierBuilder<String, Integer>();

		builder.conclusion("A").axiom(0).axiom(1).add();
		builder.conclusion("A").axiom(0).axiom(2).add();

		builder.conclusion("B").premise("A").axiom(3).add();
		builder.conclusion("B").premise("A").axiom(4).add();

		return builder;
	}

	public Exponential() {
		super(getBuilder());
	}

	@Override
	public String getQuery() {
		return "B";
	}

}
