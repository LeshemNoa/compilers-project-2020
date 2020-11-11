package ast;

import java.util.*;

public class InheritanceForest {

	private class ForestNode {
		ForestNode superNode;
		ArrayList<ForestNode> children;
		ClassDecl self;
		
		public ForestNode(ForestNode superNode, ClassDecl self) {
			this.superNode = superNode;
			this.self = self;
		}
		
		void addChild(ForestNode child) {
			if(children == null) children = new ArrayList<>();
			this.children.add(child);
		}
	}
	
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
			if(head != null) newHead.next = head.next;
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
	private MainClass main;
	
	public InheritanceForest(MainClass main, List<ClassDecl> classes) {
		this.main = main;
		trees = new ArrayList<>();
		nodeMap = new HashMap<>();
		
		ForestNode tmp;
		LLclasses classBag = new LLclasses();
		
		/*add all roots to the ArrayList trees*/
		for(ClassDecl cls : classes) {
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
	
	
}







