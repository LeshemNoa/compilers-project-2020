package ast;
import java.util.HashMap;
import java.util.List;

public class SymbolTable {
	
	private String scopeName;
    private HashMap<String, STSymbol> entries;
    private SymbolTable parentSymbolTable;

    /**
     * Constructs a symbol table for the entire program
     */
    public SymbolTable(Program program) {
        this.parentSymbolTable = null;
        this.entries = new HashMap<>();
        new SymbolTableBuilder(program, this);
    }

    /**
     * Constructs an ST for a class or a method scope
     * @param parentST  For classes - programSymbolTable. For methods - the classes where they're declared
     * @param id        Class name / method name
     */
    public SymbolTable(SymbolTable parentST, String id) {
		this.scopeName = id;
        this.parentSymbolTable = parentST;
        this.entries = new HashMap<>();
    }
    public void addEntry(String name, STSymbol symbol) {
        this.entries.put(name, symbol);
    }
    
    public String scopeName() {
    	return scopeName;
    }

    protected boolean contains(String name) {
    	return entries.containsKey(name);
    }

    public SymbolTable getParent() {
        return this.parentSymbolTable;
    }

    public STSymbol getSymbol(String name) {
        return this.entries.get(name);
    }
}
