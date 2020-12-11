package ast;
import java.util.*;

public class LLVMVisitor implements Visitor{

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

    private final StringBuilder LLVMProgram;
    private final InheritanceForest forest;
    private final SymbolTable programSymbolTable;
    private final Map<String, List<STSymbol>> vtables;
    private final Map<String, List<STSymbol>> instanceTemplates;
    /**
     * Latest index of register / label that is ready to use next.
     * Note the difference - it's not the index of the one most recently used.
     */
    private int methodCurrRegIndex;
    private int methodCurrLabelIndex;
    //to be used for calloc because it will not be that last register assigned
    private int lastCallocReg;

    public LLVMVisitor(Program program){
        LLVMProgram = new StringBuilder();
        forest = new InheritanceForest(program);
        programSymbolTable = new SymbolTable(program);
        List<Map<String, List<STSymbol>>> maps = STLookup.createProgramMaps(programSymbolTable, forest);
        vtables = maps.get(0);
        instanceTemplates = maps.get(1);
    }

    public String getLLVMProgram() {
        return LLVMProgram.toString();
    }

    @Override
    public void visit(Program program) {
        LLVMProgram.append(HELPER_METHODS + "\n");
        program.mainClass().accept(this);
        for(ClassDecl classDecl : forest.getRoots()){
            recursiveVisitTree(classDecl);
        }
    }

    private void recursiveVisitTree(ClassDecl classDecl){
        classDecl.accept(this);
        if(forest.getChildren(classDecl) == null) return;
        for(ClassDecl child : forest.getChildren(classDecl)){
            recursiveVisitTree(child);
        }
    }

    private String generateVTable(ClassDecl classDecl){
        List<STSymbol> methods = this.vtables.get(classDecl.name());
        if(methods == null || methods.size() == 0) return "";

        StringBuilder res = new StringBuilder("\n\n@." + classDecl.name() + "_vtable = global [" + methods.size() + " x i8*] [");
        for(int i = 0; i < methods.size(); i++){
            res.append(methodDeclToVTElem((MethodDecl) methods.get(i).declaration(), classDecl.name()));
            if (i != methods.size()-1) {
                res.append(",");
            }
        }
        res.append("]\n\n");
        return res.toString();
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
        if(typeName.equals("ast.IntAstType")) return "i32";
        if(typeName.equals("ast.IntArrayAstType")) return "i32*";
        if(typeName.equals("ast.BoolAstType")) return "i1";
        if(typeName.equals("ast.RefType")) return "i8*";
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
        LLVMProgram.append(generateVTable(classDecl));
        for (MethodDecl methodDecl : classDecl.methoddecls()){
            methodDecl.accept(this);
        }
    }

    @Override
    public void visit(MainClass mainClass) {
        LLVMProgram.append("define i32 @main() {\n");
        mainClass.mainStatement().accept(this);
        LLVMProgram.append("\tret i32 0\n}");
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
        LLVMProgram.append(signature.concat(") {\n"));
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
        if (methodDecl.ret().getClass().getName().equals("ast.ThisExpr")) {
            LLVMProgram.append(String.format(
                    "\tret %s %%this\n", getLLVMType(methodDecl.returnType())));
        } else {
            LLVMProgram.append(String.format(
                    "\tret %s %%_%d\n", getLLVMType(methodDecl.returnType()), methodCurrRegIndex-1));
        }
        LLVMProgram.append("}\n\n");
    }

    @Override
    public void visit(FormalArg formalArg) {
        String name = formalArg.name();
        String formalReg = "%." + name;
        String type = getLLVMType(formalArg);
        String declAndAssign = "\t%" + name + " = alloca " + type + "\n";
        declAndAssign = declAndAssign.concat("\tstore " + type + " " + formalReg + ", " + type + "* %" + name + "\n");
        LLVMProgram.append(declAndAssign);
    }

    @Override
    public void visit(VarDecl varDecl) {
        // note that this method would never be called on a class's field, only local var
        // note 2: in minijava there's no 'int x = 10;' just the declaration 'int x;' so no need to load & store right now
        LLVMProgram.append("\t%").append(varDecl.name()).append(" = alloca ").append(getLLVMType(varDecl)).append("\n");
    }

