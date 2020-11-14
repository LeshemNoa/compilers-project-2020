package ast;

import java.util.*;

public class MethodRenameVisitor implements Visitor {

	private String oldName;
	
	private String newName;
	
	private int lineNumber;
	
	private InheritanceForest forest;
	
	private Set<ClassDecl> containingClasses;
	
	public MethodRenameVisitor(String oldName, String newName, int lineNumber) {
		
		this.oldName = oldName;
		
		this.newName = newName;
		
		this.lineNumber = lineNumber;
	}
	
	@Override
	public void visit(Program program) {
		forest = new InheritanceForest(program);
		
		program.mainClass().accept(this);
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
	}
	
	private Set<String> getMethodNames(ClassDecl cls){
		Set<String> res = new HashSet<>();
		
		for(MethodDecl mtd : cls.methoddecls()) {
			res.add(mtd.name());
		}
		
		return res;
	}
	
	private void bildClassList(ClassDecl cls) {
		
		//find oldest ancestor with this method
		ClassDecl superClass = forest.nameToClassDecl(cls.superName());
		while(superClass != null && getMethodNames(superClass).contains(oldName)) {
			cls = superClass;
		}
		
		//now make the list
		containingClasses = new HashSet<>();
		containingClasses.add(cls);
		for(ClassDecl decendentCls : forest.getDecendents(cls)) {
			if(getMethodNames(decendentCls).contains(oldName)) {
				containingClasses.add(decendentCls);
			}
		}
	}

	@Override
	public void visit(ClassDecl classDecl) {
		for(MethodDecl mtd : classDecl.methoddecls()) {
			if(containingClasses != null) {
				if(mtd.name() == oldName && containingClasses.contains(classDecl)) {
					mtd.setName(newName);
					mtd.accept(this);
				}
			}
			else if(mtd.name() == oldName && mtd.lineNumber == this.lineNumber) {
				bildClassList(classDecl);
				return;
			}	
		}	
	}

	@Override
	public void visit(MainClass mainClass) {
		// TODO Auto-generated method stub

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

	@Override
	public void visit(MethodCallExpr e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visit(IntegerLiteralExpr e) {return;}

	@Override
	public void visit(TrueExpr e) {return;}

	@Override
	public void visit(FalseExpr e) {return;}

	@Override
	public void visit(IdentifierExpr e) {
		// what is this used for???
		// TODO Auto-generated method stub

	}

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
