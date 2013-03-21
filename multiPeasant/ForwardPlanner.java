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
import edu.cwru.sepia.environment.model.state.Unit.UnitView;

public class ForwardPlanner extends Agent {
	private static final long serialVersionUID = -4047208702628325380L;
	private static final Logger logger = Logger.getLogger(ForwardPlanner.class.getCanonicalName());
	public static final int GATHER_AMOUNT = 100;
	public static final int PEASANT_COST = 400;

	private int step;
	private int currentGoal;
	
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

	public ForwardPlanner(int playernum, String[] arguments) {
		super(playernum);
		currentGoal = 0;
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
		initLits.add(new AtTownHall(peasantIds.get(0))); //peasant starts at the townhall

		//goal state
		goalLits.add(new Has(townhallIds.get(0), ResourceType.GOLD, targetGold));
		goalLits.add(new Has(townhallIds.get(0), ResourceType.WOOD, targetWood));
		
		int estimatedCost = heuristic(targetGold, targetWood, false, false);
		
		Node root = new Node(null, null, initLits, 0, estimatedCost);
		
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
			int neededGold = 0; //according to the townhall
			int neededWood = 0;
			for(Literal literal : node.getStateLits()) {
				if(literal.getClass().toString().equals("class Has")
						&& ((Has)literal).getObjectID() == townhallIds.get(0)) {
					if(((Has)literal).getResource().equals(ResourceType.GOLD)) {
						neededGold = ((Has)literal).getAmount();
						if(neededGold < targetGold) needGold = true;
					} else if(((Has)literal).getResource().equals(ResourceType.WOOD)) {
						neededWood = ((Has)literal).getAmount();
						if(neededWood < targetWood) needWood = true;
					}
				}
			}
			
			if(node.containsLit(new ContainsPeasants(1))) { //only one peasant		
				//GotoResource
				if(node.containsLit(new AtTownHall(peasantIds.get(0)))
						&& !node.containsLit(new Has(peasantIds.get(0), ResourceType.GOLD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(peasantIds.get(0), ResourceType.WOOD, GATHER_AMOUNT))) { //preconditions
					if(needGold) {
						ArrayList<Literal> literalsGold = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							if(!lit.equals(new AtTownHall(peasantIds.get(0)))) { //remove list
								literalsGold.add(lit);
							}
						}
						literalsGold.add(new AtResource(peasantIds.get(0), ResourceType.GOLD)); //add list
						
						estimatedCost = heuristic(targetGold - neededGold, targetWood - neededWood, false, false);
						
						Node n = new Node(node, new GotoResource(peasantIds.get(0), ResourceType.GOLD),
								literalsGold, node.getCostToNode() + 1, estimatedCost);
						
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
							if(!lit.equals(new AtTownHall(peasantIds.get(0)))) { //remove list
								literalsWood.add(lit);
							}
						}
						literalsWood.add(new AtResource(peasantIds.get(0), ResourceType.WOOD)); //add list
						
						estimatedCost = heuristic(targetGold - neededGold, targetWood - neededWood, false, false);
						
						Node n = new Node(node, new GotoResource(peasantIds.get(0), ResourceType.WOOD),
								literalsWood, node.getCostToNode() + 1, estimatedCost);
						
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
				if((node.containsLit(new AtResource(peasantIds.get(0), ResourceType.GOLD))
						|| node.containsLit(new AtResource(peasantIds.get(0), ResourceType.WOOD)))
						&& (node.containsLit(new Has(peasantIds.get(0), ResourceType.GOLD, GATHER_AMOUNT))
								|| node.containsLit(new Has(peasantIds.get(0), ResourceType.WOOD, GATHER_AMOUNT)))) { //preconditions
					ArrayList<Literal> literals = new ArrayList<Literal>();
					for(Literal lit : node.getStateLits()) {
						if(!lit.equals(new AtResource(peasantIds.get(0), ResourceType.GOLD))
								&& !lit.equals(new AtResource(peasantIds.get(0), ResourceType.WOOD))) { //remove list
							literals.add(lit);
						}
					}
					
					literals.add(new AtTownHall(peasantIds.get(0)));
					
					estimatedCost = heuristic(targetGold - neededGold, targetWood - neededWood, true, true);
					
					Node n = new Node(node, new GotoTownHall(peasantIds.get(0)),
							literals, node.getCostToNode() + 1, estimatedCost);
					
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
				if(node.containsLit(new AtTownHall(peasantIds.get(0)))) {
					if(node.containsLit(new Has(peasantIds.get(0), ResourceType.GOLD, GATHER_AMOUNT))) { //preconditions
						ArrayList<Literal> literalsGold = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							if(!lit.equals(new Has(peasantIds.get(0), ResourceType.GOLD, GATHER_AMOUNT))) { //remove list
								if(lit.getClass().toString().equals("class Has")
										&& ((Has)lit).getObjectID() == townhallIds.get(0)
										&& ((Has)lit).getResource().equals(ResourceType.GOLD)) {
									literalsGold.add(new Has(townhallIds.get(0), ResourceType.GOLD, ((Has)lit).getAmount() + GATHER_AMOUNT));
								} else {
									literalsGold.add(lit);
								}
							}
						}
						
						estimatedCost = heuristic(targetGold - neededGold, targetWood - neededWood, false, true);
						
						Node n = new Node(node, new Deposit(peasantIds.get(0), ResourceType.GOLD, GATHER_AMOUNT),
								literalsGold, node.getCostToNode() + 1, estimatedCost);
						
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
					if(node.containsLit(new Has(peasantIds.get(0), ResourceType.WOOD, GATHER_AMOUNT))) { //preconditions
						ArrayList<Literal> literalsWood = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							if(!lit.equals(new Has(peasantIds.get(0), ResourceType.WOOD, GATHER_AMOUNT))) { //remove list
								if(lit.getClass().toString().equals("class Has")
										&& ((Has)lit).getObjectID() == townhallIds.get(0)
										&& ((Has)lit).getResource().equals(ResourceType.WOOD)) {
									literalsWood.add(new Has(townhallIds.get(0), ResourceType.WOOD, ((Has)lit).getAmount() + GATHER_AMOUNT));
								} else {
									literalsWood.add(lit);
								}
							}
						}
						
						estimatedCost = heuristic(targetGold - neededGold, targetWood - neededWood, false, true);
						
						Node n = new Node(node, new Deposit(peasantIds.get(0), ResourceType.WOOD, GATHER_AMOUNT),
								literalsWood, node.getCostToNode() + 1, estimatedCost);
						
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
				if(!node.containsLit(new Has(peasantIds.get(0), ResourceType.GOLD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(peasantIds.get(0), ResourceType.WOOD, GATHER_AMOUNT))) {
					if(node.containsLit(new AtResource(peasantIds.get(0), ResourceType.GOLD))) { //preconditions
						ArrayList<Literal> literalsGold = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							literalsGold.add(lit);
						}
						
						literalsGold.add(new Has(peasantIds.get(0), ResourceType.GOLD, GATHER_AMOUNT)); //add list
						
						estimatedCost = heuristic(targetGold - neededGold, targetWood - neededWood, true, false);
						
						Node n = new Node(node, new Gather(peasantIds.get(0), ResourceType.GOLD, GATHER_AMOUNT),
								literalsGold, node.getCostToNode() + 1, estimatedCost);
						
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
					if(node.containsLit(new AtResource(peasantIds.get(0), ResourceType.WOOD))) { //preconditions
						ArrayList<Literal> literalsWood = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							literalsWood.add(lit);
						}
						
						literalsWood.add(new Has(peasantIds.get(0), ResourceType.WOOD, GATHER_AMOUNT)); //add list
						
						estimatedCost = heuristic(targetGold - neededGold, targetWood - neededWood, true, false);
						
						Node n = new Node(node, new Gather(peasantIds.get(0), ResourceType.WOOD, GATHER_AMOUNT),
								literalsWood, node.getCostToNode() + 1, estimatedCost);
						
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
							((ContainsPeasants)lit).setNumPeasants(2); //add list
						}
					}
					
					//TODO get the new peasantID!!!!
//					literalsBuild.add(new AtTownHall(peasantIds.get(1)));
					
					estimatedCost = heuristic(targetGold - neededGold, targetWood - neededWood, false, false);//TODO fix heuristic function
					
					Node n = new Node(node, new BuildPeasant(), literalsBuild, node.getCostToNode() + 1, estimatedCost);
					
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
				//TODO deal with peasant IDs
				//GotoResource2
				if(node.containsLit(new AtTownHall(peasantIds.get(0)))
						&& node.containsLit(new AtTownHall(peasantIds.get(1)))
						&& !node.containsLit(new Has(peasantIds.get(0), ResourceType.GOLD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(peasantIds.get(1), ResourceType.GOLD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(peasantIds.get(0), ResourceType.WOOD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(peasantIds.get(0), ResourceType.WOOD, GATHER_AMOUNT))) { //preconditions
					if(needGold) {
						ArrayList<Literal> literalsGold = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							if(!lit.equals(new AtTownHall(peasantIds.get(0)))
									&& !lit.equals(new AtTownHall(peasantIds.get(1)))) { //remove list
								literalsGold.add(lit);
							}
						}
						literalsGold.add(new AtResource(peasantIds.get(0), ResourceType.GOLD)); //add list
						literalsGold.add(new AtResource(peasantIds.get(1), ResourceType.GOLD));
						
						estimatedCost = heuristic(targetGold - neededGold, targetWood - neededWood, false, false);
						
						Node n = new Node(node, new GotoResource2(peasantIds.get(0), peasantIds.get(1), ResourceType.GOLD),
								literalsGold, node.getCostToNode() + 1, estimatedCost);
						
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
							if(!lit.equals(new AtTownHall(peasantIds.get(0)))
									&& !lit.equals(new AtTownHall(peasantIds.get(1)))) { //remove list
								literalsWood.add(lit);
							}
						}
						literalsWood.add(new AtResource(peasantIds.get(0), ResourceType.WOOD)); //add list
						literalsWood.add(new AtResource(peasantIds.get(1), ResourceType.WOOD));
						
						estimatedCost = heuristic(targetGold - neededGold, targetWood - neededWood, false, false);
						
						Node n = new Node(node, new GotoResource2(peasantIds.get(0), peasantIds.get(1), ResourceType.WOOD),
								literalsWood, node.getCostToNode() + 1, estimatedCost);
						
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
				if((node.containsLit(new AtResource(peasantIds.get(0), ResourceType.GOLD))
							|| node.containsLit(new AtResource(peasantIds.get(0), ResourceType.WOOD)))
						&& (node.containsLit(new Has(peasantIds.get(0), ResourceType.GOLD, GATHER_AMOUNT))
							|| node.containsLit(new Has(peasantIds.get(0), ResourceType.WOOD, GATHER_AMOUNT)))
						&& (node.containsLit(new AtResource(peasantIds.get(1), ResourceType.GOLD))
							|| node.containsLit(new AtResource(peasantIds.get(1), ResourceType.WOOD)))
						&& (node.containsLit(new Has(peasantIds.get(1), ResourceType.GOLD, GATHER_AMOUNT))
							|| node.containsLit(new Has(peasantIds.get(1), ResourceType.WOOD, GATHER_AMOUNT)))) { //preconditions
					ArrayList<Literal> literals = new ArrayList<Literal>();
					for(Literal lit : node.getStateLits()) {
						if(!lit.equals(new AtResource(peasantIds.get(0), ResourceType.GOLD))
								&& !lit.equals(new AtResource(peasantIds.get(0), ResourceType.WOOD))
								&& !lit.equals(new AtResource(peasantIds.get(1), ResourceType.GOLD))
								&& !lit.equals(new AtResource(peasantIds.get(1), ResourceType.WOOD))) { //remove list
							literals.add(lit);
						}
					}
					
					literals.add(new AtTownHall(peasantIds.get(0))); //add list
					literals.add(new AtTownHall(peasantIds.get(1)));
					
					estimatedCost = heuristic(targetGold - neededGold, targetWood - neededWood, true, true);
					
					Node n = new Node(node, new GotoTownHall2(peasantIds.get(0), peasantIds.get(1)),
							literals, node.getCostToNode() + 1, estimatedCost);
					
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
				if(node.containsLit(new AtTownHall(peasantIds.get(0)))
						&& node.containsLit(new AtTownHall(peasantIds.get(1)))) {
					if(node.containsLit(new Has(peasantIds.get(0), ResourceType.GOLD, GATHER_AMOUNT))
							&& node.containsLit(new Has(peasantIds.get(1), ResourceType.GOLD, GATHER_AMOUNT))) { //preconditions
						ArrayList<Literal> literalsGold = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							if(!lit.equals(new Has(peasantIds.get(0), ResourceType.GOLD, GATHER_AMOUNT))
									&& !lit.equals(new Has(peasantIds.get(1), ResourceType.GOLD, GATHER_AMOUNT))) { //remove list
								if(lit.getClass().toString().equals("class Has")
										&& ((Has)lit).getObjectID() == townhallIds.get(0)
										&& ((Has)lit).getResource().equals(ResourceType.GOLD)) {
									literalsGold.add(new Has(townhallIds.get(0), ResourceType.GOLD, ((Has)lit).getAmount() + 2 * GATHER_AMOUNT));
								} else {
									literalsGold.add(lit);
								}
							}
						}
						
						estimatedCost = heuristic(targetGold - neededGold, targetWood - neededWood, false, true);
						
						Node n = new Node(node, new Deposit2(peasantIds.get(0), peasantIds.get(1), ResourceType.GOLD, 2 * GATHER_AMOUNT),
								literalsGold, node.getCostToNode() + 1, estimatedCost);
						
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
					if(node.containsLit(new Has(peasantIds.get(0), ResourceType.WOOD, GATHER_AMOUNT))
							&& node.containsLit(new Has(peasantIds.get(1), ResourceType.WOOD, GATHER_AMOUNT))) { //preconditions
						ArrayList<Literal> literalsWood = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							if(!lit.equals(new Has(peasantIds.get(0), ResourceType.WOOD, GATHER_AMOUNT))
									&& !lit.equals(new Has(peasantIds.get(1), ResourceType.WOOD, GATHER_AMOUNT))) { //remove list
								if(lit.getClass().toString().equals("class Has")
										&& ((Has)lit).getObjectID() == townhallIds.get(0)
										&& ((Has)lit).getResource().equals(ResourceType.WOOD)) {
									literalsWood.add(new Has(townhallIds.get(0), ResourceType.WOOD, ((Has)lit).getAmount() + 2 * GATHER_AMOUNT));
								} else {
									literalsWood.add(lit);
								}
							}
						}
						
						estimatedCost = heuristic(targetGold - neededGold, targetWood - neededWood, false, true);
						
						Node n = new Node(node, new Deposit2(peasantIds.get(0), peasantIds.get(1), ResourceType.WOOD, 2 * GATHER_AMOUNT),
								literalsWood, node.getCostToNode() + 1, estimatedCost);
						
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
				if(!node.containsLit(new Has(peasantIds.get(0), ResourceType.GOLD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(peasantIds.get(1), ResourceType.GOLD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(peasantIds.get(0), ResourceType.WOOD, GATHER_AMOUNT))
						&& !node.containsLit(new Has(peasantIds.get(1), ResourceType.WOOD, GATHER_AMOUNT))) {
					if(node.containsLit(new AtResource(peasantIds.get(0), ResourceType.GOLD))
							&& node.containsLit(new AtResource(peasantIds.get(1), ResourceType.GOLD))) { //preconditions
						ArrayList<Literal> literalsGold = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							literalsGold.add(lit);
						}
						
						literalsGold.add(new Has(peasantIds.get(0), ResourceType.GOLD, GATHER_AMOUNT)); //add list
						literalsGold.add(new Has(peasantIds.get(1), ResourceType.GOLD, GATHER_AMOUNT));
						
						estimatedCost = heuristic(targetGold - neededGold, targetWood - neededWood, true, false);
						
						Node n = new Node(node, new Gather2(peasantIds.get(0), peasantIds.get(1), ResourceType.GOLD, 2 * GATHER_AMOUNT),
								literalsGold, node.getCostToNode() + 1, estimatedCost);
						
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
					if(node.containsLit(new AtResource(peasantIds.get(0), ResourceType.WOOD))
							&& node.containsLit(new AtResource(peasantIds.get(1), ResourceType.WOOD))) { //preconditions
						ArrayList<Literal> literalsWood = new ArrayList<Literal>();
						for(Literal lit : node.getStateLits()) {
							literalsWood.add(lit);
						}
						
						literalsWood.add(new Has(peasantIds.get(0), ResourceType.WOOD, GATHER_AMOUNT)); //add list
						literalsWood.add(new Has(peasantIds.get(1), ResourceType.WOOD, GATHER_AMOUNT));
						
						estimatedCost = heuristic(targetGold - neededGold, targetWood - neededWood, true, false);
						
						Node n = new Node(node, new Gather2(peasantIds.get(0), peasantIds.get(1), ResourceType.WOOD, 2 * GATHER_AMOUNT),
								literalsWood, node.getCostToNode() + 1, estimatedCost);
						
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
						if(!lit.equals(new ContainsPeasants(2))) { //remove list
							if(lit.getClass().toString().equals("class Has")
									&& ((Has)lit).getObjectID() == townhallIds.get(0)
									&& ((Has)lit).getResource().equals(ResourceType.GOLD)) {
								literalsBuild.add(new Has(townhallIds.get(0), ResourceType.GOLD, ((Has)lit).getAmount() - PEASANT_COST));
							} else {
								literalsBuild.add(lit);
							}
						} else {
							((ContainsPeasants)lit).setNumPeasants(3); //add list
						}
					}
					
					//TODO get the new peasantID!!!!
					literalsBuild.add(new AtTownHall(peasantIds.get(2)));
					
					estimatedCost = heuristic(targetGold - neededGold, targetWood - neededWood, false, false);//TODO fix heuristic function
					
					Node n = new Node(node, new BuildPeasant(), literalsBuild, node.getCostToNode() + 1, estimatedCost);
					
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
		
 		if(!areAdjacent(peasantIds.get(0), nextGoalID)) {
			return null;
		}
		Map<Integer, Action> builder = new HashMap<Integer, Action>();
		if(solution.peek() != null) {
			Action b = null;
			Node poll = solution.poll();
			String actString = poll.getToState().getClass().toString();
		
			//TODO buildPeasant
			if(actString.equals("class GotoTownHall")){
				UnitView townhall = currentState.getUnit(townhallIds.get(0));
				int townX = townhall.getXPosition();
				int townY = townhall.getYPosition();
				b = new LocatedAction(peasantIds.get(0), ActionType.COMPOUNDMOVE, townX, townY);
				nextGoalID = townhallIds.get(0);
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
			} else if(actString.equals("class Gather")) {
				b = new TargetedAction(peasantIds.get(0), ActionType.COMPOUNDGATHER, nextGoalID);	
			} else if(actString.equals("class Deposit")) {
				b = new TargetedAction(peasantIds.get(0), ActionType.COMPOUNDDEPOSIT, townhallIds.get(0));
			}
			builder.put(peasantIds.get(0), b);
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
	
	public int heuristic(int neededGold, int neededWood, boolean hasResource, boolean atTownhall) {
		int amountNeeded = neededGold + neededWood;
		int dist = 0;
		if(hasResource) {
			dist++; //deposit action
			amountNeeded -= GATHER_AMOUNT;
			if(!atTownhall) {
				dist++; //move to townhall
			}
		}
		dist += 4 * (amountNeeded / GATHER_AMOUNT);
		return dist;
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
		//TODO buildPeasant
		Act act = null;
		outputPlan.println("TOTAL PLAN LENGTH: " + solution.size());
		for(Node node : solution) {
			act = node.getToState();
			if(act.getClass().toString().equals("class GotoResource")) {
				outputPlan.println("Goto " + ((GotoResource)act).getResourceString());
			} else if(act.getClass().toString().equals("class GotoTownHall")) {
				outputPlan.println("Goto TOWNHALL");
			} else if(act.getClass().toString().equals("class Gather")) {
				outputPlan.println("Gather " + ((Gather)act).getAmount() + " " + ((Gather)act).getResourceString());
			} else if(act.getClass().toString().equals("class Deposit")) {
				outputPlan.println("Deposit " + ((Deposit)act).getAmount() + " " + ((Deposit)act).getResourceString());
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
