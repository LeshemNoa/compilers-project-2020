package ast;

import java.util.List;

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
