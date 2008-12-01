package cpa.symbpredabs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Vector;

import logging.CustomLogLevel;
import logging.LazyLogger;

import cmdline.CPAMain;

import cfa.objectmodel.CFAFunctionDefinitionNode;

import cpa.common.interfaces.AbstractDomain;
import cpa.common.interfaces.AbstractElement;
import cpa.common.interfaces.ConfigurableProgramAnalysis;
import cpa.common.interfaces.MergeOperator;
import cpa.common.interfaces.StopOperator;
import cpa.common.interfaces.TransferRelation;
import cpa.symbpredabs.mathsat.BDDMathsatAbstractFormulaManager;
import cpa.symbpredabs.mathsat.MathsatPredicateParser;
import cpa.symbpredabs.mathsat.MathsatSymbolicFormulaManager;

/**
 * TODO. This is currently broken
 */
public class SymbPredAbstCPA implements ConfigurableProgramAnalysis {

    private AbstractDomain abstractDomain;
    private MergeOperator mergeOperator;
    private StopOperator stopOperator;
    private TransferRelation transferRelation;

    private PredicateMap predicateMap;
    private SymbolicFormulaManager formulaManager;
    private AbstractFormulaManager abstractManager;

    private SymbPredAbstCPA() {
        SymbPredAbstDomain domain = new SymbPredAbstDomain(this);
        abstractDomain = domain;
        mergeOperator = new SymbPredAbstMerge(domain);
        stopOperator = new SymbPredAbstStop(domain);
        transferRelation = new SymbPredAbstTransfer(domain);
        Collection<Predicate> preds = null;
        formulaManager = new MathsatSymbolicFormulaManager();
        abstractManager = new BDDMathsatAbstractFormulaManager();
        MathsatPredicateParser p = new MathsatPredicateParser(
                (MathsatSymbolicFormulaManager)formulaManager,
                (BDDMathsatAbstractFormulaManager)abstractManager);
        try {
            String pth = CPAMain.cpaConfig.getProperty("predicates.path");
            File f = new File(pth, "predicates.msat");
            InputStream in = new FileInputStream(f);
            preds = p.parsePredicates(in);
        } catch (IOException e) {
            e.printStackTrace();
            preds = new Vector<Predicate>();
        }
        predicateMap = new FixedPredicateMap(preds);
    }

    /**
     * Constructor conforming to the signature required by CompositeCPA
     * WARNING! Every time this is invoked, this sets theInstance to the
     * created object
     * @param s1
     * @param s2
     */
    public SymbPredAbstCPA(String s1, String s2) {
        this();
        assert(theInstance == null);
        theInstance = this;
    }

    public PredicateMap getPredicateMap() { return predicateMap; }

    public SymbolicFormulaManager getFormulaManager() { return formulaManager; }
    public AbstractFormulaManager getAbstractFormulaManager() {
        return abstractManager;
    }

    public AbstractDomain getAbstractDomain() {
        return abstractDomain;
    }

    public AbstractElement getInitialElement(CFAFunctionDefinitionNode node) {
        LazyLogger.log(CustomLogLevel.SpecificCPALevel,
                "Getting initial element from node: ", node.getNodeNumber());

        return new SymbPredAbstElement(node, formulaManager.makeTrue(), null);
    }

    public MergeOperator getMergeOperator() {
        return mergeOperator;
    }

    public StopOperator getStopOperator() {
        return stopOperator;
    }

    public TransferRelation getTransferRelation() {
        return transferRelation;
    }

    private static SymbPredAbstCPA theInstance = null;

    public static SymbPredAbstCPA getInstance() {
//        if (theInstance == null) {
//            theInstance = new SymbPredAbstCPA();
//        }
        assert(theInstance != null);
        return theInstance;
    }
}
