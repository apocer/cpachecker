// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.sl;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverContext.ProverOptions;

public class SLTransferRelation0 extends SingleEdgeTransferRelation {

  private final LogManager logger;

  private final Solver solver;
  private final PathFormulaManager pfm;

  private SLState state;

  public SLTransferRelation0(
      LogManager pLogger,
      Solver pSolver,
      PathFormulaManager pPfm) {
    logger = pLogger;
    solver = pSolver;
    pfm = pPfm;
  }

  @Override
  public Collection<? extends AbstractState>
      getAbstractSuccessorsForEdge(AbstractState pState, Precision pPrecision, CFAEdge pCfaEdge)
          throws CPATransferException, InterruptedException {
    state = ((SLState) pState).copyWithoutErrors();
    pfm.setContext(state);
    pfm.makeAnd(state.getPathFormula(), pCfaEdge);

    String info = "";
    info += pCfaEdge.getCode() + "\n";
    info += state + "\n";
    info += "---------------------------";
    logger.log(Level.INFO, info);
    if (pCfaEdge instanceof AssumeEdge) {
      // return handleAssumption();
    }
    return ImmutableList.of(state);

  }

  private List<SLState>
      handleAssumption() {
    ProverEnvironment prover = solver.newProverEnvironment(ProverOptions.ENABLE_SEPARATION_LOGIC);
    boolean unsat = false;
    try {
      BooleanFormula constraint = state.getPathFormula().getFormula();
      prover.addConstraint(constraint);
      unsat = prover.isUnsat();
    } catch (Exception e) {
      logger.log(Level.SEVERE, e.getMessage());
    }
    if (unsat) {
      return Collections.emptyList();
    }
    return ImmutableList.of(state);
  }
}
