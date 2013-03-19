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
		
		int currentGold = currentState.getResourceAmount(0, ResourceType.GOLD);
		int currentWood = currentState.getResourceAmount(0, ResourceType.WOOD);
		
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

		UnitView peasant = currentState.getUnit(peasantIds.get(0));
		
		ArrayList<Literal> stateLits = new ArrayList<Literal>();
		Point peasantLoc = new Point(peasant.getXPosition(), peasant.getYPosition());
		At at = new At(peasantIds.get(0), peasantLoc);
		stateLits.add(at);
		
		for(int resourceID : currentState.getAllResourceIds()) {
			ResourceView resourceNode = currentState.getResourceNode(resourceID);
			at = new At(resourceID, new Point(resourceNode.getXPosition(), resourceNode.getYPosition()));
			stateLits.add(at);
		}
		
		UnitView townhall = currentState.getUnit(townhallIds.get(0));
		at = new At(townhallIds.get(0), new Point(townhall.getXPosition(), townhall.getYPosition()));
		stateLits.add(at);
		stateLits.add(new Has(townhallIds.get(0), ResourceType.GOLD, 0));
		stateLits.add(new Has(townhallIds.get(0), ResourceType.WOOD, 0));
		
		//set the goal literals for a specific task
		goalLits.add(new Has(townhallIds.get(0), ResourceType.GOLD, 200));
		goalLits.add(new Has(townhallIds.get(0), ResourceType.WOOD, 200));
		
		ResourceView wood = currentState.getResourceNode(getClosestWoodID(peasantLoc, currentState));
		ResourceView gold = currentState.getResourceNode(getClosestGoldID(peasantLoc, currentState));

		Node root = new Node(newState, null, null, stateLits, 0, 
				new Point(wood.getXPosition(), wood.getYPosition()), 99999);
		
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
			
			State nextState = null;
			try {
				nextState = node.getState().getStateCreator().createState();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			currentGold = node.getTownHallGold(townhallIds.get(0));
			currentWood = node.getTownHallWood(townhallIds.get(0));

			peasant = node.getState().getUnit(peasantIds.get(0));
			townhall = node.getState().getUnit(townhallIds.get(0));
			
			ArrayList<Literal> literals = null;
			
			boolean hasResource = false;
			boolean hasWood = false;
			for(Literal lit : node.getStateLits()) {
				if(lit.getClass().toString().equals("class Has")
						&& ((Has)lit).getHolderID() == peasantIds.get(0)) {
					hasResource = true;
					if(((Has)lit).getToHold() == ResourceType.WOOD) hasWood = true;
					break;
				}
			}
			
			boolean needWood = false;
			boolean needGold = false;
			int desiredWood = 0;
			int desiredGold = 0;
			int neededWood = 0;
			int neededGold = 0;
			for(Literal goalLiteral : goalLits) {
				if(goalLiteral.getClass().toString().equals("class Has")
						&& ((Has)goalLiteral).getHolderID() == townhallIds.get(0)) {
					if(((Has)goalLiteral).getToHold() == ResourceType.WOOD) {
						desiredWood = ((Has)goalLiteral).getAmount();
					} else {
						desiredGold = ((Has)goalLiteral).getAmount();
					}
				}
			}
			for(Literal literal : node.getStateLits()) {
				if(literal.getClass().toString().equals("class Has")
						&& ((Has)literal).getHolderID() == townhallIds.get(0)) {
					if(((Has)literal).getToHold() == ResourceType.WOOD
							&& desiredWood > ((Has)literal).getAmount()) {
						needWood = true;
						neededWood = ((Has)literal).getAmount();
					} else if(((Has)literal).getToHold() == ResourceType.GOLD
							&& desiredGold > ((Has)literal).getAmount()) {
						needGold = true;
						neededGold = ((Has)literal).getAmount();
					}
				}
			}
			
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
						}
					}
					literals.removeAll(toRemove);
					
					//add list (nothing in this case)
					for(Literal lit: literals) {
						if(lit.getClass().toString().equals("class Has")
								&& ((Has)lit).getHolderID() == townhallIds.get(0)
								&& ((Has)lit).getToHold() == ResourceType.WOOD) {
							((Has)lit).setAmount(((Has)lit).getAmount() + woodAmt); //alter how much the townhall has
						}
					}
					
					try {
						nextState = node.getState().getStateCreator().createState();
					} catch (IOException e) {
						e.printStackTrace();
					}
					
					nextState.addResourceAmount(0, ResourceType.WOOD, peasant.getCargoAmount());
					nextState.getUnit(peasantIds.get(0)).setCargo(null, 0);//TODO is null ok here?
					
					//TODO get rid of the 200 stuff
					if(peasant.getCargoAmount() + currentWood < desiredWood) {
						Point loc = node.getUnitLoc(peasantIds.get(0));
						closestResourceID = getClosestWoodID(loc, node.getState());
						wood = currentState.getResourceNode(closestResourceID);
						estimatedCost = calculateHeuristicDistance(node, true, node.getUnitLoc(peasantIds.get(0)), 
								node.getUnitLoc(townhallIds.get(0)), neededWood+neededGold);
						
						goal.x = wood.getXPosition();
						goal.y = wood.getYPosition();
					} else if(currentGold < desiredGold) {
						Point loc = node.getUnitLoc(peasantIds.get(0));
						closestResourceID = getClosestGoldID(loc, node.getState());
						gold = currentState.getResourceNode(closestResourceID);
						
						estimatedCost = calculateHeuristicDistance(node, true, node.getUnitLoc(peasantIds.get(0)), 
								node.getUnitLoc(townhallIds.get(0)), neededWood+neededGold);
						
						goal.x = gold.getXPosition();
						goal.y = gold.getYPosition();
					} else {
						estimatedCost = 0;
					}
				} else { //if(hasGold) {
					//TODO check if this removes the right Has objects
					//generate and remove the remove list
					ArrayList<Literal> toRemove = new ArrayList<Literal>();
					int goldAmt = 0;
					for(Literal lit: node.getStateLits()) {
						if(lit.equals(new Has(peasantIds.get(0), ResourceType.GOLD, peasant.getCargoAmount()))) {
							toRemove.add(lit);
							goldAmt = ((Has)lit).getAmount();
							break;
						}
					}
					literals.removeAll(toRemove);
					
					//add list (nothing in this case)
					for(Literal lit: literals) {
						if(lit.getClass().toString().equals("class Has")
								&& ((Has)lit).getHolderID() == townhallIds.get(0)
								&& ((Has)lit).getToHold() == ResourceType.GOLD) {
							((Has)lit).setAmount(((Has)lit).getAmount() + goldAmt); //alter how much the townhall has
						}
					}
					
					try {
						nextState = node.getState().getStateCreator().createState();
					} catch (IOException e) {
						e.printStackTrace();
					}
					
					nextState.addResourceAmount(0, ResourceType.GOLD, peasant.getCargoAmount());
					nextState.getUnit(peasantIds.get(0)).setCargo(null, 0);
					//TODO get rid of 200 stuff
					if(peasant.getCargoAmount() + currentGold < desiredGold) {
						Point loc = node.getUnitLoc(peasantIds.get(0));
						closestResourceID = getClosestGoldID(loc, node.getState());
						gold = node.getState().getResourceNode(closestResourceID);
						
						estimatedCost = calculateHeuristicDistance(node, true, node.getUnitLoc(peasantIds.get(0)), 
								node.getUnitLoc(townhallIds.get(0)), neededWood+neededGold);
						
						goal.x = gold.getXPosition();
						goal.y = gold.getYPosition();
					} else if(currentWood < desiredWood) {
						Point loc = node.getUnitLoc(peasantIds.get(0));
						closestResourceID = getClosestWoodID(loc, node.getState());
						wood = node.getState().getResourceNode(closestResourceID);
						
						estimatedCost = calculateHeuristicDistance(node, true, node.getUnitLoc(peasantIds.get(0)), 
								node.getUnitLoc(townhallIds.get(0)), neededWood+neededGold);
						
						goal.x = wood.getXPosition();
						goal.y = wood.getYPosition();
					} else {
						estimatedCost = 0;
					}
				}
				
				Node n = new Node(nextState.getView(0), node, deposit, literals, 
						node.getCostToNode() + 1, goal, estimatedCost);
				
				//TODO check that this contains method is executing properly (Node.equal()?)
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
				
			
			
			//Gather Gold/Wood
			if(needWood && !hasResource) {
				int woodID = getClosestWoodID(node.getUnitLoc(peasantIds.get(0)), node.getState());
				
				if(areAdjacent(node, peasantIds.get(0), woodID)) {
					try {
						nextState = node.getState().getStateCreator().createState();
					} catch (IOException e) {
						e.printStackTrace();
					}
					
					if(nextState.getResource(woodID).getAmountRemaining() >= GATHER_AMOUNT) {
						nextState.getUnit(peasantIds.get(0)).setCargo(ResourceType.WOOD, GATHER_AMOUNT);
					} else {
						nextState.getUnit(peasantIds.get(0)).setCargo(ResourceType.WOOD, nextState.getResource(woodID).getAmountRemaining());
					}
					
					literals = new ArrayList<Literal>();
					literals.addAll(node.getStateLits());
					
					//remove list
					if(node.getState().getResourceNode(woodID).getAmountRemaining() <= GATHER_AMOUNT) {
						ResourceView woodNode = node.getState().getResourceNode(woodID);
						Point woodLoc = new Point(woodNode.getXPosition(), woodNode.getYPosition());
						literals.remove(new At(woodID, woodLoc));
					}
					
					//add list
					literals.add(new Has(peasantIds.get(0), ResourceType.WOOD, GATHER_AMOUNT));
					
					Direction dir = getDirectionBetween(node.getUnitLoc(peasantIds.get(0)), currentState.getResourceNode(woodID));
					Gather gather = new Gather(GATHER_AMOUNT, dir, ResourceType.WOOD);
					
					Point goal = new Point();
					goal.x = townhall.getXPosition();
					goal.y = townhall.getYPosition();
					
					int estimatedCost = calculateHeuristicDistance(node, false, node.getUnitLoc(peasantIds.get(0)), 
							node.getUnitLoc(townhallIds.get(0)), neededWood+neededGold);
					
					Node n = new Node(nextState.getView(0), node, gather, literals, 
							node.getCostToNode() + 1, goal, estimatedCost);
					
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
			} else if(needGold && !hasResource) {
				int goldID = getClosestGoldID(node.getUnitLoc(peasantIds.get(0)), node.getState());
				if(areAdjacent(node, peasantIds.get(0), goldID)) {
					try {
						nextState = node.getState().getStateCreator().createState();
					} catch (IOException e) {
						e.printStackTrace();
					}
					
					if(nextState.getResource(goldID).getAmountRemaining() >= GATHER_AMOUNT) {
						nextState.getUnit(peasantIds.get(0)).setCargo(ResourceType.GOLD, GATHER_AMOUNT);
					} else {
						nextState.getUnit(peasantIds.get(0)).setCargo(ResourceType.GOLD, nextState.getResource(goldID).getAmountRemaining());
					}
					
					literals = new ArrayList<Literal>();
					literals.addAll(node.getStateLits());

					//remove list
					if(currentState.getResourceNode(goldID).getAmountRemaining() <= GATHER_AMOUNT) {
						ResourceView goldNode = currentState.getResourceNode(goldID);
						Point goldLoc = new Point(goldNode.getXPosition(), goldNode.getYPosition());
						literals.remove(new At(goldID, goldLoc));
					}
					
					//add list
					literals.add(new Has(peasantIds.get(0), ResourceType.GOLD, GATHER_AMOUNT));
					
					Direction dir = getDirectionBetween(node.getUnitLoc(peasantIds.get(0)), currentState.getResourceNode(goldID));
					Gather gather = new Gather(GATHER_AMOUNT, dir, ResourceType.GOLD);
					
					Point goal = new Point();
					goal.x = townhall.getXPosition();
					goal.y = townhall.getYPosition();
					
					int estimatedCost = calculateHeuristicDistance(node, false, node.getUnitLoc(peasantIds.get(0)), 
							node.getUnitLoc(townhallIds.get(0)), neededWood+neededGold);
					
					Node n = new Node(nextState.getView(0), node, gather, literals, 
							node.getCostToNode() + 1, goal, estimatedCost);
					
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
			
			//move west
			try {
				nextState = node.getState().getStateCreator().createState(); 
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.WEST);
			
			int nextX = node.getUnitLoc(peasantIds.get(0)).x - 1;
			int nextY = node.getUnitLoc(peasantIds.get(0)).y;
			
			int estimatedCost = calculateHeuristicDistance(node, true,
					new Point(nextX, nextY), node.getUnitLoc(townhallIds.get(0)), neededWood+neededGold);
			
			Move move = new Move(Direction.WEST);
			
			if(node.getState().inBounds(nextX, nextY) 
					&& !node.getState().isResourceAt(nextX, nextY)
					&& !node.getState().isUnitAt(nextX, nextY)) {
				literals = new ArrayList<Literal>();
				literals.addAll(node.getStateLits());
				
				//generate remove list
				ArrayList<Literal> remove = new ArrayList<Literal>();
				for(Literal lit : node.getStateLits()) {
					if(lit.getClass().toString().equals("class At")
							&& ((At)lit).getObjectID() == peasantIds.get(0)) {
						remove.add(lit);
					}
				}
				literals.removeAll(remove);
				
				//generate add list
				literals.add(new At(peasantIds.get(0), new Point(node.getUnitLoc(peasantIds.get(0)).x - 1, node.getUnitLoc(peasantIds.get(0)).y)));
				
				Node n = new Node(nextState.getView(0), node, move, literals, 
						node.getCostToNode() + 1, node.getGoal(), estimatedCost);
				
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

			//move north
			try {
				nextState = node.getState().getStateCreator().createState();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.NORTH);

			nextX = node.getUnitLoc(peasantIds.get(0)).x;
			nextY = node.getUnitLoc(peasantIds.get(0)).y - 1;
			
			estimatedCost = calculateHeuristicDistance(node, true,
					new Point(nextX, nextY), node.getUnitLoc(townhallIds.get(0)), neededWood+neededGold);
			
			move = new Move(Direction.NORTH);

			if(node.getState().inBounds(nextX, nextY)
						&& !node.getState().isResourceAt(nextX, nextY)
						&& !node.getState().isUnitAt(nextX, nextY)) {
				literals = new ArrayList<Literal>();
				literals.addAll(node.getStateLits());
				
				//generate remove list
				ArrayList<Literal> remove = new ArrayList<Literal>();
				for(Literal lit : node.getStateLits()) {
					if(lit.getClass().toString().equals("class At")
							&& ((At)lit).getObjectID() == 1) {
						remove.add(lit);
					}
				}
				literals.removeAll(remove);
			
				//generate add list
				literals.add(new At(peasantIds.get(0), new Point(node.getUnitLoc(peasantIds.get(0)).x, node.getUnitLoc(peasantIds.get(0)).y - 1)));

				Node n = new Node(nextState.getView(0), node, move, literals, 
						node.getCostToNode() + 1, node.getGoal(), estimatedCost);
				
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
			
			//move east
			try {
				nextState = node.getState().getStateCreator().createState();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.EAST);
			
			nextX = node.getUnitLoc(peasantIds.get(0)).x + 1;
			nextY = node.getUnitLoc(peasantIds.get(0)).y;

			estimatedCost = calculateHeuristicDistance(node, true,
					new Point(nextX, nextY), node.getUnitLoc(townhallIds.get(0)), neededWood+neededGold);
			
			move = new Move(Direction.EAST);

			if(node.getState().inBounds(nextX, nextY)
					&& !node.getState().isResourceAt(nextX, nextY)
					&& !node.getState().isUnitAt(nextX, nextY)) {
				literals = new ArrayList<Literal>();
				literals.addAll(node.getStateLits());
				
				//generate remove list
				ArrayList<Literal> remove = new ArrayList<Literal>();
				for(Literal lit : node.getStateLits()) {
					if(lit.getClass().toString().equals("class At")
							&& ((At)lit).getObjectID() == 1) {
						remove.add(lit);
					}
				}
				literals.removeAll(remove);
			
				//generate add list
				literals.add(new At(peasantIds.get(0), new Point(node.getUnitLoc(peasantIds.get(0)).x + 1, node.getUnitLoc(peasantIds.get(0)).y)));

				Node n = new Node(nextState.getView(0), node, move, literals, 
						node.getCostToNode() + 1, node.getGoal(), estimatedCost);
				
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
			
			//move south
			try {
				nextState = node.getState().getStateCreator().createState();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.SOUTH);
			
			nextX = node.getUnitLoc(peasantIds.get(0)).x;
			nextY = node.getUnitLoc(peasantIds.get(0)).y + 1;

			estimatedCost = calculateHeuristicDistance(node, true,
					new Point(nextX, nextY), node.getUnitLoc(townhallIds.get(0)), neededWood+neededGold);
			
			move = new Move(Direction.SOUTH);

			if(node.getState().inBounds(nextX, nextY)
					&& !node.getState().isResourceAt(nextX, nextY)
					&& !node.getState().isUnitAt(nextX, nextY)) {
				literals = new ArrayList<Literal>();
				literals.addAll(node.getStateLits());

				//generate remove list
				ArrayList<Literal> remove = new ArrayList<Literal>();
				for(Literal lit : node.getStateLits()) {
					if(lit.getClass().toString().equals("class At")
							&& ((At)lit).getObjectID() == 1) {
						remove.add(lit);
					}
				}
				literals.removeAll(remove);
			
				//generate add list
				literals.add(new At(peasantIds.get(0), new Point(node.getUnitLoc(peasantIds.get(0)).x, node.getUnitLoc(peasantIds.get(0)).y + 1)));
				
				Node n = new Node(nextState.getView(0), node, move, literals, 
						node.getCostToNode() + 1, node.getGoal(), estimatedCost);
				
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
			Action b = solution.poll().getToState().act(peasantIds.get(0));
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
	
	public int calculateHeuristicDistance(Node node, boolean hasResource, Point peasantLoc, Point townhallLoc, int neededResources) {
		//relaxation: only collect from the closest resource
		//and use Chevyshev distance, rather than true distance
		Point closestLoc = getClosestResourceLoc(node, peasantLoc);
		int dist = 0;
		if(neededResources > 0) {			
			if(hasResource) { //heading toward townhall
				dist += chebyshevDistance(peasantLoc, townhallLoc);
				neededResources -= GATHER_AMOUNT;
			} else { //heading toward resource
				dist += chebyshevDistance(peasantLoc, closestLoc) + chebyshevDistance(closestLoc, townhallLoc);
				neededResources -= GATHER_AMOUNT;
			}
			while(neededResources > 0) {
				dist += 2 * chebyshevDistance(closestLoc, townhallLoc);
				neededResources -= GATHER_AMOUNT;
			}
		}
		return dist;
	}
	
	public int chebyshevDistance(Point p1, Point p2) {
		return Math.max(Math.abs(p1.x - p2.x), Math.abs(p1.y - p2.y));
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
	
	private Point getClosestResourceLoc(Node node, Point peasantLoc) {
		Point closest = null;
		int minDist = 999;
		for(Literal lit : node.getStateLits()) {
			if(lit.getClass().toString().equals("class At")
					&& ((At)lit).getObjectID() != peasantIds.get(0)
					&& ((At)lit).getObjectID() != townhallIds.get(0)) {
				int xPos = ((At)lit).getPosition().x;
				int yPos = ((At)lit).getPosition().y;
				int xDist = Math.abs(xPos - peasantLoc.x);
				int yDist = Math.abs(yPos - peasantLoc.y);
				if(xDist + yDist < minDist) {
					closest = new Point(xPos, yPos);	
					minDist = xDist + yDist;
				}
			}
		}
		return closest;
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
			if(act.getClass().toString().equals("class Move")) {
				outputPlan.println("Move " + ((Move)act).getDirectionString());
			} else if(act.getClass().toString().equals("class Deposit")) {
				outputPlan.println("Deposit " + ((Deposit)act).getAmount() + ((Deposit)act).getTypeString() + " at the Townhall");
			} else if(act.getClass().toString().equals("class Gather")) {
				outputPlan.println("Gather " + ((Gather)act).getAmount() + ((Gather)act).getTypeString());
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
