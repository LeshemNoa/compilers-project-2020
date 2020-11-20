package ast;

import java.util.*;

public class SymbolTableBuilder {
    Program program;
    SymbolTable programSymTable;

    public SymbolTableBuilder(Program program, SymbolTable programSymTable) {
        this.program = program;
        this.programSymTable = programSymTable;
        buildProgramSymbolTable();
    }

    /**
     * Populate provided class declaration's symbol table with symbols for each of its
     * fields by names.
     * @param classDecl     The class declaration
     * @param declST        The decl astNode's symbol table
     */
    private void addFieldSymbols(ClassDecl classDecl, SymbolTable declST) {
        List<VarDecl> fields = classDecl.fields();
        for (VarDecl field : fields) {
            STSymbol fieldSymbol = new STSymbol(field.name(), STSymbol.SymbolKind.FIELD, classDecl.name(), field);
            declST.addEntry(field.name(), fieldSymbol);
        }
    }

    /**
     * Populate provided class declaration's symbol table with symbols for each of its
     * methods by names.
     * @param classDecl          The class declaration
     * @param classDeclST        The the class declaration's symbol table
     */
    private void addMethodSymbols(ClassDecl classDecl, SymbolTable classDeclST) {
        List<MethodDecl> methods = classDecl.methoddecls();
        for (MethodDecl method : methods) {
            method.setEnclosingScope(classDeclST);
            SymbolTable methodST = new SymbolTable(classDeclST, method.name());
            addVariableSymbols(method, methodST);
            STSymbol methodSymbol = new STSymbol(method.name(), STSymbol.SymbolKind.METHOD, classDecl.name(), method, methodST);
            classDeclST.addEntry(method.name(), methodSymbol);
        }
    }

    /**
     * Populate provided method declaration's symbol table with symbols for each of its
     * variables by names.
     * @param method          The class declaration
     * @param methodST       The the method declaration's symbol table
     */
    private void addVariableSymbols(MethodDecl method, SymbolTable methodST) {
        List<VarDecl> variables = method.vardecls();
        List<FormalArg> arguments = method.formals();
        for (VarDecl variable : variables) {
            variable.setEnclosingScope(methodST);
            STSymbol variableSymbol = new STSymbol(variable.name(), STSymbol.SymbolKind.VAR, method.enclosingScope().scopeName(), variable);
            methodST.addEntry(variable.name(), variableSymbol);
        }
        for (FormalArg arg : arguments) {
            STSymbol argSymbol = new STSymbol(arg.name(), STSymbol.SymbolKind.VAR, method.enclosingScope().scopeName(), arg);
            methodST.addEntry(arg.name(), argSymbol);
        }
        for(Statement statement : method.body()){
            setEnclosingScopeForThisExpr(statement, methodST);
        }
        setEnclosingScopeForThisExpr(method.ret(), methodST);
    }


    /**
     * The next methods all named setEnclosingScopeForThisExpr, overloading each other,
     * are all for finding potential ThisExpr in a given statement and setting its enclosing scope.
     * This is needed for finding out which class a given ThisExpr is reffering to
     */
    private void setEnclosingScopeForThisExpr(Statement statement, SymbolTable methodST){
        switch (statement.getClass().getName()){
            case "ast.AssignArrayStatement":
                setEnclosingScopeForThisExpr((AssignArrayStatement)statement, methodST);
                break;
            case "ast.AssignStatement":
                setEnclosingScopeForThisExpr((AssignStatement)statement, methodST);
                break;
            case "ast.BlockStatement":
                setEnclosingScopeForThisExpr((BlockStatement)statement, methodST);
                break;
            case "ast.IfStatement":
                setEnclosingScopeForThisExpr((IfStatement)statement, methodST);
                break;
            case "ast.SysoutStatement":
                setEnclosingScopeForThisExpr((SysoutStatement)statement, methodST);
                break;
            case "ast.WhileStatement":
                setEnclosingScopeForThisExpr((WhileStatement)statement, methodST);
                break;
            default:
                //error
        }
    }

