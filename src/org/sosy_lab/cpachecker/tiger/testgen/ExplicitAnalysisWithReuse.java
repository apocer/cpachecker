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
package org.sosy_lab.cpachecker.tiger.testgen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.core.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.ShutdownNotifier;
import org.sosy_lab.cpachecker.core.algorithm.CEGARAlgorithmWithCounterexampleInfo;
import org.sosy_lab.cpachecker.core.algorithm.CPAAlgorithm;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.LocationMappedReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.core.waitlist.Waitlist;
import org.sosy_lab.cpachecker.cpa.arg.ARGCPA;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGStatistics;
import org.sosy_lab.cpachecker.cpa.assume.AssumeCPA;
import org.sosy_lab.cpachecker.cpa.cache.CacheCPA;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackCPA;
import org.sosy_lab.cpachecker.cpa.composite.CompositeCPA;
import org.sosy_lab.cpachecker.cpa.explicit.ExplicitCPA;
import org.sosy_lab.cpachecker.cpa.explicit.ExplicitPrecision;
import org.sosy_lab.cpachecker.cpa.explicit.refiner.DelegatingExplicitRefiner;
import org.sosy_lab.cpachecker.cpa.guardededgeautomaton.GuardedEdgeAutomatonCPA;
import org.sosy_lab.cpachecker.cpa.guardededgeautomaton.productautomaton.ProductAutomatonCPA;
import org.sosy_lab.cpachecker.cpa.location.LocationCPA;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.tiger.core.CPAtiger;
import org.sosy_lab.cpachecker.tiger.fql.ecp.translators.GuardedEdgeLabel;
import org.sosy_lab.cpachecker.tiger.util.ARTReuse;
import org.sosy_lab.cpachecker.util.Precisions;
import org.sosy_lab.cpachecker.util.automaton.NondeterministicFiniteAutomaton;
import org.sosy_lab.cpachecker.util.predicates.PathChecker;
import org.sosy_lab.cpachecker.util.predicates.interpolation.CounterexampleTraceInfo;

/**
 * Explicit analysis with explicit refiner that can reuse information.
 */
public class ExplicitAnalysisWithReuse implements AnalysisWithReuse, PrecisionCallback {


  private final LocationCPA mLocationCPA;
  private final CallstackCPA mCallStackCPA;
  private final AssumeCPA mAssumeCPA;
  // private final CFAPathCPA mCFAPathCPA;
  private final ConfigurableProgramAnalysis mExplicitCPA;

  private final Configuration mConfiguration;
  private final LogManager mLogManager;
  // TODO probably make global
  private boolean mReuseART = true;

  private final PathChecker pchecker;

  public ExplicitPrecision mPrecision;
  private boolean lUseCache;
  private DelegatingExplicitRefiner expRefiner;

  public ExplicitAnalysisWithReuse(String pSourceFileName, String pEntryFunction, ShutdownNotifier shutdownNotifier,
      CFA lCFA, LocationCPA pmLocationCPA, CallstackCPA pmCallStackCPA,
      AssumeCPA pmAssumeCPA){
    mLocationCPA = pmLocationCPA;
    mCallStackCPA = pmCallStackCPA;
    mAssumeCPA = pmAssumeCPA;

    try {
      mConfiguration = CPAtiger.createConfiguration(pSourceFileName, pEntryFunction);
      mLogManager = new BasicLogManager(mConfiguration);
    } catch (InvalidConfigurationException e) {
      throw new RuntimeException(e);
    }

    // predicate abstraction CPA
    CPAFactory factory = ExplicitCPA.factory();
    factory.set(lCFA, CFA.class);
    factory.setConfiguration(mConfiguration);
    factory.setLogger(mLogManager);
    factory.set(shutdownNotifier, ShutdownNotifier.class);
    ReachedSetFactory lReachedSetFactory;
    try {
      lReachedSetFactory = new ReachedSetFactory(mConfiguration, mLogManager);
    } catch (InvalidConfigurationException e1) {
      throw new RuntimeException(e1);
    }
    factory.set(lReachedSetFactory, ReachedSetFactory.class);

    try {
      ExplicitCPA expCPA = (ExplicitCPA) factory.createInstance();


      if (lUseCache) {
        mExplicitCPA = new CacheCPA(expCPA);
      }
      else {
        mExplicitCPA = expCPA;
      }
    } catch (InvalidConfigurationException | CPAException e) {
      throw new RuntimeException(e);
    }

    // TODO change
    CPAFactory lPredicateCPAFactory = PredicateCPA.factory();
    lPredicateCPAFactory.set(lCFA, CFA.class);
    lPredicateCPAFactory.setConfiguration(mConfiguration);
    lPredicateCPAFactory.setLogger(mLogManager);
    lPredicateCPAFactory.set(shutdownNotifier, ShutdownNotifier.class);
    try {
      lReachedSetFactory = new ReachedSetFactory(mConfiguration, mLogManager);
    } catch (InvalidConfigurationException e1) {
      throw new RuntimeException(e1);
    }
    lPredicateCPAFactory.set(lReachedSetFactory, ReachedSetFactory.class);

    PredicateCPA predCPA = null;
    try {
      predCPA = (PredicateCPA) lPredicateCPAFactory.createInstance();
    } catch (InvalidConfigurationException | CPAException e) {
      throw new RuntimeException(e);
    }

    pchecker = new PathChecker(mLogManager, predCPA.getPathFormulaManager(), predCPA.getSolver());


  }

