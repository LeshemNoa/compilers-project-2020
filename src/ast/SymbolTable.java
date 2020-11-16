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
    
    public String name() {
    	return scopeName;
    }
    
    protected boolean isHere(String name) {
    	return entries.containsKey(name);
    }
    
    public String findDeclScope(String variable) {
    	SymbolTable curr = this;
    	while(curr != null && !curr.isHere(variable)) {
    		curr = curr.getParent();
    	}
    	
    	return curr != null ? curr.name() : null;
    }
    
    public String getClassName(String variable) {
    	if(isHere(variable)) return entries.get(variable).className();
    	else return getClassName(findDeclScope(variable));
    }
    
    public SymbolTable getParent() {
    	return parentSymbolTable;
    }
}