    @Override
    public void visit(BlockStatement blockStatement) {
        for (int i = 0; i < blockStatement.statements().size(); i++) {
            blockStatement.statements().get(i).accept(this);
        }
    }

    @Override
    public void visit(IfStatement ifStatement) {
        ifStatement.cond().accept(this);
        LLVMProgram.append(String.format(
                "\tbr i1 %%_%d, label %%if%d, label %%if%d\n", methodCurrRegIndex -1, methodCurrLabelIndex, methodCurrLabelIndex +1));
        LLVMProgram.append(String.format(
                "if%d:\n", methodCurrLabelIndex++
        ));
        ifStatement.thencase().accept(this);
        LLVMProgram.append(String.format("\tbr label %%if%d\n", methodCurrLabelIndex + 1));
        LLVMProgram.append(String.format(
                "if%d:\n", methodCurrLabelIndex++
        ));
        ifStatement.elsecase().accept(this);
        LLVMProgram.append(String.format("\tbr label %%if%d\n", methodCurrLabelIndex));
        LLVMProgram.append(String.format("if%d:\n", methodCurrLabelIndex++));
    }

    @Override
    public void visit(WhileStatement whileStatement) {
        LLVMProgram.append(String.format(
                "\tbr label %%while_cond%d\n", methodCurrLabelIndex
        ));
        int whileCondLabelIndex = methodCurrLabelIndex;
        LLVMProgram.append(String.format(
                "while_cond%d:\n", methodCurrLabelIndex++
        ));
        whileStatement.cond().accept(this);
        LLVMProgram.append(String.format(
                "\tbr i1 %%_%d, label %%while_loop%d, label %%while_end%d\n",
                methodCurrRegIndex -1, methodCurrLabelIndex, methodCurrLabelIndex+1
        ));
        int while_loop = methodCurrLabelIndex++;
        int while_end = methodCurrLabelIndex++;
        LLVMProgram.append(String.format(
                "while_loop%d:\n", while_loop
        ));
        whileStatement.body().accept(this);
        LLVMProgram.append(String.format(
                "\tbr label %%while_cond%d\n", whileCondLabelIndex
        ));
        LLVMProgram.append(String.format(
                "while_end%d:\n", while_end
        ));
    }

    @Override
    public void visit(SysoutStatement sysoutStatement) {
        sysoutStatement.arg().accept(this);
        LLVMProgram.append(String.format(
                "\tcall void (i32) @print_int(i32 %%_%d)\n", methodCurrRegIndex-1
        ));
    }

