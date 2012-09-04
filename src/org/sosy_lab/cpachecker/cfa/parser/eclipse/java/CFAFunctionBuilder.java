/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
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
package org.sosy_lab.cpachecker.cfa.parser.eclipse.java;

import static com.google.common.base.Preconditions.checkState;
import static org.sosy_lab.cpachecker.cfa.CFACreationUtils.isReachableNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AssertStatement;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cfa.CFACreationUtils;
import org.sosy_lab.cpachecker.cfa.ast.AAssignment;
import org.sosy_lab.cpachecker.cfa.ast.AExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.AExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.AIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.AInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.AParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.AReturnStatement;
import org.sosy_lab.cpachecker.cfa.ast.AVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.CFileLocation;
import org.sosy_lab.cpachecker.cfa.ast.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.IADeclaration;
import org.sosy_lab.cpachecker.cfa.ast.IAExpression;
import org.sosy_lab.cpachecker.cfa.ast.IARightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.IASimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.IAStatement;
import org.sosy_lab.cpachecker.cfa.ast.IAstNode;
import org.sosy_lab.cpachecker.cfa.ast.java.JBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JBooleanLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JClassInstanzeCreation;
import org.sosy_lab.cpachecker.cfa.ast.java.JExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JFieldDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.java.JIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JMethodDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.java.JReferencedMethodInvocationExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.ADeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.AReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.CLabelNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.cfa.types.AFunctionType;
import org.sosy_lab.cpachecker.cfa.types.java.JType;
import org.sosy_lab.cpachecker.util.CFATraversal;
import org.sosy_lab.cpachecker.util.CFAUtils;

import com.google.common.collect.ImmutableList;

/**
 * Builder to traverse AST.
 *
 */
class CFAFunctionBuilder extends ASTVisitor {

  private static final boolean VISIT_CHILDS = true;

  private static final boolean SKIP_CHILDS = false;

  private static final int ONLY_EDGE = 0;

  // Data structure for maintaining our scope stack in a function
  private final Deque<CFANode> locStack = new ArrayDeque<CFANode>();

  // Data structures for handling loops & else conditions
  private final Deque<CFANode> loopStartStack = new ArrayDeque<CFANode>();
  private final Deque<CFANode> loopNextStack  = new ArrayDeque<CFANode>(); // For the node following the current if / while block
  private final Deque<CFANode> elseStack      = new ArrayDeque<CFANode>();

  // Data structure for handling switch-statements
  private final Deque<IAExpression> switchExprStack =
    new ArrayDeque<IAExpression>();
  private final Deque<CFANode> switchCaseStack = new ArrayDeque<CFANode>();

  // Data structures for label , continue , break
  private final Map<String, CLabelNode> labelMap = new HashMap<String, CLabelNode>();
  //private final Multimap<String, CFANode> gotoLabelNeeded = ArrayListMultimap.create();

  // Data structures for handling method declarations
  private FunctionEntryNode cfa = null;
  private final Set<CFANode> cfaNodes = new HashSet<CFANode>();


  private final Scope scope;
  private final ASTConverter astCreator;

  private final List<Pair<IADeclaration, String>> nonStaticFieldDeclarations;

  private final LogManager logger;

  private final TypeHierachie typeHierachie;


  public CFAFunctionBuilder(LogManager pLogger, boolean pIgnoreCasts,
      Scope pScope, ASTConverter pAstCreator, List<Pair<IADeclaration, String>> pNonStaticFieldDeclarations, TypeHierachie pTypeHierachie) {

    logger = pLogger;
    scope = pScope;
    astCreator = pAstCreator;
    nonStaticFieldDeclarations = pNonStaticFieldDeclarations;
    typeHierachie = pTypeHierachie;
  }

  FunctionEntryNode getStartNode() {
    checkState(cfa != null);
    return cfa;
  }

  Set<CFANode> getCfaNodes() {
    checkState(cfa != null);
    return cfaNodes;
  }



  //Method to handle visiting a parsing problem.  Hopefully none exist
  /* (non-Javadoc)
   * @see org.eclipse.cdt.core.dom.ast.c.ASTVisitor#visit(org.eclipse.cdt.core.dom.ast.c.CProblem)
   */
  @Override
  public void preVisit(ASTNode problem) {

    if(ASTNode.RECOVERED == problem.getFlags() || ASTNode.MALFORMED == problem.getFlags() )
    throw new CFAGenerationRuntimeException("Parse Error", problem);
  }

  /* (non-Javadoc)
   * @see org.eclipse.cdt.core.dom.ast.c.ASTVisitor#visit(org.eclipse.cdt.core.dom.ast.c.IADeclaration)
   */
  @Override
  public boolean visit(MethodDeclaration declaration) {

    if (locStack.size() != 0) {
      throw new CFAGenerationRuntimeException("Nested function declarations?");
    }

    assert cfa == null;

    final JMethodDeclaration fdef = astCreator.convert(declaration);

    handleMethodDeclaration(fdef);

    //If Declaration is Constructor add non static field member Declarations
    if(declaration.isConstructor()){
      addNonStaticFieldMember();
    }

    // Check if method has a body. Interface methods are always marked with abstract.
    if(!fdef.isAbstract() && !fdef.isNative()){
      // Stop , and manually go to Block, to protect parameter variables to be processed
      // more than one time
      declaration.getBody().accept(this);
    }

    return SKIP_CHILDS;
  }


  private void addNonStaticFieldMember() {
    for(Pair<IADeclaration, String> decl : nonStaticFieldDeclarations){
      List<IADeclaration> arr = new ArrayList<IADeclaration>();
      arr.add(decl.getFirst());

      locStack.push( addDeclarationsToCFA( arr,  decl.getFirst().getFileLocation().getStartingLineNumber(), decl.getSecond(), locStack.poll()));

    }
  }

  private void handleMethodDeclaration( JMethodDeclaration fdef) {



    final String nameOfFunction = fdef.getName();
    assert !nameOfFunction.isEmpty();

    scope.enterFunction(fdef);

    final List<AParameterDeclaration> parameters =   ((AFunctionType) fdef.getType()).getParameters();
    final List<String> parameterNames = new ArrayList<String>(parameters.size());

    for (AParameterDeclaration param : parameters) {
      scope.registerDeclaration(param); // declare parameter as local variable
      parameterNames.add(param.getName());
    }

    final FunctionExitNode returnNode = new FunctionExitNode(fdef.getFileLocation().getEndingLineNumber(), nameOfFunction);
    cfaNodes.add(returnNode);

    final FunctionEntryNode startNode = new FunctionEntryNode(
        fdef.getFileLocation().getStartingLineNumber(), fdef, returnNode, parameterNames);
    cfaNodes.add(startNode);
    cfa = startNode;

    final CFANode nextNode = new CFANode(fdef.getFileLocation().getStartingLineNumber(), nameOfFunction);
    cfaNodes.add(nextNode);
    locStack.add(nextNode);

    final BlankEdge dummyEdge = new BlankEdge("", fdef.getFileLocation().getStartingLineNumber(),
        startNode, nextNode, "Function start dummy edge");
    addToCFA(dummyEdge);


  }


  @Override
  public boolean visit(final VariableDeclarationStatement sd) {

    assert (locStack.size() > 0) : "not in a function's scope";

    CFANode prevNode = locStack.pop();

    CFANode nextNode = addDeclarationsToCFA(sd, prevNode);

    assert nextNode != null;
    locStack.push(nextNode);

    return SKIP_CHILDS;
  }


   @Override
  public boolean visit(final SingleVariableDeclaration sd) {

    assert (locStack.size() > 0) : "not in a function's scope";

    CFANode prevNode = locStack.pop();

    CFANode nextNode = addDeclarationsToCFA(sd, prevNode);

    assert nextNode != null;
    locStack.push(nextNode);

    return SKIP_CHILDS;
  }


  private void handleReturnFromObject(CFileLocation fileloc, String rawSignature, ITypeBinding cb) {

     assert cb.isClass() : cb.getName() + "is no Object Return";

     CFANode prevNode = locStack.pop();
     FunctionExitNode functionExitNode = cfa.getExitNode();


     AReturnStatement cfaObjectReturn = astCreator.getConstructorObjectReturn(cb, fileloc);



     // If return expression is function
     prevNode = handleSideassignments(prevNode, rawSignature, fileloc.getStartingLineNumber());


     //TODO toString() not allowed
     AReturnStatementEdge edge = new AReturnStatementEdge("",
      cfaObjectReturn , fileloc.getStartingLineNumber(), prevNode, functionExitNode);
     addToCFA(edge);

     CFANode nextNode = new CFANode(fileloc.getEndingLineNumber(),
         cfa.getFunctionName());
     cfaNodes.add(nextNode);
     locStack.push(nextNode);

   }

  @Override
  public void endVisit(MethodDeclaration declaration) {


    // If declaration is Constructor, add return for Object

      if(declaration.isConstructor()){

        CFileLocation fileloc = astCreator.getFileLocation(declaration);
        handleReturnFromObject(fileloc, declaration.toString(), declaration.resolveBinding().getDeclaringClass());
      }

      handleEndVisitMethodDeclaration();

  }


private void handleEndVisitMethodDeclaration(){


  if (locStack.size() != 1) {
    throw new CFAGenerationRuntimeException("Depth wrong. Geoff needs to do more work");
  }

  CFANode lastNode = locStack.pop();

  if (isReachableNode(lastNode)) {
    BlankEdge blankEdge = new BlankEdge("",
        lastNode.getLineNumber(), lastNode, cfa.getExitNode(), "default return");
    addToCFA(blankEdge);
  }



  Set<CFANode> reachableNodes = CFATraversal.dfs().collectNodesReachableFrom(cfa);


  Iterator<CFANode> it = cfaNodes.iterator();
  while (it.hasNext()) {
    CFANode n = it.next();

    if (!reachableNodes.contains(n)) {
      // node was created but isn't part of CFA (e.g. because of dead code)
      it.remove(); // remove n from currentCFANodes
    }
  }

  scope.leaveFunction();

}


