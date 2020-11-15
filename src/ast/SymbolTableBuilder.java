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
            STSymbol fieldSymbol = new STSymbol(field.name(), STSymbol.SymbolKind.FIELD, field);
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
            STSymbol methodSymbol = new STSymbol(method.name(), STSymbol.SymbolKind.METHOD, method);
            classDeclST.addEntry(method.name(), methodSymbol);
            SymbolTable methodST = new SymbolTable(classDeclST);
            method.setEnclosingScope(classDeclST);
            classDeclST.addChildSymbolTable(methodST);
            addVariableSymbols(method, methodST);
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
            STSymbol variableSymbol = new STSymbol(variable.name(), STSymbol.SymbolKind.VAR, variable);
            methodST.addEntry(variable.name(), variableSymbol);
        }
        for (FormalArg arg : arguments) {
            STSymbol argSymbol = new STSymbol(arg.name(), STSymbol.SymbolKind.VAR, arg);
            methodST.addEntry(arg.name(), argSymbol);
        }
    }

    /**
     * Putting everything together.
     * Takes a program, and builds its symbol table
     * as follows: the root ST has no entries, just child STs for each class declaration.
     * Each class declaration has its own ST with its fields and methods as symbols, and with
     * its methods' STs as children. Each method ST contains symbols for each of its variables.
     */
    private void buildProgramSymbolTable() {
        MainClass mainClass = program.mainClass();
        mainClass.setEnclosingScope(programSymTable);
        for (ClassDecl classdecl : program.classDecls()) {
            classdecl.setEnclosingScope(programSymTable);
            SymbolTable classDeclST = new SymbolTable(programSymTable);
            programSymTable.addChildSymbolTable(classDeclST);
            addFieldSymbols(classdecl, classDeclST);
            addMethodSymbols(classdecl, classDeclST);
        }
    }
}
