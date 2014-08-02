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
package org.sosy_lab.cpachecker.core.algorithm.tiger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.CParser;
import org.sosy_lab.cpachecker.cfa.CParser.FileToParse;
import org.sosy_lab.cpachecker.cfa.CSourceOriginMapping;
import org.sosy_lab.cpachecker.cfa.ParseResult;
import org.sosy_lab.cpachecker.cfa.ast.IADeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionEntryNode;
import org.sosy_lab.cpachecker.core.ShutdownNotifier;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.testgen.util.StartupConfig;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.FQLSpecificationUtil;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.PredefinedCoverageCriteria;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ast.FQLSpecification;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.ElementaryCoveragePattern;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.SingletonECPEdgeSet;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.translators.GuardedEdgeLabel;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.translators.GuardedLabel;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.translators.InverseGuardedEdgeLabel;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.translators.ToGuardedAutomatonTranslator;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.translators.ecp.CoverageSpecificationTranslator;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.translators.ecp.IncrementalCoverageSpecificationTranslator;
import org.sosy_lab.cpachecker.core.algorithm.tiger.goals.Goal;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.Wrapper;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.reachedset.LocationMappedReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.waitlist.Waitlist;
import org.sosy_lab.cpachecker.cpa.arg.ARGCPA;
import org.sosy_lab.cpachecker.cpa.composite.CompositeCPA;
import org.sosy_lab.cpachecker.cpa.guardededgeautomaton.GuardedEdgeAutomatonCPA;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CParserException;
import org.sosy_lab.cpachecker.exceptions.PredicatedAnalysisPropertyViolationException;
import org.sosy_lab.cpachecker.util.automaton.NondeterministicFiniteAutomaton;

import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;

@Options(prefix = "tiger")
public class TigerAlgorithm implements Algorithm {

  public static String originalMainFunction = null;

  @Option(name = "fqlQuery", description = "Coverage criterion given as an FQL query")
  private String fqlQuery = PredefinedCoverageCriteria.BASIC_BLOCK_COVERAGE; // default is basic block coverage

  @Option(name = "optimizeGoalAutomata", description = "Optimize the test goal automata")
  private boolean optimizeGoalAutomata = true;

  private LogManager logger;
  private StartupConfig startupConfig;

  private ConfigurableProgramAnalysis cpa;
  private CFA cfa;

  private CoverageSpecificationTranslator mCoverageSpecificationTranslator;
  private FQLSpecification fqlSpecification;

  private Wrapper wrapper;
  private GuardedEdgeLabel mAlphaLabel;
  private GuardedEdgeLabel mOmegaLabel;
  private InverseGuardedEdgeLabel mInverseAlphaLabel;

  public TigerAlgorithm(Algorithm pAlgorithm, ConfigurableProgramAnalysis pCpa, ShutdownNotifier pShutdownNotifier,
      CFA pCfa, Configuration pConfig, LogManager pLogger) throws InvalidConfigurationException {

    startupConfig = new StartupConfig(pConfig, pLogger, pShutdownNotifier);
    startupConfig.getConfig().inject(this);

    logger = pLogger;

    cpa = pCpa;
    cfa = pCfa;

    // TODO fix: add support for wrapper code
    if (pCfa.getMainFunction().getFunctionParameters().size() != 0) {
      logger.logf(Level.SEVERE, "No wrapper code available and, therefore, no input parameters allowed at the moment!");

      throw new InvalidConfigurationException("No wrapper code available and, therefore, no input parameters allowed at the moment!");
    }
    // TODO check that originalMainFunction is not null
    mCoverageSpecificationTranslator = new CoverageSpecificationTranslator(pCfa.getFunctionHead(originalMainFunction));


    wrapper = new Wrapper(pCfa, originalMainFunction);

    mAlphaLabel = new GuardedEdgeLabel(new SingletonECPEdgeSet(wrapper.getAlphaEdge()));
    mInverseAlphaLabel = new InverseGuardedEdgeLabel(mAlphaLabel);
    mOmegaLabel = new GuardedEdgeLabel(new SingletonECPEdgeSet(wrapper.getOmegaEdge()));


    // get internal representation of FQL query
    logger.logf(Level.INFO, "FQL query string: %s", fqlQuery);

    fqlSpecification = FQLSpecificationUtil.getFQLSpecification(fqlQuery);

    logger.logf(Level.INFO, "FQL query: %s", fqlSpecification.toString());

    // TODO fix this restriction
    if (fqlSpecification.hasPassingClause()) {
      logger.logf(Level.SEVERE, "No PASSING clauses supported at the moment!");

      throw new InvalidConfigurationException("No PASSING clauses supported at the moment!");
    }
  }