  /**
   * This method creates statement and declaration edges for all sideassignments.
   *
   * @return the nextnode
   */
  private CFANode handleSideassignments(CFANode prevNode, String rawSignature, int filelocStart) {
    CFANode nextNode = null;
    while(astCreator.numberOfSideAssignments() > 0){
      nextNode = new CFANode(filelocStart, cfa.getFunctionName());
      cfaNodes.add(nextNode);

      IAstNode sideeffect = astCreator.getNextSideAssignment();

      createSideAssignmentEdges(prevNode, nextNode, rawSignature, filelocStart, sideeffect);
      prevNode = nextNode;
    }
    return prevNode;
  }

  private void handleSideassignments(CFANode prevNode, String rawSignature, int filelocStart, CFANode lastNode) {
    CFANode nextNode = null;

    while (astCreator.numberOfPreSideAssignments() > 0){
      IAstNode sideeffect = astCreator.getNextPreSideAssignment();

      if (astCreator.numberOfPreSideAssignments() > 0) {
        nextNode = new CFANode(filelocStart, cfa.getFunctionName());
        cfaNodes.add(nextNode);
      } else {
        nextNode = lastNode;
      }

      createSideAssignmentEdges(prevNode, nextNode, rawSignature, filelocStart, sideeffect);
      prevNode = nextNode;
    }
  }


  /**
   * Submethod from handleSideassignments, takes an IAstNode and depending on its
   * type creates an edge.
   */
  private void createSideAssignmentEdges(CFANode prevNode, CFANode nextNode, String rawSignature,
      int filelocStart, IAstNode sideeffect) {
    CFAEdge previous;
    if(sideeffect instanceof org.sosy_lab.cpachecker.cfa.ast.IAStatement) {
      previous = new AStatementEdge(rawSignature, (org.sosy_lab.cpachecker.cfa.ast.IAStatement)sideeffect, filelocStart, prevNode, nextNode);
    } else if (sideeffect instanceof AAssignment) {
      previous = new AStatementEdge(rawSignature, (org.sosy_lab.cpachecker.cfa.ast.IAStatement)sideeffect, filelocStart, prevNode, nextNode);
    } else if (sideeffect instanceof AIdExpression) {
      previous = new AStatementEdge(rawSignature, new AExpressionStatement(sideeffect.getFileLocation(), (IAExpression) sideeffect), filelocStart, prevNode, nextNode);
    } else {
      previous = new ADeclarationEdge(rawSignature, filelocStart, prevNode, nextNode, (IADeclaration) sideeffect);
    }
    addToCFA(previous);
  }





  /**
   * This method takes a list of Declarations and adds them to the CFA.
   * The edges are inserted after startNode.
   * @return the node after the last of the new declarations
   */


  private CFANode addDeclarationsToCFA(final List<IADeclaration> declList, int filelocStart ,String rawSignature , CFANode prevNode) {

    CFANode middleNode = new CFANode(filelocStart, cfa.getFunctionName());
    cfaNodes.add(middleNode);

    if (astCreator.getConditionalExpression() != null) {
      ConditionalExpression condExp = astCreator.getConditionalExpression();
      astCreator.resetConditionalExpression();
      JIdExpression statement = astCreator.getConditionalTemporaryVariable();
      handleTernaryExpression(condExp, prevNode, middleNode, statement);
    } else {
      middleNode = prevNode;
    }

    prevNode = handleSideassignments(prevNode, rawSignature, declList.get(0).getFileLocation().getStartingLineNumber());

    // create one edge for every declaration
    for (IADeclaration newD : declList) {



      CFANode nextNode = new CFANode(declList.get(0).getFileLocation().getStartingLineNumber(), cfa.getFunctionName());
      cfaNodes.add(nextNode);

      final ADeclarationEdge edge = new ADeclarationEdge(rawSignature, declList.get(0).getFileLocation().getStartingLineNumber(),
          prevNode, nextNode, newD);
      addToCFA(edge);

      prevNode = nextNode;


      if (newD instanceof AVariableDeclaration) {
        scope.registerDeclaration(newD);

        CInitializer initializer = ((JVariableDeclaration)newD).getInitializer();

        // resolve Boolean Initializer for easier analysis
        if( initializer instanceof AInitializerExpression && astCreator.isBooleanExpression( ((AInitializerExpression)initializer).getExpression()) && !(((AInitializerExpression)initializer).getExpression() instanceof JBooleanLiteralExpression) ) {

          CFANode afterResolvedBooleanExpressionNode = new CFANode(newD.getFileLocation().getStartingLineNumber(), cfa.getFunctionName());
          cfaNodes.add(afterResolvedBooleanExpressionNode);

          AInitializerExpression booleanInitializer = (AInitializerExpression) initializer;
          resolveBooleanAssignment( booleanInitializer.getExpression() , new JIdExpression(newD.getFileLocation(), (JType) newD.getType(), newD.getName(), newD), prevNode, afterResolvedBooleanExpressionNode);
          prevNode = afterResolvedBooleanExpressionNode;
        }

      } else if (newD instanceof AFunctionDeclaration) {
        scope.registerFunctionDeclaration((AFunctionDeclaration) newD);
      }

    }


    CFANode nextNode = null;
    while (astCreator.numberOfPostSideAssignments() > 0) {
        nextNode = new CFANode(filelocStart, cfa.getFunctionName());
        cfaNodes.add(nextNode);

        IAstNode sideeffect = astCreator.getNextPostSideAssignment();

        createSideAssignmentEdges(prevNode, nextNode, rawSignature, filelocStart, sideeffect);
        prevNode = nextNode;

    }

    return prevNode;
  }



  private CFANode addDeclarationsToCFA(final VariableDeclarationStatement sd, CFANode prevNode) {

    // If statement is an else condition Statement
    // if(condition){..} else int dec;
    handleElseCondition(sd);

    final List<IADeclaration> declList =
        astCreator.convert(sd);

    final String rawSignature = sd.toString();
    return addDeclarationsToCFA(declList ,astCreator.getFileLocation(sd).getStartingLineNumber() , rawSignature, prevNode);
  }

  private CFANode addDeclarationsToCFA(final SingleVariableDeclaration sd, CFANode prevNode) {

    final List<IADeclaration> declList =new ArrayList<IADeclaration>(1) ;

    declList.add(astCreator.convert(sd));


    final String rawSignature = sd.toString();

    return addDeclarationsToCFA(declList , astCreator.getFileLocation(sd).getStartingLineNumber() ,rawSignature, prevNode);
  }


  @Override
  public boolean  visit(Block bl) {
    // TODO This works if else is a Block (else {Statement})
    // , but not if it has just one statement (else Statement)
    // In that case, every Statement needs to be visited
    // and handleElsoCondition be implemented.
    handleElseCondition(bl);
    scope.enterBlock();

    return VISIT_CHILDS;
  }

  @Override
  public void  endVisit(Block bl) {
    // This works if else is a Block (else {Statement})
    // ,but not if it has just one statement (else Statement)
    // In that case, every Statement needs to be visited
    // and handleElsoCondition be implemented.
    scope.leaveBlock();
  }


  /*

  @Override
  public boolean  visit(AssertStatement assertStatement) {


     final String message;

     if(assertStatement.getMessage() == null){
       message = "";
     } else {
       //TODO Extract Message from String
       message = assertStatement.getMessage().toString();
     }


     CFileLocation fileloc = astCreator.getFileLocation(assertStatement);
     CFANode prevNode = locStack.pop();

     //Create CFA Node for end of assert Location and push to local Stack
     CFANode postAssertNode = new CFANode(fileloc.getEndingLineNumber(),
         cfa.getFunctionName());
     cfaNodes.add(postAssertNode);
     locStack.push(postAssertNode);

     // Node for successful assert
     CFANode successfulNode = new CFANode(fileloc.getStartingLineNumber(),
         cfa.getFunctionName());
     cfaNodes.add(successfulNode);

     // Error Label Node and unsuccessfulNode for unSuccessful assert,
     CFANode unsuccessfulNode = new CFANode(fileloc.getStartingLineNumber(),
         cfa.getFunctionName());
     cfaNodes.add(unsuccessfulNode);
     CLabelNode  errorLabelNode = new CLabelNode(fileloc.getStartingLineNumber(),cfa.getFunctionName(), "ERROR");
       cfaNodes.add(errorLabelNode);

     createConditionEdges( assertStatement.getExpression() ,
         fileloc.getStartingLineNumber(), prevNode, successfulNode, unsuccessfulNode);

       //Blank Edge from successful assert to  postAssert location
       BlankEdge blankEdge = new BlankEdge(assertStatement.toString(), postAssertNode.getLineNumber(), successfulNode, postAssertNode, "assert success");
       addToCFA(blankEdge);

      // Blank Edge from unsuccessful assert to Error  location
       blankEdge = new BlankEdge(assertStatement.toString(), errorLabelNode.getLineNumber(),
           unsuccessfulNode, errorLabelNode, "asssert fail:" + message);
       addToCFA(blankEdge);

    return SKIP_CHILDS;
  }

  */

