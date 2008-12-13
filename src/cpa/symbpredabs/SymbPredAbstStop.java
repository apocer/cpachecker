package cpa.symbpredabs;

import java.util.Collection;

import logging.CustomLogLevel;
import logging.LazyLogger;
import cpa.common.interfaces.AbstractDomain;
import cpa.common.interfaces.AbstractElement;
import cpa.common.interfaces.Precision;
import cpa.common.interfaces.StopOperator;
import exceptions.CPAException;

/**
 * TODO. This is currently broken
 */
public class SymbPredAbstStop implements StopOperator {

    private final SymbPredAbstDomain domain;

    SymbPredAbstStop(SymbPredAbstDomain domain) {
        this.domain = domain;
    }

    public AbstractDomain getAbstractDomain() {
        return domain;
    }

    public <AE extends AbstractElement> boolean stop(AE element,
            Collection<AE> reached, Precision prec) throws CPAException {
        for (AbstractElement e2 : reached) {
            if (stop(element, e2)) {
                return true;
            }
        }
        return false;
    }

    public boolean stop(AbstractElement element, AbstractElement reachedElement)
            throws CPAException {
        SymbPredAbstElement e = (SymbPredAbstElement)element;
        SymbPredAbstElement e2 = (SymbPredAbstElement)reachedElement;
        SymbPredAbstCPA cpa = domain.getCPA();
        AbstractFormulaManager amgr = cpa.getAbstractFormulaManager();

        // coverage test: both elements should refer to the same location,
        // both should have only an abstract formula, and the data region
        // represented by the abstract formula of e should be included
        // in that of e2
        if (e.getLocation().equals(e2.getLocation()) &&
            e.getConcreteFormula().isTrue() &&
            e2.getConcreteFormula().isTrue() &&
            amgr.entails(e.getAbstractFormula(), e2.getAbstractFormula())) {

            LazyLogger.log(CustomLogLevel.SpecificCPALevel,
                           "Element: ", e, " covered by: ", e2);

            return true;
        } else if (e.getLocation().equals(e2.getLocation()) &&
                   e.getCoveredBy() == e2) {
            // TODO Shortcut: basically, when we merge two paths after
            // an if-then-else or a loop, we set the coveredBy of the old one to
            // the new one, so that we can then detect the coverage here.
            // This has to change to something nicer in the future!!
            LazyLogger.log(CustomLogLevel.SpecificCPALevel,
                           "Element: ", e, " covered by: ", e2);

            return true;
        }

        LazyLogger.log(LazyLogger.DEBUG_1, "Element: ", e, " not covered");

        return false;
    }

}
