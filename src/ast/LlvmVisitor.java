package ast;
import java.util.*;

public class LlvmVisitor implements Visitor{

    private static final String HELPER_METHODS = "declare i8* @calloc(i32, i32)\n" +
            "declare i32 @printf(i8*, ...)\n" +
            "declare void @exit(i32)\n" +
            "\n" +
            "@_cint = constant [4 x i8] c\"%d\\0a\\00\"\n" +
            "@_cOOB = constant [15 x i8] c\"Out of bounds\\0a\\00\"\n" +
            "define void @print_int(i32 %i) {\n" +
            "    %_str = bitcast [4 x i8]* @_cint to i8*\n" +
            "    call i32 (i8*, ...) @printf(i8* %_str, i32 %i)\n" +
            "    ret void\n" +
            "}\n" +
            "\n" +
            "define void @throw_oob() {\n" +
            "    %_str = bitcast [15 x i8]* @_cOOB to i8*\n" +
            "    call i32 (i8*, ...) @printf(i8* %_str)\n" +
            "    call void @exit(i32 1)\n" +
            "    ret void\n" +
            "}\n";

    private String LLVMProgram;
    private InheritanceForest forest;
    private SymbolTable programSymbolTable;
    private Map<String, List<STSymbol>> vtables;
    private Map<String, List<STSymbol>> instanceTemplates;
    /**
     * Latest index of register / label that is ready to use next.
     * Note the difference - it's not the index of the one most recently used.
     */
    private int methodCurrRegIndex;
    private int methodCurrLabelIndex;

    public LlvmVisitor(Program program){
        LLVMProgram = "";
        forest = new InheritanceForest(program);
        programSymbolTable = new SymbolTable(program);
        List<Map<String, List<STSymbol>>> maps = STLookup.createProgramMaps(programSymbolTable, forest);
        vtables = maps.get(0);
        instanceTemplates = maps.get(1);
    }

    @Override
    public void visit(Program program) {
        LLVMProgram = HELPER_METHODS + "\n";
        for(ClassDecl classDecl : forest.getRoots()){
            recursiveVisitTree(classDecl);
        }
        program.mainClass().accept(this);
    }

    private void recursiveVisitTree(ClassDecl classDecl){
        classDecl.accept(this);
        if(forest.getChildren(classDecl) == null) return;;
        for(ClassDecl child : forest.getChildren(classDecl)){
            recursiveVisitTree(child);
        }
    }

    private String generateVtable(ClassDecl classDecl){
        List<STSymbol> methods = this.vtables.get(classDecl.name());
        if(methods == null || methods.size() == 0) return "";

        String res = "@." + classDecl.name() + "_vtable = global [" + methods.size() + " x i8*] [\n";

        res = res.concat("\t" + methodDeclToVTElem((MethodDecl) methods.get(0).declaration(), classDecl.name()));
        for(int i = 1; i < methods.size(); i++){
            res.concat(",\n\t" + methodDeclToVTElem((MethodDecl) methods.get(0).declaration(), classDecl.name()));
        }

        return res.concat("\n]\n\n");
    }

    private String methodDeclToVTElem(MethodDecl methodDecl, String className){
        String res = "i8* bitcast (i32 (i8*";
        List<FormalArg> formals = methodDecl.formals();
        for(int i = 0; i < methodDecl.formals().size(); i++){
            res = res.concat(", " + getLLVMType(formals.get(i)));
        }
        return res.concat(")* @" + className + "." + methodDecl.name() + " to i8*)");
    }

    private String getLLVMType(VariableIntroduction varIntro){
        return getLLVMType(varIntro.type());
    }

    private String getLLVMType(AstType type){
        String typeName = type.getClass().getName();
        if(typeName.equals("IntAstType")) return "i32";
        if(typeName.equals("IntArrayAstType")) return "i32*";
        if(typeName.equals("BoolAstType")) return "i1";
        if(typeName.equals("RefType")) return "i8*";
        else{
            System.out.println("problem in getLlvmType");
            return null;
        }
    }


