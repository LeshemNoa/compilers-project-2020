package ast;

public class STLookup {
    /**
     * Purpose: get the ST where variable with provided name, used in a method, is actually declared -
     * it could be a local variable in the method, it could be a field from the enclosing class,
     * or it could be a field in an ancestor class.
     */
    public static SymbolTable findDeclTable(String variable, InheritanceForest forest, SymbolTable enclosingScope, SymbolTable programST) {
        if (enclosingScope.contains(variable)) return enclosingScope;
        // the next line is based on the fact that the SymbolTable of a class has no parent
        SymbolTable res = enclosingScope.getParent(); // never null - it's a class ST
        String nm = res.scopeName();
        ClassDecl resClass = forest.nameToClassDecl(nm);
        while(resClass != null && !res.contains(variable)) {
            resClass = forest.getSuper(resClass);
            res = classDeclToSymbolTable(resClass, programST);
        }
        return res;
    }

    public static SymbolTable classDeclToSymbolTable(ClassDecl cls, SymbolTable programST) {
    	return programST.getSymbol(cls.name()).enclosedScope();
    }

    public static AstNode getDeclNode(SymbolTable scope, String varName) {
        return scope.getSymbol(varName).declaration();
    }
}
