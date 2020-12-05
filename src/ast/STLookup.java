package ast;
import java.util.*;

public class STLookup {
    /**
     * Purpose: get the ST where variable with provided name, used in a method, is actually declared -
     * it could be a local variable in the method, it could be a field from the enclosing class,
     * or it could be a field in an ancestor class.
     */
    public static SymbolTable findDeclTable(String variable, InheritanceForest forest, SymbolTable enclosingScope, SymbolTable programST) {
        if (enclosingScope.contains(variable, false)) return enclosingScope;
        // the next line is based on the fact that the SymbolTable of a class has no parent
        SymbolTable res = enclosingScope.getParent(); // never null - it's a class ST
        String nm = res.scopeName();
        ClassDecl resClass = forest.nameToClassDecl(nm);
        while(resClass != null && !res.contains(variable, false)) {
            resClass = forest.getSuper(resClass);
            res = classDeclToSymbolTable(resClass, programST);
        }
        return res;
    }

    public static SymbolTable classDeclToSymbolTable(ClassDecl cls, SymbolTable programST) {
    	return programST.getSymbol(cls.name(), false).enclosedScope();
    }

    /**
     * Find the VariableIntro node declaring this variable name
     */
    public static AstNode getDeclNode(SymbolTable scope, String varName) {
        return scope.getSymbol(varName, false).declaration();
    }


    /**
     *
     * @param prog
     * @param forest
     * @return
     */
    public static Map<String, List<STSymbol>> getClassesAndMethodsForVtable(SymbolTable prog, InheritanceForest forest){
        Map<String, List<STSymbol>> res = new HashMap<>();

        for(ClassDecl classDecl : forest.getRoots()){
            RecursiveMethodSymbolsToRes(classDecl, prog, forest, res);
        }

        return res;
    }

    /**
     *
     * @param classDecl
     * @param classTable
     * @return
     */
    private static List<STSymbol> getMethodSymbols(ClassDecl classDecl, SymbolTable classTable){
        List<STSymbol> res = new ArrayList<>();

        for(MethodDecl method : classDecl.methoddecls()){
            res.add(classTable.getSymbol(method.name(), true));
        }

        return res;
    }

    /**
     *
     * @param classDecl
     * @param classTable
     * @param parentMethods
     * @return
     */
    private static List<STSymbol> getMethodSymbols(ClassDecl classDecl, SymbolTable classTable, List<STSymbol> parentMethods){
        Set<String> localDeclNames = new HashSet<>();
        List<STSymbol> localDecls = new ArrayList<>();

        for(MethodDecl method : classDecl.methoddecls()){
            localDecls.add(classTable.getSymbol(method.name(), true));
            localDeclNames.add(method.name());
        }

        List<STSymbol> res = new ArrayList<>();
        for(STSymbol symbol : parentMethods){
            if(!localDeclNames.contains(symbol.name())) res.add(symbol);
        }
        res.addAll(localDecls);

        return res;
    }

    /**
     *
     * @param classDecl
     * @param forest
     * @param res
     */
    private static void RecursiveMethodSymbolsToRes(ClassDecl classDecl, SymbolTable prog, InheritanceForest forest, Map<String, List<STSymbol>> res){
        SymbolTable classTable = prog.getSymbol(classDecl.name(), false).enclosedScope();

        if(classDecl.superName() == null && !res.containsKey(classDecl.name())){
            res.put(classDecl.name(), getMethodSymbols(classDecl, classTable));
        }
        else{//at this point we assume that the super list of STSymbols is already in res
            res.put(classDecl.name(), getMethodSymbols(classDecl, classTable, res.get(classDecl.superName())));
        }

        if(forest.getChildren(classDecl) == null) return;

        for(ClassDecl child : forest.getChildren(classDecl)){
            RecursiveMethodSymbolsToRes(child, prog, forest, res);
        }
    }
}
