package ast;
import java.util.*;

public class STLookup {
    /**
     * Purpose: get the ST where variable with provided name, used in a method, is actually declared -
     * it could be a local variable in the method, it could be a field from the enclosing class,
     * or it could be a field in an ancestor class.
     */
    public static SymbolTable findDeclTable(String variable, InheritanceForest forest, SymbolTable enclosingScope, SymbolTable programST) {
        if(variable == null || forest == null || enclosingScope == null || programST == null) return null;
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
        if(cls == null || programST == null) return null;
    	return programST.getSymbol(cls.name(), false).enclosedScope();
    }

    /**
     * Find the VariableIntro node declaring this variable name
     */
    public static AstNode getDeclNode(SymbolTable scope, String varName) {
        if(scope == null || varName == null) return null;
        return scope.getSymbol(varName, false).declaration();
    }

    public static String findInvokingClassNameForMethodCall(MethodCallExpr e, InheritanceForest forest, SymbolTable programST){
        if (e.ownerExpr().getClass().getName().equals("ast.ThisExpr")){
            return e.enclosingScope().getParent().scopeName();
        }
        if (e.ownerExpr().getClass().getName().equals("ast.NewObjectExpr")){
            NewObjectExpr owner = (NewObjectExpr)e.ownerExpr();
            return owner.classId();
        }
        String ownerName = ((IdentifierExpr)e.ownerExpr()).id(); //only option left for owner is identifier
        //find declNode for owner
        SymbolTable declTable = STLookup.findDeclTable(ownerName, forest, e.enclosingScope(), programST);
        VariableIntroduction decl = (VariableIntroduction) STLookup.getDeclNode(declTable, ownerName);
        //declType must be RefType otherwise couldn't invoke a method call
        return ((RefType)decl.type()).id();
    }

