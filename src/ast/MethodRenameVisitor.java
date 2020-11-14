package ast;

public class MethodRenameVisitor implements Visitor {

	private String oldName;
	
	private String newName;
	
	private int lineNumber;
	
	public MethodRenameVisitor(String oldName, String newName, int lineNumber) {
		
		this.oldName = oldName;
		
		this.newName = newName;
		
		this.lineNumber = lineNumber;
	}
	
	@Override
	public void visit(Program program) {
		program.mainClass().accept(this);
		for(ClassDecl cls : program.classDecls()) {
			cls.accept(this);
		}
	}

	@Override
	public void visit(ClassDecl classDecl) {
		for(MethodDecl mtd : classDecl.methoddecls()) {
			mtd.accept(this);
		}	
	}

	@Override
	public void visit(MainClass mainClass) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(MethodDecl methodDecl) {
		if(methodDecl.name() == oldName && methodDecl.lineNumber == this.lineNumber) {
			methodDecl.setName(newName);
		}
		
		for(Statement statement : methodDecl.body()) {
			statement.accept(this);
		}

	}

	@Override
	public void visit(FormalArg formalArg) {return;}

	@Override
	public void visit(VarDecl varDecl) {return;}

	@Override
	public void visit(BlockStatement blockStatement) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(IfStatement ifStatement) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(WhileStatement whileStatement) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(SysoutStatement sysoutStatement) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(AssignStatement assignStatement) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(AssignArrayStatement assignArrayStatement) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(AndExpr e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(LtExpr e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(AddExpr e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(SubtractExpr e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(MultExpr e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(ArrayAccessExpr e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(ArrayLengthExpr e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(MethodCallExpr e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(IntegerLiteralExpr e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(TrueExpr e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(FalseExpr e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(IdentifierExpr e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(ThisExpr e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(NewIntArrayExpr e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(NewObjectExpr e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(NotExpr e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(IntAstType t) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(BoolAstType t) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(IntArrayAstType t) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(RefType t) {
		// TODO Auto-generated method stub

	}

}
