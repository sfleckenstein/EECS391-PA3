/**
 *  Strategy Engine for Programming Intelligent Agents (SEPIA)
    Copyright (C) 2012 Case Western Reserve University

    This file is part of SEPIA.

    SEPIA is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SEPIA is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with SEPIA.  If not, see <http://www.gnu.org/licenses/>.
 */
//package edu.cwru.sepia.agent;


import java.awt.Point;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionType;
import edu.cwru.sepia.action.LocatedAction;
import edu.cwru.sepia.action.TargetedAction;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.ResourceNode.ResourceView;
import edu.cwru.sepia.environment.model.state.ResourceNode.Type;
import edu.cwru.sepia.environment.model.state.ResourceType;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Template.TemplateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;

public class MultiPeasant extends Agent {
	private static final long serialVersionUID = -4047208702628325380L;
	private static final Logger logger = Logger.getLogger(MultiPeasant.class.getCanonicalName());
	public static final int GATHER_AMOUNT = 100;
	public static final int PEASANT_COST = 400;

	private int step;
	
	private PrintWriter outputPlan;
	
	private List<Integer> peasantIds = new ArrayList<Integer>();
	private List<Integer> townhallIds = new ArrayList<Integer>();
	
	private LookupPriorityQueue<Node> open = new LookupPriorityQueue<Node>();
	private List<Node> closed = new ArrayList<Node>();
	private LinkedList<Node> solution = new LinkedList<Node>();
	
	private ArrayList<Literal> initLits = new ArrayList<Literal>();
	private ArrayList<Literal> goalLits = new ArrayList<Literal>();
	
	private StateView currentState;
	
	private int nextGoalID = 0;
	private int targetGold;
	private int targetWood;
	private int goalPeasants;

	public MultiPeasant(int playernum, String[] arguments) {
		super(playernum);
		if(arguments.length > 0 && Integer.parseInt(arguments[0]) > 0) {
			targetGold = Integer.parseInt(arguments[0]);
		} else {
			targetGold = 200;
		}
		if(arguments.length > 1 && Integer.parseInt(arguments[1]) > 0) {
			targetWood = Integer.parseInt(arguments[1]);
		} else {
			targetWood = 200;
		}
		goalPeasants = 2;
	}

	@Override
	public Map<Integer, Action> initialStep(StateView newState, History.HistoryView stateHistory) {
		step = 0;
		
		currentState = newState;
		
		List<Integer> allUnitIds = currentState.getAllUnitIds();
		peasantIds = new ArrayList<Integer>();
		townhallIds = new ArrayList<Integer>();
		for(int i = 0; i < allUnitIds.size(); i++) {
			int id = allUnitIds.get(i);
			UnitView unit = currentState.getUnit(id);
			String unitTypeName = unit.getTemplateView().getName();
			
			if(unitTypeName.equals("TownHall")) {
				townhallIds.add(id);
			}
			if(unitTypeName.equals("Peasant")) {
				peasantIds.add(id);
			}
		}
		
		//initial state
		initLits.add(new ContainsPeasants(1));
		initLits.add(new Has(townhallIds.get(0), ResourceType.GOLD, 0)); //town has no gold
		initLits.add(new Has(townhallIds.get(0), ResourceType.WOOD, 0)); //town has no wood
		initLits.add(new AtTownHall(3)); //peasant starts at the townhall

		//goal state
		goalLits.add(new Has(townhallIds.get(0), ResourceType.GOLD, targetGold));
		goalLits.add(new Has(townhallIds.get(0), ResourceType.WOOD, targetWood));
		goalLits.add(new ContainsPeasants(goalPeasants));
		
		Node root = new Node(null, null, initLits, 0, 0);

		int estimatedCost = heuristic(root);
		root.setCostToGoal(estimatedCost);
		
		open.add(root);
		
		while(true) {
			Node node = open.poll();
			
			if(node == null) {
				terminalStep(newState, stateHistory);
				break;
			}
			
			//Goal found
			boolean goalPassed = true;
			for(Literal goalLiteral : goalLits) { 				
				if(!node.containsLit(goalLiteral)) {
					goalPassed = false;
					break;
				}
			}
			if(goalPassed) {				
				while(node.getParentNode() != null) {
					solution.addFirst(node);
					node = node.getParentNode();
				}
				printPlan();
				break;
			}
			
			closed.add(node);
			
			//determine resources still needed
			boolean needGold = false;
			boolean needWood = false;
			boolean needPeasant = false;
			int neededGold = 0; //according to the townhall
			int neededWood = 0;
			for(Literal literal : node.getStateLits()) {
				if(literal.getClass().toString().equals("class ContainsPeasants") 
						&& ((ContainsPeasants)literal).getNumPeasants() < goalPeasants) {
					needPeasant = true;
				}
				if(literal.getClass().toString().equals("class Has")
						&& ((Has)literal).getObjectID() == townhallIds.get(0)) {
					if(((Has)literal).getResource().equals(ResourceType.GOLD)) {
						neededGold = ((Has)literal).getAmount();
						if(neededGold < targetGold) {
							needGold = true;
						}
					} else if(((Has)literal).getResource().equals(ResourceType.WOOD)) {
						neededWood = ((Has)literal).getAmount();
						if(neededWood < targetWood) {
							needWood = true;
						}
					}
				}
			}
			
			if(node.containsLit(new ContainsPeasants(1))) { //only one peasant		
				//GotoResource
				if(node.containsLit(new AtTownHall(3))
						&& !node.containsLit(new Has(3, ResourceType.GOLD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(3, ResourceType.WOOD, GATHER_AMOUNT))) { //preconditions
					if(needGold || needPeasant) {
						ArrayList<Literal> literalsGold = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							if(!lit.equals(new AtTownHall(3))) { //remove list
								literalsGold.add(lit);
							}
						}
						literalsGold.add(new AtResource(3, ResourceType.GOLD)); //add list
						
						
						Node n = new Node(node, new GotoResource(3, ResourceType.GOLD),
								literalsGold, node.getCostToNode() + 1, 0);
						
						estimatedCost = heuristic(n);
						
						n.setCostToGoal(estimatedCost);

						if(!closed.contains(n)) {
							open.add(n);
						} else if (open.contains(n)){
							Node toCompare = open.get(n);
							if(toCompare.getCostToNode() > n.getCostToNode()) {
								toCompare.setCostToNode(n.getCostToNode());
								toCompare.setParentNode(n.getParentNode());
							}
						}
					}
					if(needWood) {
						ArrayList<Literal> literalsWood = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							if(!lit.equals(new AtTownHall(3))) { //remove list
								literalsWood.add(lit);
							}
						}
						literalsWood.add(new AtResource(3, ResourceType.WOOD)); //add list
						
						Node n = new Node(node, new GotoResource(3, ResourceType.WOOD),
								literalsWood, node.getCostToNode() + 1, 0);
						
						estimatedCost = heuristic(n);
						
						n.setCostToGoal(estimatedCost);
						
						if(!closed.contains(n)) {
							open.add(n);
						} else if (open.contains(n)){
							Node toCompare = open.get(n);
							if(toCompare.getCostToNode() > n.getCostToNode()) {
								toCompare.setCostToNode(n.getCostToNode());
								toCompare.setParentNode(n.getParentNode());
							}
						}
					}
				}
				
				//GotoTownHall
				if((node.containsLit(new AtResource(3, ResourceType.GOLD))
						|| node.containsLit(new AtResource(3, ResourceType.WOOD)))
						&& (node.containsLit(new Has(3, ResourceType.GOLD, GATHER_AMOUNT))
								|| node.containsLit(new Has(3, ResourceType.WOOD, GATHER_AMOUNT)))) { //preconditions
					ArrayList<Literal> literals = new ArrayList<Literal>();
					for(Literal lit : node.getStateLits()) {
						if(!lit.equals(new AtResource(3, ResourceType.GOLD))
								&& !lit.equals(new AtResource(3, ResourceType.WOOD))) { //remove list
							literals.add(lit);
						}
					}
					
					literals.add(new AtTownHall(3));
					
					
					Node n = new Node(node, new GotoTownHall(3),
							literals, node.getCostToNode() + 1, 0);
					
					estimatedCost = heuristic(n);
					
					n.setCostToGoal(estimatedCost);
					
					if(!closed.contains(n)) {
						open.add(n);
					} else if (open.contains(n)){
						Node toCompare = open.get(n);
						if(toCompare.getCostToNode() > n.getCostToNode()) {
							toCompare.setCostToNode(n.getCostToNode());
							toCompare.setParentNode(n.getParentNode());
						}
					}
				}
				
				//Deposit
				if(node.containsLit(new AtTownHall(3))) {
					if(node.containsLit(new Has(3, ResourceType.GOLD, GATHER_AMOUNT))) { //preconditions
						ArrayList<Literal> literalsGold = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							if(!lit.equals(new Has(3, ResourceType.GOLD, GATHER_AMOUNT))) { //remove list
								if(lit.getClass().toString().equals("class Has")
										&& ((Has)lit).getObjectID() == townhallIds.get(0)
										&& ((Has)lit).getResource().equals(ResourceType.GOLD)) {
									literalsGold.add(new Has(townhallIds.get(0), ResourceType.GOLD, ((Has)lit).getAmount() + GATHER_AMOUNT));
								} else {
									literalsGold.add(lit);
								}
							}
						}
						
						Node n = new Node(node, new Deposit(3, ResourceType.GOLD, GATHER_AMOUNT),
								literalsGold, node.getCostToNode() + 1, 0);
						
						estimatedCost = heuristic(n);
						
						n.setCostToGoal(estimatedCost);
						
						if(!closed.contains(n)) {
							open.add(n);
						} else if (open.contains(n)){
							Node toCompare = open.get(n);
							if(toCompare.getCostToNode() > n.getCostToNode()) {
								toCompare.setCostToNode(n.getCostToNode());
								toCompare.setParentNode(n.getParentNode());
							}
						}
					}
					if(node.containsLit(new Has(3, ResourceType.WOOD, GATHER_AMOUNT))) { //preconditions
						ArrayList<Literal> literalsWood = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							if(!lit.equals(new Has(3, ResourceType.WOOD, GATHER_AMOUNT))) { //remove list
								if(lit.getClass().toString().equals("class Has")
										&& ((Has)lit).getObjectID() == townhallIds.get(0)
										&& ((Has)lit).getResource().equals(ResourceType.WOOD)) {
									literalsWood.add(new Has(townhallIds.get(0), ResourceType.WOOD, ((Has)lit).getAmount() + GATHER_AMOUNT));
								} else {
									literalsWood.add(lit);
								}
							}
						}
						
						Node n = new Node(node, new Deposit(3, ResourceType.WOOD, GATHER_AMOUNT),
								literalsWood, node.getCostToNode() + 1, 0);
						
						estimatedCost = heuristic(n);
						
						n.setCostToGoal(estimatedCost);
						
						if(!closed.contains(n)) {
							open.add(n);
						} else if (open.contains(n)){
							Node toCompare = open.get(n);
							if(toCompare.getCostToNode() > n.getCostToNode()) {
								toCompare.setCostToNode(n.getCostToNode());
								toCompare.setParentNode(n.getParentNode());
							}
						}
					}
				}
				
