/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.core.algorithm.mpa;

import static org.sosy_lab.cpachecker.util.AbstractStates.isTargetState;

import java.util.Collection;
import java.util.Set;

import javax.annotation.Nonnull;

import org.sosy_lab.common.Classes;
import org.sosy_lab.common.configuration.ClassOption;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.mpa.interfaces.InitOperator;
import org.sosy_lab.cpachecker.core.algorithm.mpa.interfaces.PartitioningOperator;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Property;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGCPA;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonPrecision;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonState;
import org.sosy_lab.cpachecker.exceptions.CPAEnabledAnalysisPropertyViolationException;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.Precisions;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

@Options(prefix="analysis.mpa")
public final class MultiPropertyAlgorithm implements Algorithm {

  private final Algorithm wrapped;
  private final LogManager logger;
  private final ConfigurableProgramAnalysis cpa;

  @Option(secure=true, description = "Operator for determining the partitions of properties that have to be checked.")
  @ClassOption(packagePrefix = "org.sosy_lab.cpachecker.core.algorithm.mpa")
  @Nonnull private Class<? extends PartitioningOperator> partitionOperatorClass = DefaultPartitioningOperator.class;
  private final PartitioningOperator partitionOperator;

  @Option(secure=true, description = "Operator for initializing the waitlist after the partitioning of properties was performed.")
  @ClassOption(packagePrefix = "org.sosy_lab.cpachecker.core.algorithm.mpa")
  @Nonnull private Class<? extends InitOperator> initOperatorClass = DefaultInitOperator.class;
  private final InitOperator initOperator;

  public MultiPropertyAlgorithm(Algorithm pAlgorithm, ConfigurableProgramAnalysis pCpa,
    Configuration pConfig, LogManager pLogger)
      throws InvalidConfigurationException, CPAException {

    pConfig.inject(this);

    this.wrapped = pAlgorithm;
    this.logger = pLogger;
    this.cpa = pCpa;

    this.initOperator = createInitOperator();
    this.partitionOperator = createPartitioningOperator();
  }

  private InitOperator createInitOperator() throws CPAException, InvalidConfigurationException {
    return Classes.createInstance(InitOperator.class, initOperatorClass, new Class[] { }, new Object[] { }, CPAException.class);
  }

  private PartitioningOperator createPartitioningOperator() throws CPAException, InvalidConfigurationException {
    return Classes.createInstance(PartitioningOperator.class, partitionOperatorClass, new Class[] { }, new Object[] { }, CPAException.class);
  }

  private ImmutableSetMultimap<AbstractState, Property> identifyViolationsInRun(ReachedSet pReachedSet) {
    ImmutableSetMultimap.Builder<AbstractState, Property> result = ImmutableSetMultimap.<AbstractState, Property>builder();

    // ASSUMPTION: no "global refinement" is used! (not yet implemented for this algorithm!)

    final AbstractState e = pReachedSet.getLastState();
    if (isTargetState(e)) {
      Set<Property> violated = AbstractStates.extractViolatedProperties(e, Property.class);
      result.putAll(e, violated);
    }

    return result.build();
  }

  /**
   * Get the properties that are active in all instances of a precision!
   *    A property is not included if it is INACTIVE in one of
   *    the precisions in the given set reached.
   *
   * It MUST be EXACT or an UnderAPPROXIMATION (of the set of checked properties)
   *    to ensure SOUNDNESS of the analysis result!
   *
   * @return            Set of properties
   */
  private ImmutableSet<Property> getActiveProperties(
      final AbstractState pAbstractState,
      final ReachedSet pReachedSet) {

    Preconditions.checkNotNull(pAbstractState);
    Preconditions.checkNotNull(pReachedSet);
    Preconditions.checkState(!pReachedSet.isEmpty());

    Set<Property> properties = Sets.newHashSet();

    // Retrieve the checked properties from the abstract state
    Collection<AutomatonState> automataStates = AbstractStates.extractStatesByType(
        pAbstractState, AutomatonState.class);
    for (AutomatonState e: automataStates) {
      properties.addAll(e.getOwningAutomaton().getEncodedProperties());
    }

    // Blacklisted properties from the precision
    Precision prec = pReachedSet.getPrecision(pAbstractState);
    for (Precision p: Precisions.asIterable(prec)) {
      if (p instanceof AutomatonPrecision) {
        AutomatonPrecision ap = (AutomatonPrecision) p;
        properties.removeAll(ap.getBlacklist());
      }
    }

    return ImmutableSet.copyOf(properties);
  }

  /**
   * Get the INACTIVE properties from the set reached.
   *    (properties that are inactive in any precision)
   *
   * It MUST be EXACT or an OverAPPROXIMATION (of the set of checked properties)
   *    to ensure SOUNDNESS of the analysis result!
   *
   * @param pReachedSet
   * @return  Set of properties
   */
  private ImmutableSet<Property> getInactiveProperties(
      final ReachedSet pReachedSet) {

    ARGCPA argCpa = CPAs.retrieveCPA(cpa, ARGCPA.class);
    Preconditions.checkNotNull(argCpa, "An ARG must be constructed for this type of analysis!");

    // IMPORTANT: Ensure that an reset is performed for this information
    //  as soon the analysis (re-)starts with a new set of properties!!!

    return argCpa.getCexSummary().getDisabledProperties();
  }