  @Override
  public boolean  visit(AssertStatement assertStatement) {


     final String message;

     if(assertStatement.getMessage() == null){
       message = "";
     } else {
       //TODO Extract Message from String
       message = assertStatement.getMessage().toString();
     }


     CFileLocation fileloc = astCreator.getFileLocation(assertStatement);
     CFANode prevNode = locStack.pop();

     //Create CFA Node for end of assert Location and push to local Stack
     CFANode postAssertNode = new CFANode(fileloc.getEndingLineNumber(),
         cfa.getFunctionName());
     cfaNodes.add(postAssertNode);
     locStack.push(postAssertNode);

     // Node for successful assert
     CFANode successfulNode = new CFANode(fileloc.getStartingLineNumber(),
         cfa.getFunctionName());
     cfaNodes.add(successfulNode);

     // Error Label Node and unsuccessfulNode for unSuccessful assert,
     CFANode unsuccessfulNode = new CFANode(fileloc.getStartingLineNumber(),
         cfa.getFunctionName());
     cfaNodes.add(unsuccessfulNode);
     CLabelNode  errorLabelNode = new CLabelNode(fileloc.getStartingLineNumber(),cfa.getFunctionName(), "ERROR");
       cfaNodes.add(errorLabelNode);

       CONDITION kind = getConditionKind(assertStatement.getExpression());

       createConditionEdges( assertStatement.getExpression() ,
           fileloc.getStartingLineNumber(), prevNode, successfulNode, unsuccessfulNode);

       boolean createUnsuccessfulEdge = true;
       boolean createSuccessfulEdge = true;

       switch(kind){
       case ALWAYS_TRUE:
         createUnsuccessfulEdge = false;break;
       case ALWAYS_FALSE: createSuccessfulEdge = false;break;
       }


       BlankEdge blankEdge;

       //Blank Edge from successful assert to  postAssert location
       if(createSuccessfulEdge){
       blankEdge = new BlankEdge(assertStatement.toString(), postAssertNode.getLineNumber(), successfulNode, postAssertNode, "assert success");
       addToCFA(blankEdge);
       }

      // Blank Edge from unsuccessful assert to Error  location
       if(createUnsuccessfulEdge){
       blankEdge = new BlankEdge(assertStatement.toString(), errorLabelNode.getLineNumber(),
           unsuccessfulNode, errorLabelNode, "asssert fail:" + message);
       addToCFA(blankEdge);
       }

    return SKIP_CHILDS;
  }

 /**
  * This Method checks, if Statement is start of a else Condition Block
  * or Statement, changes the cfa accordingly.
  *
  *
  * @param statement Given statement to be checked.
  */
 private void handleElseCondition(Statement statement){


    ASTNode node = statement;


   // Handle special condition for else
   // TODO Investigate why == works, but not equals
   if (node.getLocationInParent() == IfStatement.ELSE_STATEMENT_PROPERTY) {
     // Edge from current location to post if-statement location
     CFANode prevNode = locStack.pop();
     CFANode nextNode = locStack.peek();

     if (isReachableNode(prevNode)) {
       BlankEdge blankEdge = new BlankEdge("", nextNode.getLineNumber(), prevNode, nextNode, "");
       addToCFA(blankEdge);
     }

     //  Push the start of the else clause onto our location stack
     CFANode elseNode = elseStack.pop();
     locStack.push(elseNode);
   }
 }

 @Override
 public boolean visit(ExpressionStatement expressionStatement) {

   // When else is not in a block (else Statement)
   handleElseCondition(expressionStatement);

   CFANode prevNode = locStack.pop ();

   IAStatement statement = astCreator.convert(expressionStatement);


   // If this is a ReferencedFunctionCall, see if
   // the Run-Time-Class can be inferred
   if(statement instanceof AFunctionCall
       && ((AFunctionCall)statement).getFunctionCallExpression()
                 instanceof JReferencedMethodInvocationExpression ){

          searchForRunTimeClass( (JReferencedMethodInvocationExpression )
                                    ((AFunctionCall)statement).getFunctionCallExpression(), prevNode);
   }

   //TODO Solution not allowed, find better Solution
   String rawSignature = expressionStatement.toString();


   CFANode lastNode = new CFANode(statement.getFileLocation().getStartingLineNumber(), cfa.getFunctionName());
   cfaNodes.add(lastNode);

   if(astCreator.getConditionalExpression() != null) {
     handleConditionalStatement(prevNode, lastNode, statement);

   } else {
     CFANode nextNode = handleSideassignments(prevNode, rawSignature, statement.getFileLocation().getStartingLineNumber());


     // Resolve boolean Assignments, resolve & , && , | , || to be easier analyzed
     if(statement instanceof AExpressionAssignmentStatement && astCreator.isBooleanExpression(((AExpressionAssignmentStatement) statement).getRightHandSide()) && !(((AExpressionAssignmentStatement) statement).getRightHandSide() instanceof JBooleanLiteralExpression )) {

       Assignment booleanAssignment = (Assignment) expressionStatement.getExpression();
       AExpressionAssignmentStatement booleanAssignmentExpression = (AExpressionAssignmentStatement) statement;

       resolveBooleanAssignment(booleanAssignment.getRightHandSide(), booleanAssignmentExpression.getLeftHandSide() , nextNode , lastNode);

     } else {


     AStatementEdge edge = new AStatementEdge(rawSignature, statement,
         statement.getFileLocation().getStartingLineNumber(), nextNode, lastNode);
     addToCFA(edge);
   }

   locStack.push(lastNode);


   }


   return SKIP_CHILDS;

 }



 private void handleConditionalStatement( CFANode prevNode , CFANode lastNode , IAStatement statement){

   ConditionalExpression condExp = astCreator.getConditionalExpression();
   astCreator.resetConditionalExpression();

   //unpack unaryExpressions if there are some
   ASTNode parentExp = condExp.getParent();
   while(parentExp.getNodeType() == ASTNode.PARENTHESIZED_EXPRESSION
          || parentExp.getNodeType() == ASTNode.POSTFIX_EXPRESSION
          || parentExp.getNodeType() == ASTNode.PREFIX_EXPRESSION) {
     parentExp = parentExp.getParent();
   }

   //evaluates to true if the ternary expressions return value is not used (i. e. var==0 ? 0 : 1;)
   if(parentExp.getNodeType() == ASTNode.EXPRESSION_STATEMENT) {
     handleTernaryStatement(condExp, prevNode, lastNode);
   } else {
     handleTernaryExpression(condExp, prevNode, lastNode, statement);
   }

 }






/*
 private void resolveBooleanAssignment(Expression condition,
    IAExpression variableExpression, CFANode prevNode , CFANode lastNode) {

   CFANode trueNode = new CFANode(variableExpression.getFileLocation().getStartingLineNumber(), cfa.getFunctionName());
   cfaNodes.add(trueNode);

   CFANode falseNode = new CFANode(variableExpression.getFileLocation().getStartingLineNumber(), cfa.getFunctionName());
   cfaNodes.add(falseNode);

   createConditionEdges(condition, variableExpression.getFileLocation().getStartingLineNumber(), prevNode, trueNode, falseNode);

   AExpressionAssignmentStatement trueAssign = astCreator.getBooleanAssign(variableExpression , true);
   AExpressionAssignmentStatement falseAssign = astCreator.getBooleanAssign(variableExpression , false);

   AStatementEdge trueAssignmentEdge = new AStatementEdge(condition.toString(), trueAssign,
       variableExpression.getFileLocation().getStartingLineNumber(), trueNode, lastNode);
   addToCFA(trueAssignmentEdge);

   AStatementEdge falseAssignmentEdge = new AStatementEdge(condition.toString(), falseAssign,
       variableExpression.getFileLocation().getStartingLineNumber(), falseNode, lastNode);
   addToCFA(falseAssignmentEdge);

}

*/



 private void resolveBooleanAssignment(Expression condition,
     IAExpression variableExpression, CFANode prevNode , CFANode afterResolvedBooleanExpressionNode) {
   resolveBooleanAssignment(astCreator.convertBooleanExpression(condition), variableExpression, prevNode, afterResolvedBooleanExpressionNode);
 }

 private void resolveBooleanAssignment(IAExpression condition, IAExpression variableExpression, CFANode prevNode,
     CFANode afterResolvedBooleanExpressionNode) {

   CFANode trueNode = new CFANode(variableExpression.getFileLocation().getStartingLineNumber(), cfa.getFunctionName());
   cfaNodes.add(trueNode);

   CFANode falseNode = new CFANode(variableExpression.getFileLocation().getStartingLineNumber(), cfa.getFunctionName());
   cfaNodes.add(falseNode);

   createConditionEdges(condition, variableExpression.getFileLocation().getStartingLineNumber(), prevNode, trueNode, falseNode);

   AExpressionAssignmentStatement trueAssign = astCreator.getBooleanAssign(variableExpression , true);
   AExpressionAssignmentStatement falseAssign = astCreator.getBooleanAssign(variableExpression , false);

   AStatementEdge trueAssignmentEdge = new AStatementEdge(condition.toString(), trueAssign,
       variableExpression.getFileLocation().getStartingLineNumber(), trueNode, afterResolvedBooleanExpressionNode);
   addToCFA(trueAssignmentEdge);

   AStatementEdge falseAssignmentEdge = new AStatementEdge(condition.toString(), falseAssign,
       variableExpression.getFileLocation().getStartingLineNumber(), falseNode, afterResolvedBooleanExpressionNode);
   addToCFA(falseAssignmentEdge);

 }



private void searchForRunTimeClass(JReferencedMethodInvocationExpression methodInvocation, CFANode prevNode) {

   // This Algorithm  goes backwards from methodInvocation and searches
   // for a Class Instance Creation, which Class can be distinctly assigned as Run Time Class
   // If there is a distinct variable Assignment , the searched for variable reference will
   // changes to the assigned variable reference.

 // Class can only be found if there is only one Path to
 // a ClassInstanceCreation, stop if there is not exactly one
 boolean finished = prevNode.getNumEnteringEdges() != 1;
 CFANode traversedNode = prevNode;

 IASimpleDeclaration referencedVariable =  methodInvocation.getReferencedVariable();

 while(!finished){
   CFAEdge currentEdge = traversedNode.getEnteringEdge(ONLY_EDGE);

   // Look for Instance Creation Assignment and Variable Assignment.
   // Stop if there is a function Call and the Variable is a FieldDeclaration
   // or there is an Assignment Function Call which isn't a Instance Creation Assignment
   if(currentEdge.getEdgeType() == CFAEdgeType.StatementEdge) {

     IAStatement statement = ((AStatementEdge) currentEdge).getStatement();

     if(statement instanceof AExpressionAssignmentStatement) {
        if(isReferencableVariable(referencedVariable, (AAssignment)statement)){
          referencedVariable = assignVariableReference((AExpressionAssignmentStatement) statement);
        } else {
          finished = isReferenced(referencedVariable, (AAssignment)statement);
        }
     } else if(statement instanceof AFunctionCallStatement) {
        finished = (referencedVariable instanceof JFieldDeclaration);
     } else if(statement instanceof AFunctionCallAssignmentStatement){
          finished = isReferenced(referencedVariable, (AAssignment)statement);
        if(finished) {
          assignClassRunTimeInstanceIfInstanceCreation(methodInvocation ,(AFunctionCallAssignmentStatement) statement);
        }
     }
   }

   // if not finished, continue iff there is only one path
   finished = finished || !(traversedNode.getNumEnteringEdges() != 1);

   if(!finished){
     traversedNode = currentEdge.getPredecessor();
   }
 }

}


