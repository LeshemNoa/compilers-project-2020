package ast;

public class STSymbol {
    public enum SymbolKind {
        VAR, METHOD, FIELD, CLASS_DECL
    }

    private String id;
    private SymbolKind kind;
    /**
     * Actual AST node corresponding to this symbol from when it was
     * instantiated - that way we enjoy all the data in the node
     */
    private AstNode decl;

    public STSymbol(String id, SymbolKind kind, AstNode decl) {
        this.id = id;
        this.decl = decl;
        this.kind = kind;
    }
}