    private void setEnclosingScopeForThisExpr(AssignArrayStatement statement, SymbolTable methodST){
        setEnclosingScopeForThisExpr(statement.rv(), methodST);
        setEnclosingScopeForThisExpr(statement.index(), methodST);
    }

    private void setEnclosingScopeForThisExpr(AssignStatement statement, SymbolTable methodST){
        setEnclosingScopeForThisExpr(statement.rv(), methodST);
    }

    private void setEnclosingScopeForThisExpr(BlockStatement statement, SymbolTable methodST){
        for(Statement stmnt : statement.statements()){
            setEnclosingScopeForThisExpr(stmnt, methodST);
        }
    }

    private void setEnclosingScopeForThisExpr(IfStatement statement, SymbolTable methodST){
        setEnclosingScopeForThisExpr(statement.cond(), methodST);
        setEnclosingScopeForThisExpr(statement.thencase(), methodST);
        setEnclosingScopeForThisExpr(statement.elsecase(), methodST);
    }

    private void setEnclosingScopeForThisExpr(SysoutStatement statement, SymbolTable methodST){
        setEnclosingScopeForThisExpr(statement.arg(), methodST);
    }

    private void setEnclosingScopeForThisExpr(WhileStatement statement, SymbolTable methodST){
        setEnclosingScopeForThisExpr(statement.cond(), methodST);
        setEnclosingScopeForThisExpr(statement.body(), methodST);
    }

    private void setEnclosingScopeForThisExpr(Expr e, SymbolTable methodST){
        String classTypeName = e.getClass().getName();
        String binars[] = {"ast.AddExpr", "ast.AndExpr", "ast.LtExpr", "ast.MultExpr", "ast.SubtractExpr"};
        if(Arrays.asList(binars).contains(classTypeName)){
            setEnclosingScopeForThisExpr(((BinaryExpr)e).e1(), methodST);
            setEnclosingScopeForThisExpr(((BinaryExpr)e).e2(), methodST);
            return;
        }
        switch(classTypeName){
            case "ast.ThisExpr":
            case "ast.IdentifierExpr":
                e.setEnclosingScope(methodST);
                return;
            case "ast.NewIntArrayExpr":
                setEnclosingScopeForThisExpr(((NewIntArrayExpr)e).lengthExpr(), methodST);
                return;
            case "ast.ArrayLengthExpr":
                setEnclosingScopeForThisExpr(((ArrayLengthExpr)e).arrayExpr(), methodST);
                return;
            case "ast.ArrayAccessExpr":
                setEnclosingScopeForThisExpr(((ArrayAccessExpr)e).arrayExpr(), methodST);
                setEnclosingScopeForThisExpr(((ArrayAccessExpr)e).indexExpr(), methodST);
                return;
            case "ast.MethodCallExpr":
                setEnclosingScopeForThisExpr(((MethodCallExpr)e).ownerExpr(), methodST);
                for(Expr ex : ((MethodCallExpr)e).actuals()){
                    setEnclosingScopeForThisExpr(ex, methodST);
                }
                return;
            case "ast.NotExpr":
                setEnclosingScopeForThisExpr(((NotExpr)e).e(), methodST);
                return;
        }

    }


    /**
     * Putting everything together.
     * Takes a program, and builds its symbol table as follows:
     * The root ST's entries have class names for keys and their corresponding STSymbols
     * point to their enclosed scope's table.
     * Each of the class tables has symbols for its fields and symbols for its methods, where
     * the method symbols have a pointer to their own scope's symbol table.
     */
    private void buildProgramSymbolTable() {
        for (ClassDecl classdecl : program.classDecls()) {
            classdecl.setEnclosingScope(programSymTable);
            SymbolTable classDeclST = new SymbolTable(programSymTable, classdecl.name());
            addMethodSymbols(classdecl, classDeclST);
            addFieldSymbols(classdecl, classDeclST);
            STSymbol classDeclSymbol = new STSymbol(classdecl.name(), STSymbol.SymbolKind.CLASS_DECL, classdecl, classDeclST);
            programSymTable.addEntry(classdecl.name(), classDeclSymbol);
        }
    }
}
