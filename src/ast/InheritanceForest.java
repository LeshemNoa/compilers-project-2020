package ast;

import java.util.*;

public class InheritanceForest {

	private class ForestNode {
		ForestNode superNode;
		ArrayList<ForestNode> children;
		ClassDecl value;
		
		public ForestNode(ForestNode superNode, ClassDecl value) {
			this.superNode = superNode;
			this.value = value;
		}
		
		void addChild(ForestNode child) {
			if(children == null) children = new ArrayList<>();
			this.children.add(child);
		}

	}
	
	/*helper Linked Lists for builder*/
	private class LLclasses{
		private class node{
			node next;
			ClassDecl value;
		}
		node head;
		
		public LLclasses() {head = null;}
		
		void add(ClassDecl cls) {
			node newHead = new node();
			newHead.value = cls;
			newHead.next = this.head;
			head = newHead;
		}
		
		void removeHead() {
			if(head == null) return;
			head = head.next;
		}
		
		void removeNext(node curr) {
			if(curr.next == null) return;
			curr.next = curr.next.next;
		}
		
	}
	
	private ArrayList<ForestNode> trees;
	private HashMap<String, ForestNode> nodeMap;
	private MainClass mainClass;
	private boolean isLegalForest;
	
	public InheritanceForest(Program prog) {
		isLegalForest = true;
		this.mainClass = prog.mainClass();
		trees = new ArrayList<>();
		nodeMap = new HashMap<>();
		
		ForestNode tmp;
		LLclasses classBag = new LLclasses();

		//for semantic checks
		Set<String> allClassDeclNames = new HashSet<>();
		
		/*add all roots to the ArrayList trees*/
		for(ClassDecl cls : prog.classDecls()) {
			allClassDeclNames.add(cls.name());
			if(cls.superName() == null) {
				// semantic check - no name repetition (req 3)
				if(nodeMap.containsKey(cls.name()) || cls.name().equals(mainClass.name())){
					isLegalForest = false;
					return;
				}

				tmp = new ForestNode(null, cls);
				trees.add(tmp);
				nodeMap.put(cls.name(), tmp);
			}
			else {
				classBag.add(cls);
			}
		}
		
		LLclasses.node curr;
		String superName;

		/*add all other classes to the forest*/
		while(classBag.head != null) {
			superName = classBag.head.value.superName();
			//semantic checks
			if(!legalClassDecl(classBag.head.value, superName, allClassDeclNames)) return;

			if(nodeMap.containsKey(superName)){
				addToForest(classBag.head.value);
				classBag.removeHead();
				continue;
			}
			curr = classBag.head;
			while(curr.next != null) {
				superName = curr.next.value.superName();
				//semantic checks
				if(!legalClassDecl(curr.next.value, superName, allClassDeclNames)) return;

				if(nodeMap.containsKey(superName)) {
					addToForest(curr.next.value);
					classBag.removeNext(curr);
				}
				else curr = curr.next;
			}
		}
	}
	
	private boolean legalClassDecl(ClassDecl cls, String superName, Set<String> allNames){
		// checks:
		//1. super is defined
		//2. super is not self
		//3. super is not main (req 2)
		//4. class name is not a repetition
		//5. super is defined before self (req 1)
		//
		if(!allNames.contains(superName) || superName.equals(cls.name()) || superName.equals(mainClass.name()) ||
				nodeMap.containsKey(cls.name()) || cls.name().equals(mainClass.name()) ||
				nodeMap.get(superName).value.lineNumber >= cls.lineNumber){
			isLegalForest = false;
		}
		return isLegalForest;
	}
	
	
	private void addToForest(ClassDecl cls) {
		if(!nodeMap.containsKey(cls.superName())) return;
		ForestNode tmp, parr;
		parr = nodeMap.get(cls.superName());
		tmp = new ForestNode(parr, cls);
		parr.addChild(tmp);
		nodeMap.put(cls.name(), tmp);
	}
	
	public MainClass mainClass() {return mainClass;}
	
	public List<ClassDecl> getChildren(String className) {
		if(nodeMap.get(className).children == null) return null;
		List<ClassDecl> res = new ArrayList<>();
		for(ForestNode child : nodeMap.get(className).children) {
			res.add(child.value);
		}
		return res;
	}
	
	public List<ClassDecl> getDescendants(String className){
		if(nodeMap.get(className).children == null) return null;
		return recGetDecendents(className);
	}
	
	private List<ClassDecl> recGetDecendents(String className){
		if(nodeMap.get(className).children == null) return new ArrayList<ClassDecl>();
		List<ClassDecl> res = getChildren(className);
		List<ClassDecl> tmp = getChildren(className);
		for(ClassDecl child : tmp) {
			res.addAll(recGetDecendents(child.name()));
		}
		return res;
	}
	
	public List<ClassDecl> getAncestors(String className){
		if(nodeMap.get(className).superNode == null) return null;
		ArrayList<ClassDecl> res = new ArrayList<>();
		ForestNode parr = nodeMap.get(className).superNode;
		while(parr != null) {
			res.add(parr.value);
			parr = parr.superNode;
		}
		return res;
	}

	public boolean isA(String descendant, String ancestor){
		if(descendant.equals(ancestor)) return true;
		//note that we could have just used getAncestors but this is more efficient
		boolean res = false;
		ClassDecl curr = nodeMap.get(descendant).value;
		while(curr.superName() != null){
			if(curr.superName().equals(ancestor)){
				res = true;
				break;
			}
			curr = nodeMap.get(curr.superName()).value;
		}
		return res;
	}
	
	public ClassDecl getSuper(String className) {
		ForestNode node = nodeMap.get(className).superNode;
		return node == null ? null : node.value;
	}
	
	public ClassDecl nameToClassDecl(String name) {
		if(name == null) return null;
		return nodeMap.get(name).value;
	}

	public List<ClassDecl> getRoots(){
		List<ClassDecl> res = new ArrayList<>();
		for(ForestNode node : trees){
			res.add(node.value);
		}
		return res;
	}
	
	/*
	 * Overloading with input type to be ClassDecl for conveniece
	 */
	public List<ClassDecl> getChildren(ClassDecl cls){
		return getChildren(cls.name());
	}
	
	public List<ClassDecl> getDescendants(ClassDecl cls){
		return getDescendants(cls.name());
	}
	public List<ClassDecl> getAncestors(ClassDecl cls){
		return getAncestors(cls.name());
	}
	public ClassDecl getSuper(ClassDecl cls) {
		return getSuper(cls.name());
	}

	public boolean isA(ClassDecl descendant, ClassDecl ancestor){
		return isA(descendant.name(), ancestor.name());
	}
	public boolean isA(ClassDecl descendant, String ancestor){
		return isA(descendant.name(), ancestor);
	}
	public boolean isA(String descendant, ClassDecl ancestor){
		return isA(descendant, ancestor.name());
	}

	/**
	 * Meant for semantic checks of class declerations
	 * @return boolean value representing the legality of the tree
	 */
	public boolean isLegalForest(){return this.isLegalForest;}
	
}

