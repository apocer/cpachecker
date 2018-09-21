/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.hybrid.util;

import java.util.HashMap;
import java.util.Map;

import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.hybrid.ValueAnalysisHybridStrengthenOperator;
import org.sosy_lab.cpachecker.cpa.hybrid.abstraction.HybridStrengthenOperator;
import org.sosy_lab.cpachecker.cpa.hybrid.exception.UnsupportedStateException;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisState;

public final class StrengthenOperatorFactory
{

    // cache
    private static Map<String, HybridStrengthenOperator<? extends AbstractState>> operatorMap;

    static
    {
        operatorMap = new HashMap<>();
    }

    // static factory class will not be instantiated
    private StrengthenOperatorFactory() {}

    @SuppressWarnings("unchecked")
    public static HybridStrengthenOperator<? extends AbstractState> ProvideStrenghtenOperator(AbstractState state)
        throws UnsupportedStateException
    {
        // first check if the 
        String stateClassName = state.getClass().getName();
        if(operatorMap.containsKey(stateClassName))
        {
            return operatorMap.get(stateClassName);
        }

        // check for new domain states

        if(state instanceof ValueAnalysisState)
        {
            return pushAndReturn(
                new ValueAnalysisHybridStrengthenOperator(),
                stateClassName);
        }

        throw new UnsupportedStateException(String.format("The state %s is not supported for HybridAnalysis Strengthening Operator.", state.toString()));
    }

    // assume existence in map is already checked
    private static HybridStrengthenOperator<? extends AbstractState> 
        pushAndReturn(
            HybridStrengthenOperator<? extends AbstractState> operator,
            String key)
    {
        operatorMap.put(key, operator);
        return operator;
    }
}