  @Override
  public boolean run(ReachedSet pReachedSet) throws CPAException, InterruptedException,
      PredicatedAnalysisPropertyViolationException {

    // (ii) translate query into set of test goals
    // TODO move to constructor?
    ElementaryCoveragePattern[] lGoalPatterns = extractTestGoalPatterns(fqlSpecification);


    // (iii) do test generation for test goals ...
    // Problem: pReachedSet does not match the internal CPA structure!

    boolean wasSound = true;
    if (!testGeneration(lGoalPatterns, pReachedSet)) {
      logger.logf(Level.WARNING, "Test generation contained unsound reachability analysis runs!");
      wasSound = false;
    }

    // TODO write generated test suite and mapping to file system

    return wasSound;
  }

  private ElementaryCoveragePattern[] extractTestGoalPatterns(FQLSpecification pFQLQuery) {
    logger.logf(Level.INFO, "Extracting test goals.");


    // TODO check for (temporarily) unsupported features

    // TODO enable use of infeasibility propagation


    IncrementalCoverageSpecificationTranslator lTranslator = new IncrementalCoverageSpecificationTranslator(mCoverageSpecificationTranslator.mPathPatternTranslator);

    int lNumberOfTestGoals = lTranslator.getNumberOfTestGoals(pFQLQuery.getCoverageSpecification());
    logger.logf(Level.INFO, "Number of test goals: %d", lNumberOfTestGoals);

    Iterator<ElementaryCoveragePattern> lGoalIterator = lTranslator.translate(pFQLQuery.getCoverageSpecification());
    ElementaryCoveragePattern[] lGoalPatterns = new ElementaryCoveragePattern[lNumberOfTestGoals];

    for (int lGoalIndex = 0; lGoalIndex < lNumberOfTestGoals; lGoalIndex++) {
      lGoalPatterns[lGoalIndex] = lGoalIterator.next();
    }

    return lGoalPatterns;
  }

  private boolean testGeneration(ElementaryCoveragePattern[] pTestGoalPatterns, ReachedSet reachedSet) throws CPAException, InterruptedException {
    boolean wasSound = true;

    for (ElementaryCoveragePattern lTestGoalPattern : pTestGoalPatterns) {
      if (!testGeneration(lTestGoalPattern, reachedSet)) {
        logger.logf(Level.WARNING, "Analysis run was unsound!");
        wasSound = false;
      }
    }

    return wasSound;
  }

