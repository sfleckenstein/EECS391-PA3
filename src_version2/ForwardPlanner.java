/**
d *  Strategy Engine for Programming Intelligent Agents (SEPIA)
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
import java.io.IOException;
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
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.ResourceNode.ResourceView;
import edu.cwru.sepia.environment.model.state.ResourceNode.Type;
import edu.cwru.sepia.environment.model.state.ResourceType;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.util.Direction;

public class ForwardPlanner extends Agent {
	private static final long serialVersionUID = -4047208702628325380L;
	private static final Logger logger = Logger.getLogger(ForwardPlanner.class.getCanonicalName());
	public static final int GATHER_AMOUNT = 100;

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

	public ForwardPlanner(int playernum, String[] arguments) {
		super(playernum);
		currentGoal = 0;
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
		initLits.add(new Has(townhallIds.get(0), ResourceType.GOLD, 0)); //town has no gold
		initLits.add(new Has(townhallIds.get(0), ResourceType.WOOD, 0)); //town has no wood
		initLits.add(new AtTownHall(peasantIds.get(0))); //peasant starts at the townhall

		//goal state
		goalLits.add(new Has(townhallIds.get(0), ResourceType.GOLD, 200));
		goalLits.add(new Has(townhallIds.get(0), ResourceType.WOOD, 200));
		
		int goalGoldAmt = 0;
		int goalWoodAmt = 0;
		for(Literal goalLit : goalLits) { 				
			if(goalLit.getClass().toString().equals("class Has")
					&& ((Has)goalLit).getObjectID() == townhallIds.get(0)) {
				if(((Has)goalLit).getResource().equals(ResourceType.GOLD)) {
					goalGoldAmt = ((Has)goalLit).getAmount();
				} else if(((Has)goalLit).getResource().equals(ResourceType.WOOD)) {
					goalWoodAmt = ((Has)goalLit).getAmount();
				}
			}
		}
		
		
		int estimatedCost = 0; //TODO fix to use heuristic
		
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
			
			//TODO set needGold/Wood properly
			boolean needGold = false;
			boolean needWood = false;
			for(Literal literal : node.getStateLits()) {
				if(literal.getClass().toString().equals("class Has")
						&& ((Has)literal).getObjectID() == peasantIds.get(0)) {
					if(((Has)literal).getResource().equals(ResourceType.GOLD)) {
						if(((Has)literal).getAmount() < goalGoldAmt) needGold = true;
					} else if(((Has)literal).getResource().equals(ResourceType.GOLD)) {
						if(((Has)literal).getAmount() < goalWoodAmt) needWood = true;
					}
				}
			}
			
<<<<<<< HEAD
			//Deposit Gold/Wood
			if(hasResource && areAdjacent(node, peasantIds.get(0), townhallIds.get(0))) { //preconditions
				peasant = node.getState().getUnit(peasantIds.get(0));
				literals = new ArrayList<Literal>();
				literals.addAll(node.getStateLits());
				
				Deposit deposit = new Deposit(peasant.getCargoAmount(),
						getDirectionBetween(peasant, townhall),
						peasant.getCargoType());
				
				Point goal = node.getGoal();
				int estimatedCost = 99999;
				int closestResourceID;
				
				if(hasWood) {
					//generate and remove the remove list
					ArrayList<Literal> toRemove = new ArrayList<Literal>();
					int woodAmt = 0;
					for(Literal lit: node.getStateLits()) {
						if(lit.equals(new Has(peasantIds.get(0), ResourceType.WOOD, peasant.getCargoAmount()))) {
							toRemove.add(lit);
							woodAmt = ((Has)lit).getAmount();
=======
			//GotoResource
			if(node.containsLit(new AtTownHall(peasantIds.get(0)))
					&& !node.containsLit(new Has(peasantIds.get(0), ResourceType.GOLD, GATHER_AMOUNT))
					&& !node.containsLit(new Has(peasantIds.get(0), ResourceType.WOOD, GATHER_AMOUNT))) { //preconditions
				if(needGold) {
					ArrayList<Literal> literalsGold = new ArrayList<Literal>();
					for(Literal lit : node.getStateLits()) {
						if(!lit.equals(new AtTownHall(peasantIds.get(0)))) { //remove list
							literalsGold.add(lit);
>>>>>>> 6cb7a56c82cd13ead45aea35aaa36a41ca3a5b67
						}
					}
					literalsGold.add(new AtResource(peasantIds.get(0), ResourceType.GOLD)); //add list
					
					estimatedCost = 0; //TODO fix to use heuristic
					
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
					literalsWood.add(new AtResource(peasantIds.get(0), ResourceType.GOLD)); //add list
					
					estimatedCost = 0; //TODO fix to use heuristic
					
					Node n = new Node(node, new GotoResource(peasantIds.get(0), ResourceType.GOLD),
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
				literals.add(new AtResource(peasantIds.get(0), ResourceType.GOLD)); //add list
				
				estimatedCost = 0; //TODO fix to use heuristic
				
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
					
					estimatedCost = 0;//TODO fix to use heuristic
					
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
					
					estimatedCost = 0; //TODO fix to use heuristic
					
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
					&& !node.containsLit(new Has(peasantIds.get(0), ResourceType.GOLD, GATHER_AMOUNT))) {
				if(node.containsLit(new AtResource(peasantIds.get(0), ResourceType.GOLD))) { //preconditions
					ArrayList<Literal> literalsGold = new ArrayList<Literal>();
					for(Literal lit : node.getStateLits()) {
							literalsGold.add(lit);
					}
					
					literalsGold.add(new Has(peasantIds.get(0), ResourceType.GOLD, GATHER_AMOUNT)); //add list
					
					estimatedCost = 0; //TODO fix to use heuristic
					
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
				if(node.containsLit(new AtResource(peasantIds.get(0), ResourceType.WOOD))) { //preconditions
					ArrayList<Literal> literalsWood = new ArrayList<Literal>();
					for(Literal lit : node.getStateLits()) {
							literalsWood.add(lit);
					}
					
					literalsWood.add(new Has(peasantIds.get(0), ResourceType.WOOD, GATHER_AMOUNT)); //add list
					
					estimatedCost = 0; //TODO fix to use heuristic
					
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
		}
		return middleStep(newState, stateHistory);
	}

	@Override
	public Map<Integer, Action> middleStep(StateView newState, History.HistoryView statehistory) {
		step++;
		if(logger.isLoggable(Level.FINE)) {
			logger.fine("=> Step: " + step);
		}
		
		Map<Integer, Action> builder = new HashMap<Integer, Action>();
		if(solution.peek() != null) {
			//Action b = solution.poll().getToState().act(peasantIds.get(0));
			//builder.put(peasantIds.get(0), b);
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
	
	//TODO fix the heuristic
	//
	public int calculateHeuristicDistance(Node node, boolean hasResource, Point peasantLoc, Point townhallLoc, int neededResources) {
		int dist = 0;
//		if(neededResources > 0) {			
//			if(hasResource) { //heading toward townhall
//				dist += chebyshevDistance(peasantLoc, townhallLoc);
//				neededResources -= GATHER_AMOUNT;
//			} else { //heading toward resource
//				dist += chebyshevDistance(peasantLoc, closestLoc) + chebyshevDistance(closestLoc, townhallLoc);
//				neededResources -= GATHER_AMOUNT;
//			}
//			while(neededResources > 0) {
//				dist += 2 * chebyshevDistance(closestLoc, townhallLoc);
//				neededResources -= GATHER_AMOUNT;
//			}
//		}
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
			ResourceView resource = currentState.getResourceNode(goldID);
			int xDist = Math.abs(resource.getXPosition() - unit.x);
			int yDist = Math.abs(resource.getYPosition() - unit.y);
			if(xDist + yDist < minDist) {
				minDist = xDist + yDist;
				closestGoldID = goldID;
			}
		}
		return closestGoldID;
	}
		
	private Direction getDirectionBetween(UnitView unit, UnitView townHall) {
		if(unit.getXPosition() < townHall.getXPosition()
				&& unit.getYPosition() == townHall.getYPosition()) {
			return Direction.EAST;
		} else if(unit.getXPosition() < townHall.getXPosition()
				&& unit.getYPosition() < townHall.getYPosition()) {
			return Direction.SOUTHEAST;
		} else if(unit.getXPosition() == townHall.getXPosition()
				&& unit.getYPosition() < townHall.getYPosition()) {
			return Direction.SOUTH;
		} else if(unit.getXPosition() > townHall.getXPosition()
				&& unit.getYPosition() < townHall.getYPosition()) {
			return Direction.SOUTHWEST;
		} else if(unit.getXPosition() > townHall.getXPosition()
				&& unit.getYPosition() == townHall.getYPosition()) {
			return Direction.WEST;
		} else if(unit.getXPosition() > townHall.getXPosition()
				&& unit.getYPosition() > townHall.getYPosition()) {
			return Direction.NORTHWEST;
		} else if(unit.getXPosition() == townHall.getXPosition()
				&& unit.getYPosition() > townHall.getYPosition()) {
			return Direction.NORTH;
		} else {
			return Direction.NORTHEAST;
		}
	}
	
	private Direction getDirectionBetween(Point unit, ResourceView resource) {
		if(unit.x < resource.getXPosition()
				&& unit.y == resource.getYPosition()) {
			return Direction.EAST;
		} else if(unit.x < resource.getXPosition()
				&& unit.y < resource.getYPosition()) {
			return Direction.SOUTHEAST;
		} else if(unit.x == resource.getXPosition()
				&& unit.y < resource.getYPosition()) {
			return Direction.SOUTH;
		} else if(unit.x > resource.getXPosition()
				&& unit.y < resource.getYPosition()) {
			return Direction.SOUTHWEST;
		} else if(unit.x > resource.getXPosition()
				&& unit.y == resource.getYPosition()) {
			return Direction.WEST;
		} else if(unit.x > resource.getXPosition()
				&& unit.y > resource.getYPosition()) {
			return Direction.NORTHWEST;
		} else if(unit.x == resource.getXPosition()
				&& unit.y > resource.getYPosition()) {
			return Direction.NORTH;
		} else {
			return Direction.NORTHEAST;
		}
	}
	
<<<<<<< HEAD
	public boolean areAdjacent(Node node, int objOneId, int objTwoId) {
		for(Literal lit1 : node.getStateLits()) {
			if(lit1.getClass().toString().equals("class At")) {
				for(Literal lit2 : node.getStateLits()) {
					if(lit2.getClass().toString().equals("class At")) {
						
						At at1 = (At)lit1;
						At at2 = (At)lit2;
						
						if(at1.getObjectID() == objOneId
								&& at2.getObjectID() == objTwoId) {
							Point p1 = at1.getPosition();
							Point p2 = at2.getPosition();
							
							if(Math.abs(p1.getX() - p2.getX()) <=1
									&& Math.abs(p1.getY() - p2.getY()) <=1 ) {
								return true;
							}
						}
					}
				}
			}
		}
		return false;
	}
=======
//	public boolean areAdjacent(Node node, int objOneId, int objTwoId) {
//		for(Literal lit1 : node.getStateLits()) {
//			if(lit1.getClass().toString().equals("class At")) {
//				for(Literal lit2 : node.getStateLits()) {
//					if(lit2.getClass().toString().equals("class At")) {
//						
//						At at1 = (At)lit1;
//						At at2 = (At)lit2;
//						
//						if(at1.getObjectID() == objOneId
//								&& at2.getObjectID() == objTwoId) {
//							Point p1 = at1.getPosition();
//							Point p2 = at2.getPosition();
//							
//							if(Math.abs(p1.getX() - p2.getX()) <=1
//									&& Math.abs(p1.getY() - p2.getY()) <=1 ) {
//								return true;
//							}
//						}
//					}
//				}
//			}
//		}
//		return false;
//	}
>>>>>>> 6cb7a56c82cd13ead45aea35aaa36a41ca3a5b67
	
	private void printPlan() {
		try {
			outputPlan = new PrintWriter(new File("outputPlan.txt"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		Act act = null;
		for(Node node : solution) {
			act = node.getToState();
			//outputPlan.println(act.getClass().toString());
			if(act.getClass().toString().equals("class GotoResource")) {
				outputPlan.println("Goto " + ((GotoResource)act).getResourceString());
			} else if(act.getClass().toString().equals("class GotoTownHall")) {
				outputPlan.println("Goto TOWNHALL");
			} else if(act.getClass().toString().equals("class Gather")) {
				outputPlan.println("Gather " + ((Gather)act).getAmount() + ((Gather)act).getResourceString());
			} else if(act.getClass().toString().equals("class Deposit")) {
				outputPlan.println("Deposit " + ((Deposit)act).getAmount() + ((Deposit)act).getResourceString());
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
