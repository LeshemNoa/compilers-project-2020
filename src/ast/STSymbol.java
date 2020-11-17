package ast;

public class STSymbol {
    public enum SymbolKind {
        VAR, METHOD, FIELD, CLASS_DECL
    }

    private String id;
    private SymbolKind kind;
    private String className;
    /**
     * Actual AST node corresponding to this symbol from when it was
     * instantiated - that way we enjoy all the data in the node
     */
    private AstNode decl;

    /**
     * A pointer from CLASS_DECL & METHOD symbols to their enclosed
     * scope's ST
     */
    private SymbolTable enclosedScope = null;

    /**
     * Symbol constructor for VAR and FIELD symbol kinds
     * @param id
     * @param kind
     * @param className
     * @param decl
     */
    public STSymbol(String id, SymbolKind kind, String className, AstNode decl) {
        this.id = id;
        this.decl = decl;
        this.kind = kind;
        this.className = className;
    }

    /**
     * ST symbol constructor for CLASS_DECL symbols
     * @param id                the class name. Will be used for className field as well
     * @param kind              == SymbolKind.CLASS_DECL
     * @param decl              The AST node for classDecl
     * @param enclosedScope     Pointer to the ST of the class scope
     */
    public STSymbol(String id, SymbolKind kind, AstNode decl, SymbolTable enclosedScope) {
        this.id = id;
        this.decl = decl;
        this.kind = kind;
        this.className = id;
        this.enclosedScope = enclosedScope;
    }

    /**
     * ST symbol constructor for METHOD symbols
     * @param id            The method name
     * @param kind          == SymbolKind.METHOD
     * @param className     The name of the class where the method is declared
     * @param decl          The AST node for methodDecl
     * @param enclosedScope Pointer to the ST of the method scope
     */
    public STSymbol(String id, SymbolKind kind, String className, AstNode decl, SymbolTable enclosedScope) {
        this.id = id;
        this.decl = decl;
        this.kind = kind;
        this.className = className;
        this.enclosedScope = enclosedScope;
    }

    public String name() {return id;}
    
    public SymbolKind kind() {return kind;}
    
    public AstNode declaration() {return decl;}
    
    public String className() {return className;}

    public SymbolTable enclosedScope()  { return enclosedScope; }
}
