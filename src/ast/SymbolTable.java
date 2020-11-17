package ast;
import java.util.HashMap;
import java.util.List;

public class SymbolTable {
	
	private String scopeName;
    private HashMap<String, STSymbol> entries;
    private SymbolTable parentSymbolTable;
    private List<SymbolTable> childSymbolTables;


    public SymbolTable(Program program) {
        this.parentSymbolTable = null;
        this.entries = new HashMap<>();
        SymbolTableBuilder STBuilder = new SymbolTableBuilder(program, this);
    }

    public SymbolTable(SymbolTable parentST, String id) {
		this.scopeName = id;
        this.parentSymbolTable = parentST;
        this.entries = new HashMap<>();
    }
    public void addEntry(String name, STSymbol symbol) {
        this.entries.put(name, symbol);
    }

    public void addChildSymbolTable(SymbolTable childST) {
        this.childSymbolTables.add(childST);
    }
    
    public String scopeName() {
    	return scopeName;
    }
    
    protected boolean isHere(String name) {
    	return entries.containsKey(name);
    }
    
    public String findDeclScope(String variable, InheritanceForest forest) {
    	SymbolTable tbl = findDeclTable(variable, forest);
    	return tbl != null ? tbl.scopeName() : null; 	
    }
    
    public SymbolTable findDeclTable(String variable, InheritanceForest forest) {
    	if(isHere(variable)) return this;
    	//the next line is based on the fact that the SymbolTable of a class has no parent
    	SymbolTable res = parentSymbolTable != null ? parentSymbolTable : this;
    	String nm = res != null ? res.scopeName() : null; 
    	ClassDecl resClass = forest.nameToClassDecl(nm);
    	while(resClass != null && !res.isHere(variable)) {
    		resClass = forest.getSuper(resClass);
    		res = classDeclToSymbolTable(resClass);
    	}
    	return res;

    }
    
    
    //this feels a little silly to define, but it helps not change builers
    private SymbolTable classDeclToSymbolTable(ClassDecl cls) {
    	//Assuming at least one field or one method are defined in cls
    	//if the above is not true than the esle case will have null pointer exception
    	if(cls.fields().size() > 0) return cls.fields().get(0).getEnclosingScope();
    	else return cls.methoddecls().get(0).getEnclosingScope();
    }
    
    public String getClassName(String variable, InheritanceForest forest) {
    	if(isHere(variable)) return entries.get(variable).className();
    	else return findDeclTable(variable, forest).getClassName(variable, forest);
    }
    
    public SymbolTable getParent() {
    	return parentSymbolTable;
    }
    
    public AstNode getDeclNode(String varName){
    	if(!entries.containsKey(varName)) return null;
    	return entries.get(varName).decleration();
    }
}