				//Gather
				if(!node.containsLit(new Has(3, ResourceType.GOLD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(3, ResourceType.WOOD, GATHER_AMOUNT))) {
					if(node.containsLit(new AtResource(3, ResourceType.GOLD))) { //preconditions
						ArrayList<Literal> literalsGold = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							literalsGold.add(lit);
						}
						
						literalsGold.add(new Has(3, ResourceType.GOLD, GATHER_AMOUNT)); //add list
						
						Node n = new Node(node, new Gather(3, ResourceType.GOLD, GATHER_AMOUNT),
								literalsGold, node.getCostToNode() + 1, 0);
						
						estimatedCost = heuristic(n);
						
						n.setCostToGoal(estimatedCost);
						
						if(!closed.contains(n)) {
							open.add(n);
						} else if (open.contains(n)){
							Node toCompare = open.get(n);
							if(toCompare.getCostToNode() > n.getCostToNode()) {
								toCompare.setCostToNode(n.getCostToNode());
								toCompare.setParentNode(n.getParentNode());
							}
						}
					}
					if(node.containsLit(new AtResource(3, ResourceType.WOOD))) { //preconditions
						ArrayList<Literal> literalsWood = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							literalsWood.add(lit);
						}
						
						literalsWood.add(new Has(3, ResourceType.WOOD, GATHER_AMOUNT)); //add list
						
						Node n = new Node(node, new Gather(3, ResourceType.WOOD, GATHER_AMOUNT),
								literalsWood, node.getCostToNode() + 1, 0);
						
						estimatedCost = heuristic(n);
						
						n.setCostToGoal(estimatedCost);
						
						if(!closed.contains(n)) {
							open.add(n);
						} else if (open.contains(n)){
							Node toCompare = open.get(n);
							if(toCompare.getCostToNode() > n.getCostToNode()) {
								toCompare.setCostToNode(n.getCostToNode());
								toCompare.setParentNode(n.getParentNode());
							}
						}
					}
				}
				
				//BuildPeasant
				boolean enoughGold = false;
				for(Literal lit : node.getStateLits()) {
					if(lit.getClass().toString().equals("class Has")
							&& ((Has)lit).getObjectID() == townhallIds.get(0)
							&& ((Has)lit).getResource().equals(ResourceType.GOLD)) {
						if(((Has)lit).getAmount() >= PEASANT_COST) {
							enoughGold = true;
							break;
						}
					}
				}
				
				if(enoughGold) { //preconditions
					ArrayList<Literal> literalsBuild = new ArrayList<Literal>();
					for(Literal lit : node.getStateLits()) {
						if(!lit.equals(new ContainsPeasants(1))) { //remove list
							if(lit.getClass().toString().equals("class Has")
									&& ((Has)lit).getObjectID() == townhallIds.get(0)
									&& ((Has)lit).getResource().equals(ResourceType.GOLD)) {
								literalsBuild.add(new Has(townhallIds.get(0), ResourceType.GOLD, ((Has)lit).getAmount() - PEASANT_COST));
							} else {
								literalsBuild.add(lit);
							}
						} else {
							literalsBuild.add(0, new ContainsPeasants(2)); //add list
						}
					}
					
					literalsBuild.add(new AtTownHall(1));
					
					Node n = new Node(node, new BuildPeasant(), literalsBuild, node.getCostToNode() + 1, 0);
					
					estimatedCost = heuristic(n);
					
					n.setCostToGoal(estimatedCost);
					
					if(!closed.contains(n)) {
						open.add(n);
					} else if (open.contains(n)){
						Node toCompare = open.get(n);
						if(toCompare.getCostToNode() > n.getCostToNode()) {
							toCompare.setCostToNode(n.getCostToNode());
							toCompare.setParentNode(n.getParentNode());
						}
					}
				}
			} else if(node.containsLit(new ContainsPeasants(2))) { //two peasants
				//GotoResource2
				if(node.containsLit(new AtTownHall(3))
						&& node.containsLit(new AtTownHall(1))
						&& !node.containsLit(new Has(3, ResourceType.GOLD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(1, ResourceType.GOLD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(3, ResourceType.WOOD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(1, ResourceType.WOOD, GATHER_AMOUNT))) { //preconditions
					if(needGold || needPeasant) {
						ArrayList<Literal> literalsGold = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							if(!lit.equals(new AtTownHall(3))
									&& !lit.equals(new AtTownHall(1))) { //remove list
								literalsGold.add(lit);
							}
						}
						literalsGold.add(new AtResource(3, ResourceType.GOLD)); //add list
						literalsGold.add(new AtResource(1, ResourceType.GOLD));
						
						Node n = new Node(node, new GotoResource2(3, 1, ResourceType.GOLD),
								literalsGold, node.getCostToNode() + 1, 0);
						
						estimatedCost = heuristic(n);
						
						n.setCostToGoal(estimatedCost);
						
						if(!closed.contains(n)) {
							open.add(n);
						} else if (open.contains(n)){
							Node toCompare = open.get(n);
							if(toCompare.getCostToNode() > n.getCostToNode()) {
								toCompare.setCostToNode(n.getCostToNode());
								toCompare.setParentNode(n.getParentNode());
							}
						}
					}
					if(needWood) {
						ArrayList<Literal> literalsWood = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							if(!lit.equals(new AtTownHall(3))
									&& !lit.equals(new AtTownHall(1))) { //remove list
								literalsWood.add(lit);
							}
						}
						literalsWood.add(new AtResource(3, ResourceType.WOOD)); //add list
						literalsWood.add(new AtResource(1, ResourceType.WOOD));
						
						Node n = new Node(node, new GotoResource2(3, 1, ResourceType.WOOD),
								literalsWood, node.getCostToNode() + 1, 0);
						
						estimatedCost = heuristic(n);
						
						n.setCostToGoal(estimatedCost);
						
						if(!closed.contains(n)) {
							open.add(n);
						} else if (open.contains(n)){
							Node toCompare = open.get(n);
							if(toCompare.getCostToNode() > n.getCostToNode()) {
								toCompare.setCostToNode(n.getCostToNode());
								toCompare.setParentNode(n.getParentNode());
							}
						}
					}
				}
				
				//GotoTownHall2
				if((node.containsLit(new AtResource(3, ResourceType.GOLD))
							|| node.containsLit(new AtResource(3, ResourceType.WOOD)))
						&& (node.containsLit(new Has(3, ResourceType.GOLD, GATHER_AMOUNT))
							|| node.containsLit(new Has(3, ResourceType.WOOD, GATHER_AMOUNT)))
						&& (node.containsLit(new AtResource(1, ResourceType.GOLD))
							|| node.containsLit(new AtResource(1, ResourceType.WOOD)))
						&& (node.containsLit(new Has(1, ResourceType.GOLD, GATHER_AMOUNT))
							|| node.containsLit(new Has(1, ResourceType.WOOD, GATHER_AMOUNT)))) { //preconditions
					ArrayList<Literal> literals = new ArrayList<Literal>();
					for(Literal lit : node.getStateLits()) {
						if(!lit.equals(new AtResource(3, ResourceType.GOLD))
								&& !lit.equals(new AtResource(3, ResourceType.WOOD))
								&& !lit.equals(new AtResource(1, ResourceType.GOLD))
								&& !lit.equals(new AtResource(1, ResourceType.WOOD))) { //remove list
							literals.add(lit);
						}
					}
					
					literals.add(new AtTownHall(3)); //add list
					literals.add(new AtTownHall(1));
					
					Node n = new Node(node, new GotoTownHall2(3, 1),
							literals, node.getCostToNode() + 1, 0);
					
					estimatedCost = heuristic(n);
					
					n.setCostToGoal(estimatedCost);
					
					if(!closed.contains(n)) {
						open.add(n);
					} else if (open.contains(n)){
						Node toCompare = open.get(n);
						if(toCompare.getCostToNode() > n.getCostToNode()) {
							toCompare.setCostToNode(n.getCostToNode());
							toCompare.setParentNode(n.getParentNode());
						}
					}
				}
				
				//Deposit2
				if(node.containsLit(new AtTownHall(3))
						&& node.containsLit(new AtTownHall(1))) {
					if(node.containsLit(new Has(3, ResourceType.GOLD, GATHER_AMOUNT))
							&& node.containsLit(new Has(1, ResourceType.GOLD, GATHER_AMOUNT))) { //preconditions
						ArrayList<Literal> literalsGold = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							if(!lit.equals(new Has(3, ResourceType.GOLD, GATHER_AMOUNT))
									&& !lit.equals(new Has(1, ResourceType.GOLD, GATHER_AMOUNT))) { //remove list
								if(lit.getClass().toString().equals("class Has")
										&& ((Has)lit).getObjectID() == townhallIds.get(0)
										&& ((Has)lit).getResource().equals(ResourceType.GOLD)) {
									literalsGold.add(new Has(townhallIds.get(0), ResourceType.GOLD, ((Has)lit).getAmount() + 2 * GATHER_AMOUNT));
								} else {
									literalsGold.add(lit);
								}
							}
						}
						
						Node n = new Node(node, new Deposit2(3, 1, ResourceType.GOLD, 2 * GATHER_AMOUNT),
								literalsGold, node.getCostToNode() + 1, 0);
						
						estimatedCost = heuristic(n);
						
						n.setCostToGoal(estimatedCost);
						
						if(!closed.contains(n)) {
							open.add(n);
						} else if (open.contains(n)){
							Node toCompare = open.get(n);
							if(toCompare.getCostToNode() > n.getCostToNode()) {
								toCompare.setCostToNode(n.getCostToNode());
								toCompare.setParentNode(n.getParentNode());
							}
						}
					}
					if(node.containsLit(new Has(3, ResourceType.WOOD, GATHER_AMOUNT))
							&& node.containsLit(new Has(1, ResourceType.WOOD, GATHER_AMOUNT))) { //preconditions
						ArrayList<Literal> literalsWood = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							if(!lit.equals(new Has(3, ResourceType.WOOD, GATHER_AMOUNT))
									&& !lit.equals(new Has(1, ResourceType.WOOD, GATHER_AMOUNT))) { //remove list
								if(lit.getClass().toString().equals("class Has")
										&& ((Has)lit).getObjectID() == townhallIds.get(0)
										&& ((Has)lit).getResource().equals(ResourceType.WOOD)) {
									literalsWood.add(new Has(townhallIds.get(0), ResourceType.WOOD, ((Has)lit).getAmount() + 2 * GATHER_AMOUNT));
								} else {
									literalsWood.add(lit);
								}
							}
						}
						
						Node n = new Node(node, new Deposit2(3, 1, ResourceType.WOOD, 2 * GATHER_AMOUNT),
								literalsWood, node.getCostToNode() + 1, 0);
						
						estimatedCost = heuristic(n);
						
						n.setCostToGoal(estimatedCost);
						
						if(!closed.contains(n)) {
							open.add(n);
						} else if (open.contains(n)){
							Node toCompare = open.get(n);
							if(toCompare.getCostToNode() > n.getCostToNode()) {
								toCompare.setCostToNode(n.getCostToNode());
								toCompare.setParentNode(n.getParentNode());
							}
						}
					}
				}
				
				//Gather2
				if(!node.containsLit(new Has(3, ResourceType.GOLD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(1, ResourceType.GOLD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(3, ResourceType.WOOD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(1, ResourceType.WOOD, GATHER_AMOUNT))) {
					if(node.containsLit(new AtResource(3, ResourceType.GOLD))
							&& node.containsLit(new AtResource(1, ResourceType.GOLD))) { //preconditions
						ArrayList<Literal> literalsGold = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							literalsGold.add(lit);
						}
						
						literalsGold.add(new Has(3, ResourceType.GOLD, GATHER_AMOUNT)); //add list
						literalsGold.add(new Has(1, ResourceType.GOLD, GATHER_AMOUNT));
						
						Node n = new Node(node, new Gather2(3, 1, ResourceType.GOLD, 2 * GATHER_AMOUNT),
								literalsGold, node.getCostToNode() + 1, 0);
						
						estimatedCost = heuristic(n);
						
						n.setCostToGoal(estimatedCost);
						
						if(!closed.contains(n)) {
							open.add(n);
						} else if (open.contains(n)){
							Node toCompare = open.get(n);
							if(toCompare.getCostToNode() > n.getCostToNode()) {
								toCompare.setCostToNode(n.getCostToNode());
								toCompare.setParentNode(n.getParentNode());
							}
						}
					}
					if(node.containsLit(new AtResource(3, ResourceType.WOOD))
							&& node.containsLit(new AtResource(1, ResourceType.WOOD))) { //preconditions
						ArrayList<Literal> literalsWood = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							literalsWood.add(lit);
						}
						
						literalsWood.add(new Has(3, ResourceType.WOOD, GATHER_AMOUNT)); //add list
						literalsWood.add(new Has(1, ResourceType.WOOD, GATHER_AMOUNT));
						
						Node n = new Node(node, new Gather2(3, 1, ResourceType.WOOD, 2 * GATHER_AMOUNT),
								literalsWood, node.getCostToNode() + 1, 0);
						
						estimatedCost = heuristic(n);
						
						n.setCostToGoal(estimatedCost);
						
						if(!closed.contains(n)) {
							open.add(n);
						} else if (open.contains(n)){
							Node toCompare = open.get(n);
							if(toCompare.getCostToNode() > n.getCostToNode()) {
								toCompare.setCostToNode(n.getCostToNode());
								toCompare.setParentNode(n.getParentNode());
							}
						}
					}
				}
				
				//BuildPeasant2
				boolean enoughGold = false;
				for(Literal lit : node.getStateLits()) {
					if(lit.getClass().toString().equals("class Has")
							&& ((Has)lit).getObjectID() == townhallIds.get(0)
							&& ((Has)lit).getResource().equals(ResourceType.GOLD)) {
						if(((Has)lit).getAmount() >= PEASANT_COST) {
							enoughGold = true;
							break;
						}
					}
				}
				
				if(enoughGold) { //preconditions
					ArrayList<Literal> literalsBuild = new ArrayList<Literal>();
					for(Literal lit : node.getStateLits()) {
						if(!lit.equals(new ContainsPeasants(2))) { //remove list
							if(lit.getClass().toString().equals("class Has")
									&& ((Has)lit).getObjectID() == townhallIds.get(0)
									&& ((Has)lit).getResource().equals(ResourceType.GOLD)) {
								literalsBuild.add(new Has(townhallIds.get(0), ResourceType.GOLD, ((Has)lit).getAmount() - PEASANT_COST));
							} else {
								literalsBuild.add(lit);
							}
						} else {
							literalsBuild.add(0, new ContainsPeasants(3)); //add list
						}
					}
					
					literalsBuild.add(new AtTownHall(2));
					
					Node n = new Node(node, new BuildPeasant(), literalsBuild, node.getCostToNode() + 1, 0);
					
					estimatedCost = heuristic(n);
					
					n.setCostToGoal(estimatedCost);
					
					if(!closed.contains(n)) {
						open.add(n);
					} else if (open.contains(n)){
						Node toCompare = open.get(n);
						if(toCompare.getCostToNode() > n.getCostToNode()) {
							toCompare.setCostToNode(n.getCostToNode());
							toCompare.setParentNode(n.getParentNode());
						}
					}
				}
			} else if(node.containsLit(new ContainsPeasants(3))){ //three peasants
				//GotoResource3
				if(node.containsLit(new AtTownHall(3))
						&& node.containsLit(new AtTownHall(1))
						&& node.containsLit(new AtTownHall(2))
						&& !node.containsLit(new Has(3, ResourceType.GOLD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(1, ResourceType.GOLD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(2, ResourceType.GOLD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(3, ResourceType.WOOD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(1, ResourceType.WOOD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(2, ResourceType.WOOD, GATHER_AMOUNT))) { //preconditions
					if(needGold) {
						ArrayList<Literal> literalsGold = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							if(!lit.equals(new AtTownHall(3))
									&& !lit.equals(new AtTownHall(1))
									&& !lit.equals(new AtTownHall(2))) { //remove list
								literalsGold.add(lit);
							}
						}
						literalsGold.add(new AtResource(3, ResourceType.GOLD)); //add list
						literalsGold.add(new AtResource(1, ResourceType.GOLD));
						literalsGold.add(new AtResource(2, ResourceType.GOLD));
						
						Node n = new Node(node, new GotoResource3(3, 1, 2, ResourceType.GOLD),
								literalsGold, node.getCostToNode() + 1, 0);
						
						estimatedCost = heuristic(n);
						
						n.setCostToGoal(estimatedCost);
						
						if(!closed.contains(n)) {
							open.add(n);
						} else if (open.contains(n)){
							Node toCompare = open.get(n);
							if(toCompare.getCostToNode() > n.getCostToNode()) {
								toCompare.setCostToNode(n.getCostToNode());
								toCompare.setParentNode(n.getParentNode());
							}
						}
					}
					if(needWood) {
						ArrayList<Literal> literalsWood = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							if(!lit.equals(new AtTownHall(3))
									&& !lit.equals(new AtTownHall(1))
									&& !lit.equals(new AtTownHall(2))) { //remove list
								literalsWood.add(lit);
							}
						}
						literalsWood.add(new AtResource(3, ResourceType.WOOD)); //add list
						literalsWood.add(new AtResource(1, ResourceType.WOOD));
						literalsWood.add(new AtResource(2, ResourceType.WOOD));
						
						Node n = new Node(node, new GotoResource3(3, 1, 2, ResourceType.WOOD),
								literalsWood, node.getCostToNode() + 1, 0);
						
						estimatedCost = heuristic(n);
						
						n.setCostToGoal(estimatedCost);
						
						if(!closed.contains(n)) {
							open.add(n);
						} else if (open.contains(n)){
							Node toCompare = open.get(n);
							if(toCompare.getCostToNode() > n.getCostToNode()) {
								toCompare.setCostToNode(n.getCostToNode());
								toCompare.setParentNode(n.getParentNode());
							}
						}
					}
				}
				
				//GotoTownHall3
				if((node.containsLit(new AtResource(3, ResourceType.GOLD))
							|| node.containsLit(new AtResource(3, ResourceType.WOOD)))
						&& (node.containsLit(new Has(3, ResourceType.GOLD, GATHER_AMOUNT))
							|| node.containsLit(new Has(3, ResourceType.WOOD, GATHER_AMOUNT)))
						&& (node.containsLit(new AtResource(1, ResourceType.GOLD))
							|| node.containsLit(new AtResource(1, ResourceType.WOOD)))
						&& (node.containsLit(new Has(1, ResourceType.GOLD, GATHER_AMOUNT))
							|| node.containsLit(new Has(1, ResourceType.WOOD, GATHER_AMOUNT)))
						&& (node.containsLit(new AtResource(2, ResourceType.GOLD))
							|| node.containsLit(new AtResource(2, ResourceType.WOOD)))
						&& (node.containsLit(new Has(2, ResourceType.GOLD, GATHER_AMOUNT))
							|| node.containsLit(new Has(2, ResourceType.WOOD, GATHER_AMOUNT)))) { //preconditions
					ArrayList<Literal> literals = new ArrayList<Literal>();
					for(Literal lit : node.getStateLits()) {
						if(!lit.equals(new AtResource(3, ResourceType.GOLD))
								&& !lit.equals(new AtResource(3, ResourceType.WOOD))
								&& !lit.equals(new AtResource(1, ResourceType.GOLD))
								&& !lit.equals(new AtResource(1, ResourceType.WOOD))
								&& !lit.equals(new AtResource(2, ResourceType.GOLD))
								&& !lit.equals(new AtResource(2, ResourceType.WOOD))) { //remove list
							literals.add(lit);
						}
					}
					
					literals.add(new AtTownHall(3)); //add list
					literals.add(new AtTownHall(1));
					literals.add(new AtTownHall(2));

					Node n = new Node(node, new GotoTownHall3(3, 1, 2),
							literals, node.getCostToNode() + 1, 0);
					
					estimatedCost = heuristic(n);
					
					n.setCostToGoal(estimatedCost);
					
					if(!closed.contains(n)) {
						open.add(n);
					} else if (open.contains(n)){
						Node toCompare = open.get(n);
						if(toCompare.getCostToNode() > n.getCostToNode()) {
							toCompare.setCostToNode(n.getCostToNode());
							toCompare.setParentNode(n.getParentNode());
						}
					}
				}
				
				//Deposit3
				if(node.containsLit(new AtTownHall(3))
						&& node.containsLit(new AtTownHall(1))
						&& node.containsLit(new AtTownHall(2))) {
					if(node.containsLit(new Has(3, ResourceType.GOLD, GATHER_AMOUNT))
							&& node.containsLit(new Has(1, ResourceType.GOLD, GATHER_AMOUNT))
							&& node.containsLit(new Has(2, ResourceType.GOLD, GATHER_AMOUNT))) { //preconditions
						ArrayList<Literal> literalsGold = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							if(!lit.equals(new Has(3, ResourceType.GOLD, GATHER_AMOUNT))
									&& !lit.equals(new Has(1, ResourceType.GOLD, GATHER_AMOUNT))
									&& !lit.equals(new Has(2, ResourceType.GOLD, GATHER_AMOUNT))) { //remove list
								if(lit.getClass().toString().equals("class Has")
										&& ((Has)lit).getObjectID() == townhallIds.get(0)
										&& ((Has)lit).getResource().equals(ResourceType.GOLD)) {
									literalsGold.add(new Has(townhallIds.get(0), ResourceType.GOLD, ((Has)lit).getAmount() + 3 * GATHER_AMOUNT));
								} else {
									literalsGold.add(lit);
								}
							}
						}
						
						Node n = new Node(node, new Deposit3(3, 1, 2, ResourceType.GOLD, 3 * GATHER_AMOUNT),
								literalsGold, node.getCostToNode() + 1, 0);
						
						estimatedCost = heuristic(n);
						
						n.setCostToGoal(estimatedCost);
						
						if(!closed.contains(n)) {
							open.add(n);
						} else if (open.contains(n)){
							Node toCompare = open.get(n);
							if(toCompare.getCostToNode() > n.getCostToNode()) {
								toCompare.setCostToNode(n.getCostToNode());
								toCompare.setParentNode(n.getParentNode());
							}
						}
					}
					if(node.containsLit(new Has(3, ResourceType.WOOD, GATHER_AMOUNT))
							&& node.containsLit(new Has(1, ResourceType.WOOD, GATHER_AMOUNT))
							&& node.containsLit(new Has(2, ResourceType.WOOD, GATHER_AMOUNT))) { //preconditions
						ArrayList<Literal> literalsWood = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							if(!lit.equals(new Has(3, ResourceType.WOOD, GATHER_AMOUNT))
									&& !lit.equals(new Has(1, ResourceType.WOOD, GATHER_AMOUNT))
									&& !lit.equals(new Has(2, ResourceType.WOOD, GATHER_AMOUNT))) { //remove list
								if(lit.getClass().toString().equals("class Has")
										&& ((Has)lit).getObjectID() == townhallIds.get(0)
										&& ((Has)lit).getResource().equals(ResourceType.WOOD)) {
									literalsWood.add(new Has(townhallIds.get(0), ResourceType.WOOD, ((Has)lit).getAmount() + 3 * GATHER_AMOUNT));
								} else {
									literalsWood.add(lit);
								}
							}
						}
						
						Node n = new Node(node, new Deposit3(3, 1, 2, ResourceType.WOOD, 3 * GATHER_AMOUNT),
								literalsWood, node.getCostToNode() + 1, 0);
						
						estimatedCost = heuristic(n);
						
						n.setCostToGoal(estimatedCost);
						
						if(!closed.contains(n)) {
							open.add(n);
						} else if (open.contains(n)){
							Node toCompare = open.get(n);
							if(toCompare.getCostToNode() > n.getCostToNode()) {
								toCompare.setCostToNode(n.getCostToNode());
								toCompare.setParentNode(n.getParentNode());
							}
						}
					}
				}
				
				//Gather3
				if(!node.containsLit(new Has(3, ResourceType.GOLD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(1, ResourceType.GOLD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(2, ResourceType.GOLD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(3, ResourceType.WOOD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(1, ResourceType.WOOD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(2, ResourceType.WOOD, GATHER_AMOUNT))) {
					if(node.containsLit(new AtResource(3, ResourceType.GOLD))
							&& node.containsLit(new AtResource(1, ResourceType.GOLD))
							&& node.containsLit(new AtResource(2, ResourceType.GOLD))) { //preconditions
						ArrayList<Literal> literalsGold = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							literalsGold.add(lit);
						}
						
						literalsGold.add(new Has(3, ResourceType.GOLD, GATHER_AMOUNT)); //add list
						literalsGold.add(new Has(1, ResourceType.GOLD, GATHER_AMOUNT));
						literalsGold.add(new Has(2, ResourceType.GOLD, GATHER_AMOUNT));
						
						Node n = new Node(node, new Gather3(3, 1, 2, ResourceType.GOLD, 3 * GATHER_AMOUNT),
								literalsGold, node.getCostToNode() + 1, 0);
						
						estimatedCost = heuristic(n);
						
						n.setCostToGoal(estimatedCost);
						
						if(!closed.contains(n)) {
							open.add(n);
						} else if (open.contains(n)){
							Node toCompare = open.get(n);
							if(toCompare.getCostToNode() > n.getCostToNode()) {
								toCompare.setCostToNode(n.getCostToNode());
								toCompare.setParentNode(n.getParentNode());
							}
						}
					}
					if(node.containsLit(new AtResource(3, ResourceType.WOOD))
							&& node.containsLit(new AtResource(1, ResourceType.WOOD))
							&& node.containsLit(new AtResource(2, ResourceType.WOOD))) { //preconditions
						ArrayList<Literal> literalsWood = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							literalsWood.add(lit);
						}
						
						literalsWood.add(new Has(3, ResourceType.WOOD, GATHER_AMOUNT)); //add list
						literalsWood.add(new Has(1, ResourceType.WOOD, GATHER_AMOUNT));
						literalsWood.add(new Has(2, ResourceType.WOOD, GATHER_AMOUNT));
						
						Node n = new Node(node, new Gather3(3, 1, 2, ResourceType.WOOD, 3 * GATHER_AMOUNT),
								literalsWood, node.getCostToNode() + 1, 0);
						
						estimatedCost = heuristic(n);
						
						n.setCostToGoal(estimatedCost);
						
						if(!closed.contains(n)) {
							open.add(n);
						} else if (open.contains(n)){
							Node toCompare = open.get(n);
							if(toCompare.getCostToNode() > n.getCostToNode()) {
								toCompare.setCostToNode(n.getCostToNode());
								toCompare.setParentNode(n.getParentNode());
							}
						}
					}
				}
			}
		}
		return middleStep(newState, stateHistory);
	}

	@Override
	public Map<Integer, Action> middleStep(StateView newState, History.HistoryView statehistory) {
		step++;
		if(logger.isLoggable(Level.FINE)) {
			logger.fine("=> Step: " + step);
		}
		
		currentState = newState;
		
		List<Integer> allUnitIds = currentState.getAllUnitIds();
		for(int i = 0; i < allUnitIds.size(); i++) {
			int id = allUnitIds.get(i);
			UnitView unit = currentState.getUnit(id);
			String unitTypeName = unit.getTemplateView().getName();
			
			if(unitTypeName.equals("Peasant")) {
				peasantIds.add(id);
			}
		}
		
		currentState = newState;
		
 		if(!areAdjacent(peasantIds.get(0), nextGoalID)) {
			return null;
		}
		Map<Integer, Action> builder = new HashMap<Integer, Action>();
		if(solution.peek() != null) {
			Action b = null;
			Node poll = solution.poll();
			String actString = poll.getToState().getClass().toString();
		
			if(actString.equals("class GotoTownHall")){
				UnitView townhall = currentState.getUnit(townhallIds.get(0));
				int townX = townhall.getXPosition();
				int townY = townhall.getYPosition();
				b = new LocatedAction(peasantIds.get(0), ActionType.COMPOUNDMOVE, townX, townY);
				nextGoalID = townhallIds.get(0);
				builder.put(peasantIds.get(0), b);
			} else if(actString.equals("class GotoTownHall2")){
				UnitView townhall = currentState.getUnit(townhallIds.get(0));
				int townX = townhall.getXPosition();
				int townY = townhall.getYPosition();
				b = new LocatedAction(peasantIds.get(0), ActionType.COMPOUNDMOVE, townX, townY);
				nextGoalID = townhallIds.get(0);

				builder.put(peasantIds.get(0), b);
				builder.put(peasantIds.get(1), b);
			} else if(actString.equals("class GotoTownHall3")){
				UnitView townhall = currentState.getUnit(townhallIds.get(0));
				int townX = townhall.getXPosition();
				int townY = townhall.getYPosition();
				b = new LocatedAction(peasantIds.get(0), ActionType.COMPOUNDMOVE, townX, townY);
				nextGoalID = townhallIds.get(0);
				
				builder.put(peasantIds.get(0), b);
				builder.put(peasantIds.get(1), b);
				builder.put(peasantIds.get(2), b);
			} else if(actString.equals("class GotoResource")) {
				ResourceType resource = ((GotoResource)poll.getToState()).getResource();
				if(resource.equals(ResourceType.GOLD)) {
					//goto nearest gold
					UnitView peasant = currentState.getUnit(peasantIds.get(0));
					nextGoalID = getClosestGoldID(new Point(peasant.getXPosition(), peasant.getYPosition()), currentState);
					Point goldPos = getResourceLoc(nextGoalID);
					b = new LocatedAction(peasantIds.get(0), ActionType.COMPOUNDMOVE, goldPos.x, goldPos.y);
				} else if(resource.equals(ResourceType.WOOD)) {
					//goto nearest wood
					UnitView peasant = currentState.getUnit(peasantIds.get(0));
					nextGoalID = getClosestWoodID(new Point(peasant.getXPosition(), peasant.getYPosition()), currentState);
					Point woodPos = getResourceLoc(nextGoalID);
					b = new LocatedAction(peasantIds.get(0), ActionType.COMPOUNDMOVE, woodPos.x, woodPos.y);
				}
				builder.put(peasantIds.get(0), b);
			} else if(actString.equals("class GotoResource2")) {
				ResourceType resource = ((GotoResource2)poll.getToState()).getResource();
				if(resource.equals(ResourceType.GOLD)) {
					//goto nearest gold
					UnitView peasant = currentState.getUnit(peasantIds.get(0));
					nextGoalID = getClosestGoldID(new Point(peasant.getXPosition(), peasant.getYPosition()), currentState);
					Point goldPos = getResourceLoc(nextGoalID);
					b = new LocatedAction(peasantIds.get(0), ActionType.COMPOUNDMOVE, goldPos.x, goldPos.y);
				} else if(resource.equals(ResourceType.WOOD)) {
					//goto nearest wood
					UnitView peasant = currentState.getUnit(peasantIds.get(0));
					nextGoalID = getClosestWoodID(new Point(peasant.getXPosition(), peasant.getYPosition()), currentState);
					Point woodPos = getResourceLoc(nextGoalID);
					b = new LocatedAction(peasantIds.get(0), ActionType.COMPOUNDMOVE, woodPos.x, woodPos.y);
				}
				builder.put(peasantIds.get(0), b);

				resource = ((GotoResource2)poll.getToState()).getResource();
				if(resource.equals(ResourceType.GOLD)) {
					//goto nearest gold
					UnitView peasant = currentState.getUnit(peasantIds.get(1));
					nextGoalID = getClosestGoldID(new Point(peasant.getXPosition(), peasant.getYPosition()), currentState);
					Point goldPos = getResourceLoc(nextGoalID);
					b = new LocatedAction(peasantIds.get(0), ActionType.COMPOUNDMOVE, goldPos.x, goldPos.y);
				} else if(resource.equals(ResourceType.WOOD)) {
					//goto nearest wood
					UnitView peasant = currentState.getUnit(peasantIds.get(1));
					nextGoalID = getClosestWoodID(new Point(peasant.getXPosition(), peasant.getYPosition()), currentState);
					Point woodPos = getResourceLoc(nextGoalID);
					b = new LocatedAction(peasantIds.get(0), ActionType.COMPOUNDMOVE, woodPos.x, woodPos.y);
				}
				builder.put(peasantIds.get(1), b);
			} else if(actString.equals("class GotoResource3")) {
				//peasant 1
				ResourceType resource = ((GotoResource3)poll.getToState()).getResource();
				if(resource.equals(ResourceType.GOLD)) {
					//goto nearest gold
					UnitView peasant = currentState.getUnit(peasantIds.get(0));
					nextGoalID = getClosestGoldID(new Point(peasant.getXPosition(), peasant.getYPosition()), currentState);
					Point goldPos = getResourceLoc(nextGoalID);
					b = new LocatedAction(peasantIds.get(0), ActionType.COMPOUNDMOVE, goldPos.x, goldPos.y);
				} else if(resource.equals(ResourceType.WOOD)) {
					//goto nearest wood
					UnitView peasant = currentState.getUnit(peasantIds.get(0));
					nextGoalID = getClosestWoodID(new Point(peasant.getXPosition(), peasant.getYPosition()), currentState);
					Point woodPos = getResourceLoc(nextGoalID);
					b = new LocatedAction(peasantIds.get(0), ActionType.COMPOUNDMOVE, woodPos.x, woodPos.y);
				}
				builder.put(peasantIds.get(0), b);

				//peasant 2
				resource = ((GotoResource3)poll.getToState()).getResource();
				if(resource.equals(ResourceType.GOLD)) {
					//goto nearest gold
					UnitView peasant = currentState.getUnit(peasantIds.get(1));
					nextGoalID = getClosestGoldID(new Point(peasant.getXPosition(), peasant.getYPosition()), currentState);
					Point goldPos = getResourceLoc(nextGoalID);
					b = new LocatedAction(peasantIds.get(0), ActionType.COMPOUNDMOVE, goldPos.x, goldPos.y);
				} else if(resource.equals(ResourceType.WOOD)) {
					//goto nearest wood
					UnitView peasant = currentState.getUnit(peasantIds.get(1));
					nextGoalID = getClosestWoodID(new Point(peasant.getXPosition(), peasant.getYPosition()), currentState);
					Point woodPos = getResourceLoc(nextGoalID);
					b = new LocatedAction(peasantIds.get(0), ActionType.COMPOUNDMOVE, woodPos.x, woodPos.y);
				}
				builder.put(peasantIds.get(1), b);
				
				//peasant 3
				resource = ((GotoResource3)poll.getToState()).getResource();
				if(resource.equals(ResourceType.GOLD)) {
					//goto nearest gold
					UnitView peasant = currentState.getUnit(peasantIds.get(2));
					nextGoalID = getClosestGoldID(new Point(peasant.getXPosition(), peasant.getYPosition()), currentState);
					Point goldPos = getResourceLoc(nextGoalID);
					b = new LocatedAction(peasantIds.get(0), ActionType.COMPOUNDMOVE, goldPos.x, goldPos.y);
				} else if(resource.equals(ResourceType.WOOD)) {
					//goto nearest wood
					UnitView peasant = currentState.getUnit(peasantIds.get(2));
					nextGoalID = getClosestWoodID(new Point(peasant.getXPosition(), peasant.getYPosition()), currentState);
					Point woodPos = getResourceLoc(nextGoalID);
					b = new LocatedAction(peasantIds.get(0), ActionType.COMPOUNDMOVE, woodPos.x, woodPos.y);
				}
				builder.put(peasantIds.get(2), b);
			} else if(actString.equals("class Gather")) {
				b = new TargetedAction(peasantIds.get(0), ActionType.COMPOUNDGATHER, nextGoalID);
				builder.put(peasantIds.get(0), b);
			} else if(actString.equals("class Gather2")) {
				//peasant 1
				b = new TargetedAction(peasantIds.get(0), ActionType.COMPOUNDGATHER, nextGoalID);
				builder.put(peasantIds.get(0), b);
				
				//TODO check that nextGoalID is set properly for each of the peasants
				//peasant 2
				b = new TargetedAction(peasantIds.get(1), ActionType.COMPOUNDGATHER, nextGoalID);
				builder.put(peasantIds.get(1), b);
			} else if(actString.equals("class Gather3")) {
				//peasant 1
				b = new TargetedAction(peasantIds.get(0), ActionType.COMPOUNDGATHER, nextGoalID);
				builder.put(peasantIds.get(0), b);
				
				//peasant 2
				b = new TargetedAction(peasantIds.get(1), ActionType.COMPOUNDGATHER, nextGoalID);
				builder.put(peasantIds.get(1), b);
				
				//peasant 3
				b = new TargetedAction(peasantIds.get(2), ActionType.COMPOUNDGATHER, nextGoalID);
				builder.put(peasantIds.get(2), b);
			} else if(actString.equals("class Deposit")) {
				b = new TargetedAction(peasantIds.get(0), ActionType.COMPOUNDDEPOSIT, townhallIds.get(0));
				builder.put(peasantIds.get(0), b);
			} else if(actString.equals("class Deposit2")) {
				//peasant 1
				b = new TargetedAction(peasantIds.get(0), ActionType.COMPOUNDDEPOSIT, townhallIds.get(0));
				builder.put(peasantIds.get(0), b);
				
				//peasant 2
				b = new TargetedAction(peasantIds.get(1), ActionType.COMPOUNDDEPOSIT, townhallIds.get(0));
				builder.put(peasantIds.get(1), b);
			} else if(actString.equals("class Deposit3")) {
				//peasant 1
				b = new TargetedAction(peasantIds.get(0), ActionType.COMPOUNDDEPOSIT, townhallIds.get(0));
				builder.put(peasantIds.get(0), b);
				
				//peasant 2
				b = new TargetedAction(peasantIds.get(1), ActionType.COMPOUNDDEPOSIT, townhallIds.get(0));
				builder.put(peasantIds.get(1), b);
				
				//peasant 3
				b = new TargetedAction(peasantIds.get(2), ActionType.COMPOUNDDEPOSIT, townhallIds.get(0));
				builder.put(peasantIds.get(2), b);
			} else if(actString.equals("class BuildPeasant")) {
				TemplateView peasantTemplate = currentState.getTemplate(playernum, "Peasant");
				int peasantTemplateID = peasantTemplate.getID();
				builder.put(townhallIds.get(0), Action.createCompoundProduction(townhallIds.get(0), peasantTemplateID));
			}
		}
		return builder;
	}
	
	@Override
	public void terminalStep(StateView newstate, History.HistoryView statehistory) {
		step++;
		if(logger.isLoggable(Level.FINE)) {
			logger.fine("=> Step: " + step);
		}
		
		int currentGold = newstate.getResourceAmount(0, ResourceType.GOLD);
		int currentWood = newstate.getResourceAmount(0, ResourceType.WOOD);
		
		if(logger.isLoggable(Level.FINE)) {
			logger.fine("Current Gold: " + currentGold);
		}
		if(logger.isLoggable(Level.FINE)) {
			logger.fine("Current Wood: " + currentWood);
		}
		if(logger.isLoggable(Level.FINE)) {
			logger.fine("Congratulations! You have finished the task!");
		}
	}
	
//	public int heuristic(int neededGold, int neededWood, boolean hasResource, boolean atTownhall) {
//		int amountNeeded = neededGold + neededWood;
//		int dist = 0;
//		
////		boolean hasResource = false;
////		int i = 0;
////		while(peasantIds.get(i) != null) {
////			
////		}
//		if(hasResource) {
//			dist++; //deposit action
//			amountNeeded -= GATHER_AMOUNT;
//			if(!atTownhall) {
//				dist++; //move to townhall
//			}
//		}
//		dist += 4 * (amountNeeded / GATHER_AMOUNT);
//		return dist;
//	}
	
	public int heuristic(Node node) {
		int heuristic = 0;
		int goldAmount = 0;
		int woodAmount = 0;
		int peasantCount = 0;
		for(Literal lit : node.getStateLits()) {
			String litStr = lit.getClass().toString();
			if(litStr.equals("class Has") && ((Has)lit).getObjectID() == townhallIds.get(0)
					&& ((Has)lit).getResource().equals(ResourceType.GOLD)) {
				goldAmount = ((Has)lit).getAmount();
			}
			if(litStr.equals("class Has") && ((Has)lit).getObjectID() == townhallIds.get(0)
					&& ((Has)lit).getResource().equals(ResourceType.WOOD)) {
				woodAmount = ((Has)lit).getAmount();
			}
			if(litStr.equals("class ContainsPeasants")) {
				peasantCount = ((ContainsPeasants)lit).getNumPeasants();
			}
		}
		
		heuristic += (targetGold - goldAmount) * 5;
		heuristic += (targetWood - woodAmount) * 4;
		
		heuristic -= 3000 * peasantCount;
		
//		Act actToState = node.getToState();
//		if(actToState != null) {
//			String actStr = actToState.getClass().toString();
//			if(actStr.contains("Gather")) {
//				if(((Gather)actToState).getResource().equals(ResourceType.GOLD)) {
//					heuristic -= peasantCount * 10;
//				} else {
//					heuristic -= peasantCount * 5;
//				}
//			}
//			if(actStr.contains("Deposit")) {
//				if(((Deposit)actToState).getResource().equals(ResourceType.GOLD)) {
//					heuristic -= peasantCount * 20;
//				} else {
//					heuristic -= peasantCount * 15;
//				}
//			}
//		}
		return heuristic;
	}

	private int getClosestWoodID(Point unit, StateView state) {
		List<Integer> resourceIds = state.getResourceNodeIds(Type.TREE);
		int closestWoodID = resourceIds.get(0);
		int minDist = 999;
		for(Integer woodID : resourceIds) {
			ResourceView resource = currentState.getResourceNode(woodID);
			int xDist = Math.abs(resource.getXPosition() - unit.x);
			int yDist = Math.abs(resource.getYPosition() - unit.y);
			if(xDist + yDist < minDist) {
				minDist = xDist + yDist;
				closestWoodID = woodID;
			}
		}
		return closestWoodID;
	}
	
	private int getClosestGoldID(Point unit, StateView state) {
		List<Integer> resourceIds = state.getResourceNodeIds(Type.GOLD_MINE);
		int closestGoldID = resourceIds.get(0);
		int minDist = 999;
		for(Integer goldID : resourceIds) {
			ResourceView resource = state.getResourceNode(goldID);
			if(resource.getAmountRemaining() < GATHER_AMOUNT) {
				continue;
			}
			int xDist = Math.abs(resource.getXPosition() - unit.x);
			int yDist = Math.abs(resource.getYPosition() - unit.y);
			if(xDist + yDist < minDist) {
				minDist = xDist + yDist;
				closestGoldID = goldID;
			}
		}
		return closestGoldID;
	}
	
	private Point getResourceLoc(int resourceID) {
		ResourceView resource = currentState.getResourceNode(resourceID);
		return new Point(resource.getXPosition(), resource.getYPosition());
	}
		
	public boolean areAdjacent(int objOneId, int objTwoId) {
		UnitView obj1 = currentState.getUnit(objOneId);
		if(currentState.getUnit(objTwoId) !=  null) {
			UnitView obj2 = currentState.getUnit(objTwoId);
			if(Math.abs(obj1.getXPosition() - obj2.getXPosition()) <= 1
					&& Math.abs(obj1.getYPosition() - obj2.getYPosition()) <= 1) {
				return true;
			}
			return false;
		} else {
			ResourceView obj2 = currentState.getResourceNode(objTwoId);
			if(obj2 == null) {
				return true;
			}
			if(Math.abs(obj1.getXPosition() - obj2.getXPosition()) <= 1
					&& Math.abs(obj1.getYPosition() - obj2.getYPosition()) <= 1) {
				return true;
			}
			return false;
		}
	}
	
	private void printPlan() {
		try {
			outputPlan = new PrintWriter(new File("outputPlan.txt"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		Act act = null;
		outputPlan.println("TOTAL PLAN LENGTH: " + solution.size());
		for(Node node : solution) {
			act = node.getToState();
			if(act.getClass().toString().equals("class GotoResource")) {
				outputPlan.println("Goto " + ((GotoResource)act).getResourceString());
			} else if(act.getClass().toString().contains("class GotoTownHall")) {
				outputPlan.println("Goto TOWNHALL");
			} else if(act.getClass().toString().equals("class Gather")) {
				outputPlan.println("Gather " + ((Gather)act).getAmount() + " " + ((Gather)act).getResourceString());
			} else if(act.getClass().toString().equals("class Deposit")) {
				outputPlan.println("Deposit " + ((Deposit)act).getAmount() + " " + ((Deposit)act).getResourceString());
			} else if(act.getClass().toString().equals("class BuildPeasant")) {
				outputPlan.println("Produce Peasant");
			} else if(act.getClass().toString().equals("class GotoResource2")) {
				outputPlan.println("Goto " + ((GotoResource2)act).getResourceString());
			} else if(act.getClass().toString().equals("class Gather2")) {
				outputPlan.println("Gather " + ((Gather2)act).getAmount() + " " + ((Gather2)act).getResourceString());
			} else if(act.getClass().toString().equals("class Deposit2")) {
				outputPlan.println("Deposit " + ((Deposit2)act).getAmount() + " " + ((Deposit2)act).getResourceString());
			} else if(act.getClass().toString().equals("class GotoResource3")) {
				outputPlan.println("Goto " + ((GotoResource3)act).getResourceString());
			} else if(act.getClass().toString().equals("class Gather3")) {
				outputPlan.println("Gather " + ((Gather3)act).getAmount() + " " + ((Gather3)act).getResourceString());
			} else if(act.getClass().toString().equals("class Deposit3")) {
				outputPlan.println("Deposit " + ((Deposit3)act).getAmount() + " " + ((Deposit3)act).getResourceString());
			}
		}
		outputPlan.close();
	}

	@Override
	public void savePlayerData(OutputStream os) {
		//this agent lacks learning and so has nothing to persist.
	}
	
	@Override
	public void loadPlayerData(InputStream is) {
		//this agent lacks learning and so has nothing to persist.
	}
}
