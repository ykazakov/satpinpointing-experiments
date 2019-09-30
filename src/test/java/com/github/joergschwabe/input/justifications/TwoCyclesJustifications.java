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
package com.github.joergschwabe.input.justifications;

import java.util.Collection;
import java.util.Set;

import com.github.joergschwabe.input.TwoCycles;
import com.google.common.collect.ImmutableSet;

public class TwoCyclesJustifications extends TwoCycles {

	@Override
	public Collection<? extends Set<? extends Integer>> getExpectedResult() {
		// @formatter:off
		return ImmutableSet.of(
				ImmutableSet.of(8, 1, 4, 7),
				ImmutableSet.of(8, 1, 2),
				ImmutableSet.of(4, 8, 3)				
			);
		// @formatter:on
	}
}