 private boolean isReferenced(IASimpleDeclaration referencedVariable, AAssignment assignment) {
   IAExpression leftHandSide = assignment.getLeftHandSide();

  return (leftHandSide instanceof JIdExpression) && ((JIdExpression)leftHandSide).getDeclaration().getName().equals(referencedVariable.getName()) ;
}

private void assignClassRunTimeInstanceIfInstanceCreation(JReferencedMethodInvocationExpression methodInvocation, AFunctionCallAssignmentStatement functionCallAssignment) {

   AFunctionCallExpression  functionCall = functionCallAssignment.getFunctionCallExpression();

   if(functionCall instanceof JClassInstanzeCreation){
     astCreator.assignRunTimeClass(methodInvocation , (JClassInstanzeCreation) functionCall);
   }

}

private IASimpleDeclaration assignVariableReference(AExpressionAssignmentStatement expressionAssignment) {

   JIdExpression newReferencedVariable = (JIdExpression) expressionAssignment.getRightHandSide();
  return newReferencedVariable.getDeclaration();
}

private boolean isReferencableVariable(IASimpleDeclaration referencedVariable, AAssignment assignment) {

   IAExpression leftHandSide = assignment.getLeftHandSide();
   IARightHandSide rightHandSide = assignment.getRightHandSide();

  return (leftHandSide instanceof JIdExpression) && (rightHandSide instanceof JIdExpression) &&((JIdExpression)leftHandSide).getDeclaration().getName().equals(referencedVariable.getName()) ;
}

private void handleTernaryExpression(ConditionalExpression condExp, CFANode rootNode, CFANode lastNode, IAstNode statement) {
   CFileLocation fileLoc = astCreator.getFileLocation(condExp);
   int filelocStart = fileLoc.getStartingLineNumber();

   AIdExpression tempVar = astCreator.getConditionalTemporaryVariable();
   rootNode = handleSideassignments(rootNode, condExp.toString(), filelocStart);

   CFANode thenNode = new CFANode(filelocStart, cfa.getFunctionName());
   cfaNodes.add(thenNode);
   CFANode elseNode = new CFANode(filelocStart, cfa.getFunctionName());
   cfaNodes.add(elseNode);
   buildConditionTree(condExp.getExpression(), filelocStart, rootNode, thenNode, elseNode, thenNode, elseNode, true, true);
   CFANode middle;
   //TODO IASTSimpleDeclaration is what?
    if (condExp.getParent().getNodeType() == ASTNode.VARIABLE_DECLARATION_EXPRESSION || condExp.getParent().getNodeType() == ASTNode.VARIABLE_DECLARATION_FRAGMENT || condExp.getParent().getNodeType() == ASTNode.VARIABLE_DECLARATION_STATEMENT || statement instanceof IAStatement) {
     middle = new CFANode(filelocStart, cfa.getFunctionName());
     cfaNodes.add(middle);
    } else {
      middle = lastNode;
    }

   createTernaryExpressionEdges(condExp.getThenExpression(), middle, filelocStart, thenNode, tempVar);
   createTernaryExpressionEdges(condExp.getElseExpression(), middle, filelocStart, elseNode, tempVar);

   //TODO  Correctly set condition, so that it is äquivalent to IASTSimpleDeclaration
   if(condExp.getParent().getNodeType() == ASTNode.VARIABLE_DECLARATION_EXPRESSION || condExp.getParent().getNodeType() == ASTNode.VARIABLE_DECLARATION_FRAGMENT || condExp.getParent().getNodeType() == ASTNode.VARIABLE_DECLARATION_STATEMENT) {
     createSideAssignmentEdges(middle, lastNode, statement.toASTString(), filelocStart, statement);
   } else if (statement instanceof IAStatement){
     addToCFA(new AStatementEdge(condExp.toString(),
                                 (IAStatement) statement,
                                 filelocStart,
                                 middle,
                                 lastNode));
   }
 }

 private void handleTernaryStatement(ConditionalExpression condExp, CFANode rootNode, CFANode lastNode) {
   CFileLocation fileLoc = astCreator.getFileLocation(condExp);
   int filelocStart = fileLoc.getStartingLineNumber();

   while(astCreator.numberOfPreSideAssignments() > 0) {
     astCreator.getNextPreSideAssignment();
   }

   CFANode thenNode = new CFANode(filelocStart, cfa.getFunctionName());
   cfaNodes.add(thenNode);
   CFANode elseNode = new CFANode(filelocStart, cfa.getFunctionName());
   cfaNodes.add(elseNode);
   buildConditionTree(condExp.getExpression(), filelocStart, rootNode, thenNode, elseNode, thenNode, elseNode, true, true);

   createTernaryStatementEdges(condExp.getThenExpression(), lastNode, filelocStart, thenNode);
   createTernaryStatementEdges(condExp.getElseExpression(), lastNode, filelocStart, elseNode);

 }

 private void createTernaryExpressionEdges(Expression condExp, CFANode lastNode, int filelocStart, CFANode prevNode, AIdExpression tempVar) {
   IAstNode exp = astCreator.convertExpressionWithSideEffects(condExp);

   if (exp != astCreator.getConditionalTemporaryVariable() && astCreator.getConditionalExpression() == null) {

     CFANode tmp;
     if (astCreator.getConditionalExpression() != null) {
       tmp = new CFANode(filelocStart, cfa.getFunctionName());
       cfaNodes.add(tmp);
       handleTernaryExpressionTail(exp, filelocStart, prevNode, tmp, tempVar);
       prevNode = tmp;
     } else if (astCreator.numberOfPreSideAssignments() > 0){
       tmp = new CFANode(filelocStart, cfa.getFunctionName());
       cfaNodes.add(tmp);
       handleSideassignments(prevNode, exp.toASTString(), filelocStart, tmp);
       prevNode = tmp;
     }

     AStatementEdge edge;
     if (exp instanceof IAExpression) {
       edge  = new AStatementEdge(condExp.toString(),
                                 new AExpressionAssignmentStatement(astCreator.getFileLocation(condExp),
                                                                       tempVar,
                                                                       (IAExpression) exp),
                                 filelocStart, prevNode, lastNode);
       addToCFA(edge);
     } else if (exp instanceof AFunctionCallExpression) {
       edge  = new AStatementEdge(condExp.toString(),
                                 new AFunctionCallAssignmentStatement(astCreator.getFileLocation(condExp),
                                                                         tempVar,
                                                                         (AFunctionCallExpression) exp),
                                 filelocStart, prevNode, lastNode);
       addToCFA(edge);
     } else {
       CFANode middle = new CFANode(filelocStart, cfa.getFunctionName());
       cfaNodes.add(middle);
       edge  = new AStatementEdge(condExp.toString(), (IAStatement) exp, filelocStart, prevNode, middle);
       addToCFA(edge);
       edge  = new AStatementEdge(condExp.toString(),
                                 new AExpressionAssignmentStatement(astCreator.getFileLocation(condExp),
                                                                       tempVar,
                                                                       ((AAssignment) exp).getLeftHandSide()),
                                 filelocStart, middle, lastNode);
       addToCFA(edge);
     }
   } else {
     handleTernaryExpressionTail(exp, filelocStart, prevNode, lastNode, tempVar);
   }
 }

 private void createTernaryStatementEdges(Expression condExp, CFANode lastNode, int filelocStart, CFANode prevNode) {
   IAstNode exp = astCreator.convertExpressionWithSideEffects(condExp);

   if (exp != astCreator.getConditionalTemporaryVariable() && astCreator.getConditionalExpression() == null) {

     CFANode tmp;
     if (astCreator.getConditionalExpression() != null) {
       tmp = new CFANode(filelocStart, cfa.getFunctionName());
       cfaNodes.add(tmp);
       handleTernaryStatementTail(exp, filelocStart, prevNode, tmp);
       prevNode = tmp;
     } else if (astCreator.numberOfPreSideAssignments() > 0){
       tmp = new CFANode(filelocStart, cfa.getFunctionName());
       cfaNodes.add(tmp);
       handleSideassignments(prevNode, exp.toASTString(), filelocStart, tmp);
       prevNode = tmp;
     }

     AStatementEdge edge;



     if (exp instanceof IAExpression) {
       edge  = new AStatementEdge(condExp.toString(),
                                 new AExpressionStatement(   astCreator.getFileLocation(condExp), (IAExpression) exp),
                                 filelocStart, prevNode, lastNode);
       addToCFA(edge);
     } else if (exp instanceof AFunctionCallExpression) {
       edge  = new AStatementEdge(condExp.toString(),
                                 (new AFunctionCallStatement(astCreator.getFileLocation(condExp), (AFunctionCallExpression) exp)),
                                 filelocStart, prevNode, lastNode);
       addToCFA(edge);
     } else {
       CFANode middle = new CFANode(filelocStart, cfa.getFunctionName());
       cfaNodes.add(middle);
       edge  = new AStatementEdge(condExp.toString(), (IAStatement) exp, filelocStart, prevNode, middle);
       addToCFA(edge);
       edge  = new AStatementEdge(condExp.toString(),
                                 new AExpressionStatement(astCreator.getFileLocation(condExp),
                                                                       ((AExpressionAssignmentStatement) exp).getLeftHandSide()),
                                 filelocStart, middle, lastNode);
       addToCFA(edge);
     }
   } else {
     handleTernaryStatementTail(exp, filelocStart, prevNode, lastNode);
   }
 }