    @Override
    public void visit(AssignStatement assignStatement) {
        String assigneeName = assignStatement.lv();
        SymbolTable enclosingST = STLookup.findDeclTable(assigneeName, forest, assignStatement.enclosingScope(), programSymbolTable) ;
        VariableIntroduction assigneeVarIntro = (VariableIntroduction) enclosingST.getSymbol(assigneeName, false).declaration();
        String assigneeLLType = getLLVMType(assigneeVarIntro);
        /*
          Case 1: assignee is a local variable in the method
         */
        if (enclosingST.contains(assigneeName, false)) {
            assignStatement.rv().accept(this);
            boolean isNew = assignStatement.rv().getClass().getName().equals("ast.NewObjectExpr") || assignStatement.rv().getClass().getName().equals("ast.NewIntArrayExpr");
            int rvReg = isNew ? lastCallocReg : methodCurrRegIndex-1;
            LLVMProgram.append(String.format(
                    "\tstore %s %%_%d, %s* %%%s\n", assigneeLLType, rvReg, assigneeLLType, assigneeName
            ));
            return;
        }
        String enclosingClassName = enclosingST.getParent().scopeName();
        List<STSymbol> classInstanceShape = instanceTemplates.get(enclosingClassName);
        /*
          Case 2: assignee is a field of %this
         */
        if (classInstanceHasField(classInstanceShape, assigneeName)) {
            assignStatement.rv().accept(this);
            boolean isNew = assignStatement.rv().getClass().getName().equals("ast.NewObjectExpr") || assignStatement.rv().getClass().getName().equals("ast.NewIntArrayExpr");
            int assignedValReg = isNew ? lastCallocReg : methodCurrRegIndex-1;

            int offset = calcFieldOffset(classInstanceShape, assigneeName);
            int assigneePtrReg = methodCurrRegIndex;
            LLVMProgram.append(String.format(
                    "\t%%_%d = getelementptr i8, i8* %%this, i32 %d\n", assigneePtrReg, offset
            ));
            methodCurrRegIndex++;
            int assigneePtrRegPostCast = methodCurrRegIndex;
            LLVMProgram.append(String.format(
                    "\t%%_%d = bitcast i8* %%_%d to %s*\n", assigneePtrRegPostCast, assigneePtrReg, assigneeLLType
            ));
            methodCurrRegIndex++;
            LLVMProgram.append(String.format(
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
        int assigneePtrReg;
        /*
          Case 1: assignee is a local variable in the method
         */
        if (enclosingST.contains(assigneeName, false)) {
            assigneePtrReg = methodCurrRegIndex++;
            LLVMProgram.append(String.format(
                    "\t%%_%d = load i32*, i32** %%%s", assigneePtrReg, assigneeName
            ));
        }
        /*
         * Case 2: assignee is a field of %this
         */
        else if (classInstanceHasField(classInstanceShape, assigneeName)) {
            assigneePtrReg = methodCurrRegIndex;
            int offset = calcFieldOffset(classInstanceShape, assigneeName);
            LLVMProgram.append(String.format(
                    "\t%%_%d = getelementptr i8, i8* %%this, i32 %d\n", assigneePtrReg, offset
            ));
            methodCurrRegIndex++;
            int assigneePtrRegPostCast = methodCurrRegIndex;
            LLVMProgram.append(String.format(
                    "\t%%_%d = bitcast i8* %%_%d to i32*\n", assigneePtrRegPostCast, assigneePtrReg
            ));
            methodCurrRegIndex++;
            assigneePtrReg = assigneePtrRegPostCast;
        } else return;
        /*
         * Check that index is legal, throw exception / assign
         */
        assignArrayStatement.index().accept(this);
        int indexReg = methodCurrRegIndex-1;
        validateIndexArray(indexReg, assigneePtrReg);
        // All ok, we can safely index the array now
        LLVMProgram.append(String.format(
                "arr_alloc%d:\n", methodCurrLabelIndex++
        ));
        LLVMProgram.append(String.format(
                "\t%%_%d = add i32 %%%d, 1", methodCurrRegIndex++, indexReg
        )); // indexReg value is now outdated.
        LLVMProgram.append(String.format(
                "\t%%_%d = getelementptr i32, i32* %%_%d, i32 %%_%d\n", methodCurrRegIndex, assigneePtrReg, methodCurrRegIndex-1
        ));
        int assignLocPtr = methodCurrRegIndex++;
        assignArrayStatement.rv().accept(this);
        LLVMProgram.append(String.format(
                "\tstore i32 %%_%d, i32* %%_%d", methodCurrRegIndex-1, assignLocPtr
        ));
    }

    /**
     * if the index is valid than after this the array may be accessed as desired.
     * if the index is not valid, there will be an oob thrown
     * @param indexReg register that is holding the int representing the desired index
     * @param arrayPtrReg pointer to int
     */
    private void validateIndexArray(int indexReg, int arrayPtrReg){
        // Check that the index is greater than zero
        LLVMProgram.append(String.format(
                "\t%%_%d = icmp slt i32 %%_%d, 0\n", methodCurrRegIndex++, indexReg
        ));
        LLVMProgram.append(String.format(
                "\tbr i1 %%_%d, label %%arr_alloc%d, label %%arr_alloc%d\n", methodCurrRegIndex-1, methodCurrLabelIndex, methodCurrLabelIndex +1
        ));
        // Else throw out of bounds exception
        LLVMProgram.append(String.format(
                "arr_alloc%d:\n", methodCurrLabelIndex++
        ));
        LLVMProgram.append(String.format(
                "\tcall void @throw_oob()\n\tbr label %%arr_alloc%d\n", methodCurrLabelIndex
        ));
        // ok, continue. Load the size of the array (first integer of the array)
        LLVMProgram.append(String.format(
                "arr_alloc%d:\n", methodCurrLabelIndex++
        ));
        LLVMProgram.append(String.format(
                "\t%%_%d = getelementptr i32, i32* %%_%d, i32 0\n", methodCurrRegIndex++, arrayPtrReg
        ));
        LLVMProgram.append(String.format(
                "\t%%_%d = load i32, i32* %%_%d\n", methodCurrRegIndex, methodCurrRegIndex-1
        ));
        methodCurrRegIndex++;
        // Check that the index is less than the size of the array
        // sle rather than slt because the size is off by one because a[0] is occupied
        // refer to Arrays.ll 87-94
        LLVMProgram.append(String.format(
                "\t%%_%d = icmp sle i32 %%_%d, %%_%d\n", methodCurrRegIndex, methodCurrRegIndex-1, indexReg
        ));
        methodCurrRegIndex++;
        LLVMProgram.append(String.format(
                "\tbr i1 %%_%d, label %%arr_alloc%d, label %%arr_alloc%d\n", methodCurrRegIndex-1, methodCurrLabelIndex, methodCurrLabelIndex +1
        ));
        // Else throw out of bounds exception
        LLVMProgram.append(String.format(
                "arr_alloc%d:\n", methodCurrLabelIndex++
        ));
        LLVMProgram.append(String.format(
                "\tcall void @throw_oob()\n\tbr label %%arr_alloc%d\n", methodCurrLabelIndex
        ));
        //if we've reached this line of code, the index is legal
    }

    @Override
    public void visit(AndExpr e) {
        e.e1().accept(this);
        int leftValReg = methodCurrRegIndex-1;
        int leftIsTrue = methodCurrLabelIndex++;
        int endAnd = methodCurrLabelIndex++;
        //if false jump to end_and
        String endE1 = String.format(
                "\tbr i1 %%_%d, label %%if%d, label %%end_and%d\n",
                leftValReg, leftIsTrue, endAnd) + String.format("if%d:\n", leftIsTrue);
        LLVMProgram.append(endE1);
        //asses e2
        e.e2().accept(this);
        int rightValReg = methodCurrRegIndex-1;
        //jump to end_and
        String endE2 = String.format("\tbr label %%end_and%d\n", endAnd);
        //do and
        /* notice that if e1 is false than rightVal could have garbage,
         * but we don't care because leftVal has false so the and will be false*/
        String endAndCommand = String.format("end_and%d:\n\t%%_%d = and i1 %%_%d, %%_%d\n", endAnd, methodCurrRegIndex++, leftValReg, rightValReg);
        LLVMProgram.append(endE2.concat(endAndCommand));
    }

    /**
     * after this function runs, the last line of code in LLVMProgram will be
     * the assignment of the result of the operation into a register
     * Note: this method is NOT meant to be used on AndExpr, that should implement a whole other thing
     * @param e
     * @param operation one of: add, sub, mul, icmp slt
     */
    private void binaryVisit(BinaryExpr e, String operation){
        e.e1().accept(this);
        int leftValueReg = methodCurrRegIndex-1;
        e.e2().accept(this);
        int rightValueReg = methodCurrRegIndex-1;
        LLVMProgram.append(String.format(
                "\t%%_%d = %s i32 %%_%d, %%_%d\n", methodCurrRegIndex++, operation, leftValueReg, rightValueReg));
    }

    @Override
    public void visit(LtExpr e) {
        binaryVisit(e, "icmp slt");
    }

    @Override
    public void visit(AddExpr e) {
        binaryVisit(e, "add");
    }

    @Override
    public void visit(SubtractExpr e) {
        binaryVisit(e, "sub");
    }

    @Override
    public void visit(MultExpr e) {
        binaryVisit(e, "mul");
    }

    @Override
    public void visit(ArrayAccessExpr e) {
        //get pointer to array
        e.arrayExpr().accept(this);
        //now register number methodCurrRegIndex - 1 is the i8* pointer to the pointer to the array

        int arrayPointerReg = methodCurrRegIndex - 1;

        //check if index is not out of bounds
        e.indexExpr().accept(this);
        int indexReg = methodCurrRegIndex - 1;
        validateIndexArray(indexReg, arrayPointerReg);

        LLVMProgram.append(String.format(
                "arr_alloc%d:\n", methodCurrLabelIndex++ //this is not an allocation but let's keep the index validation intact
        ));

        //put value into register
        String updateIndex = String.format("\t%%_%d = add i32* %%_%d, 1\n", methodCurrRegIndex++, indexReg);
        StringBuilder getElem = new StringBuilder(String.format(
                "\t%%_%d = getelementptr i32, i32* %%_%d, i32 %%_%d\n", methodCurrRegIndex, arrayPointerReg, methodCurrRegIndex-1));
        methodCurrRegIndex++;
        getElem.append(String.format("\t%%_%d = load i32, i32* %%_%d\n", methodCurrRegIndex, methodCurrRegIndex-1));
        methodCurrRegIndex++;

        LLVMProgram.append(updateIndex).append(getElem.toString());
    }


    @Override
    public void visit(ArrayLengthExpr e) {
        //get pointer to array
        e.arrayExpr().accept(this);
        int arrayPointerReg = methodCurrRegIndex - 1;

        LLVMProgram.append(String.format("\t%%_%d = load i32, i32* %%_%d\n", methodCurrRegIndex++, arrayPointerReg));
    }

    @Override
    public void visit(MethodCallExpr e) {
        boolean thisExpr = false;
        if (!e.ownerExpr().getClass().getName().equals("ast.ThisExpr")) {
            e.ownerExpr().accept(this);
        } else {
            thisExpr = true;
        }
        //now reg number methodCurrRegIndex - 1 is holding the i8* to the object
        boolean isNew = e.ownerExpr().getClass().getName().equals("ast.NewObjectExpr") || e.ownerExpr().getClass().getName().equals("ast.NewIntArrayExpr");
        int ownerReg = isNew ? lastCallocReg : methodCurrRegIndex-1;
        //Now access the vtable
        if (!thisExpr) {
            LLVMProgram.append(String.format("\t%%_%d = bitcast i8* %%_%d to i8***\n", methodCurrRegIndex++, ownerReg));
        } else {
            LLVMProgram.append(String.format("\t%%_%d = bitcast i8* %%this to i8***\n", methodCurrRegIndex++));
        }
        int vtableReg = methodCurrRegIndex;
        LLVMProgram.append(String.format("\t%%_%d = load i8**, i8*** %%_%d\n", vtableReg, methodCurrRegIndex - 1));
        methodCurrRegIndex++;
        int methodIndex = getMethodIndexInVtable(e);
        LLVMProgram.append(String.format(
                "\t%%_%d = getelementptr i8*, i8** %%_%d, i32 %d\n", methodCurrRegIndex++, vtableReg, methodIndex));
        int methodReg = methodCurrRegIndex;
        LLVMProgram.append(String.format(
                "\t%%_%d = load i8*, i8** %%_%d\n", methodCurrRegIndex, methodCurrRegIndex - 1));
        methodCurrRegIndex++;
        //put actuals into registers
        int numberOfActuals = e.actuals().size();
        List<Integer> actualsRegs = new ArrayList<>(numberOfActuals);
        for(int i = 0; i < e.actuals().size(); i++){
            Expr actual = e.actuals().get(i);
            actual.accept(this);
            actualsRegs.add(i, methodCurrRegIndex-1);
        }
        //find out return type
        String invokerClass = findInvokingClassNameForMethodCall(e);
        MethodDecl methodDecl = (MethodDecl) vtables.get(invokerClass).get(methodIndex).declaration();
        String returnType = getLLVMType(methodDecl.returnType());
        //bitcast to function signature
        StringBuilder castToSignature = new StringBuilder();
        castToSignature.append(String.format("\t%%_%d = bitcast i8* %%_%d to %s (i8*", methodCurrRegIndex++, methodReg, returnType));
        List<FormalArg> formals = methodDecl.formals();
        for(FormalArg formal : formals){
            castToSignature.append(String.format(", %s", getLLVMType(formal.type())));
        }
        castToSignature.append(")*\n");
        LLVMProgram.append(castToSignature.toString());
        //call function
        int methodPointer = methodCurrRegIndex - 1;
        StringBuilder callCommand;
        if(thisExpr){
            callCommand = new StringBuilder(String.format(
                    "\t%%_%d = call %s %%_%d(i8* %%this", methodCurrRegIndex++, returnType, methodPointer));
        }
        else{
            callCommand = new StringBuilder(String.format(
                    "\t%%_%d = call %s %%_%d(i8* %%_%d", methodCurrRegIndex++, returnType, methodPointer, ownerReg));
        }
        for(int i = 0; i < actualsRegs.size(); i++){
            callCommand.append(String.format(", %s %%_%d", getLLVMType(formals.get(i).type()), actualsRegs.get(i)));
        }
        callCommand.append(")\n");
        LLVMProgram.append(callCommand);
    }

    private int getMethodIndexInVtable(MethodCallExpr e){
        String classDeclName = findInvokingClassNameForMethodCall(e);
        List<STSymbol> vt = vtables.get(classDeclName);
        for(int i = 0; i < vt.size(); i++){
            if(vt.get(i).name().equals(e.methodId())) return i;
        }
        //shouldn't reach this part ever
        System.out.println("something went wrong in getting method index");
        return -1;
    }

    private String findInvokingClassNameForMethodCall(MethodCallExpr e){
        if(e.ownerExpr().getClass().getName().equals("ast.ThisExpr")) return e.enclosingScope().getParent().scopeName();
        if(e.ownerExpr().getClass().getName().equals("ast.NewObjectExpr")){
            NewObjectExpr owner = (NewObjectExpr)e.ownerExpr();
            return owner.classId();
        }
        String ownerName = ((IdentifierExpr)e.ownerExpr()).id(); //only option left for owner is identifier
        //find decelNode for owner
        SymbolTable declTable = STLookup.findDeclTable(ownerName, forest, e.enclosingScope(), programSymbolTable);
        VariableIntroduction decl = (VariableIntroduction) STLookup.getDeclNode(declTable, ownerName);
        //declType must be RefType otherwise couldn't envoke a method call
        return ((RefType)decl.type()).id();
    }

    @Override
    public void visit(IntegerLiteralExpr e) {
        LLVMProgram.append(String.format(
                "\t%%_%d = add i32 0, %d\n", methodCurrRegIndex++, e.num()
        ));
    }

    @Override
    public void visit(TrueExpr e) {
        LLVMProgram.append(String.format(
                "\t%%_%d = add i1 0, 1\n", methodCurrRegIndex++
        ));
    }

    @Override
    public void visit(FalseExpr e) {
        LLVMProgram.append(String.format(
                "\t%%_%d = add i1 0, 0\n", methodCurrRegIndex++
        ));
    }

    @Override
    public void visit(IdentifierExpr e) {
        String id = e.id();
        SymbolTable enclosingST = STLookup.findDeclTable(id, forest, e.enclosingScope(), programSymbolTable);
        VariableIntroduction idIntro = (VariableIntroduction) enclosingST.getSymbol(id, false).declaration();
        String idLLType = getLLVMType(idIntro);
        /*
          Case 1: id is a local variable in the method
         */
        if (e.enclosingScope().contains(id, false)) {
            LLVMProgram.append(String.format(
                    "\t%%_%d = load %s, %s* %%%s\n", methodCurrRegIndex++, idLLType, idLLType, id
            ));
            return;
        }
        //if we've reached this code than e is defined as a field of enclosingST
        String enclosingClassName = enclosingST.scopeName();
        List<STSymbol> classInstanceShape = instanceTemplates.get(enclosingClassName);
        /*
          Case 2: id is a field of %this
         */
        if (classInstanceHasField(classInstanceShape, id)) {
            int offset = calcFieldOffset(classInstanceShape, id);
            int idPtrReg = methodCurrRegIndex;
            LLVMProgram.append(String.format(
                    "\t%%_%d = getelementptr i8, i8* %%this, i32 %d\n", idPtrReg, offset
            ));
            methodCurrRegIndex++;
            int idPtrRegPostCast = methodCurrRegIndex;
            LLVMProgram.append(String.format(
                    "\t%%_%d = bitcast i8* %%_%d to %s*\n", idPtrRegPostCast, idPtrReg, idLLType
            ));
            methodCurrRegIndex++;
            LLVMProgram.append(String.format(
                    "\t%%_%d = load %s, %s* %%_%d\n", methodCurrRegIndex++, idLLType, idLLType, idPtrRegPostCast
            ));
        }
    }

    @Override
    public void visit(ThisExpr e) {

    }

    @Override
    public void visit(NewIntArrayExpr e) {
        e.lengthExpr().accept(this);
        int arraySizeReg = methodCurrRegIndex-1;
        //check if size is >= 0, if not throw oob or something
        String compareSize = String.format("\t%%_%d = icmp slt i32 %%_%d, 0\n", methodCurrRegIndex++, arraySizeReg);
        String branch = String.format("\tbr i1 %%_%d, label %%alloc_arr%d, label %%alloc_arr%d\n",
                methodCurrRegIndex - 1, methodCurrLabelIndex, methodCurrLabelIndex + 1);
        String oob = String.format("alloc_arr%d:\n\tcall void @throw_oob()\n\tbr label %%alloc_arr%d\nalloc_arr%d:\n",
                methodCurrLabelIndex, methodCurrLabelIndex + 1, methodCurrLabelIndex + 1);
        methodCurrLabelIndex += 2;
        //size is good
        int actualSize = methodCurrRegIndex;
        String updateSize = String.format("\t%%_%d = add i32 %%_%d, 1\n",methodCurrRegIndex++, arraySizeReg);
        int lenReg = methodCurrRegIndex - 1;
        lastCallocReg = methodCurrRegIndex;
        String allocate = String.format("\t%%_%d = call i8* @calloc(i32 4, i32 %%_%d)\n", methodCurrRegIndex++, lenReg);
        int arrayReg = methodCurrRegIndex;
        String bitcast = String.format("\t%%_%d = bitcast i8* %%_%d to i32*\n", methodCurrRegIndex++, methodCurrRegIndex - 1);
        String inputSize = String.format("\tstore i32 %%_%d, i32* %%_%d\n", actualSize, arrayReg);
        LLVMProgram.append(compareSize)
                .append(branch)
                .append(oob)
                .append(updateSize)
                .append(allocate)
                .append(bitcast)
                .append(inputSize);
    }

    @Override
    public void visit(NewObjectExpr e) {
        int allocationSize = 8;
        for(STSymbol symbol : instanceTemplates.get(e.classId())){
            allocationSize += getSizeInBytes(symbol);
        }
        int objectAddressReg = methodCurrRegIndex++;
        lastCallocReg = objectAddressReg;
        String allocate = String.format("\t%%_%d = call i8* @calloc(i32 1, i32 %d)\n", objectAddressReg, allocationSize);
        int castedI8Pointer = methodCurrRegIndex++;
        String bitcast = String.format("\t%%_%d = bitcast i8* %%_%d to i8***\n", castedI8Pointer, objectAddressReg);
        int vtableAddress = methodCurrRegIndex++;
        int vtableSize = vtables.get(e.classId()).size();
        String getVtable = String.format("\t%%_%d = getelementptr [%d x i8*], [%d x i8*]* @.%s_vtable, i32 0, i32 0\n",
                vtableAddress, vtableSize, vtableSize, e.classId());
        String storeVtable = String.format("\tstore i8** %%_%d, i8*** %%_%d\n", vtableAddress, castedI8Pointer);
        LLVMProgram.append(allocate).append(bitcast).append(getVtable).append(storeVtable);
        //memset to 0?
    }

    @Override
    public void visit(NotExpr e) {
        e.e().accept(this);
        int valReg = methodCurrRegIndex-1;
        LLVMProgram.append(String.format("\t%%_%d = sub i1 1, %s\n", methodCurrRegIndex++, valReg));
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