  private boolean testGeneration(ElementaryCoveragePattern pTestGoalPattern, ReachedSet reachedSet) throws CPAException, InterruptedException {
    ElementaryCoveragePattern lGoalPattern = pTestGoalPattern;
    Goal lGoal = constructGoal(lGoalPattern, mAlphaLabel, mInverseAlphaLabel, mOmegaLabel,  optimizeGoalAutomata);

    GuardedEdgeAutomatonCPA lAutomatonCPA = new GuardedEdgeAutomatonCPA(lGoal.getAutomaton());


    LinkedList<ConfigurableProgramAnalysis> lComponentAnalyses = new LinkedList<>();
    // TODO what is the more efficient order for the CPAs?
    lComponentAnalyses.add(lAutomatonCPA);
    lComponentAnalyses.add(cpa);

    System.out.println(cpa);

    ARGCPA lARTCPA;
    try {
      // create composite CPA
      CPAFactory lCPAFactory = CompositeCPA.factory();
      lCPAFactory.setChildren(lComponentAnalyses);
      lCPAFactory.setConfiguration(startupConfig.getConfig());
      lCPAFactory.setLogger(logger);
      lCPAFactory.set(cfa, CFA.class);

      ConfigurableProgramAnalysis lCPA = lCPAFactory.createInstance();

      // create ART CPA
      CPAFactory lARTCPAFactory = ARGCPA.factory();
      lARTCPAFactory.set(cfa, CFA.class);
      lARTCPAFactory.setChild(lCPA);
      lARTCPAFactory.setConfiguration(startupConfig.getConfig());
      lARTCPAFactory.setLogger(logger);

      lARTCPA = (ARGCPA)lARTCPAFactory.createInstance();
    } catch (InvalidConfigurationException | CPAException e) {
      throw new RuntimeException(e);
    }

    // TODO implement reuse
    ReachedSet pReachedSet = new LocationMappedReachedSet(Waitlist.TraversalMethod.BFS); // TODO why does TOPSORT not exist anymore?

    AbstractState lInitialElement = lARTCPA.getInitialState(cfa.getMainFunction());
    Precision lInitialPrecision = lARTCPA.getInitialPrecision(cfa.getMainFunction());

    pReachedSet.add(lInitialElement, lInitialPrecision);


    /*ShutdownNotifier algNotifier = ShutdownNotifier.createWithParent(startupConfig.getShutdownNotifier());

    CPAAlgorithm cpaAlg;

    try {
      cpaAlg = CPAAlgorithm.create(lARTCPA, logger, startupConfig.getConfig(), algNotifier);
    } catch (InvalidConfigurationException e1) {
      throw new RuntimeException(e1);
    }

    // TODO Problem: reachedSet does not match the internal CPA structure!
    boolean analysisWasSound = cpaAlg.run(reachedSet);*/
    boolean analysisWasSound = true;

    /*PredicateCPARefiner lRefiner;
    try {
      lRefiner = PredicateRefiner.cpatiger_create(lARTCPA, this);
    } catch (CPAException | InvalidConfigurationException e) {
      throw new RuntimeException(e);
    }

    try {
      cegarAlg = new CEGARAlgorithmWithCounterexampleInfo(cpaAlg, lRefiner, mConfiguration, mLogManager);
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
    cegarAlg.collectStatistics(lStatistics);

    if (mReuseART) {

      if (mUseSummarise){
        Set<AbstractState> set = ARTReuse.modifyReachedSet2(pReachedSet, pEntryNode, lARTCPA, lProductAutomatonIndex, pPreviousAutomaton, pAutomatonCPA.getAutomaton());

        // keep only abstractions state for predicate abstraction

        for (AbstractState s : set){
          PredicateAbstractState ps = AbstractStates.extractStateByType(s, PredicateAbstractState.class);
          assert ps != null;

          if (ps.isAbstractionState()){
            summaries.add(s);
          }
          //removedRoots.add(s);
        }

      } else {
        ARTReuse.modifyReachedSet(pReachedSet, pEntryNode, lARTCPA, lProductAutomatonIndex, pPreviousAutomaton, pAutomatonCPA.getAutomaton());
      }

      if (mPrecision != null) {
        for (AbstractState lWaitlistElement : pReachedSet.getWaitlist()) {
          Precision lOldPrecision = pReachedSet.getPrecision(lWaitlistElement);
          Precision lNewPrecision = Precisions.replaceByType(lOldPrecision, mPrecision, PredicatePrecision.class);

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

    // run algorithm with an optionall timeout
    boolean isSound = executor.execute(cegarAlg, pReachedSet, algNotifier, timelimit, TimeUnit.SECONDS);
    CounterexampleInfo cex = cegarAlg.getCex();*/

    return analysisWasSound;
  }

  /**
   * Constructs a test goal from the given pattern.
   * @param pGoalPattern
   * @param pAlphaLabel
   * @param pInverseAlphaLabel
   * @param pOmegaLabel
   * @param pUseAutomatonOptimization
   * @return
   */
  private Goal constructGoal(ElementaryCoveragePattern pGoalPattern, GuardedEdgeLabel pAlphaLabel,
      GuardedEdgeLabel pInverseAlphaLabel, GuardedLabel pOmegaLabel, boolean pUseAutomatonOptimization) {

    NondeterministicFiniteAutomaton<GuardedEdgeLabel> automaton = ToGuardedAutomatonTranslator.toAutomaton(pGoalPattern, pAlphaLabel, pInverseAlphaLabel, pOmegaLabel);
    automaton = FQLSpecificationUtil.optimizeAutomaton(automaton, pUseAutomatonOptimization);

    Goal lGoal = new Goal(pGoalPattern, automaton);

    return lGoal;
  }