 private void handleTernaryExpressionTail(IAstNode exp, int filelocStart, CFANode branchNode, CFANode lastNode, AIdExpression leftHandSide) {
     CFANode nextNode = new CFANode(filelocStart, cfa.getFunctionName());
     cfaNodes.add(nextNode);

     ConditionalExpression condExp = astCreator.getConditionalExpression();
     astCreator.resetConditionalExpression();

     AIdExpression rightHandSide = astCreator.getConditionalTemporaryVariable();

     handleTernaryExpression(condExp, branchNode, nextNode, exp);
     IAStatement stmt = new AExpressionAssignmentStatement(exp.getFileLocation(), leftHandSide, rightHandSide);
     addToCFA(new AStatementEdge(stmt.toASTString(), stmt, filelocStart, nextNode, lastNode));
 }

 private void handleTernaryStatementTail(IAstNode exp, int filelocStart, CFANode branchNode, CFANode lastNode) {
   CFANode nextNode;
   nextNode = new CFANode(filelocStart, cfa.getFunctionName());
   cfaNodes.add(nextNode);

   ConditionalExpression condExp = astCreator.getConditionalExpression();
   astCreator.resetConditionalExpression();

   AIdExpression rightHandSide = astCreator.getConditionalTemporaryVariable();

   handleTernaryExpression(condExp, branchNode, nextNode, exp);
   IAStatement stmt = new AExpressionStatement(exp.getFileLocation(), rightHandSide);
   addToCFA(new AStatementEdge(stmt.toASTString(), stmt, filelocStart, nextNode, lastNode));
 }

@Override
  public boolean visit(IfStatement ifStatement) {
   CFileLocation fileloc = astCreator.getFileLocation(ifStatement);

   // If parent Else is not a Block
   handleElseCondition(ifStatement);


    CFANode prevNode = locStack.pop();

    CFANode postIfNode = new CFANode(fileloc.getEndingLineNumber(),
        cfa.getFunctionName());
    cfaNodes.add(postIfNode);
    locStack.push(postIfNode);

    CFANode thenNode = new CFANode(fileloc.getStartingLineNumber(),
        cfa.getFunctionName());
    cfaNodes.add(thenNode);
    locStack.push(thenNode);

    CFANode elseNode;
    // elseNode is the start of the else branch,
    // or the node after the loop if there is no else branch
    if (ifStatement.getElseStatement() == null) {
      elseNode = postIfNode;
    } else {
      elseNode = new CFANode(fileloc.getStartingLineNumber(),
          cfa.getFunctionName());
      cfaNodes.add(elseNode);
      elseStack.push(elseNode);
    }

    createConditionEdges(ifStatement.getExpression(),
        fileloc.getStartingLineNumber(), prevNode, thenNode, elseNode);

    return VISIT_CHILDS;
  }

 @Override
 public void endVisit(IfStatement ifStatement) {

     final CFANode prevNode = locStack.pop();
     final CFANode nextNode = locStack.peek();

     if (isReachableNode(prevNode)) {

       for (CFAEdge prevEdge : ImmutableList.copyOf(CFAUtils.allEnteringEdges(prevNode))) {
         if ((prevEdge instanceof BlankEdge)
             && prevEdge.getDescription().equals("")) {

           // the only entering edge is a BlankEdge, so we delete this edge and prevNode

           CFANode prevPrevNode = prevEdge.getPredecessor();
           assert prevPrevNode.getNumLeavingEdges() == 1;
           prevNode.removeEnteringEdge(prevEdge);
           prevPrevNode.removeLeavingEdge(prevEdge);

           BlankEdge blankEdge = new BlankEdge("", prevNode.getLineNumber(),
               prevPrevNode, nextNode, "");
           addToCFA(blankEdge);
         }
       }

       if (prevNode.getNumEnteringEdges() > 0) {
         BlankEdge blankEdge = new BlankEdge("", prevNode.getLineNumber(),
             prevNode, nextNode, "");
         addToCFA(blankEdge);
       }
     }
 }





  private static enum CONDITION { NORMAL, ALWAYS_FALSE, ALWAYS_TRUE }


  /*
  private void createConditionEdges(final Expression condition,
      final int filelocStart, CFANode rootNode, CFANode thenNode,
      final CFANode elseNode) {

    assert condition != null;

    final CONDITION kind = getConditionKind(condition);
    //TODO solution not allowed, find better Solution
        String rawSignature = condition.toString();

    switch (kind) {
    case ALWAYS_FALSE:
      // no edge connecting rootNode with thenNode,
      // so the "then" branch won't be connected to the rest of the CFA

      final BlankEdge falseEdge = new BlankEdge(rawSignature, filelocStart, rootNode, elseNode, "");
      addToCFA(falseEdge);
      break;

    case ALWAYS_TRUE:
      final BlankEdge trueEdge = new BlankEdge(rawSignature, filelocStart, rootNode, thenNode, "");
      addToCFA(trueEdge);

      // no edge connecting prevNode with elseNode,
      // so the "else" branch won't be connected to the rest of the CFA
      break;

    case NORMAL:
        buildConditionTree(condition, filelocStart, rootNode, thenNode, elseNode, thenNode, elseNode, true, true);
      break;

    default:
      throw new InternalError("Missing switch clause");
    }
  }

  */

  private void createConditionEdges(final Expression condition,
      final int filelocStart, CFANode rootNode, CFANode thenNode,
      final CFANode elseNode) {
    createConditionEdges(astCreator.convertBooleanExpression(condition), filelocStart, rootNode, thenNode, elseNode);
  }

  private void createConditionEdges(IAExpression condition, final int filelocStart, CFANode rootNode, CFANode thenNode,
      final CFANode elseNode) {
    assert condition != null;

    final CONDITION kind = getConditionKind(condition);
    //TODO solution not allowed, find better Solution
        String rawSignature = condition.toString();

    switch (kind) {
    case ALWAYS_FALSE:
      // no edge connecting rootNode with thenNode,
      // so the "then" branch won't be connected to the rest of the CFA

      final BlankEdge falseEdge = new BlankEdge(rawSignature, filelocStart, rootNode, elseNode, "");
      addToCFA(falseEdge);
      break;

    case ALWAYS_TRUE:
      final BlankEdge trueEdge = new BlankEdge(rawSignature, filelocStart, rootNode, thenNode, "");
      addToCFA(trueEdge);

      // no edge connecting prevNode with elseNode,
      // so the "else" branch won't be connected to the rest of the CFA
      break;

    case NORMAL:
        buildConditionTree(condition, filelocStart, rootNode, thenNode, elseNode, thenNode, elseNode, true, true);
      break;

    default:
      throw new InternalError("Missing switch clause");
    }
  }

  private void buildConditionTree(IAExpression condition, final int filelocStart,
      CFANode rootNode, CFANode thenNode, final CFANode elseNode,
      CFANode thenNodeForLastThen, CFANode elseNodeForLastElse,
      boolean furtherThenComputation, boolean furtherElseComputation) {

    CFANode nextNode =
        handleSideassignments(rootNode, condition.toASTString(), condition.getFileLocation().getStartingLineNumber());


      if (condition instanceof JBinaryExpression
          && ((((JBinaryExpression) condition).getOperator() == JBinaryExpression.BinaryOperator.CONDITIONAL_AND) || ((JBinaryExpression) condition).getOperator() == JBinaryExpression.BinaryOperator.LOGICAL_AND)) {
        CFANode innerNode = new CFANode(filelocStart, cfa.getFunctionName());
        cfaNodes.add(innerNode);
        buildConditionTree(((JBinaryExpression) condition).getOperand1(), filelocStart, rootNode, innerNode, elseNode,
            thenNodeForLastThen, elseNodeForLastElse, true, false);
        buildConditionTree(((JBinaryExpression) condition).getOperand2(), filelocStart, innerNode, thenNode, elseNode,
            thenNodeForLastThen, elseNodeForLastElse, true, true);

      } else if (condition instanceof JBinaryExpression
        &&  (((JBinaryExpression) condition).getOperator() == JBinaryExpression.BinaryOperator.CONDITIONAL_OR  || ((JBinaryExpression) condition).getOperator() == JBinaryExpression.BinaryOperator.LOGICAL_OR) ) {
        CFANode innerNode = new CFANode(filelocStart, cfa.getFunctionName());
        cfaNodes.add(innerNode);
        buildConditionTree(((JBinaryExpression) condition).getOperand1(), filelocStart, rootNode, thenNode, innerNode,
            thenNodeForLastThen, elseNodeForLastElse, false, true);
        buildConditionTree(((JBinaryExpression) condition).getOperand2(), filelocStart, innerNode, thenNode, elseNode,
            thenNodeForLastThen, elseNodeForLastElse, true, true);

      }  else {

        String rawSignature = condition.toASTString();


        if (furtherThenComputation) {
          thenNodeForLastThen = thenNode;
        }
        if (furtherElseComputation) {
          elseNodeForLastElse = elseNode;
        }


        // edge connecting last condition with elseNode
        final AssumeEdge JAssumeEdgeFalse = new AssumeEdge("!(" + rawSignature + ")",
            filelocStart,
            nextNode,
            elseNodeForLastElse,
            condition,
            false);
        addToCFA(JAssumeEdgeFalse);

        // edge connecting last condition with thenNode
        final AssumeEdge JAssumeEdgeTrue = new AssumeEdge(rawSignature,
            filelocStart,
            nextNode,
            thenNodeForLastThen,
            condition,
            true);
        addToCFA(JAssumeEdgeTrue);
      }
  }


