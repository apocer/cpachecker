//#Unsafe

//@ ltl invariant positive: []AP(x > 0);

extern int __VERIFIER_nondet_int();

int x,y;

main(){
    x = __VERIFIER_nondet_int();
    y = __VERIFIER_nondet_int();
    while(x>0){
        x = x-y;
    }
}