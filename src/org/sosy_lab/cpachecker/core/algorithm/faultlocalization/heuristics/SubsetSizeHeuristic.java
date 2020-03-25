/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2020  Dirk Beyer
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
package org.sosy_lab.cpachecker.core.algorithm.faultlocalization.heuristics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sosy_lab.cpachecker.core.algorithm.faultlocalization.common.ErrorIndicatorSet;
import org.sosy_lab.cpachecker.core.algorithm.faultlocalization.common.FaultLocalizationHeuristicImpl;
import org.sosy_lab.cpachecker.core.algorithm.faultlocalization.common.FaultLocalizationOutput;
import org.sosy_lab.cpachecker.core.algorithm.faultlocalization.common.FaultLocalizationReason;
import org.sosy_lab.cpachecker.core.algorithm.faultlocalization.common.FaultLocalizationSetOutput;
import org.sosy_lab.cpachecker.core.algorithm.faultlocalization.common.FaultLocalizationSubsetHeuristic;

public class SubsetSizeHeuristic<I extends FaultLocalizationOutput> implements FaultLocalizationSubsetHeuristic<I> {
  @Override
  public Map<FaultLocalizationSetOutput<I>, Integer> rankSubsets(
      ErrorIndicatorSet<I> errorIndicators) {
    Map<Set<I>, Integer> mapSetToSize = new HashMap<>();
    errorIndicators.forEach(l -> mapSetToSize.put(l, l.size()));
    List<Set<I>> sorted = new ArrayList<>(mapSetToSize.keySet());
    sorted.sort(Comparator.comparingInt(l -> mapSetToSize.get(l)));

    int sizeSum = errorIndicators.stream().mapToInt(l -> l.size()).sum();
    Map<FaultLocalizationSetOutput<I>, Double> scoreMap = new HashMap<>();
    for(Set<I> set: sorted){
      FaultLocalizationSetOutput<I> temp = new FaultLocalizationSetOutput<>(set);
      double likelihood = (sizeSum-temp.size())/(double)sizeSum;
      temp.addReason(new FaultLocalizationReason("The set has a size of " + temp.size() + ".", likelihood));
      scoreMap.put(temp, likelihood);
    }
    return FaultLocalizationHeuristicImpl.scoreToRankMapSet(scoreMap);
  }
}
