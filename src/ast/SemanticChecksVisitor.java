package ast;

import java.util.*;

public class SemanticChecksVisitor implements Visitor {

    private InheritanceForest forest;
    private SymbolTable programST;
    private Map<String, List<STSymbol>> vtables;
    private Map<String, List<STSymbol>> instanceTemplates;
    private boolean isLegalForest;
    private boolean isLegalST;

    public SemanticChecksVisitor(){
        isLegalForest = true;
        isLegalST = true;
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

    }

    @Override
    public void visit(MainClass mainClass) {

    }

    @Override
    public void visit(MethodDecl methodDecl) {

    }

    @Override
    public void visit(FormalArg formalArg) {

    }

    @Override
    public void visit(VarDecl varDecl) {

    }

    @Override
    public void visit(BlockStatement blockStatement) {

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
