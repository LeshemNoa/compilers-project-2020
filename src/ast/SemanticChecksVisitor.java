package ast;

import java.util.*;

public class SemanticChecksVisitor implements Visitor {

    private InheritanceForest forest;
    private SymbolTable programST;
    private Map<String, List<STSymbol>> vtables;
    private Map<String, List<STSymbol>> instanceTemplates;
    private boolean isLegalForest;
    private boolean isLegalST;
    private boolean visitResult;
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
        if(!a.type().getClass().getName().equals(b.type().getClass().getName())) return false;

        if(a.type().getClass().getName().equals("ast.RefType")){
            //both reference, check that to the same class
            return ((RefType)a.type()).id().equals(((RefType)b.type()).id());
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
            if(!varToCheck.getClass().getName().equals("ast.RefType")){
                //they are both the same non reference type - either int boolean or int[]
                return true;
            }
            //now both are identifier, check if there is inheritance
            return forest.isA(((RefType)varToCheck).id(), ((RefType)toCheckFor).id());
        }
        return false;
    }

    @Override
    public void visit(MainClass mainClass) {

    }

    /**
     * given an expression this method checks its type and returns it
     * if it's a reference type this method makes sure to have the correct id
     * @param e
     * @return AstType representing the type of the expression, null if the expression is not legal
     */
    private AstType getExprType(Expr e){
        String dinamicExprName = e.getClass().getName();

        if(isIntExpr(e)) return new IntAstType();

        if(isBooleanExpr(e)) return new BoolAstType();

        String binaryOps[] = {"ast.AddExpr", "ast.SubtractExpr", "ast.LtExpr", "ast.MultExpr","ast.AndExpr"};
        if(Arrays.asList(binaryOps).contains(dinamicExprName)){
            //binary must be either int or bool, so if we're here than it's not a legal expression
            return null;
        }

        //int array
        if(dinamicExprName.equals("ast.NewIntArrayExpr")) return new IntArrayAstType();

        //now we are left with: method caller, this, identifier, newObject
        if(dinamicExprName.equals("ast.IdentifierExpr")){
            IdentifierExpr ie = (IdentifierExpr)e;
            //get node where identifier was declared
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
            id = e.enclosingScope().getParent().scopeName();
        }
        return new RefType(id);
    }

    /**
     *
     * @param e
     * @return
     */
    private boolean isIntExpr(Expr e){
        String dinamicExprName = e.getClass().getName();
        String exprNames[];

        exprNames = new String[]{"ast.IntegerLiteralExpr", "ast.ArrayLengthExpr", "ast.ArrayAccessExpr"};
        if(Arrays.asList(exprNames).contains(dinamicExprName)){
            return true;
        }

        exprNames = new String[]{"ast.AddExpr", "ast.SubtractExpr", "ast.LtExpr", "ast.MultExpr"};
        if(Arrays.asList(exprNames).contains(dinamicExprName)){
            BinaryExpr be = (BinaryExpr)e;
            //both need to be int
            if(!isIntExpr(be.e1()) || !isIntExpr(be.e2())) return false;
            return true;
        }

        return false;
    }

    /**
     *
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
            if(!isBooleanExpr(ae.e1()) || !isBooleanExpr(ae.e2())) return false;
            return true;
        }

        if(dinamicExprName.equals("ast.NotExpr")){
            if(!isBooleanExpr(((NotExpr)e).e())) return false;
            return true;
        }
        return true;
    }

    /**
     *
     * @param e
     * @return
     */
    private AstType methodCallerToReturnType(MethodCallExpr e){
        String ownerExprType = e.ownerExpr().getClass().getName();
        String callerClassName;

        if(ownerExprType.equals("ast.ThisExpr")){
            callerClassName = e.enclosingScope().getParent().scopeName();
        }

        else if(ownerExprType.equals("ast.NewObjectExpr")){
            callerClassName = ((NewObjectExpr)(e.ownerExpr())).classId();
        }

        else if(ownerExprType.equals("ast.IdentifierExpr")){
            IdentifierExpr ie = (IdentifierExpr)(e.ownerExpr());
            AstNode decl = STLookup.getDeclNode(STLookup.findDeclTable(ie.id(),forest,e.enclosingScope(),programST), ie.id());
            VariableIntroduction varIntro = (VariableIntroduction)decl;
            //check to see that caller is RefType
            if(!varIntro.type().getClass().getName().equals("ast.RefType")) return null;
            callerClassName = ((RefType)varIntro.type()).id();
        }
        //if the caller is none of the above than it's not a legal call
        else return null;

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
        definitelyInitialized = new HashSet<>();
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

    private void visit(VariableIntroduction varIntro){
        AstType varType = varIntro.type();
        if(varType.getClass().getName().equals("ast.RefType")){
            if(forest.nameToClassDecl(((RefType)varType).id()) == null) visitResult = false;
        }
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
