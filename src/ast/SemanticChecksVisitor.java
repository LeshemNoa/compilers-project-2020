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
    private void setVisitResult(boolean visitResult) {
        this.visitResult = visitResult;
    }
    private Stack<Set<String>> definitelyInitialized;

    public SemanticChecksVisitor() {
        isLegalForest = true;
        isLegalST = true;
        setVisitResult(true);
        definitelyInitialized = new Stack<>();
    }

    public boolean isLegalProgram() {
        return isVisitResult() && isLegalForest && isLegalST;
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

        program.mainClass().accept(this);
        if(!isVisitResult()) return;

        for (ClassDecl classDecl : program.classDecls()) {
            classDecl.accept(this);
        }
    }

    @Override
    public void visit(ClassDecl classDecl) {
        definitelyInitialized.push(new HashSet<>()); //for req 14

        if (classDecl.superName() != null) {
            String superName = classDecl.superName();
            //semantic check - no variable with name already defined in ancestor (req 4)
            Set<String> parentFieldNames = new HashSet<>();

            for (STSymbol symbol : instanceTemplates.get(superName)) {
                parentFieldNames.add(symbol.name());
                // add parent fields to initialized set
                definitelyInitialized.peek().add(symbol.name());
            }

            for (VarDecl varDecl : classDecl.fields()) {
                //check for overriding fields - this would be illegal
                if (parentFieldNames.contains(varDecl.name())) {
                    setVisitResult(false);
                    return;
                }
            }

            //semantic check - overriding methods match signature (req 6)
            Map<String, MethodDecl> parentMethodDecls = new HashMap<>();
            for (STSymbol symbol : vtables.get(superName)) {
                parentMethodDecls.put(symbol.name(), (MethodDecl) symbol.declaration());
            }
            for (MethodDecl methodDecl : classDecl.methoddecls()) {
                if (parentMethodDecls.containsKey(methodDecl.name())) {
                    MethodDecl overwritten = parentMethodDecls.get(methodDecl.name());
                    if (!legalMethodOverride(methodDecl, overwritten)) {
                        setVisitResult(false);
                        return;
                    }
                }
            }
        }

        // add fields to initialized set
        for (VarDecl varDecl : classDecl.fields()) {
            varDecl.accept(this);
            if(!isVisitResult()) return;
            definitelyInitialized.peek().add(varDecl.name());
        }

        for (MethodDecl methodDecl : classDecl.methoddecls()) {
            methodDecl.accept(this);
            if (!isVisitResult()) return;
        }

        definitelyInitialized.pop();
    }

    /**
     * given that two methods with the same name are defined in two classes where one extends another,
     * we want to make sure that it's a legal overriding (no overloading in minijava)
     *
     * @param overriding
     * @param overwritten
     * @return boolean value saying if this is legal or not
     */
    private boolean legalMethodOverride(MethodDecl overriding, MethodDecl overwritten) {
        //check for the same number of formals
        if (overriding.formals().size() != overwritten.formals().size()) return false;
        //check that formals match static type
        for (int i = 0; i < overriding.formals().size(); i++) {
            if (!sameStaticType(overriding.formals().get(i), overwritten.formals().get(i))) {
                return false;
            }
        }
        //check that return type is covariant
        if (!isCovariant(overriding.returnType(), overwritten.returnType())) return false;

        return true;
    }

    /**
     * given two VariableIntroduction this method checks if they are of the same static type
     *
     * @param a
     * @param b
     * @return
     */
    private boolean sameStaticType(VariableIntroduction a, VariableIntroduction b) {
        //not the same ast type - return false
        String aTypeName = a.type().getClass().getName();
        String bTypeName = b.type().getClass().getName();
        if (!aTypeName.equals(bTypeName)) return false;

        if (aTypeName.equals("ast.RefType")) {
            //both reference, check that to the same class
            String aClassName = ((RefType) a.type()).id();
            String bClassName = ((RefType) b.type()).id();
            return aClassName.equals(bClassName);
        }
        //both the same type and not reference - int, boolean or int[]
        return true;
    }

    /**
     * checking for covariance for a given pair of AstType
     *
     * @param varToCheck
     * @param toCheckFor
     * @return varToCheck is an covariant of toCheckFor
     */
    private boolean isCovariant(AstType varToCheck, AstType toCheckFor) {
        if (varToCheck.getClass().getName().equals(toCheckFor.getClass().getName())) {
            //both the same ast type - check which one
            if (!varToCheck.getClass().getName().equals("ast.RefType")) {
                //they are both the same non reference type - either int, boolean or int[]
                return true;
            }
            //now both are identifier, check if there is inheritance
            return forest.isA(((RefType) varToCheck).id(), ((RefType) toCheckFor).id());
        }
        return false;
    }

    @Override
    public void visit(MainClass mainClass) {
        definitelyInitialized.push(new HashSet<>()); //for req 14
        mainClass.mainStatement().accept(this);
        definitelyInitialized.pop();
    }

    /**
     * given an expression this method checks its type and returns it
     * if it's a reference type this method makes sure to have the correct id set
     *
     * @param e
     * @return AstType representing the type of e, null if the expression is not legal
     */
    private AstType getExprType(Expr e) {
        String dynamicExprName = e.getClass().getName();

        //int array
        if (dynamicExprName.equals("ast.NewIntArrayExpr")) return new IntArrayAstType();

        if (isIntExpr(e)) return new IntAstType();

        if (isBooleanExpr(e)) return new BoolAstType();

        String[] binaryOps = {"ast.AddExpr", "ast.SubtractExpr", "ast.LtExpr", "ast.MultExpr", "ast.AndExpr"};
        if (Arrays.asList(binaryOps).contains(dynamicExprName)) {
            //binary must be either int or bool, so if we're here than it's not a legal expression
            return null;
        }

        //now we are left with: MethodCallExpr, IdentifierExpr, NewObjectExpr or ThisExpr
        if (dynamicExprName.equals("ast.IdentifierExpr")) {
            return identifierExprToType((IdentifierExpr) e);
        }
        if (dynamicExprName.equals("ast.MethodCallExpr")) {
            return methodCallerToReturnType((MethodCallExpr) e);
        }
        //in the next two cases, the result will be a RefType
        // but it doesn't exists in the program so we have to build it
        String id;
        if (dynamicExprName.equals("ast.NewObjectExpr")) {
            id = ((NewObjectExpr) e).classId();
        } else { //the only possible expression left is ThisExpr
            //if enclosingScope is null we must be in the main class ahd therefore ThisExpr is illegal
            if(e.enclosingScope() == null) return null;

            id = e.enclosingScope().getParent().scopeName();
        }
        return new RefType(id);
    }

    /**
     * This method checks if a given expression is of type int
     * For illegal binary expressions it returns false
     * Note that it doesn't check the legality of: ArrayLengthExpr or ArrayAccessExpr
     * but simply assumes they are int
     *
     * @param e
     * @return
     */
    private boolean isIntExpr(Expr e) {
        String dynamicExprName = e.getClass().getName();
        String[] exprNames;

        exprNames = new String[]{"ast.IntegerLiteralExpr", "ast.ArrayLengthExpr", "ast.ArrayAccessExpr"};
        if (Arrays.asList(exprNames).contains(dynamicExprName)) {
            //for ArrayLengthExpr and ArrayAccessExpr other places in the visitor should check legality
            return true;
        }

        exprNames = new String[]{"ast.AddExpr", "ast.SubtractExpr", "ast.MultExpr"};
        if (Arrays.asList(exprNames).contains(dynamicExprName)) {
            BinaryExpr be = (BinaryExpr) e;
            //both need to be int
            //we call the original getExprType because it could be identifier or method call
            boolean isE1Int = getExprType(be.e1()) instanceof IntAstType;
            boolean isE2Int = getExprType(be.e2()) instanceof IntAstType;
            return isE1Int && isE2Int;
        }
        //if we're here than the Expr doesn't fit an int type
        return false;
    }

    /**
     * This method checks if a given expression is of type boolean
     * For an illegal and expressions it returns false
     *
     * @param e
     * @return
     */
    private boolean isBooleanExpr(Expr e) {
        String dynamicExprName = e.getClass().getName();

        if (dynamicExprName.equals("ast.TrueExpr") || dynamicExprName.equals("ast.FalseExpr")) {
            return true;
        }

        if (dynamicExprName.equals("ast.NotExpr")) {
            return getExprType(((NotExpr) e).e()) instanceof BoolAstType;
        }

        if (dynamicExprName.equals("ast.AndExpr")) {
            AndExpr ae = (AndExpr) e;
            //both need to be boolean
            boolean isE1Bool = getExprType(ae.e1()) instanceof BoolAstType;
            boolean isE2Bool = getExprType(ae.e2()) instanceof BoolAstType;
            return isE1Bool && isE2Bool;
        }

        if (dynamicExprName.equals("ast.LtExpr")) {
            //both need to be int
            BinaryExpr be = (BinaryExpr) e;
            boolean isE1Int = getExprType(be.e1()) instanceof IntAstType;
            boolean isE2Int = getExprType(be.e2()) instanceof IntAstType;
            return isE1Int && isE2Int;
        }

        //if we're here than the Expr doesn't fit a boolean type
        return false;
    }

    /**
     *
     * @param e
     * @return
     */
    private AstType identifierExprToType(IdentifierExpr e){
        AstNode decl = STLookup.getDeclNode(STLookup.findDeclTable(e.id(), forest, e.enclosingScope(), programST), e.id());
        return decl != null ? ((VariableIntroduction) decl).type() : null;
    }

    /**
     * given a MethodCallExpr this method returns the AstType that method returns
     * if it's a reference type this method makes sure to have the correct id set
     *
     * @param e
     * @return AstType that method called returns, null if the call is illegal
     */
    private AstType methodCallerToReturnType(MethodCallExpr e) {
        String ownerExprType = e.ownerExpr().getClass().getName();
        //we'll get the invoker class name and than lookup the vtable to get the MethodDecl
        String callerClassName;

        if (ownerExprType.equals("ast.ThisExpr")) {
            callerClassName = e.enclosingScope().getParent().scopeName();
        } else if (ownerExprType.equals("ast.NewObjectExpr")) {
            callerClassName = ((NewObjectExpr) (e.ownerExpr())).classId();
        } else if (ownerExprType.equals("ast.IdentifierExpr")) {
            IdentifierExpr ie = (IdentifierExpr) (e.ownerExpr());
            //get declaration node of invoker
            AstNode decl = STLookup.getDeclNode(STLookup.findDeclTable(ie.id(), forest, e.enclosingScope(), programST), ie.id());
            if(decl == null) return null;
            VariableIntroduction varIntro = (VariableIntroduction) decl;
            //check to see that caller is RefType
            if (!varIntro.type().getClass().getName().equals("ast.RefType")) return null;
            callerClassName = ((RefType) varIntro.type()).id();
        }
        /* if the caller is none of the above than it's not a legal call
         * check https://www.cs.tau.ac.il/research/yotam.feldman/courses/wcc20/project.html
         * two bullets before last in the overview to be convinced of this*/
        else return null;

        //check to see that owner is actually a class defined in the program
        if (forest.nameToClassDecl(callerClassName) == null) return null;

        for (STSymbol symbol : vtables.get(callerClassName)) {
            if (symbol.name().equals(e.methodId())) {
                return ((MethodDecl) (symbol.declaration())).returnType();
            }
        }
        //if we're here than the method called doesn't exists
        return null;
    }


    @Override
    public void visit(MethodDecl methodDecl) {
        // clone class scope
        definitelyInitialized.push(new HashSet<>(definitelyInitialized.peek()));

        for (VarDecl var : methodDecl.vardecls()) {
            // remove hidden fields from set for current scope
            definitelyInitialized.peek().remove(var.name());
            var.accept(this);
        }

        for (FormalArg formal : methodDecl.formals()) {
            formal.accept(this);
            if (!isVisitResult()) return;
        }

        for (Statement statement : methodDecl.body()) {
            statement.accept(this);
            if (!isVisitResult()) return;
        }

        //check that retType fits the return statement (req 17)
        AstType returnStatementType = getExprType(methodDecl.ret());
        if (returnStatementType == null || !isCovariant(returnStatementType, methodDecl.returnType())) {
            setVisitResult(false);
        }

        methodDecl.ret().accept(this);

        // clear for next method
        definitelyInitialized.pop();
    }

    @Override
    public void visit(FormalArg formalArg) {
        definitelyInitialized.peek().add(formalArg.name());
        visit((VariableIntroduction) formalArg);
    }

    @Override
    public void visit(VarDecl varDecl) {
        visit((VariableIntroduction) varDecl);
    }

    /**
     * This visit makes one check - that the variable class is actually a class in the program (req 7)
     *
     * @param varIntro
     */
    private void visit(VariableIntroduction varIntro) {
        AstType varType = varIntro.type();
        if (varType.getClass().getName().equals("ast.RefType")) {
            if (forest.nameToClassDecl(((RefType) varType).id()) == null) setVisitResult(false);
        }
        //else than the type is not a reference so it's a legal type
    }

    @Override
    public void visit(BlockStatement blockStatement) {
        for (Statement statement : blockStatement.statements()) {
            statement.accept(this);
            if (!isVisitResult()) return;
        }
    }

    @Override
    public void visit(IfStatement ifStatement) {
        Expr cond = ifStatement.cond();
        cond.accept(this);
        if (!isVisitResult()) {
            return;
        }
        // req 16 - cond is boolean
        AstType condType = getExprType(cond);
        if (!(condType instanceof BoolAstType)) {
            setVisitResult(false);
            return;
        }
        Set<String> base = new HashSet<>(definitelyInitialized.peek());

        definitelyInitialized.push(new HashSet<>(base));
        ifStatement.thencase().accept(this);
        definitelyInitialized.push(new HashSet<>(base));
        ifStatement.elsecase().accept(this);

        Set <String> branch2 =  definitelyInitialized.pop();
        Set <String> branch1 =  definitelyInitialized.pop();
        branch1.retainAll(branch2); // set intersection

        // add the intersection back to the current scope
        definitelyInitialized.peek().addAll(branch1);
    }

    @Override
    public void visit(WhileStatement whileStatement) {
        Expr cond = whileStatement.cond();
        cond.accept(this);
        if (!isVisitResult()) {
            return;
        }
        // req 16 - cond is boolean
        AstType condType = getExprType(cond);
        if (!(condType instanceof BoolAstType)) {
            setVisitResult(false);
            return;
        }
        definitelyInitialized.push(new HashSet<>(definitelyInitialized.peek()));
        whileStatement.body().accept(this);
        // we don't know if the condition so we assume it didn't change anything in upper scope
        definitelyInitialized.pop();
    }

    @Override
    public void visit(SysoutStatement sysoutStatement) {
        Expr arg = sysoutStatement.arg();
        arg.accept(this);
        if (!isVisitResult()) return;
        AstType argExprType = getExprType(sysoutStatement.arg());
        // req 18 - arg of sysout stmt must be an int
        if (!(argExprType instanceof IntAstType)) setVisitResult(false);
    }

    /**
     * this method is written as to not duplecate code for both AssignStatement and AssignArrayStatement
     * @param assigneeName
     * @param enclosingScope
     * @return VariableIntroduction node where assigneeName was defined or null if it wasn't
     */
    private VariableIntroduction assigneeDeclNode(String assigneeName, SymbolTable enclosingScope){
        SymbolTable declTable = STLookup.findDeclTable(assigneeName, forest, enclosingScope, programST);
        //req 16 - assignedValueType is defined
        if(declTable == null){
            setVisitResult(false);
            return null;
        }
        return (VariableIntroduction) STLookup.getDeclNode(declTable, assigneeName);
    }

    @Override
    public void visit(AssignStatement assignStatement) {
        VariableIntroduction assigneeDecl = assigneeDeclNode(assignStatement.lv(), assignStatement.enclosingScope());
        //req 16 - assignedValueType is defined
        if(assigneeDecl == null) return;

        AstType assigneeType = assigneeDecl.type();
        AstType assignedValueType = getExprType(assignStatement.rv());
        if (assignedValueType == null) {
            setVisitResult(false);
            return;
        }
        // req 15 - assignedValueType is a subtype of the static assignee type
        if (!isCovariant(assignedValueType, assigneeType)) {
            setVisitResult(false);
            return;
        }
        assignStatement.rv().accept(this);
        definitelyInitialized.peek().add(assignStatement.lv());
    }

    @Override
    public void visit(AssignArrayStatement assignArrayStatement) {
        VariableIntroduction arrayDecl = assigneeDeclNode(assignArrayStatement.lv(), assignArrayStatement.enclosingScope());
        //req 16 - assignedValueType is defined
        if(arrayDecl == null) return;

        // req 21 type checking
        if (!(arrayDecl.type() instanceof IntArrayAstType) ||
            !(getExprType(assignArrayStatement.index()) instanceof IntAstType) ||
                !(getExprType(assignArrayStatement.rv()) instanceof IntAstType)){
            setVisitResult(false);
            return;
        }

        if (!definitelyInitialized.peek().contains(assignArrayStatement.lv())) {
            setVisitResult(false);
            return;
        }

        assignArrayStatement.index().accept(this);
        assignArrayStatement.rv().accept(this);
    }

    private void visit(BinaryExpr e) {
        e.e1().accept(this);
        e.e2().accept(this);
    }

    @Override
    public void visit(AndExpr e) {
        // req 19 - binary expressions type checking
        AstType e1Type = getExprType(e.e1());
        AstType e2Type = getExprType(e.e2());
        if (!(e1Type instanceof BoolAstType) ||
                !(e2Type instanceof BoolAstType)) {
            setVisitResult(false);
            return;
        }
        visit((BinaryExpr)e);
    }

    @Override
    public void visit(LtExpr e) {
        // req 19 - binary expressions type checking
        AstType e1Type = getExprType(e.e1());
        AstType e2Type = getExprType(e.e2());
        if (!(e1Type instanceof IntAstType) ||
                !(e2Type instanceof IntAstType)) {
            setVisitResult(false);
            return;
        }
        visit((BinaryExpr)e);
    }

    @Override
    public void visit(AddExpr e) {
        // req 19 - binary expressions type checking
        if (!(getExprType(e.e1()) instanceof IntAstType) ||
                !(getExprType(e.e2()) instanceof IntAstType)) {
            setVisitResult(false);
            return;
        }
        visit((BinaryExpr)e);
    }

    @Override
    public void visit(SubtractExpr e) {
        // req 19 - binary expressions type checking
        if (!(getExprType(e.e1()) instanceof IntAstType) ||
                !(getExprType(e.e2()) instanceof IntAstType)) {
            setVisitResult(false);
            return;
        }
        visit((BinaryExpr)e);
    }

    @Override
    public void visit(MultExpr e) {
        // req 19 - binary expressions type checking
        if (!(getExprType(e.e1()) instanceof IntAstType) ||
                !(getExprType(e.e2()) instanceof IntAstType)) {
            setVisitResult(false);
            return;
        }
        visit((BinaryExpr)e);
    }

    @Override
    public void visit(ArrayAccessExpr e) {
        // req 20 - make sure index is an int and that the array is int[]
        if (!(getExprType(e.arrayExpr()) instanceof IntArrayAstType) ||
                !(getExprType(e.indexExpr()) instanceof IntAstType)) {
            setVisitResult(false);
            return;
        }
        e.arrayExpr().accept(this);
        e.indexExpr().accept(this);
    }

    @Override
    public void visit(ArrayLengthExpr e) {
        // req 12 - x.length where x is an int[]
        if (!(getExprType(e.arrayExpr()) instanceof IntArrayAstType)) {
            setVisitResult(false);
            return;
        }
        e.arrayExpr().accept(this);
    }

    @Override
    public void visit(MethodCallExpr e) {
        // req 11 - a method call is invoked on a ref type (this, new, or
        // var / formal /  field that are ref types
        AstType ownerType = getExprType(e.ownerExpr());
        // req 9 - must be a reference type
        if (!(ownerType instanceof RefType)) {
            setVisitResult(false);
            return;
        }
        e.ownerExpr().accept(this);
        if (!isVisitResult()) {
            return;
        }

        // req 10 method signature check
        String invokingClass = STLookup.findInvokingClassNameForMethodCall(e, forest, programST);
        List<STSymbol> invokingClassVT = vtables.get(invokingClass);
        // invoking class is undeclared
        if (invokingClassVT == null) {
            setVisitResult(false);
            return;
        }
        int methodIndexInVT = -1;
        for (int i = 0; i < invokingClassVT.size(); i++){
            if (invokingClassVT.get(i).name().equals(e.methodId())) {
                methodIndexInVT = i;
                break;
            }
        }
        // method not in VT
        if (methodIndexInVT == -1) {
            setVisitResult(false);
            return;
        }

        MethodDecl methodDecl = (MethodDecl) vtables.get(invokingClass).get(methodIndexInVT).declaration();

        if (methodDecl.formals().size() != e.actuals().size()) {
            setVisitResult(false);
            return;
        }
        for (int i = 0; i < methodDecl.formals().size(); i++) {
            AstType formalType = methodDecl.formals().get(i).type();
            Expr currActual = e.actuals().get(i);
            currActual.accept(this);
            if (!isVisitResult()) {
                return;
            }
            AstType actualType = getExprType(currActual);

            if (actualType == null || formalType.getClass() != actualType.getClass()) {
                setVisitResult(false);
                return;
            } else if (!isCovariant(actualType, formalType)) {
                    setVisitResult(false);
                    return;
            }
        }

        e.ownerExpr().accept(this);
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
        // req 13 - make sure this identifier is a local variable, a formal parameter, or a field
        SymbolTable enclosingST = STLookup.findDeclTable(e.id(), forest, e.enclosingScope(), programST);
        // var is not declared
        if (enclosingST == null) {
            setVisitResult(false);
            return;
        }
        if (enclosingST.contains(e.id(), false)) {
            if (!definitelyInitialized.peek().contains(e.id())) {
                setVisitResult(false);
            }
            return;
        }
        String enclosingClassName = e.enclosingScope().getParent().scopeName();
        List<STSymbol> classInstanceShape = instanceTemplates.get(enclosingClassName);
        if (classInstanceShape == null || !STLookup.classInstanceHasField(classInstanceShape, e.id())) {
            setVisitResult(false);
        }
    }

    @Override
    public void visit(ThisExpr e) {

    }

    @Override
    public void visit(NewIntArrayExpr e) {
        // req 23 -  check length is an int
        if (!(getExprType(e.lengthExpr()) instanceof IntAstType)){
            setVisitResult(false);
            return;
        }
        e.lengthExpr().accept(this);
    }

    @Override
    public void visit(NewObjectExpr e) {
        // req 8 - new A(); <=> A if defined
        if (!programST.contains(e.classId(), false)) {
            setVisitResult(false);
            return;
        }
    }

    @Override
    public void visit(NotExpr e) {
        // req 19 - not expression type checking
        if (!(getExprType(e.e()) instanceof BoolAstType)){
            setVisitResult(false);
            return;
        }
        e.e().accept(this);
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

    public boolean isVisitResult() {
        return visitResult;
    }

}
