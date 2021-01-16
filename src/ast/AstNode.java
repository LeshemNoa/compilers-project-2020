package ast;

import javax.xml.bind.annotation.XmlElement;

public abstract class AstNode {
    @XmlElement(required = false)
    public Integer lineNumber;

    public AstNode() {
        lineNumber = null;
        enclosingScope = null;
    }

    public AstNode(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    abstract public void accept(Visitor v);

    /**
     * ST where ast node is declared
     */
    private SymbolTable enclosingScope;

    public void setEnclosingScope(SymbolTable enclosingScope) {
        this.enclosingScope = enclosingScope;
    }

    public SymbolTable enclosingScope() {
        return this.enclosingScope;
    }
}
