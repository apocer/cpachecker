// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.util.predicates.pathformula.tatoformula.featureencodings.actionencodings;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.sosy_lab.cpachecker.cfa.ast.timedautomata.TaDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.timedautomata.TaVariable;
import org.sosy_lab.cpachecker.util.predicates.pathformula.tatoformula.TimedAutomatonView;
import org.sosy_lab.cpachecker.util.predicates.pathformula.tatoformula.featureencodings.BooleanVarFeatureEncoding;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.java_smt.api.BooleanFormula;

public class BooleanVarActionEncoding extends BooleanVarFeatureEncoding<TaVariable>
    implements ActionEncoding {
  private TaVariable delayAction;
  private TaVariable idleAction;
  private Map<TaDeclaration, TaVariable> localDummyActions;
  private final TimedAutomatonView automata;

  public BooleanVarActionEncoding(FormulaManagerView pFmgr, TimedAutomatonView pAutomata) {
    super(pFmgr);
    localDummyActions = new HashMap<>();
    automata = pAutomata;

    automata
        .getAllAutomata()
        .forEach(
            automaton -> {
              automaton
                  .getActions()
                  .forEach(action -> addEntry(automaton, action, action.getName()));

              var localDummyAction =
                  TaVariable.createDummyVariable("#dummy", automaton.getName(), true);
              addEntry(automaton, localDummyAction, localDummyAction.getName());
              localDummyActions.put(automaton, localDummyAction);
            });

    delayAction = createDummyEntry("delay#dummy");
    idleAction = createDummyEntry("idle#dummy");
  }

  private TaVariable createDummyEntry(String pVarName) {
    var dummyVar = TaVariable.createDummyVariable(pVarName, "global", false);
    addEntryToAllAutomata(dummyVar, pVarName);
    return dummyVar;
  }

  @Override
  public BooleanFormula makeActionEqualsFormula(
      TaDeclaration pAutomaton, int pVariableIndex, TaVariable pVariable) {
    return makeEqualsFormula(pAutomaton, pVariableIndex, pVariable);
  }

  @Override
  public BooleanFormula makeDelayActionFormula(TaDeclaration pAutomaton, int pVariableIndex) {
    return makeActionEqualsFormula(pAutomaton, pVariableIndex, delayAction);
  }

  @Override
  public BooleanFormula makeIdleActionFormula(TaDeclaration pAutomaton, int pVariableIndex) {
    return makeActionEqualsFormula(pAutomaton, pVariableIndex, idleAction);
  }

  @Override
  public BooleanFormula makeLocalDummyActionFormula(TaDeclaration pAutomaton, int pVariableIndex) {
    return makeActionEqualsFormula(pAutomaton, pVariableIndex, localDummyActions.get(pAutomaton));
  }

  @Override
  public Iterable<BooleanFormula> makeAllActionFormulas(
      int pVariableIndex, boolean pIncludeDelay, boolean pIncludeIdle) {
    var result = new HashSet<BooleanFormula>();
    var processedActions = new HashSet<TaVariable>();
    for (var automaton : automata.getAllAutomata()) {
      for (var action : automata.getActionsByAutomaton(automaton)) {
        // it is possible that automata share an action. However, the automaton definition is needed
        // for formula cration. Thus remember actions that have been processed by another automaton
        if (!processedActions.contains(action)) {
          result.add(makeActionEqualsFormula(automaton, pVariableIndex, action));
          processedActions.add(action);
        }
      }
      result.add(makeLocalDummyActionFormula(automaton, pVariableIndex));
    }

    // dummy and delay are globally unique, add only once
    if (pIncludeIdle) {
      result.add(
          makeIdleActionFormula(automata.getAllAutomata().iterator().next(), pVariableIndex));
    }

    if (pIncludeDelay) {
      result.add(
          makeDelayActionFormula(automata.getAllAutomata().iterator().next(), pVariableIndex));
    }

    return result;
  }
}