  private void buildConditionTree(Expression condition, final int filelocStart,
      CFANode rootNode, CFANode thenNode, final CFANode elseNode,
      CFANode thenNodeForLastThen, CFANode elseNodeForLastElse,
      boolean furtherThenComputation, boolean furtherElseComputation) {
    buildConditionTree(astCreator.convertBooleanExpression(condition), filelocStart, rootNode, thenNode, elseNode, thenNodeForLastThen, elseNodeForLastElse, furtherThenComputation, furtherElseComputation);
  }




/*

  private void buildConditionTreeForInstanceOf(IAExpression orExpOrEqualsRunTimeTyp, IAExpression boolLit, BinaryOperator op, final int filelocStart,
      CFANode rootNode, CFANode thenNode, final CFANode elseNode,
      CFANode thenNodeForLastThen, CFANode elseNodeForLastElse,
      boolean furtherThenComputation, boolean furtherElseComputation) {

    // TODO add eager way (just define tmp variables which are not used to simulate
    // TODO the handling of an eager Condition)



     if (orExpOrEqualsRunTimeTyp instanceof JBinaryExpression
      &&  (((JBinaryExpression) orExpOrEqualsRunTimeTyp).getOperator() == JBinaryExpression.BinaryOperator.CONDITIONAL_OR   || ((JBinaryExpression) orExpOrEqualsRunTimeTyp).getOperator() == JBinaryExpression.BinaryOperator.LOGICAL_OR) ) {
      CFANode innerNode = new CFANode(filelocStart, cfa.getFunctionName());
      cfaNodes.add(innerNode);
      buildConditionTreeForInstanceOf(((JBinaryExpression) orExpOrEqualsRunTimeTyp).getOperand1(),boolLit, op,  filelocStart, rootNode, thenNode, innerNode,
          thenNodeForLastThen, elseNodeForLastElse, false, true);
      buildConditionTreeForInstanceOf(((JBinaryExpression) orExpOrEqualsRunTimeTyp).getOperand2(), boolLit, op, filelocStart, innerNode, thenNode, elseNode,
          thenNodeForLastThen, elseNodeForLastElse, true, true);

    } else {

      String rawSignature = orExpOrEqualsRunTimeTyp.toString();
      CFANode nextNode =
          handleSideassignments(rootNode, rawSignature, orExpOrEqualsRunTimeTyp.getFileLocation().getStartingLineNumber());
      if (furtherThenComputation) {
        thenNodeForLastThen = thenNode;
      }
      if (furtherElseComputation) {
        elseNodeForLastElse = elseNode;
      }

      // edge connecting last condition with elseNode
      final AssumeEdge JAssumeEdgeFalse = new AssumeEdge("!(" + rawSignature + ")",
          filelocStart,
          nextNode,
          elseNodeForLastElse,
          new JBinaryExpression(orExpOrEqualsRunTimeTyp.getFileLocation(), (JType) orExpOrEqualsRunTimeTyp.getExpressionType(), (JExpression)orExpOrEqualsRunTimeTyp, (JExpression)boolLit, op),
          false);
      addToCFA(JAssumeEdgeFalse);

      // edge connecting last condition with thenNode
      final AssumeEdge JAssumeEdgeTrue = new AssumeEdge(rawSignature,
          filelocStart,
          nextNode,
          thenNodeForLastThen,
          new JBinaryExpression(orExpOrEqualsRunTimeTyp.getFileLocation(), (JType) orExpOrEqualsRunTimeTyp.getExpressionType(), (JExpression)orExpOrEqualsRunTimeTyp, (JExpression)boolLit, op),
          true);
      addToCFA(JAssumeEdgeTrue);
    }
  }

*/


/*

  private void buildConditionTree(Expression condition, final int filelocStart,
      CFANode rootNode, CFANode thenNode, final CFANode elseNode,
      CFANode thenNodeForLastThen, CFANode elseNodeForLastElse,
      boolean furtherThenComputation, boolean furtherElseComputation) {

    // TODO add eager way (just define tmp variables which are not used to simulate
    // the handling of an eager Condition)

    if (condition.getNodeType() == ASTNode.PARENTHESIZED_EXPRESSION) {
      buildConditionTree(((ParenthesizedExpression)condition).getExpression(), filelocStart, rootNode, thenNode, elseNode, thenNode, elseNode, true, true);
    } else if (condition.getNodeType() == ASTNode.INFIX_EXPRESSION
        && ((((InfixExpression) condition).getOperator() == InfixExpression.Operator.CONDITIONAL_AND) || ((InfixExpression) condition).getOperator() == InfixExpression.Operator.AND)) {
      CFANode innerNode = new CFANode(filelocStart, cfa.getFunctionName());
      cfaNodes.add(innerNode);
      buildConditionTree(((InfixExpression) condition).getLeftOperand(), filelocStart, rootNode, innerNode, elseNode,
          thenNodeForLastThen, elseNodeForLastElse, true, false);
      buildConditionTree(((InfixExpression) condition).getRightOperand(), filelocStart, innerNode, thenNode, elseNode,
          thenNodeForLastThen, elseNodeForLastElse, true, true);

    } else if (condition.getNodeType() == ASTNode.INFIX_EXPRESSION
      &&  (((InfixExpression) condition).getOperator() == InfixExpression.Operator.CONDITIONAL_OR   || ((InfixExpression) condition).getOperator() == InfixExpression.Operator.OR) ) {
      CFANode innerNode = new CFANode(filelocStart, cfa.getFunctionName());
      cfaNodes.add(innerNode);
      buildConditionTree(((InfixExpression) condition).getLeftOperand(), filelocStart, rootNode, thenNode, innerNode,
          thenNodeForLastThen, elseNodeForLastElse, false, true);
      buildConditionTree(((InfixExpression) condition).getRightOperand(), filelocStart, innerNode, thenNode, elseNode,
          thenNodeForLastThen, elseNodeForLastElse, true, true);

    } else if(condition.getNodeType() ==   ASTNode.INSTANCEOF_EXPRESSION){
      final IAExpression exp = astCreator.convertBooleanExpression(condition);
      final IAExpression orExp = ((JBinaryExpression) exp).getOperand1();
      final IAExpression BoolLit = ((JBinaryExpression) exp).getOperand2();
      final BinaryOperator op = ((JBinaryExpression) exp).getOperator();

      buildConditionTreeForInstanceOf(orExp, BoolLit, op,  filelocStart, rootNode, thenNode, elseNode, thenNodeForLastThen, elseNodeForLastElse, furtherThenComputation, furtherElseComputation);
    } else {


      final IAExpression exp = astCreator.convertBooleanExpression(condition);

      //TODO Solution not allowed, find better Solution
      String rawSignature = condition.toString();

      CFANode nextNode =
          handleSideassignments(rootNode, rawSignature, exp.getFileLocation().getStartingLineNumber());

      if (furtherThenComputation) {
        thenNodeForLastThen = thenNode;
      }
      if (furtherElseComputation) {
        elseNodeForLastElse = elseNode;
      }


      // edge connecting last condition with elseNode
      final AssumeEdge JAssumeEdgeFalse = new AssumeEdge("!(" + rawSignature + ")",
          filelocStart,
          nextNode,
          elseNodeForLastElse,
          exp,
          false);
      addToCFA(JAssumeEdgeFalse);

      // edge connecting last condition with thenNode
      final AssumeEdge JAssumeEdgeTrue = new AssumeEdge(rawSignature,
          filelocStart,
          nextNode,
          thenNodeForLastThen,
          exp,
          true);
      addToCFA(JAssumeEdgeTrue);
    }
  }

  */

  private CONDITION getConditionKind(IAExpression condition) {
    if(condition instanceof JBooleanLiteralExpression){
      if(((JBooleanLiteralExpression) condition).getValue()){
        return CONDITION.ALWAYS_TRUE;
      } else {
        return CONDITION.ALWAYS_FALSE;
      }
    }
    return CONDITION.NORMAL;
  }



  private CONDITION getConditionKind(Expression cond) {

    while(cond.getNodeType() == ASTNode.PARENTHESIZED_EXPRESSION){
      cond = ((ParenthesizedExpression) cond).getExpression();
    }

    if (cond.getNodeType() == ASTNode.BOOLEAN_LITERAL) {
         if(((BooleanLiteral) cond).booleanValue()){
           return CONDITION.ALWAYS_TRUE;
         } else{
           return CONDITION.ALWAYS_FALSE;
         }
    }
    return CONDITION.NORMAL;
}




  @Override
  public boolean visit(LabeledStatement labelStatement) {

    //If parent is a else Condition without block
    handleElseCondition(labelStatement);

    CFileLocation fileloc = astCreator.getFileLocation(labelStatement);

    String labelName = labelStatement.getLabel().getIdentifier();
    if (labelMap.containsKey(labelName)) {
      throw new CFAGenerationRuntimeException("Duplicate label " + labelName
          + " in function " + cfa.getFunctionName(), labelStatement);
    }


    // Label Node is in Java after Label Body
    CLabelNode labelNode = new CLabelNode(fileloc.getStartingLineNumber(),
        cfa.getFunctionName(), labelName);
    cfaNodes.add(labelNode);
    labelMap.put(labelName, labelNode);



    //  Skip to Body
    labelStatement.getBody().accept(this);

    return SKIP_CHILDS;
  }

  @Override
  public void endVisit(LabeledStatement labelStatement) {

    String labelName = labelStatement.getLabel().getIdentifier();

    assert labelMap.containsKey(labelName) : "Label Name " + labelName + " to be deleted "
    + "out of scope, but scope does not contain it";

    // Add Edge from end of Label Body to Label
    CLabelNode labelNode = labelMap.get(labelStatement.getLabel().getIdentifier());
    CFANode prevNode = locStack.pop();

    if (isReachableNode(prevNode)) {
      BlankEdge blankEdge = new BlankEdge(labelStatement.toString(),
          labelNode.getLineNumber(), prevNode, labelNode, "Label: " + labelName);
      addToCFA(blankEdge);
    }


    locStack.push(labelNode);

    labelMap.remove(labelStatement.getLabel().getIdentifier());
  }


