package ast;

import java.util.List;

public class SymTableBuildingVisitor implements Visitor{
    SymbolTable symbolTable = new SymbolTable(null);

    /**
     * Take astNode and create a new symbol table for it. Set the table as a child for its
     * parent symbol table, and the parent field in the new table accordingly.
     *
     * @param astNode  The node for which we wish to construct the new symbol table
     * @return  The newly created and linked to and from parent symbol table
     */
    private SymbolTable createNewSTForAstNode(AstNode astNode) {
        SymbolTable enclosingScope = astNode.getEnclosingScope();
        SymbolTable astNodeST = new SymbolTable(enclosingScope);
        enclosingScope.addChildSymbolTable(astNodeST);
        return astNodeST;
    }


    /**
     * Populate provided node's symbol table with symbols for each of its
     * fields by names.
     * @param fieldNames    List of names of fields in provided AstNode
     * @param decl          The astNode where the fields are declared
     * @param declST        The decl astNode's symbol table
     */
    private void addFieldSymbols(String[] fieldNames, AstNode decl, SymbolTable declST) {
        for (String fieldName : fieldNames) {
            STSymbol fieldSymbol = new STSymbol(fieldName, STSymbol.SymbolKind.FIELD, decl);
            declST.addEntry(fieldName, fieldSymbol);
        }
    }

    @Override
    public void visit(Program program) {
        MainClass mainClass = program.mainClass();
        mainClass.setEnclosingScope(symbolTable);
        STSymbol mainClassSymbol = new STSymbol(mainClass.name(), STSymbol.SymbolKind.CLASS_DECL, mainClass);
        program.mainClass().accept(this);
        for (ClassDecl classdecl : program.classDecls()) {
            STSymbol declSymbol = new STSymbol(classdecl.name(), STSymbol.SymbolKind.CLASS_DECL, classdecl);
            symbolTable.addEntry(classdecl.name(), declSymbol);
            classdecl.accept(this);
        }
    }

    @Override
    public void visit(ClassDecl classDecl) {

    }

    @Override
    public void visit(MainClass mainClass) {
        SymbolTable mainClassST = this.createNewSTForAstNode(mainClass);

        String[] fieldNames = {"name", "argsName", "statements"};
        addFieldSymbols(fieldNames, mainClass, mainClassST);

        Statement mainStatement = mainClass.mainStatement();
        mainStatement.setEnclosingScope(mainClassST);
        mainStatement.accept(this);
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
        SymbolTable blockStmtST = createNewSTForAstNode(blockStatement);

        String[] fieldNames = {"statements"};
        addFieldSymbols(fieldNames, blockStatement, blockStmtST);

        List<Statement> statements = blockStatement.statements();
        for (Statement stmt : statements) {
            stmt.setEnclosingScope(blockStmtST);
            stmt.accept(this);
        }
    }

    @Override
    public void visit(IfStatement ifStatement) {
        SymbolTable ifStmtST = createNewSTForAstNode(ifStatement);

        String[] fieldNames = {"cond", "thencase", "elsecase"};
        addFieldSymbols(fieldNames, ifStatement, ifStmtST);
        // TODO: continue implementation

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