    /**
     * Check whether provided list of STSymbols representing fields in a class instance, contains
     * a field with provided name.
     * @param instanceShape     List of STSymbols representing the LL structure of a class instance
     * @param fieldName         Field name we search for
     * @return                  true if class instance has this field, else false
     */
    private boolean classInstanceHasField(List<STSymbol> instanceShape, String fieldName) {
        for (STSymbol field : instanceShape) {
            if (field.name().equals(fieldName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculate how many bytes the variable corresponding to the ST symbol provided take up
     * in the stack.
     */
    private int getSizeInBytes(STSymbol symbol){
        VariableIntroduction varIntro = (VariableIntroduction) symbol.declaration();
        String LLVMType = getLLVMType(varIntro);
        if(LLVMType.equals("i32")) return 4;
        if(LLVMType.equals("i32*")) return 8;
        if(LLVMType.equals("i1")) return 1;
        if(LLVMType.equals("i8*")) return 8;
        else{
            System.out.println("problem in getLlvmType");
            return 0;
        }
    }

    /**
     * Calculate, according to the instanceShape list of fields in class instance,
     * at what offset from the base %this ptr the field we're searching for is located.
     */
    private int calcFieldOffset(List<STSymbol> instanceShape, String fieldName) {
        int offset = 8; // initialized to 8 because at offset 0 there's a VT ptr
        for (STSymbol field : instanceShape) {
            if (field.name().equals(fieldName)) {
                break;
            }
            offset += getSizeInBytes(field);
        }
        return offset;
    }

    @Override
    public void visit(ClassDecl classDecl) {
        LLVMProgram = LLVMProgram.concat(generateVtable(classDecl));
        for (MethodDecl methodDecl : classDecl.methoddecls()){
            methodDecl.accept(this);
        }
    }

    @Override
    public void visit(MainClass mainClass) {

    }

    @Override
    public void visit(MethodDecl methodDecl) {
        methodCurrRegIndex = 0;
        methodCurrLabelIndex = 0;
        String signature = "define " + getLLVMType(methodDecl.returnType()) + " @";
        signature = signature.concat(methodDecl.enclosingScope().scopeName() + "." + methodDecl.name() + "(i8* %this");
        for (FormalArg formal : methodDecl.formals()) {
            signature = signature.concat(", " + getLLVMType(formal) + " %." + formal.name());
        }
        LLVMProgram = LLVMProgram.concat(signature.concat(") {\n"));
        for(FormalArg formal : methodDecl.formals()) {
            formal.accept(this);
        }
        for (VarDecl varDecl : methodDecl.vardecls()) {
            varDecl.accept(this);
        }
        for (Statement stmt : methodDecl.body()) {
            stmt.accept(this);
        }
        methodDecl.ret().accept(this);
        LLVMProgram = LLVMProgram.concat("}\n\n");
    }

    @Override
    public void visit(FormalArg formalArg) {
        String name = formalArg.name();
        String formalReg = "%." + name;
        String type = getLLVMType(formalArg);
        String declAndAssign = "\t%" + name + " = alloca " + type + "\n";
        declAndAssign = declAndAssign.concat("\tstore " + type + " " + formalReg + ", " + type + "* %" + name + "\n");
        LLVMProgram = LLVMProgram.concat(declAndAssign);
    }

    @Override
    public void visit(VarDecl varDecl) {
        // note that this method would never be called on a class's field, only local var
        // note 2: in minijava there's no 'int x = 10;' just the declaration 'int x;' so no need to load & store right now
        LLVMProgram = LLVMProgram.concat("\t%" + varDecl.name() + " = alloca " + getLLVMType(varDecl) + "\n");
    }

    @Override
    public void visit(BlockStatement blockStatement) {
        for (int i = 0; i < blockStatement.statements().size(); i++) {
            blockStatement.statements().get(i).accept(this);
        };
    }

    @Override
    public void visit(IfStatement ifStatement) {
        ifStatement.cond().accept(this);
        LLVMProgram = LLVMProgram.concat(String.format(
                "\tbr i1 %%_%d, label %%if%d, label %%if%d\n", methodCurrRegIndex -1, methodCurrLabelIndex, methodCurrLabelIndex +1));
        LLVMProgram = LLVMProgram.concat(String.format(
                "if%d:\n", methodCurrLabelIndex++
        ));
        ifStatement.thencase().accept(this);
        LLVMProgram = LLVMProgram.concat(String.format(
                "if%d:\n", methodCurrLabelIndex++
        ));
        ifStatement.elsecase().accept(this);
    }

    @Override
    public void visit(WhileStatement whileStatement) {
        LLVMProgram = LLVMProgram.concat(String.format(
                "\tbr label %%while_cond%d\n", methodCurrLabelIndex
        ));
        int whileCondLabelIndex = methodCurrLabelIndex;
        LLVMProgram = LLVMProgram.concat(String.format(
                "while_cond%d:\n", methodCurrLabelIndex++
        ));
        whileStatement.cond().accept(this);
        LLVMProgram = LLVMProgram.concat(String.format(
                "\tbr i1 %%_%d, label %%while_loop%d, label %%while_end%d\n", methodCurrRegIndex -1, methodCurrLabelIndex, methodCurrLabelIndex+1
        ));
        LLVMProgram = LLVMProgram.concat(String.format(
                "while_loop%d:\n", methodCurrLabelIndex++
        ));
        LLVMProgram = LLVMProgram.concat(String.format(
                "\tbr label %%while_cond%d\n", whileCondLabelIndex
        ));
        whileStatement.body().accept(this);
        LLVMProgram = LLVMProgram.concat(String.format(
                "while_end%d:\n", methodCurrLabelIndex++
        ));
    }

    @Override
    public void visit(SysoutStatement sysoutStatement) {
        sysoutStatement.arg().accept(this);
        LLVMProgram = LLVMProgram.concat(String.format(
                "\tcall void (i32) @print_int(i32 %%_%d)\n", methodCurrRegIndex-1
        ));
    }

    @Override
    public void visit(AssignStatement assignStatement) {
        String assigneeName = assignStatement.lv();
        SymbolTable enclosingST = STLookup.findDeclTable(assigneeName, forest, assignStatement.enclosingScope(), programSymbolTable) ;
        VariableIntroduction assigneeVarIntro = (VariableIntroduction) enclosingST.getSymbol(assigneeName, false).declaration();
        String assigneeLLType = getLLVMType(assigneeVarIntro);
        /**
         * Case 1: assignee is a local variable in the method
         */
        if (enclosingST.contains(assigneeName, false)) {
            assignStatement.rv().accept(this);
            LLVMProgram = LLVMProgram.concat(String.format(
                    "\tstore %s %%_%d, %s* %%%s\n", assigneeLLType, methodCurrRegIndex-1, assigneeLLType, assigneeName
            ));
            return;
        }
        String enclosingClassName = enclosingST.getParent().scopeName();
        List<STSymbol> classInstanceShape = instanceTemplates.get(enclosingClassName);
        /**
         * Case 2: assignee is a field of %this
         */
        if (classInstanceHasField(classInstanceShape, assigneeName)) {
            assignStatement.rv().accept(this);
            int assignedValReg = methodCurrRegIndex - 1;
            int offset = calcFieldOffset(classInstanceShape, assigneeName);
            int assigneePtrReg = methodCurrRegIndex;
            LLVMProgram = LLVMProgram.concat(String.format(
                    "\t%%_%d = getelementptr i8, i8* %this, i32 %d\n", assigneePtrReg, offset
            ));
            methodCurrRegIndex++;
            int assigneePtrRegPostCast = methodCurrRegIndex;
            LLVMProgram = LLVMProgram.concat(String.format(
                    "\t%%_%d = bitcast i8* %%_%d to %s*\n", assigneePtrRegPostCast, assigneePtrReg, assigneeLLType
            ));
            methodCurrRegIndex++;
            LLVMProgram = LLVMProgram.concat(String.format(
                    "\tstore %s %%_%d, %s* %%_%d\n", assigneeLLType, assignedValReg, assigneeLLType, assigneePtrRegPostCast
            ));
        }
    }

    @Override
    public void visit(AssignArrayStatement assignArrayStatement) {
        String assigneeName = assignArrayStatement.lv();
        SymbolTable enclosingST = STLookup.findDeclTable(assigneeName, forest, assignArrayStatement.enclosingScope(), programSymbolTable) ;
        String enclosingClassName = enclosingST.getParent().scopeName();
        List<STSymbol> classInstanceShape = instanceTemplates.get(enclosingClassName);

        VariableIntroduction assigneeVarIntro = (VariableIntroduction) enclosingST.getSymbol(assigneeName, false).declaration();
        int assigneePtrReg = -1;
        /**
         * Case 1: assignee is a local variable in the method
         */
        if (enclosingST.contains(assigneeName, false)) {
            assigneePtrReg = methodCurrRegIndex++;
            LLVMProgram = LLVMProgram.concat(String.format(
                    "\t%%_%d = load i32*, i32** %%%s", assigneePtrReg, assigneeName
            ));
        }
        /**
         * Case 2: assignee is a field of %this
         */
        else if (classInstanceHasField(classInstanceShape, assigneeName)) {
            assigneePtrReg = methodCurrRegIndex;
            int offset = calcFieldOffset(classInstanceShape, assigneeName);
            LLVMProgram = LLVMProgram.concat(String.format(
                    "\t%%_%d = getelementptr i8, i8* %this, i32 %d\n", assigneePtrReg, offset
            ));
            methodCurrRegIndex++;
            int assigneePtrRegPostCast = methodCurrRegIndex;
            LLVMProgram = LLVMProgram.concat(String.format(
                    "\t%%_%d = bitcast i8* %%_%d to i32*\n", assigneePtrRegPostCast, assigneePtrReg
            ));
            methodCurrRegIndex++;
            assigneePtrReg = assigneePtrRegPostCast;
        } else return;
        /**
         * Check that index is legal, throw exception / assign
         */
        assignArrayStatement.index().accept(this);
        int indexReg = methodCurrRegIndex-1;
        // Check that the index is greater than zero
        LLVMProgram = LLVMProgram.concat(String.format(
                "\t%%_%d = icmp slt i32 %d, 0\n", methodCurrRegIndex++, indexReg
        ));
        LLVMProgram = LLVMProgram.concat(String.format(
                "\tbr i1 %%_%d, label %%arr_alloc%d, label %arr_alloc%d\n", methodCurrRegIndex-1, methodCurrLabelIndex, methodCurrLabelIndex +1
        ));
        // Else throw out of bounds exception
        LLVMProgram = LLVMProgram.concat(String.format(
                "arr_alloc%d:\n", methodCurrLabelIndex++
        ));
        LLVMProgram = LLVMProgram.concat(String.format(
                "\tcall void @throw_oob()\n\tbr label %%arr_alloc%d\n", methodCurrLabelIndex
        ));
        // ok, continue. Load the size of the array (first integer of the array)
        LLVMProgram = LLVMProgram.concat(String.format(
                "arr_alloc%d:\n", methodCurrLabelIndex++
        ));
        LLVMProgram = LLVMProgram.concat(String.format(
                "\t%%_%d = getelementptr i32, i32* %%_%d, i32 0\n", methodCurrRegIndex++, assigneePtrReg
        ));
        LLVMProgram = LLVMProgram.concat(String.format(
                "\t%%_%d = load i32, i32* %%_%d\n", methodCurrRegIndex, methodCurrRegIndex-1
        ));
        methodCurrRegIndex++;
        // Check that the index is less than the size of the array
        // sle rather than slt because the size is off by one because a[0] is occupied
        // refer to Arrays.ll 87-94
        LLVMProgram = LLVMProgram.concat(String.format(
                "\t%%_%d = icmp sle i32 %%_%d, %%_%d", methodCurrRegIndex, methodCurrRegIndex-1, indexReg
        ));
        methodCurrRegIndex++;
        LLVMProgram = LLVMProgram.concat(String.format(
                "\tbr i1 %%_%d, label %%arr_alloc%d, label %arr_alloc%d\n", methodCurrRegIndex-1, methodCurrLabelIndex, methodCurrLabelIndex +1
        ));
        // Else throw out of bounds exception
        LLVMProgram = LLVMProgram.concat(String.format(
                "arr_alloc%d:\n", methodCurrLabelIndex++
        ));
        LLVMProgram = LLVMProgram.concat(String.format(
                "\tcall void @throw_oob()\n\tbr label %%arr_alloc%d\n", methodCurrLabelIndex
        ));
        // All ok, we can safely index the array now
        LLVMProgram = LLVMProgram.concat(String.format(
                "arr_alloc%d:\n", methodCurrLabelIndex++
        ));
        LLVMProgram = LLVMProgram.concat(String.format(
                "\t%%_%d = add i32 %%%d, 1", methodCurrRegIndex++, indexReg
        )); // indexReg value is now outdated.
        LLVMProgram = LLVMProgram.concat(String.format(
                "\t%%_%d = getelementptr i32, i32* %%_%d, i32 %%_%d\n", methodCurrRegIndex, assigneePtrReg, methodCurrRegIndex-1
        ));
        int assignLocPtr = methodCurrRegIndex++;
        assignArrayStatement.rv().accept(this);
        LLVMProgram = LLVMProgram.concat(String.format(
                "\tstore i32 %%_%d, i32* %%_%d", methodCurrRegIndex-1, assignLocPtr
        ));
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
