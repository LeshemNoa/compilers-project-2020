package ast;

import java.util.*;

public class SemanticChecksVisitor implements Visitor {

    private InheritanceForest forest;
    private SymbolTable programST;
    private Map<String, List<STSymbol>> vtables;
    private Map<String, List<STSymbol>> instanceTemplates;
    private boolean isLegalForest;
    private boolean isLegalST;
    private boolean visitResult; //should be set to true at builder but any subsequent sets are to false
    private Set<String> definitelyInitialized;

    public SemanticChecksVisitor(){
        isLegalForest = true;
        isLegalST = true;
        visitResult = true;
    }

    public boolean isLegalProgram(){
        return visitResult;
    }

    @Override
    public void visit(Program program) {
        forest = new InheritanceForest(program);
        isLegalForest = forest.isLegalForest();
        programST = new SymbolTable(program);
        isLegalST = programST.isTableValid();
        if (!isLegalForest || !isLegalST) return;

        List<Map<String, List<STSymbol>>> maps = STLookup.createProgramMaps(programST, forest);
        vtables = maps.get(0);
        instanceTemplates = maps.get(1);

        for(ClassDecl classDecl : program.classDecls()){
            classDecl.accept(this);
        }
    }

    @Override
    public void visit(ClassDecl classDecl) {
        if(classDecl.superName() != null){
            String superName = classDecl.superName();
            //semantic check - no variable with name already defined in ancestor (req 4)
            Set<String> parentFieldNames = new HashSet<>();
            for(STSymbol symbol : instanceTemplates.get(superName)) parentFieldNames.add(symbol.name());
            for(VarDecl varDecl : classDecl.fields()){
                if(parentFieldNames.contains(varDecl.name())){
                    visitResult = false;
                    return;
                }
            }

            //semantic check - overriding methods match signature (req 6)
            Map<String, MethodDecl> parentMethodDecls = new HashMap<>();
            for(STSymbol symbol : vtables.get(superName)){
                parentMethodDecls.put(symbol.name(),(MethodDecl) symbol.declaration() );
            }
            for(MethodDecl methodDecl : classDecl.methoddecls()){
                if(parentMethodDecls.containsKey(methodDecl.name())){
                    MethodDecl overwritten = parentMethodDecls.get(methodDecl.name());
                    if(!legalMethodOverride(methodDecl, overwritten)){
                        visitResult = false;
                        return;
                    }
                }
            }
        }

        for(MethodDecl methodDecl : classDecl.methoddecls()){
            methodDecl.accept(this);
            if(!visitResult) return;
        }
    }

    /**
     * given that two methods with the same name are defined in two classes where one extends another,
     * we want to make sure that it's a legal overriding (no overloading in minijava)
     * @param overriding
     * @param overwritten
     * @return boolean value saying if this is legal or not
     */
    private boolean legalMethodOverride(MethodDecl overriding, MethodDecl overwritten){
        //check for the same number of formals
        if(overriding.formals().size() != overwritten.formals().size()) return false;
        //check that formals match static type
        for(int i = 0; i < overriding.formals().size(); i++){
            if(!sameStaticType(overriding.formals().get(i), overwritten.formals().get(i))){
                return false;
            }
        }
        //check that return type is covariant
        if(!isCovariant(overriding.returnType(), overwritten.returnType())) return false;

        return true;
    }

    /**
     * given two VariableIntroduction this method checks if they are of the same static type
     * @param a
     * @param b
     * @return
     */
    private boolean sameStaticType(VariableIntroduction a, VariableIntroduction b){
        //not the same ast type - return false
        String aTypeName = a.type().getClass().getName();
        String bTypeName = b.type().getClass().getName();
        if(!aTypeName.equals(bTypeName)) return false;

        if(aTypeName.equals("ast.RefType")){
            //both reference, check that to the same class
            String aClassName = ((RefType)a.type()).id();
            String bClassName = ((RefType)b.type()).id();
            return aClassName.equals(bClassName);
        }
        //both the same type and not reference - int, boolean or int[]
        return true;
    }

    /**
     * checking for covariance for a given pair of AstType
     * @param varToCheck
     * @param toCheckFor
     * @return varToCheck is an covariant of toCheckFor
     */
    private boolean isCovariant(AstType varToCheck, AstType toCheckFor){
        if(varToCheck.getClass().getName().equals(toCheckFor.getClass().getName())){
            //both the same ast type - check which one
            if(!varToCheck.getClass().getName().equals("ast.RefType")){
                //they are both the same non reference type - either int, boolean or int[]
                return true;
            }
            //now both are identifier, check if there is inheritance
            return forest.isA(((RefType)varToCheck).id(), ((RefType)toCheckFor).id());
        }
        return false;
    }