  @Override
  public AlgorithmStatus run(final ReachedSet pReachedSet) throws CPAException,
      CPAEnabledAnalysisPropertyViolationException {

    AlgorithmStatus status = AlgorithmStatus.SOUND_AND_PRECISE;

    final ImmutableSet<Property> all = getActiveProperties(pReachedSet.getFirstState(), pReachedSet);
    final Set<Property> violated = Sets.newHashSet();
    final Set<Property> satisfied = Sets.newHashSet();

    final ImmutableSet<ImmutableSet<Property>> noPartitioning = ImmutableSet.of();

    ImmutableSet<ImmutableSet<Property>> checkPartitions =
        partitionOperator.partition(noPartitioning, all);

    initReached(pReachedSet, checkPartitions);

    do {

      try {
        // Run the wrapped algorithm (for example, CEGAR)
        status = status.update(wrapped.run(pReachedSet));

      } catch (InterruptedException ie) {
        // The shutdown notifier might trigger the interrupted exception
        // either because
        //    A) the resource limit for the analysis run has exceeded
        // or
        //    B) the user (or the operating system) requested a stop of the verifier.
        Preconditions.checkState(!pReachedSet.isEmpty());
      }

      // ASSUMPTION:
      //    The wrapped algorithm immediately returns
      //    for each FEASIBLE counterexample that has been found!
      //    (no global refinement)

      // Identify the properties that were violated during the last verification run
      final ImmutableSetMultimap<AbstractState, Property> runViolated;
      runViolated = identifyViolationsInRun(pReachedSet);

      if (runViolated.size() > 0) {
        // We have to perform another iteration of the algorithm
        //  to check the remaining properties
        //    (or identify more feasible counterexamples)

        // Add the properties that were violated in this run.
        violated.addAll(runViolated.values());

        // The partitioning operator might remove the violated properties
        //  if we have found sufficient counterexamples
        checkPartitions = removePropertiesFrom(checkPartitions, ImmutableSet.<Property>copyOf(runViolated.values()));

        // TODO: Just adjust the precision of the states in the waitlist

      } else {

        if (pReachedSet.getWaitlist().isEmpty()) {
          // We have reached a fixpoint for the non-blacklisted properties.

          // Properties that are still active are considered to be save!
          SetView<Property> active = Sets.difference(all, getInactiveProperties(pReachedSet));
          satisfied.addAll(active);

        } else {
          // The analysis terminated because it ran out of resources

          // It is not possible to make any statements about
          //   the satisfaction of more properties here!

          // The partitioning must take care that we verify
          //  smaller (or other) partitions in the next run!
        }

        // A new partitioning must be computed.
        Set<Property> remaining = Sets.difference(all, Sets.union(violated, satisfied));
        checkPartitions = partitionOperator.partition(noPartitioning, remaining);

        // Re-initialize the sets 'waitlist' and 'reached'
        initReached(pReachedSet, checkPartitions);
      }

      // Run as long as...
      //  ... (1) the fixpoint has not been reached
      //  ... (2) or not all properties have been checked so far.
    } while (pReachedSet.hasWaitingState()
        || Sets.difference(all, Sets.union(violated, satisfied)).size() > 0);

    // Compute the overall result:
    //    Violated properties (might have multiple counterexamples)
    //    Safe properties: Properties that were neither violated nor disabled
    //    Not fully checked properties (that were disabled)
    //        (could be derived from the precision of the leaf states)

    return status;
  }

  private void initReached(ReachedSet pReachedSet, ImmutableSet<ImmutableSet<Property>> pCheckPartitions) {
    // Delegate the initialization of the set reached (and the waitlist) to the init operator
    initOperator.init(pReachedSet, pCheckPartitions);

    // Reset the information in counterexamples, inactive properties, ...
    ARGCPA argCpa = CPAs.retrieveCPA(cpa, ARGCPA.class);
    Preconditions.checkNotNull(argCpa, "An ARG must be constructed for this type of analysis!");
    argCpa.getCexSummary().resetForNewSetOfProperties();
  }

  private ImmutableSet<ImmutableSet<Property>> removePropertiesFrom(
      ImmutableSet<ImmutableSet<Property>> pOldPartitions,
      Set<Property> pRunViolated) {

    ImmutableSet.Builder<ImmutableSet<Property>> result = ImmutableSet.<ImmutableSet<Property>>builder();

    for (ImmutableSet<Property> p: pOldPartitions) {
      result.add(ImmutableSet.<Property>copyOf(Sets.difference(p, pRunViolated)));
    }

    return result.build();
  }


}
