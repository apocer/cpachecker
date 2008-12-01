package cpa.symbpredabs.mathsat;

import cpa.symbpredabs.Predicate;

/**
 * A predicate represented as a BDD variable
 *
 * @author Alberto Griggio <alberto.griggio@disi.unitn.it>
 */
public class BDDPredicate implements Predicate {
    private int bdd; // this is the BDD representing this single variable.
                     // That is, a node with variable varindex and two
                     // children 0 and 1
    private int varindex; // this is the variable itself

    public BDDPredicate(int var, int idx) {
        bdd = var;
        varindex = idx;
    }

    public int getBDD() {
        return bdd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o instanceof BDDPredicate) {
            return varindex == ((BDDPredicate)o).varindex;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return varindex;
    }

    @Override
    public String toString() {
        return "BDD(" + varindex + ")";
    }
}
