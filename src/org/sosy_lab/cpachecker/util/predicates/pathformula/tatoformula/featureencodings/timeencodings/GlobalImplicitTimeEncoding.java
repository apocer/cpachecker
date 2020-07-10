// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.util.predicates.pathformula.tatoformula.featureencodings.timeencodings;

import static com.google.common.collect.FluentIterable.from;

import org.sosy_lab.cpachecker.cfa.ast.timedautomata.TaDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.timedautomata.TaVariable;
import org.sosy_lab.cpachecker.cfa.ast.timedautomata.TaVariableExpression;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.java_smt.api.BooleanFormula;

public class GlobalImplicitTimeEncoding extends AbstractTimeEncoding {
  private static final String DELAY_VARIABLE_NAME = "#delay";

  public GlobalImplicitTimeEncoding(FormulaManagerView pFmgr) {
    super(pFmgr);
  }

  @Override
  protected BooleanFormula makeVariableExpression(
      TaDeclaration pAutomaton, int pVariableIndex, TaVariableExpression expression) {
    var variableFormula =
        fmgr.makeVariable(CLOCK_VARIABLE_TYPE, expression.getVariable().getName(), pVariableIndex);
    var constantFormula = makeRealNumber(expression.getConstant());

    return makeVariableExpression(variableFormula, constantFormula, expression.getOperator());
  }

  @Override
  protected BooleanFormula makeResetFormula(
      TaDeclaration pAutomaton, int pVariableIndex, TaVariable pVariable) {
    var zero = fmgr.makeNumber(CLOCK_VARIABLE_TYPE, 0);
    var variable = fmgr.makeVariable(CLOCK_VARIABLE_TYPE, pVariable.getName(), pVariableIndex);
    return fmgr.makeEqual(variable, zero);
  }

  @Override
  public BooleanFormula makeInitiallyZeroFormula(TaDeclaration pAutomaton, int pVariableIndex) {
    var allClocksZero =
        from(pAutomaton.getClocks())
            .transform(clock -> makeResetFormula(pAutomaton, pVariableIndex, clock));
    return bFmgr.and(allClocksZero.toSet());
  }

  @Override
  public BooleanFormula makeTimeUpdateFormula(TaDeclaration pAutomaton, int pIndexBefore) {
    var delayVariable =
        fmgr.makeVariable(CLOCK_VARIABLE_TYPE, DELAY_VARIABLE_NAME, pIndexBefore + 1);
    var clockUpdateFormulas =
        from(pAutomaton.getClocks())
            .transform(
                clock -> {
                  var newVariable =
                      fmgr.makeVariable(CLOCK_VARIABLE_TYPE, clock.getName(), pIndexBefore + 1);
                  var oldVariable =
                      fmgr.makeVariable(CLOCK_VARIABLE_TYPE, clock.getName(), pIndexBefore);
                  return fmgr.makeEqual(newVariable, fmgr.makePlus(oldVariable, delayVariable));
                });

    var zero = fmgr.makeNumber(CLOCK_VARIABLE_TYPE, 0);
    var delayLowerBound = fmgr.makeGreaterOrEqual(delayVariable, zero, true);

    return bFmgr.and(delayLowerBound, bFmgr.and(clockUpdateFormulas.toSet()));
  }

  @Override
  public BooleanFormula makeTimeDoesNotAdvanceFormula(TaDeclaration pAutomaton, int pIndexBefore) {
    return bFmgr.makeTrue();
  }
}