  @Override
  public Pair<Boolean, CounterexampleInfo> analyse(CFA pCFA, ReachedSet pReachedSet,
      NondeterministicFiniteAutomaton<GuardedEdgeLabel> pPreviousAutomaton, GuardedEdgeAutomatonCPA pAutomatonCPA,
      FunctionEntryNode pEntryNode, GuardedEdgeAutomatonCPA pPassingCPA) {

    LinkedList<ConfigurableProgramAnalysis> lComponentAnalyses = new LinkedList<>();
    lComponentAnalyses.add(mLocationCPA);

    lComponentAnalyses.add(mCallStackCPA);

    List<ConfigurableProgramAnalysis> lAutomatonCPAs = new ArrayList<>(2);

    if (pPassingCPA != null) {
      lAutomatonCPAs.add(pPassingCPA);
    }

    lAutomatonCPAs.add(pAutomatonCPA);

    int lProductAutomatonIndex = lComponentAnalyses.size();
    lComponentAnalyses.add(ProductAutomatonCPA.create(lAutomatonCPAs, false));

    lComponentAnalyses.add(mExplicitCPA);

    lComponentAnalyses.add(mAssumeCPA);

    ARGCPA lARTCPA;
    try {
      // create composite CPA
      CPAFactory lCPAFactory = CompositeCPA.factory();
      lCPAFactory.setChildren(lComponentAnalyses);
      lCPAFactory.setConfiguration(mConfiguration);
      lCPAFactory.setLogger(mLogManager);
      lCPAFactory.set(pCFA, CFA.class);

      ConfigurableProgramAnalysis lCPA = lCPAFactory.createInstance();

      // create ART CPA
      CPAFactory lARTCPAFactory = ARGCPA.factory();
      lARTCPAFactory.setChild(lCPA);
      lARTCPAFactory.setConfiguration(mConfiguration);
      lARTCPAFactory.setLogger(mLogManager);

      lARTCPA = (ARGCPA)lARTCPAFactory.createInstance();
    } catch (InvalidConfigurationException | CPAException e) {
      throw new RuntimeException(e);
    }

    if (expRefiner == null){
      try {
        expRefiner = DelegatingExplicitRefiner.create(lARTCPA);

        expRefiner.registerPrecisionCallback(this);

      } catch (CPAException | InvalidConfigurationException e) {
        throw new RuntimeException(e);
      }
    }



    CPAAlgorithm lBasicAlgorithm;
    try {
      lBasicAlgorithm = new CPAAlgorithm(lARTCPA, mLogManager, mConfiguration, ShutdownNotifier.create());
    } catch (InvalidConfigurationException e1) {
      throw new RuntimeException(e1);
    }

    CEGARAlgorithmWithCounterexampleInfo lAlgorithm;
    try {
      lAlgorithm = new CEGARAlgorithmWithCounterexampleInfo(lBasicAlgorithm, this.expRefiner, mConfiguration, mLogManager);
    } catch (InvalidConfigurationException | CPAException e) {
      throw new RuntimeException(e);
    }

    ARGStatistics lARTStatistics;
    try {
      lARTStatistics = new ARGStatistics(mConfiguration, lARTCPA);
    } catch (InvalidConfigurationException e) {
      throw new RuntimeException(e);
    }
    Set<Statistics> lStatistics = new HashSet<>();
    lStatistics.add(lARTStatistics);
    lAlgorithm.collectStatistics(lStatistics);

    if (mReuseART) {
      ARTReuse.modifyReachedSet(pReachedSet, pEntryNode, lARTCPA, lProductAutomatonIndex, pPreviousAutomaton, pAutomatonCPA.getAutomaton());

      if (mPrecision != null) {
        for (AbstractState lWaitlistElement : pReachedSet.getWaitlist()) {
          Precision lOldPrecision = pReachedSet.getPrecision(lWaitlistElement);
          Precision lNewPrecision = Precisions.replaceByType(lOldPrecision, mPrecision, ExplicitPrecision.class);

          pReachedSet.updatePrecision(lWaitlistElement, lNewPrecision);
        }
      }
    }
    else {
      pReachedSet = new LocationMappedReachedSet(Waitlist.TraversalMethod.BFS); // TODO why does TOPSORT not exist anymore?

      AbstractState lInitialElement = lARTCPA.getInitialState(pEntryNode);
      Precision lInitialPrecision = lARTCPA.getInitialPrecision(pEntryNode);

      pReachedSet.add(lInitialElement, lInitialPrecision);
    }

    // run the algorithm
    boolean isSound = false;
    CounterexampleInfo lCounterexampleInfo = null;
    try {
      // isSound = lAlgorithm.run(pReachedSet);
      Pair<Boolean, CounterexampleInfo> lResult = lAlgorithm.runWithCounterexample(pReachedSet);
      isSound = lResult.getFirst();
      lCounterexampleInfo = lResult.getSecond();
    } catch (CPAException | InterruptedException e) {
      throw new RuntimeException(e);
    }

    assert isSound;
    Boolean reachable = false;

    // check the counterexample
    if (lCounterexampleInfo != null && !lCounterexampleInfo.isSpurious()){

      ARGPath path = lCounterexampleInfo.getTargetPath();

      try {
        CounterexampleTraceInfo cexTrace = pchecker.checkPath(path.asEdgesList());

        if (cexTrace.isSpurious()){
          lCounterexampleInfo = CounterexampleInfo.spurious();
        } else {
          lCounterexampleInfo = CounterexampleInfo.feasible(path, cexTrace.getModel());
        }

      } catch (CPATransferException | InterruptedException e1) {
        throw new RuntimeException(e1);
      }
      reachable = true;

    }


    /* AbstractState lastState = pReachedSet.getLastState();
    if (AbstractStates.isTargetState(lastState)){
      assert ARGUtils.checkARG(pReachedSet) : "ARG and reached set do not match before refinement";
      final ARGState lastStateARG = (ARGState) lastState;
      assert lastStateARG.isTarget() : "Last element in reached is not a target state before refinement";

      ARGPath path = ARGUtils.getOnePathTo(lastStateARG);
      path.asEdgesList();

      // check feasibility of the reported error path
      try {
        CounterexampleTraceInfo cexTrace = pchecker.checkPath(path.asEdgesList());

        if (cexTrace.isSpurious()){
          lCounterexampleInfo = CounterexampleInfo.spurious();
        } else {
          lCounterexampleInfo = CounterexampleInfo.feasible(path, cexTrace.getModel());
        }

      } catch (CPATransferException | InterruptedException e1) {
        throw new RuntimeException(e1);
      }
      reachable = true;
    }*/

    assert !reachable || lCounterexampleInfo != null;
    return Pair.of(reachable, lCounterexampleInfo);
  }

  @Override
  public Precision getPrecision() {
    return mPrecision;
  }

  @Override
  public void setPrecision(Precision pNewPrec) {
    mPrecision = (ExplicitPrecision) pNewPrec;
  }
}
