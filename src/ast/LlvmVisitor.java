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

    private String LlvmProgram;
    private InheritanceForest forest;
    private SymbolTable programSymbolTable;
    private Map<String, List<STSymbol>> vtables;
    private Map<String, List<STSymbol>> instanceTemplates;
    private int registerPerMethodCounter;

    public LlvmVisitor(Program program){
        LlvmProgram = "";
        forest = new InheritanceForest(program);
        programSymbolTable = new SymbolTable(program);
        List<Map<String, List<STSymbol>>> maps = STLookup.createProgramMaps(programSymbolTable, forest);
        vtables = maps.get(0);
        instanceTemplates = maps.get(1);
    }

    @Override
    public void visit(Program program) {
        LlvmProgram = HELPER_METHODS + "\n";
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

        res = res.concat("\t" + methodDeclToVtableElem((MethodDecl) methods.get(0).declaration(), classDecl.name()));
        for(int i = 1; i < methods.size(); i++){
            res.concat(",\n\t" + methodDeclToVtableElem((MethodDecl) methods.get(0).declaration(), classDecl.name()));
        }

        return res.concat("\n]\n\n");
    }

    private String methodDeclToVtableElem(MethodDecl methodDecl, String className){
        String res = "i8* bitcast (i32 (i8*";
        List<FormalArg> formals = methodDecl.formals();
        for(int i = 0; i < methodDecl.formals().size(); i++){
            res = res.concat(", " + getLlvmType(formals.get(i)));
        }
        return res.concat(")* @" + className + "." + methodDecl.name() + " to i8*)");
    }

    private String getLlvmType(VariableIntroduction varIntro){
        return getLlvmType(varIntro.type());
    }

    private String getLlvmType(AstType type){
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

    @Override
    public void visit(ClassDecl classDecl) {
        LlvmProgram = LlvmProgram.concat(generateVtable(classDecl));
        for(MethodDecl methodDecl : classDecl.methoddecls()){
            methodDecl.accept(this);
        }
    }

    @Override
    public void visit(MainClass mainClass) {

    }

    @Override
    public void visit(MethodDecl methodDecl) {
        registerPerMethodCounter = 0;
        String sinature = "define " + getLlvmType(methodDecl.returnType()) + " @";
        sinature = sinature.concat(methodDecl.enclosingScope().scopeName() + "." + methodDecl.name() + "(i8* %this");
        List<FormalArg> formals = methodDecl.formals();
        for(int i = 0; i < formals.size(); i++){
            sinature = sinature.concat(", " + getLlvmType(formals.get(i)) + " %." + formals.get(i).name());
        }
        LlvmProgram = LlvmProgram.concat(sinature.concat(") {\n"));

        for(FormalArg formal : formals) formal.accept(this);
        for(VarDecl varDecl : methodDecl.vardecls()) varDecl.accept(this);
        for(int i = 0; i < methodDecl.body().size(); i++) methodDecl.body().get(i).accept(this);
        methodDecl.ret().accept(this);

        LlvmProgram = LlvmProgram.concat("}\n\n");
    }

    @Override
    public void visit(FormalArg formalArg) {
        String name = formalArg.name();
        String formalReg = "%." + name;
        String type = getLlvmType(formalArg);
        String declAndAssign = "\t%" + name + " = alloca " + type + "\n";
        declAndAssign = declAndAssign.concat("\tstore " + type + " " + formalReg + ", " + type + "* %" + name + "\n");
        LlvmProgram = LlvmProgram.concat(declAndAssign);
    }

    @Override
    public void visit(VarDecl varDecl) {
        //note that this method would never be called on a class's field, only local var
        LlvmProgram = LlvmProgram.concat("\t%" + varDecl.name() + " = alloca " + getLlvmType(varDecl) + "\n");
    }

    @Override
    public void visit(BlockStatement blockStatement) {
        for(int i = 0; i < blockStatement.statements().size(); i++) blockStatement.statements().get(i).accept(this);
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
