package ast;

import java.util.*;

public class MethodRenameVisitor implements Visitor {

	private String oldName;

	private String newName;

	private int lineNumber;

	private InheritanceForest forest;

	private Set<String> containingClasses;

	private SymbolTable programSymbolTable;

	public MethodRenameVisitor(String oldName, String newName, int lineNumber) {
		
		this.oldName = oldName;
		
		this.newName = newName;
		
		this.lineNumber = lineNumber;
	}
	
	@Override
	public void visit(Program program) {
		forest = new InheritanceForest(program);
		programSymbolTable = new SymbolTable(program);

		/* two for loops:
		 * once to find the method and build containingClasses set
		 * second time to do the actual renaming
		 */
		for(ClassDecl cls : program.classDecls()) {
			if(containingClasses != null) break;
			cls.accept(this);
		}
		
		for(ClassDecl cls : program.classDecls()) {
			cls.accept(this);
		}
		
		program.mainClass().accept(this);
	}
	
	private Set<String> getMethodNames(ClassDecl cls){
		Set<String> res = new HashSet<>();
		
		for(MethodDecl mtd : cls.methoddecls()) {
			res.add(mtd.name());
		}
		
		return res;
	}
	
	private void buildClassList(ClassDecl cls) {
		
		//find oldest ancestor with this method
		ClassDecl superClass = forest.nameToClassDecl(cls.superName());
		while(superClass != null && getMethodNames(superClass).contains(oldName)) {
			cls = superClass;
		}
		
		//now make the list
		containingClasses = new HashSet<>();
		containingClasses.add(cls.name());
		for(ClassDecl descendantCls : forest.getDescendants(cls)) {
			/**
			 * possible bug: getMethodNames returns list of declarations
			 * and inherited methods are not declared. I think we'll have to
			 */
			if(getMethodNames(descendantCls).contains(oldName)) {
				containingClasses.add(descendantCls.name());
			}
		}
	}

	@Override
	public void visit(ClassDecl classDecl) {
		for(MethodDecl mtd : classDecl.methoddecls()) {
			if(containingClasses != null) {
				if(mtd.name().equals(oldName) && containingClasses.contains(classDecl.name())) {
					mtd.setName(newName);
				}
				mtd.accept(this);
			}
			else if(mtd.name().equals(oldName) && mtd.lineNumber == this.lineNumber) {
				buildClassList(classDecl);
				return;
			}	
		}	
	}

	@Override
	public void visit(MainClass mainClass) {
		mainClass.mainStatement().accept(this);

	}

	@Override
	public void visit(MethodDecl methodDecl) {

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
		for(Statement statement : blockStatement.statements()) {
			statement.accept(this);
		}
	}

	@Override
	public void visit(IfStatement ifStatement) {
		ifStatement.cond().accept(this);
		ifStatement.thencase().accept(this);
		ifStatement.elsecase().accept(this);
	}

	@Override
	public void visit(WhileStatement whileStatement) {
		whileStatement.cond().accept(this);
		whileStatement.body().accept(this);
	}

	@Override
	public void visit(SysoutStatement sysoutStatement) {
		sysoutStatement.arg().accept(this);

	}

	@Override
	public void visit(AssignStatement assignStatement) {
		assignStatement.rv().accept(this);
	}

	@Override
	public void visit(AssignArrayStatement assignArrayStatement) {
		assignArrayStatement.index().accept(this);
		assignArrayStatement.rv().accept(this);
	}
	
	private void visit(BinaryExpr bnexpr) {
		bnexpr.e1().accept(this);
		bnexpr.e2().accept(this);
	}

	@Override
	public void visit(AndExpr e) {
		visit((BinaryExpr) e);
	}

	@Override
	public void visit(LtExpr e) {
		visit((BinaryExpr) e);

	}

	@Override
	public void visit(AddExpr e) {
		visit((BinaryExpr) e);

	}

	@Override
	public void visit(SubtractExpr e) {
		visit((BinaryExpr) e);

	}

	@Override
	public void visit(MultExpr e) {
		visit((BinaryExpr) e);

	}

	@Override
	public void visit(ArrayAccessExpr e) {
		e.arrayExpr().accept(this);
		e.indexExpr().accept(this);
	}

	@Override
	public void visit(ArrayLengthExpr e) {
		e.arrayExpr().accept(this);

	}

	/*
	 * In o.f(arg1, ...) expressions, o must be either:
	 * a local variable, a field, a new A(), or this,
	 * but more complex expressions for o are disallowed.
	 */
	
	private boolean changeNeeded(Expr e) {
		String className = "";
		//switch case
		switch (e.getClass().getName()) {
		case "ThisExpr":
			className = e.getEnclosingScope().getParent().scopeName();
			break;
		case "IdentifierExpr":
			IdentifierExpr idf = (IdentifierExpr)e;
			String varName = idf.id();
			SymbolTable tbl = STLookup.findDeclTable(varName, forest, e.getEnclosingScope(), programSymbolTable);
			VarDecl decl = (VarDecl) STLookup.getDeclNode(tbl, varName);
			className = varDeclToTypeName(decl);
			break;
		case "NewObjectExpr":
			NewObjectExpr newOb = (NewObjectExpr)e;
			className = newOb.classId();
			break;
			
		default:
			//error. throw exception (?)
			
		}
		return containingClasses.contains(className);
	}
	
	private String varDeclToTypeName(VarDecl decl){
	
		switch (decl.type().getClass().getName()){
		
			case("BoolAstType"):
				return "boolean";
			
			case("IntAstType"):
				return "int";
				
			case("IntArrayAstType"):
				return "intArray";
				
			case("RefType"):
				return ((RefType)(decl.type())).id();
			
			default:
				//error
				return null;
		}
	}
	
	@Override
	public void visit(MethodCallExpr e) {
		for(Expr expr : e.actuals()) {
			expr.accept(this);
		}
		
		if(e.methodId() != oldName) return;
		
		if(changeNeeded(e.ownerExpr())) e.setMethodId(newName);
	}

	@Override
	public void visit(IntegerLiteralExpr e) {return;}

	@Override
	public void visit(TrueExpr e) {return;}

	@Override
	public void visit(FalseExpr e) {return;}

	@Override
	public void visit(IdentifierExpr e) {return;}

	@Override
	public void visit(ThisExpr e) {return;}

	@Override
	public void visit(NewIntArrayExpr e) {
		e.lengthExpr().accept(this);

	}

	@Override
	public void visit(NewObjectExpr e) {return;}

	@Override
	public void visit(NotExpr e) {
		e.e().accept(this);

	}

	@Override
	public void visit(IntAstType t) {return;}

	@Override
	public void visit(BoolAstType t) {return;}

	@Override
	public void visit(IntArrayAstType t) {return;}

	@Override
	public void visit(RefType t) {return;}

}