  // TODO move all these wrapper related code into TigerAlgorithmUtil class
  public static final String CPAtiger_MAIN = "__CPAtiger__main";

  public static FileToParse getWrapperCFunction(CFunctionEntryNode pMainFunction) throws IOException {

    StringWriter lWrapperFunction = new StringWriter();
    PrintWriter lWriter = new PrintWriter(lWrapperFunction);

    // TODO interpreter is not capable of handling initialization of global declarations

    lWriter.println(pMainFunction.getFunctionDefinition().toASTString());
    lWriter.println();
    lWriter.println("int __VERIFIER_nondet_int();");
    lWriter.println();
    lWriter.println("void " + CPAtiger_MAIN + "()");
    lWriter.println("{");

    for (CParameterDeclaration lDeclaration : pMainFunction.getFunctionParameters()) {
      lWriter.println("  " + lDeclaration.toASTString() + ";");
    }

    for (CParameterDeclaration lDeclaration : pMainFunction.getFunctionParameters()) {
      // TODO do we need to handle lDeclaration more specifically?
      lWriter.println("  " + lDeclaration.getName() + " = __VERIFIER_nondet_int();");
    }

    lWriter.println();
    lWriter.print("  " + pMainFunction.getFunctionName() + "(");

    boolean isFirst = true;

    for (CParameterDeclaration lDeclaration : pMainFunction.getFunctionParameters()) {
      if (isFirst) {
        isFirst = false;
      }
      else {
        lWriter.print(", ");
      }

      lWriter.print(lDeclaration.getName());
    }

    lWriter.println(");");
    lWriter.println("  return;");
    lWriter.println("}");

    File f = File.createTempFile(CPAtiger_MAIN, ".c", null);
    f.deleteOnExit();

    Writer writer = null;

    try {
        writer = new BufferedWriter(new OutputStreamWriter(
              new FileOutputStream(f), "utf-8"));
        writer.write(lWrapperFunction.toString());
    } catch (IOException ex) {
      // TODO report
    } finally {
       try {writer.close();} catch (Exception ex) {}
    }

    return new FileToParse(f.getAbsolutePath(), CPAtiger_MAIN + "__");
  }

  public static ParseResult addWrapper(CParser cParser, ParseResult tmpParseResult, CSourceOriginMapping sourceOriginMapping) throws IOException, CParserException, InvalidConfigurationException, InterruptedException {
    // create wrapper code
    CFunctionEntryNode entryNode = (CFunctionEntryNode)tmpParseResult.getFunctions().get(TigerAlgorithm.originalMainFunction);

    List<FileToParse> tmpList = new ArrayList<>();
    tmpList.add(TigerAlgorithm.getWrapperCFunction(entryNode));

    ParseResult wrapperParseResult = cParser.parseFile(tmpList, sourceOriginMapping);

    // TODO add checks for consistency
    SortedMap<String, FunctionEntryNode> mergedFunctions = new TreeMap<>();
    mergedFunctions.putAll(tmpParseResult.getFunctions());
    mergedFunctions.putAll(wrapperParseResult.getFunctions());

    SortedSetMultimap<String, CFANode> mergedCFANodes = TreeMultimap.create();
    mergedCFANodes.putAll(tmpParseResult.getCFANodes());
    mergedCFANodes.putAll(wrapperParseResult.getCFANodes());

    List<Pair<IADeclaration, String>> mergedGlobalDeclarations = new ArrayList<> (tmpParseResult.getGlobalDeclarations().size() + wrapperParseResult.getGlobalDeclarations().size());
    mergedGlobalDeclarations.addAll(tmpParseResult.getGlobalDeclarations());
    mergedGlobalDeclarations.addAll(wrapperParseResult.getGlobalDeclarations());

    return new ParseResult(mergedFunctions, mergedCFANodes, mergedGlobalDeclarations, tmpParseResult.getLanguage());
  }

}