    @Override
    public void visit(MainClass mainClass) {
        //TODO
    }

    /**
     * given an expression this method checks its type and returns it
     * if it's a reference type this method makes sure to have the correct id set
     * @param e
     * @return AstType representing the type of e, null if the expression is not legal
     */
    private AstType getExprType(Expr e){
        String dinamicExprName = e.getClass().getName();

        //int array
        if(dinamicExprName.equals("ast.NewIntArrayExpr")) return new IntArrayAstType();

        if(isIntExpr(e)) return new IntAstType();

        if(isBooleanExpr(e)) return new BoolAstType();

        String binaryOps[] = {"ast.AddExpr", "ast.SubtractExpr", "ast.LtExpr", "ast.MultExpr","ast.AndExpr"};
        if(Arrays.asList(binaryOps).contains(dinamicExprName)){
            //binary must be either int or bool, so if we're here than it's not a legal expression
            return null;
        }

        //now we are left with: MethodCallExpr, IdentifierExpr, NewObjectExpr ot ThisExpr
        if(dinamicExprName.equals("ast.IdentifierExpr")){
            IdentifierExpr ie = (IdentifierExpr)e;
            //get node where variable was declared
            AstNode decl = STLookup.getDeclNode(STLookup.findDeclTable(ie.id(),forest,e.enclosingScope(),programST), ie.id());
            return ((VariableIntroduction)decl).type();
        }
        if(dinamicExprName.equals("ast.MethodCallExpr")){
            return methodCallerToReturnType((MethodCallExpr) e);
        }
        //in the next two cases, the result will be a RefType
        // but it doesn't exists in the program so we have to build it
        String id;
        if(dinamicExprName.equals("ast.NewObjectExpr")){
            id = ((NewObjectExpr)e).classId();
        }
        else{ //the only possible expression left is ThisExpr
            //we might want to check this explicitly because of possible unknowed bugs - Noa your input here
            id = e.enclosingScope().getParent().scopeName();
        }
        return new RefType(id);
    }

    /**
     * This method checks if a given expression is of type int
     * For illegal binary expressions it returns false
     * Note that it doesn't check the legality of: ArrayLengthExpr or ArrayAccessExpr
     * but simply assumes they are int
     * @param e
     * @return
     */
    private boolean isIntExpr(Expr e){
        String dinamicExprName = e.getClass().getName();
        String exprNames[];

        exprNames = new String[]{"ast.IntegerLiteralExpr", "ast.ArrayLengthExpr", "ast.ArrayAccessExpr"};
        if(Arrays.asList(exprNames).contains(dinamicExprName)){
            //for ArrayLengthExpr and ArrayAccessExpr other places in the visitor should check legality
            return true;
        }

        exprNames = new String[]{"ast.AddExpr", "ast.SubtractExpr", "ast.LtExpr", "ast.MultExpr"};
        if(Arrays.asList(exprNames).contains(dinamicExprName)){
            BinaryExpr be = (BinaryExpr)e;
            //both need to be int
            return isIntExpr(be.e1()) && isIntExpr(be.e2());
        }
        //if we're here than the Expr doesn't fit an int type
        return false;
    }

    /**
     * This method checks if a given expression is of type boolean
     * For an illegal and expressions it returns false
     * @param e
     * @return
     */
    private boolean isBooleanExpr(Expr e){
        String dinamicExprName = e.getClass().getName();
        if(dinamicExprName.equals("ast.TrueExpr") || dinamicExprName.equals("ast.FalseExpr")){
            return true;
        }

        if(dinamicExprName.equals("ast.AndExpr")){
            AndExpr ae = (AndExpr)e;
            //both need to be boolean
            return isBooleanExpr(ae.e1()) && isBooleanExpr(ae.e2());
        }

        if(dinamicExprName.equals("ast.NotExpr")){
            return isBooleanExpr(((NotExpr)e).e());
        }
        //if we're here than the Expr doesn't fit a boolean type
        return false;
    }

