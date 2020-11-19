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
	
	public InheritanceForest(Program prog) {
		this.mainClass = prog.mainClass();
		trees = new ArrayList<>();
		nodeMap = new HashMap<>();
		
		ForestNode tmp;
		LLclasses classBag = new LLclasses();
		
		/*add all roots to the ArrayList trees*/
		for(ClassDecl cls : prog.classDecls()) {
			if(cls.superName() == null) {
				tmp = new ForestNode(null, cls);
				trees.add(tmp);
				nodeMap.put(cls.name(), tmp);
			}
			else {
				classBag.add(cls);
			}
		}
		
		LLclasses.node curr;
		/*add all other classes to the forest*/
		while(classBag.head != null) {
			if(nodeMap.containsKey(classBag.head.value.superName())){
				addToForest(classBag.head.value);
				classBag.removeHead();
				continue;
			}
			curr = classBag.head;
			while(curr.next != null) {
				if(nodeMap.containsKey(curr.next.value.superName())) {
					addToForest(curr.next.value);
					classBag.removeNext(curr);
				}
				else curr = curr.next;
			}
		}
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
	
	public ClassDecl getSuper(String className) {
		return nodeMap.get(className).value;
	}
	
	public ClassDecl nameToClassDecl(String name) {
		if(name == null) return null;
		return nodeMap.get(name).value;
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
	
}

