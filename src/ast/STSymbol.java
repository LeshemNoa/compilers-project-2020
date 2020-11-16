package ast;

public class STSymbol {
    public enum SymbolKind {
        VAR, METHOD, FIELD,
    }

    private String id;
    private SymbolKind kind;
    private String className;
    /**
     * Actual AST node corresponding to this symbol from when it was
     * instantiated - that way we enjoy all the data in the node
     */
    private AstNode decl;

    public AstNode decl() { return decl; }
    
    public STSymbol(String id, SymbolKind kind, String className, AstNode decl) {
        this.id = id;
        this.decl = decl;
        this.kind = kind;
        this.className = className;
    }
    
    public String name() {return id;}
    
    public SymbolKind kind() {return kind;}
    
    public AstNode decleration() {return decl;}
    
    public String className() {return className;}
}
