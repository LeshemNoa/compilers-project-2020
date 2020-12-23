package ast;
import java.util.HashMap;
import java.util.List;

public class SymbolTable {
	
	private String scopeName;
    private HashMap<String, STSymbol> entries;
    private  HashMap<String, STSymbol> methodEntries;
    private SymbolTable parentSymbolTable;
    private boolean tableValid;

    public boolean isTableValid() {
        return tableValid;
    }

    /**
     * Constructs a symbol table for the entire program
     */
    public SymbolTable(Program program) {
        tableValid = true;
        this.parentSymbolTable = null;
        this.entries = new HashMap<>();
        SymbolTableBuilder stb = new SymbolTableBuilder(program, this);
        if (!stb.isBuildSuccessful()) {
            tableValid = false;
        }
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
        this.methodEntries = new HashMap<>();
    }
    public void addEntry(String name, STSymbol symbol) {
        if (symbol.kind() == STSymbol.SymbolKind.METHOD) {
            this.methodEntries.put(name, symbol);
        } else {
            this.entries.put(name, symbol);
        }
    }
    
    public String scopeName() {
    	return scopeName;
    }

    protected boolean contains(String name, boolean isMethod) {
        if (isMethod) {
            return this.methodEntries.containsKey(name);
        } else {
            return this.entries.containsKey(name);
        }
    }

    public SymbolTable getParent() {
        return this.parentSymbolTable;
    }

    public STSymbol getSymbol(String name, boolean isMethod) {
        if (isMethod) {
            return this.methodEntries.get(name);
        } else {
            return this.entries.get(name);
        }
    }
}
