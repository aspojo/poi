/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.poi.ss.usermodel;

import java.math.BigDecimal;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.format.SimpleFraction;
import org.apache.poi.ss.formula.eval.NotImplementedException;

/**
 * <p>Format class that handles Excel style fractions, such as "# #/#" and "#/###"</p>
 *
 * <p>As of this writing, this is still not 100% accurate, but it does a reasonable job
 * of trying to mimic Excel's fraction calculations.  It does not currently
 * maintain Excel's spacing.</p>
 *
 * <p>This class relies on a method lifted nearly verbatim from org.apache.math.fraction.
 *  If further uses for Commons Math are found, we will consider adding it as a dependency.
 *  For now, we have in-lined the one method to keep things simple.</p>
 */

@SuppressWarnings("serial")
public class FractionFormat extends Format {
    private static final Logger LOGGER = LogManager.getLogger(FractionFormat.class);
    private static final Pattern DENOM_FORMAT_PATTERN = Pattern.compile("(?:(#+)|(\\d+))");

    //this was chosen to match the earlier limitation of max denom power
    //it can be expanded to get closer to Excel's calculations
    //with custom formats # #/#########
    //but as of this writing, the numerators and denominators
    //with formats of that nature on very small values were quite
    //far from Excel's calculations
    private static final int MAX_DENOM_POW = 4;

    //there are two options:
    //a) an exact denominator is specified in the formatString
    //b) the maximum denominator can be calculated from the formatString
    private final int exactDenom;
    private final int maxDenom;

    private final String wholePartFormatString;

    /**
     * Single parameter ctor
     * @param denomFormatString The format string for the denominator
     */
    public FractionFormat(String wholePartFormatString, String denomFormatString) {
        this.wholePartFormatString = wholePartFormatString;

        // initialize exactDenom and maxDenom
        Matcher m = DENOM_FORMAT_PATTERN.matcher(denomFormatString);
        int tmpExact = -1;
        int tmpMax = -1;
        if (m.find()){
            if (m.group(2) != null){
                try{
                    tmpExact = Integer.parseInt(m.group(2));
                    //if the denom is 0, fall back to the default: tmpExact=100

                    if (tmpExact == 0){
                        tmpExact = -1;
                    }
                } catch (NumberFormatException e){
                    // should not happen because the pattern already verifies that this is a number,
                    // but a number larger than Integer.MAX_VALUE can cause it,
                    // so throw an exception if we somehow end up here
                    throw new IllegalStateException(e);
                }
            } else if (m.group(1) != null) {
                int len = m.group(1).length();
                len = len > MAX_DENOM_POW ? MAX_DENOM_POW : len;
                tmpMax = (int)Math.pow(10, len);
            } else {
                tmpExact = 100;
            }
        }
        if (tmpExact <= 0 && tmpMax <= 0){
            //use 100 as the default denom if something went horribly wrong
            tmpExact = 100;
        }
        exactDenom = tmpExact;
        maxDenom = tmpMax;
    }

    @SuppressWarnings("squid:S2111")
    public String format(Number num) {

        final BigDecimal doubleValue = new BigDecimal(num.doubleValue());

        final boolean isNeg = doubleValue.compareTo(BigDecimal.ZERO) < 0;

        final BigDecimal absValue = doubleValue.abs();
        final BigDecimal wholePart = new BigDecimal(absValue.toBigInteger());
        final BigDecimal decPart = absValue.remainder(BigDecimal.ONE);

        if (wholePart.add(decPart).compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }

        // if the absolute value is smaller than 1 over the exact or maxDenom
        // you can stop here and return "0"
        // reciprocal is result of an int devision ... and so it's nearly always 0
        // double reciprocal = 1/Math.max(exactDenom,  maxDenom);
        // if (absDoubleValue < reciprocal) {
        //    return "0";
        // }

        //this is necessary to prevent overflow in the maxDenom calculation
        if (decPart.compareTo(BigDecimal.ZERO) == 0){

            StringBuilder sb = new StringBuilder();
            if (isNeg){
                sb.append('-');
            }
            sb.append(wholePart);
            return sb.toString();
        }

        final SimpleFraction fract;
        try {
            //this should be the case because of the constructor
            if (exactDenom > 0){
                fract = SimpleFraction.buildFractionExactDenominator(decPart.doubleValue(), exactDenom);
            } else {
                fract = SimpleFraction.buildFractionMaxDenominator(decPart.doubleValue(), maxDenom);
            }
        } catch (RuntimeException e){
            LOGGER.atWarn().withThrowable(e).log("Can't format fraction");
            return Double.toString(doubleValue.doubleValue());
        }

        StringBuilder sb = new StringBuilder();

        //now format the results
        if (isNeg){
            sb.append('-');
        }

        //if whole part has to go into the numerator
        if (wholePartFormatString == null || wholePartFormatString.isEmpty()){
            final int fden = fract.getDenominator();
            final int fnum = fract.getNumerator();
            BigDecimal trueNum = wholePart.multiply(new BigDecimal(fden)).add(new BigDecimal(fnum));
            sb.append(trueNum.toBigInteger()).append("/").append(fden);
            return sb.toString();
        }


        //short circuit if fraction is 0 or 1
        if (fract.getNumerator() == 0){
            sb.append(wholePart);
            return sb.toString();
        } else if (fract.getNumerator() == fract.getDenominator()){
            sb.append(wholePart.add(BigDecimal.ONE));
            return sb.toString();
        }
       //as mentioned above, this ignores the exact space formatting in Excel
        if (wholePart.compareTo(BigDecimal.ZERO) > 0){
            sb.append(wholePart).append(" ");
        }
        sb.append(fract.getNumerator()).append("/").append(fract.getDenominator());
        return sb.toString();
    }

    public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
        return toAppendTo.append(format((Number)obj));
    }

    public Object parseObject(String source, ParsePosition pos) {
        throw new NotImplementedException("Reverse parsing not supported");
    }

}