  @SuppressWarnings("unchecked")
  @Override
  public boolean visit(final SwitchStatement statement) {

    // If parent is else and not a block
    handleElseCondition(statement);


    CFileLocation fileloc = astCreator.getFileLocation(statement);

    final CFANode prevNode = locStack.pop();

    // firstSwitchNode is first Node of switch-Statement.
    // TODO useful or unnecessary? it can be replaced through prevNode.
    final CFANode firstSwitchNode =
        new CFANode(fileloc.getStartingLineNumber(),
            cfa.getFunctionName());
    cfaNodes.add(firstSwitchNode);

    IAExpression switchExpression = astCreator
        .convertExpressionWithoutSideEffects(statement.getExpression());

    // TODO Solution not allowed (toString() ASTNode)
    String rawSignature = "switch (" + statement.getExpression().toString() + ")";
    String description = "switch (" + switchExpression.toASTString() + ")";
    addToCFA(new BlankEdge(rawSignature, fileloc.getStartingLineNumber(),
        prevNode, firstSwitchNode, description));

    switchExprStack.push(switchExpression);
    switchCaseStack.push(firstSwitchNode);

    // postSwitchNode is Node after the switch-statement
    final CFANode postSwitchNode =
        new CLabelNode(fileloc.getEndingLineNumber(),
            cfa.getFunctionName(), "");
    cfaNodes.add(postSwitchNode);
    loopNextStack.push(postSwitchNode);
    locStack.push(postSwitchNode);

    locStack.push(new CFANode(fileloc.getStartingLineNumber(),
        cfa.getFunctionName()));

    // visit body,
    for( Statement st :(List<Statement>) statement.statements()){
         st.accept(this);
    }



    // leave switch
    final CFANode lastNodeInSwitch = locStack.pop();
    final CFANode lastNotCaseNode = switchCaseStack.pop();
    switchExprStack.pop(); // switchExpr is not needed after this point

    assert postSwitchNode == loopNextStack.pop();
    assert postSwitchNode == locStack.peek();
    assert switchExprStack.size() == switchCaseStack.size();

    final BlankEdge blankEdge = new BlankEdge("", lastNotCaseNode.getLineNumber(),
        lastNotCaseNode, postSwitchNode, "");
    addToCFA(blankEdge);

    final BlankEdge blankEdge2 = new BlankEdge("", lastNodeInSwitch.getLineNumber(),
        lastNodeInSwitch, postSwitchNode, "");
    addToCFA(blankEdge2);

    // skip visiting children of switch, because switchBody was handled before
    return SKIP_CHILDS;
  }



  @Override
  public boolean visit(SwitchCase switchCase) {
    if (switchCase.isDefault()) {
      handleDefault(astCreator.getFileLocation(switchCase));
    } else {
      handleCase(switchCase, astCreator.getFileLocation(switchCase));
    }
    return VISIT_CHILDS;
  }


  private void handleCase(final SwitchCase statement ,CFileLocation fileloc) {

    final int filelocStart = fileloc.getStartingLineNumber();

    // build condition, left part, "a"
    final JExpression switchExpr =
        (JExpression) switchExprStack.peek();

    // build condition, right part, "2"
    final JExpression caseExpr =
        astCreator.convertExpressionWithoutSideEffects(statement
            .getExpression());

    // build condition, "a==2", TODO correct type?
    final JBinaryExpression binExp =
        new JBinaryExpression(fileloc,
            (JType) switchExpr.getExpressionType(), switchExpr, caseExpr,
            JBinaryExpression.BinaryOperator.EQUALS);

    // build condition edges, to caseNode with "a==2", to notCaseNode with "!(a==2)"
    final CFANode rootNode = switchCaseStack.pop();
    final CFANode caseNode = new CFANode(filelocStart,
        cfa.getFunctionName());
    final CFANode notCaseNode = new CFANode(filelocStart,
        cfa.getFunctionName());
    cfaNodes.add(caseNode);
    cfaNodes.add(notCaseNode);

    // fall-through (case before has no "break")
    final CFANode oldNode = locStack.pop();
    if (oldNode.getNumEnteringEdges() > 0) {
      final BlankEdge blankEdge =
          new BlankEdge("", filelocStart, oldNode, caseNode, "fall through");
      addToCFA(blankEdge);
    }


    switchCaseStack.push(notCaseNode);
    locStack.push(caseNode);

    // edge connecting rootNode with notCaseNode, "!(a==2)"
    final AssumeEdge JAssumeEdgeFalse = new AssumeEdge("!(" + binExp.toASTString() + ")",
        filelocStart, rootNode, notCaseNode, binExp, false);
    addToCFA(JAssumeEdgeFalse);

    // edge connecting rootNode with caseNode, "a==2"
    final AssumeEdge JAssumeEdgeTrue = new AssumeEdge(binExp.toASTString(),
        filelocStart, rootNode, caseNode, binExp, true);
    addToCFA(JAssumeEdgeTrue);


  }


  private void handleDefault(CFileLocation fileloc) {

    final int filelocStart = fileloc.getStartingLineNumber();

    // build blank edge to caseNode with "default", no edge to notCaseNode
    final CFANode rootNode = switchCaseStack.pop();
    final CFANode caseNode = new CFANode(filelocStart, cfa.getFunctionName());
    final CFANode notCaseNode = new CFANode(filelocStart, cfa.getFunctionName());
    cfaNodes.add(caseNode);
    cfaNodes.add(notCaseNode);

    // fall-through (case before has no "break")
    final CFANode oldNode = locStack.pop();
    if (oldNode.getNumEnteringEdges() > 0) {
      final BlankEdge blankEdge =
          new BlankEdge("", filelocStart, oldNode, caseNode, "fall through");
      addToCFA(blankEdge);
    }

    switchCaseStack.push(notCaseNode); // for later cases, only reachable through jumps
    locStack.push(caseNode);

    // blank edge connecting rootNode with caseNode
    final BlankEdge trueEdge =
        new BlankEdge("default :", filelocStart, rootNode, caseNode, "default");
    addToCFA(trueEdge);
  }


  @Override
  public boolean visit (WhileStatement whileStatement) {

    handleElseCondition(whileStatement);

    CFileLocation fileloc = astCreator.getFileLocation(whileStatement);

    final CFANode prevNode = locStack.pop();

    final CFANode loopStart = new CFANode(fileloc.getStartingLineNumber(),
        cfa.getFunctionName());
    cfaNodes.add(loopStart);
    loopStart.setLoopStart();
    loopStartStack.push(loopStart);

    final CFANode firstLoopNode = new CFANode(fileloc.getStartingLineNumber(),
        cfa.getFunctionName());
    cfaNodes.add(firstLoopNode);

    final CFANode postLoopNode = new CLabelNode(fileloc.getEndingLineNumber(),
        cfa.getFunctionName(), "");
    cfaNodes.add(postLoopNode);
    loopNextStack.push(postLoopNode);

    // inverse order here!
    locStack.push(postLoopNode);
    locStack.push(firstLoopNode);

    final BlankEdge blankEdge = new BlankEdge("", fileloc.getStartingLineNumber(),
        prevNode, loopStart, "while");
    addToCFA(blankEdge);

    createConditionEdges(whileStatement.getExpression(), fileloc.getStartingLineNumber(),
        loopStart, firstLoopNode, postLoopNode);

    //Visit Body, Expression already handled.
    whileStatement.getBody().accept(this);

    return SKIP_CHILDS;
  }

  @Override
  public boolean visit(DoStatement doStatement) {

    handleElseCondition(doStatement);

    CFileLocation fileloc = astCreator.getFileLocation(doStatement);

    final CFANode prevNode = locStack.pop();

    final CFANode loopStart = new CFANode(fileloc.getStartingLineNumber(),
        cfa.getFunctionName());
    cfaNodes.add(loopStart);
    loopStart.setLoopStart();
    loopStartStack.push(loopStart);

    final CFANode firstLoopNode = new CFANode(fileloc.getStartingLineNumber(),
        cfa.getFunctionName());
    cfaNodes.add(firstLoopNode);

    final CFANode postLoopNode = new CLabelNode(fileloc.getEndingLineNumber(),
        cfa.getFunctionName(), "");
    cfaNodes.add(postLoopNode);
    loopNextStack.push(postLoopNode);

    // inverse order here!
    locStack.push(postLoopNode);
    locStack.push(firstLoopNode);

    final BlankEdge blankEdge = new BlankEdge("", fileloc.getStartingLineNumber(),
        prevNode, firstLoopNode, "do");
    addToCFA(blankEdge);
    createConditionEdges(doStatement.getExpression(), fileloc.getStartingLineNumber(),
        loopStart, firstLoopNode, postLoopNode);

    // Visit Body not Children
    doStatement.getBody().accept(this);

    return SKIP_CHILDS;
  }


  @Override
  public void endVisit(WhileStatement whileStatement){
    handleLeaveWhileLoop();
  }

  @Override
  public void endVisit(DoStatement doStatement){
    handleLeaveWhileLoop();
  }

  private void handleLeaveWhileLoop(){
    CFANode prevNode = locStack.pop();
    CFANode startNode = loopStartStack.pop();

    if (isReachableNode(prevNode)) {
      BlankEdge blankEdge = new BlankEdge("", prevNode.getLineNumber(),
          prevNode, startNode, "");
      addToCFA(blankEdge);
    }
    CFANode nextNode = loopNextStack.pop();
    assert nextNode == locStack.peek();
  }


  @SuppressWarnings({ "cast", "unchecked" })
  @Override
  public boolean visit(final ForStatement forStatement) {

    scope.enterBlock();

    handleElseCondition(forStatement);

    final CFileLocation fileloc = astCreator.getFileLocation(forStatement);
    final int filelocStart = fileloc.getStartingLineNumber();
    final CFANode prevNode = locStack.pop();

    // loopInit is Node before "counter = 0;"
    final CFANode loopInit = new CFANode(filelocStart, cfa.getFunctionName());
    cfaNodes.add(loopInit);
    addToCFA(new BlankEdge("", filelocStart, prevNode, loopInit, "for"));

    // loopStartNodes is the Node before the loop itself,
    // it is the the one after the init edge(s)

    final CFANode loopStart = createInitEdgeForForLoop(((List<Expression>)forStatement.initializers()),
        filelocStart, loopInit);
    loopStart.setLoopStart();



    // firstLoopNode is Node after "counter < 5"
    final CFANode firstLoopNode = new CFANode(
        filelocStart, cfa.getFunctionName());
    cfaNodes.add(firstLoopNode);

    // firstLoopNode is Node after "!(counter < 5)"
    final CFANode postLoopNode = new CLabelNode(
        fileloc.getEndingLineNumber(), cfa.getFunctionName(), "");
    cfaNodes.add(postLoopNode);
    loopNextStack.push(postLoopNode);

    // inverse order here!
    locStack.push(postLoopNode);
    locStack.push(firstLoopNode);

    createConditionEdgesForForLoop(forStatement.getExpression(),
        filelocStart, loopStart, postLoopNode, firstLoopNode);


    // Node before Update "counter++"
    final CFANode lastNodeInLoop = new CFANode(filelocStart, cfa.getFunctionName());
    cfaNodes.add(lastNodeInLoop);

    loopStartStack.push(lastNodeInLoop);


    // visit only loop body, not children
    forStatement.getBody().accept(this);

 // leave loop
    final CFANode prev = locStack.pop();

    final BlankEdge blankEdge = new BlankEdge("",
        filelocStart, prev, lastNodeInLoop , "");
    addToCFA(blankEdge);

    // loopEnd is end of Loop after "counter++;"

    createLastNodesAndEdgeForForLoop(((List<Expression>)forStatement.updaters()),
        filelocStart, lastNodeInLoop, loopStart);

    assert lastNodeInLoop == loopStartStack.pop();
    assert postLoopNode == loopNextStack.pop();
    assert postLoopNode == locStack.peek();

    scope.leaveBlock();

    // skip visiting children of loop, because loopbody was handled before
    return SKIP_CHILDS;
  }