    /**
     * given a MethodCallExpr this method returns the AstType that method returns
     * if it's a reference type this method makes sure to have the correct id set
     * @param e
     * @return AstType that method called returns, null if the call is illegal
     */
    private AstType methodCallerToReturnType(MethodCallExpr e){
        String ownerExprType = e.ownerExpr().getClass().getName();
        //we'll get the invoker class name and than lookup the vtable to get the MethodDecl
        String callerClassName;

        if(ownerExprType.equals("ast.ThisExpr")){
            callerClassName = e.enclosingScope().getParent().scopeName();
        }

        else if(ownerExprType.equals("ast.NewObjectExpr")){
            callerClassName = ((NewObjectExpr)(e.ownerExpr())).classId();
        }

        else if(ownerExprType.equals("ast.IdentifierExpr")){
            IdentifierExpr ie = (IdentifierExpr)(e.ownerExpr());
            //get declaration node of invoker
            AstNode decl = STLookup.getDeclNode(STLookup.findDeclTable(ie.id(),forest,e.enclosingScope(),programST), ie.id());
            VariableIntroduction varIntro = (VariableIntroduction)decl;
            //check to see that caller is RefType
            if(!varIntro.type().getClass().getName().equals("ast.RefType")) return null;
            callerClassName = ((RefType)varIntro.type()).id();
        }
        /* if the caller is none of the above than it's not a legal call
         * check https://www.cs.tau.ac.il/research/yotam.feldman/courses/wcc20/project.html
         * two bullets before last in the overview to be convinced of this*/
        else return null;

        //check to see that owner is actually a class defined in the program
        if(forest.nameToClassDecl(callerClassName) == null) return null;

        for(STSymbol symbol : vtables.get(callerClassName)){
            if(symbol.name().equals(e.methodId())){
                return ((MethodDecl)(symbol.declaration())).returnType();
            }
        }
        //if we're here than the method called doesn't exists
        return null;
    }


    @Override
    public void visit(MethodDecl methodDecl) {
        for(FormalArg formal : methodDecl.formals()){
            formal.accept(this);
            if(!visitResult) return;
        }
        definitelyInitialized = new HashSet<>(); //for req 14
        for(Statement statement : methodDecl.body()){
            statement.accept(this);
            if(!visitResult) return;
        }
        //check that retType fits the return statement (req 17)
        AstType returnStatementType = getExprType(methodDecl.ret());
        if(returnStatementType == null || !isCovariant(returnStatementType, methodDecl.returnType())){
            visitResult = false;
        }
    }

    @Override
    public void visit(FormalArg formalArg) {
        visit((VariableIntroduction)formalArg);
    }

    @Override
    public void visit(VarDecl varDecl) {
        visit((VariableIntroduction)varDecl);
    }

    /**
     * This visit makes one check - that the variable class is actually a class in the program (req 7)
     * @param varIntro
     */
    private void visit(VariableIntroduction varIntro){
        AstType varType = varIntro.type();
        if(varType.getClass().getName().equals("ast.RefType")){
            if(forest.nameToClassDecl(((RefType)varType).id()) == null) visitResult = false;
        }
        //else than the type is not a reference so it's a legal type
    }

    @Override
    public void visit(BlockStatement blockStatement) {
        for(Statement statement : blockStatement.statements()){
            statement.accept(this);
            if(!visitResult) return;
        }
    }

    @Override
    public void visit(IfStatement ifStatement) {

    }

    @Override
    public void visit(WhileStatement whileStatement) {

    }

    @Override
    public void visit(SysoutStatement sysoutStatement) {

    }

    @Override
    public void visit(AssignStatement assignStatement) {

    }

    @Override
    public void visit(AssignArrayStatement assignArrayStatement) {

    }

    @Override
    public void visit(AndExpr e) {

    }

    @Override
    public void visit(LtExpr e) {

    }

    @Override
    public void visit(AddExpr e) {

    }

    @Override
    public void visit(SubtractExpr e) {

    }

    @Override
    public void visit(MultExpr e) {

    }

    @Override
    public void visit(ArrayAccessExpr e) {

    }

    @Override
    public void visit(ArrayLengthExpr e) {

    }

    @Override
    public void visit(MethodCallExpr e) {

    }

    @Override
    public void visit(IntegerLiteralExpr e) {

    }

    @Override
    public void visit(TrueExpr e) {

    }

    @Override
    public void visit(FalseExpr e) {

    }

    @Override
    public void visit(IdentifierExpr e) {

    }

    @Override
    public void visit(ThisExpr e) {

    }

    @Override
    public void visit(NewIntArrayExpr e) {

    }

    @Override
    public void visit(NewObjectExpr e) {

    }

    @Override
    public void visit(NotExpr e) {

    }

    @Override
    public void visit(IntAstType t) {

    }

    @Override
    public void visit(BoolAstType t) {

    }

    @Override
    public void visit(IntArrayAstType t) {

    }

    @Override
    public void visit(RefType t) {

    }
}
