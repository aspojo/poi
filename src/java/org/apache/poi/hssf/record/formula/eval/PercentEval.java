/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */

package org.apache.poi.hssf.record.formula.eval;


/**
 * Implementation of Excel formula token '%'. <p/>
 * @author Josh Micich
 */
public final class PercentEval implements OperationEval {

	public static final OperationEval instance = new PercentEval();

	private PercentEval() {
		// enforce singleton
	}

	public Eval evaluate(Eval[] args, int srcRow, short srcCol) {
		if (args.length != 1) {
			return ErrorEval.VALUE_INVALID;
		}
		double d0;
		try {
			ValueEval ve = OperandResolver.getSingleValue(args[0], srcRow, srcCol);
			d0 = OperandResolver.coerceValueToDouble(ve);
		} catch (EvaluationException e) {
			return e.getErrorEval();
		}
		return new NumberEval(d0 / 100);
	}

	public int getNumberOfOperands() {
		return 1;
	}
}