    /**
     * Check whether provided list of STSymbols representing fields in a class instance, contains
     * a field with provided name.
     * @param instanceShape     List of STSymbols representing the LL structure of a class instance
     * @param fieldName         Field name we search for
     * @return                  true if class instance has this field, else false
     */
    public static boolean classInstanceHasField(List<STSymbol> instanceShape, String fieldName) {
        for (STSymbol field : instanceShape) {
            if (field.name().equals(fieldName)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Creates two mappings:
     * 1. Map from class names to lists of method declarations that belong to them,
     *      either by declaration or by inheritance.
     * 2. Map from class names to lists of field declarations that belong to them,
     *      either by declaration or by inheritance.
     * @param prog      Symbol table for entire program
     * @param forest    Class inheritance forest
     * @return          List containing the two maps
     */
    public static List<Map<String, List<STSymbol>>> createProgramMaps(SymbolTable prog, InheritanceForest forest){
        Map<String, List<STSymbol>> vtablesMap = new HashMap<>();
        Map<String, List<STSymbol>> instanceTemplateMap = new HashMap<>();

        for(ClassDecl classDecl : forest.getRoots()){
            recPopulateMaps(classDecl, prog, forest, vtablesMap, instanceTemplateMap);
        }
        
        List<Map<String, List<STSymbol>>> results = new ArrayList<>();
        results.add(vtablesMap);
        results.add(instanceTemplateMap);
        return results;
    }


    /**
     * Returns list of methods declared in provided class.
     * @param classDecl     declaration node for said class
     * @param classTable    The class's symbol table
     * @return              List of methoddecls
     */
    private static List<STSymbol> getMethodSymbols(ClassDecl classDecl, SymbolTable classTable){
        List<STSymbol> res = new ArrayList<>();

        for(MethodDecl method : classDecl.methoddecls()){
            res.add(classTable.getSymbol(method.name(), true));
        }

        return res;
    }

    /**
     * Returns list of fields declared in provided class.
     * @param classDecl     declaration node for said class
     * @param classTable    The class's symbol table
     * @return              List of vardecls
     */
    private static List<STSymbol> getFieldSymbols(ClassDecl classDecl, SymbolTable classTable){
        List<STSymbol> res = new ArrayList<>();

        for(VarDecl field: classDecl.fields()){
            res.add(classTable.getSymbol(field.name(), false));
        }

        return res;
    }

    /**
     * Returns list of methods, both declared and inherited, in provided class.
     * There is somthing somewhat tricky here and that is that the parent methods need to apeer
     * in the order in the child's vtable.
     * This has to do with inheritance in runtime
     * @param classDecl      declaration node for said class
     * @param classTable     The class's symbol table
     * @param parentMethods  List of methods inherited from the superclass
     * @return List of methoddecls
     */
    private static List<STSymbol> getMethodSymbols(ClassDecl classDecl, SymbolTable classTable, List<STSymbol> parentMethods){
        Set<String> localDeclNames = new HashSet<>();
        List<STSymbol> localDecls = new ArrayList<>();
        for(MethodDecl methodDecl : classDecl.methoddecls()){
            localDecls.add(classTable.getSymbol(methodDecl.name(), true));
            localDeclNames.add(methodDecl.name());
        }

        int addedFromLocal = 0;
        List<STSymbol> res = new ArrayList<>();

        for(STSymbol symbol : parentMethods){
            if (!localDeclNames.contains(symbol.name())) res.add(symbol);
            else{
                res.add(localDecls.get(addedFromLocal++));
            }
        }

        for(; addedFromLocal < localDecls.size(); addedFromLocal++){
            res.add(localDecls.get(addedFromLocal));
        }

        return res;
    }

    /**
     * Returns list of fields, both declared and inherited, in provided class.
     * @param classDecl      declaration node for said class
     * @param classTable     The class's symbol table
     * @param parentFields  List of fields inherited from the superclasses
     * @return List of vardecls
     */
    private static List<STSymbol> getFieldSymbols(ClassDecl classDecl, SymbolTable classTable, List<STSymbol> parentFields){
        Set<String> localDeclNames = new HashSet<>();
        List<STSymbol> localDecls = new ArrayList<>();

        for(VarDecl field : classDecl.fields()){
            localDecls.add(classTable.getSymbol(field.name(), false));
            localDeclNames.add(field.name());
        }

        int addedFromLocal = 0;
        List<STSymbol> res = new ArrayList<>();
        for(STSymbol symbol : parentFields){
            if (!localDeclNames.contains(symbol.name())) res.add(symbol);
            else{
                res.add(localDecls.get(addedFromLocal++));
            }
        }
        for(; addedFromLocal < localDecls.size(); addedFromLocal++){
            res.add(localDecls.get(addedFromLocal));
        }

        return res;
    }

    /**
     * Recursive function traverse the inheritance forest and populate the VT map and the instance map.
     * @param classDecl     Current class declaration reached during traversal, class whose method and field lists are created
     * @param forest        Inheritance forest for class hierarchy, for incremental construction of each class's
     *                      method and field lists, implementing prefixing
     * @param VTMap         VT hash map into which the collected method list is added
     * @param instanceMap   VT hash map into which the collected field list is added
     */
    private static void recPopulateMaps(
            ClassDecl classDecl,
            SymbolTable prog,
            InheritanceForest forest,
            Map<String, List<STSymbol>> VTMap,
            Map<String, List<STSymbol>> instanceMap
    ){
        SymbolTable classTable = prog.getSymbol(classDecl.name(), false).enclosedScope();

        if (classDecl.superName() == null && !VTMap.containsKey(classDecl.name())){
            VTMap.put(classDecl.name(), getMethodSymbols(classDecl, classTable));
            instanceMap.put(classDecl.name(), getFieldSymbols(classDecl, classTable));
        }
        else { //at this point we assume that the super list of STSymbols is already in res
            VTMap.put(classDecl.name(), getMethodSymbols(classDecl, classTable, VTMap.get(classDecl.superName())));
            instanceMap.put(classDecl.name(), getFieldSymbols(classDecl, classTable, instanceMap.get(classDecl.superName())));
        }

        if (forest.getChildren(classDecl) == null) return;

        for (ClassDecl child : forest.getChildren(classDecl)) {
            recPopulateMaps(child, prog, forest, VTMap, instanceMap);
        }
    }
}
