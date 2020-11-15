package ast;
import java.util.HashMap;
import java.util.List;

public class SymbolTable {
    private HashMap<String, STSymbol> entries;
    private SymbolTable parentSymbolTable;
    private List<SymbolTable> childSymbolTables;

    public SymbolTable(Program program) {
        this.parentSymbolTable = null;
        this.entries = new HashMap<>();
        SymbolTableBuilder STBuilder = new SymbolTableBuilder(program, this);
    }

    public SymbolTable(SymbolTable parentST) {
        this.parentSymbolTable = parentST;
        this.entries = new HashMap<>();
    }
    public void addEntry(String name, STSymbol symbol) {
        this.entries.put(name, symbol);
    }

    public void addChildSymbolTable(SymbolTable childST) {
        this.childSymbolTables.add(childST);
    }
}