  private void createConditionEdgesForForLoop(Expression condition, int filelocStart, CFANode loopStart,
      CFANode postLoopNode, CFANode firstLoopNode) {

    if (condition == null) {
      // no condition -> only a blankEdge from loopStart to firstLoopNode
      final BlankEdge blankEdge = new BlankEdge("", filelocStart, loopStart,
          firstLoopNode, "");
      addToCFA(blankEdge);

    } else {
      createConditionEdges(condition, filelocStart, loopStart, firstLoopNode,
          postLoopNode);
    }
  }

  private void createLastNodesAndEdgeForForLoop(List<Expression> updaters, int filelocStart, CFANode loopEnd , CFANode loopStart) {

    int size = updaters.size();

    if (size == 0) {
      // no update

      final BlankEdge blankEdge = new BlankEdge("",
          filelocStart, loopEnd, loopStart , "");
      addToCFA(blankEdge);


    } else {

      CFANode prevNode = loopEnd;
      CFANode nextNode = null;


      for (Expression exp : updaters) {
        //TODO Investigate if we can use Expression without Side Effect here
        final IAstNode node = astCreator.convertExpressionWithSideEffects(exp);

        // If last Expression, use last loop Node

          nextNode = new CFANode(filelocStart, cfa.getFunctionName());
          cfaNodes.add(nextNode);


        if (node instanceof AIdExpression) {
          final BlankEdge blankEdge = new BlankEdge(node.toASTString(),
              filelocStart, prevNode, nextNode , "");
          addToCFA(blankEdge);

          // "counter++;"
          //TODO Find better Solution (to String not allowed)
        } else if (node instanceof AExpressionAssignmentStatement) {
          final AStatementEdge lastEdge = new AStatementEdge(exp.toString(),
              (AExpressionAssignmentStatement) node, filelocStart, prevNode, nextNode);
          addToCFA(lastEdge);

          //TODO Find  better Solution (to String not allowed)
        } else if (node instanceof AFunctionCallAssignmentStatement) {
          final AStatementEdge edge = new AStatementEdge(exp.toString(),
              (AFunctionCallAssignmentStatement) node, filelocStart, prevNode, nextNode);
          addToCFA(edge);

        } else { // TODO: are there other iteration-expressions in a for-loop?
          throw new AssertionError("CFABuilder: unknown iteration-expressions in for-statement:\n"
              + exp.getClass());
        }
      }

      //TODO Blank edge here right?
      final BlankEdge blankEdge = new BlankEdge("",
          filelocStart, nextNode, loopStart , "");
      addToCFA(blankEdge);

      assert(nextNode != null) : "Null Pointer not expected. Unexpected behaviour in For Statementen" ;


    }
  }

  @SuppressWarnings("unchecked")
  private CFANode createInitEdgeForForLoop(List<Expression> initializers, int filelocStart, CFANode loopInit) {

      CFANode nextNode = loopInit;

      // counter indicating current element


      for (Expression exp : initializers) {

        final IAstNode node = astCreator.convertExpressionWithSideEffects(exp);

        if(node == null && astCreator.numberOfForInitDeclarations() > 0) {

          nextNode = addDeclarationsToCFA(astCreator.getForInitDeclaration(),filelocStart, initializers.toString() ,nextNode);
          astCreator.getForInitDeclaration().clear();

        } else if (node instanceof AIdExpression) {


          nextNode = new CFANode(filelocStart, cfa.getFunctionName());
          cfaNodes.add(nextNode);


          final BlankEdge blankEdge = new BlankEdge(node.toASTString(),
              filelocStart, loopInit, nextNode , "");
          addToCFA(blankEdge);

        } else if (node instanceof AExpressionAssignmentStatement) {


          nextNode = new CFANode(filelocStart, cfa.getFunctionName());
          cfaNodes.add(nextNode);


          final AStatementEdge lastEdge = new AStatementEdge(exp.toString(),
              (AExpressionAssignmentStatement) node, filelocStart, loopInit, nextNode);
          addToCFA(lastEdge);


        } else if (node instanceof AFunctionCallAssignmentStatement) {


          nextNode = new CFANode(filelocStart, cfa.getFunctionName());
          cfaNodes.add(nextNode);


          final AStatementEdge edge = new AStatementEdge(exp.toString(),
              (AFunctionCallAssignmentStatement) node, filelocStart,loopInit ,   nextNode);
          addToCFA(edge);

        } else { // TODO: are there other iteration-expressions in a for-loop?
          throw new AssertionError("CFABuilder: unknown iteration-expressions in for-statement:\n"
              + exp.getClass());
        }
      }

      assert nextNode != null;

      return nextNode;
  }

  @Override
  public boolean visit(BreakStatement breakStatement) {

    handleElseCondition(breakStatement);



    if(breakStatement.getLabel() == null ){
      handleBreakStatement(breakStatement);
    }else{
      handleLabeledBreakStatement(breakStatement);
    }

    return SKIP_CHILDS;
  }


  private void handleBreakStatement (BreakStatement breakStatement) {


    CFileLocation fileloc = astCreator.getFileLocation(breakStatement);
    CFANode prevNode = locStack.pop();
    CFANode postLoopNode = loopNextStack.peek();


    BlankEdge blankEdge = new BlankEdge(breakStatement.toString(),
        fileloc.getStartingLineNumber(), prevNode, postLoopNode, "break");
    addToCFA(blankEdge);

    CFANode nextNode = new CFANode(fileloc.getEndingLineNumber(),
        cfa.getFunctionName());
    cfaNodes.add(nextNode);
    locStack.push(nextNode);

  }


  private void handleLabeledBreakStatement (BreakStatement breakStatement) {

    CFileLocation fileloc = astCreator.getFileLocation(breakStatement);
    CFANode prevNode = locStack.pop();
    CFANode postLoopNode = labelMap.get(breakStatement.getLabel().getIdentifier());


    BlankEdge blankEdge = new BlankEdge(breakStatement.toString(),
        fileloc.getStartingLineNumber(), prevNode, postLoopNode, "break ");
    addToCFA(blankEdge);

    CFANode nextNode = new CFANode(fileloc.getEndingLineNumber(),
        cfa.getFunctionName());
    cfaNodes.add(nextNode);
    locStack.push(nextNode);
  }


  @Override
  public boolean visit(ContinueStatement continueStatement) {

    handleElseCondition(continueStatement);



    if(continueStatement.getLabel() == null ){
      handleContinueStatement(continueStatement);
    }else{
      //TODO Implement Labeled Continue
     throw new CFAGenerationRuntimeException("Labeled Continue not yet implemented");
    }

    return SKIP_CHILDS;
  }

  private void handleContinueStatement(ContinueStatement continueStatement) {

    CFileLocation fileloc = astCreator.getFileLocation(continueStatement);

    CFANode prevNode = locStack.pop();
    CFANode loopStartNode = loopStartStack.peek();


    BlankEdge blankEdge = new BlankEdge(continueStatement.toString(),
        fileloc.getStartingLineNumber(), prevNode, loopStartNode, "continue");
    addToCFA(blankEdge);

    CFANode nextNode = new CFANode(fileloc.getEndingLineNumber(),
        cfa.getFunctionName());
    locStack.push(nextNode);
  }


  @Override
  public boolean visit(ReturnStatement returnStatement) {

    CFileLocation fileloc = astCreator.getFileLocation(returnStatement);

    CFANode prevNode = locStack.pop();

    CFANode nextNode = new CFANode(fileloc.getEndingLineNumber(),
        cfa.getFunctionName());
    cfaNodes.add(nextNode);


    FunctionExitNode functionExitNode = cfa.getExitNode();

    AReturnStatement cfaReturnStatement = astCreator.convert(returnStatement);

    // If return expression is function
    prevNode = handleSideassignments(prevNode, returnStatement.toString(), fileloc.getStartingLineNumber());

    // TODO After String is supported, delete this
    if(astCreator.getConditionalExpression() != null) {
      astCreator.resetConditionalExpression();
    }

    AReturnStatementEdge edge = new AReturnStatementEdge(returnStatement.toString(),
        cfaReturnStatement , fileloc.getStartingLineNumber(), prevNode, functionExitNode);
    addToCFA(edge);

    locStack.push(nextNode);

    return SKIP_CHILDS;
  }

  /**
   * This method adds this edge to the leaving and entering edges
   * of its predecessor and successor respectively, but it does so only
   * if the edge does not contain dead code
   */
  private void addToCFA(CFAEdge edge) {
    CFACreationUtils.addEdgeToCFA(edge, logger);
  }

  public void createDefaultConstructor(ITypeBinding classBinding) {

    if (locStack.size() != 0) { throw new CFAGenerationRuntimeException("Nested function declarations?"); }

    assert cfa == null;

    final JMethodDeclaration fdef = astCreator.createDefaultConstructor(classBinding);
    handleMethodDeclaration(fdef);
    addNonStaticFieldMember();
    handleReturnFromObject(new CFileLocation(0, "", 0, 0, 0), classBinding.getName(), classBinding);
    handleEndVisitMethodDeclaration();

  }
}
