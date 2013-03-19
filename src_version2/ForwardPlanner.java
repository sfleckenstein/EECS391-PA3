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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
	public static final int GATHER_AMOUNT = 100;	//TODO find the correct amount

	private int step;
	private int currentGoal;
	
	private List<Integer> peasantIds = new ArrayList<Integer>();
	private List<Integer> townhallIds = new ArrayList<Integer>();
	
	private LookupPriorityQueue<Node> open = new LookupPriorityQueue<Node>();
	private List<Node> closed = new ArrayList<Node>();
	private LinkedList<Node> solution = new LinkedList<Node>();
	
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
			//TODO Has(townhall, 200 gold/wood)
			if(currentWood >= 200 && currentGold >= 200) {
				while(node.getParentNode() != null) {
					solution.addFirst(node);
					node = node.getParentNode();
				}
				break;
			}
			
			closed.add(node);
			
			State nextState = null;
			try {
				nextState = node.getState().getStateCreator().createState();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			currentGold = node.getState().getResourceAmount(0, ResourceType.GOLD);
			currentWood = node.getState().getResourceAmount(0, ResourceType.WOOD);

			peasant = node.getState().getUnit(peasantIds.get(0));
			townhall = node.getState().getUnit(townhallIds.get(0));
			
			ArrayList<Literal> literals = null;
			
			boolean hasResource = false;
			for(Literal lit : node.getStateLits()) {
				if(lit.getClass().toString().equals("class Has")) {
					hasResource = true;
					break;
				}
			}
			
			//TODO write plan to txt file
			
			//Deposit Gold/Wood
			if(hasResource && areAdjacent(node, peasantIds.get(0), townhallIds.get(0))) { //preconditions
				peasant = node.getState().getUnit(peasantIds.get(0));
				literals = new ArrayList<Literal>();
				literals.addAll(node.getStateLits());
				
				Deposit deposit = new Deposit(peasant.getCargoAmount(), getDirectionBetween(peasant, townhall));
				
				Point goal = node.getGoal();
				int estimatedCost = 99999;
				int closestResourceID;
				
				if(peasant.getCargoType().equals(ResourceType.WOOD)) {
					//TODO check if this actually removes the right Has objects
					//generate and remove the remove list
					//TODO only remove one literal Has(1, wood)
					ArrayList<Literal> toRemove = new ArrayList<Literal>();
					for(Literal lit: node.getStateLits()) {
						if(lit.equals(new Has(peasantIds.get(0), ResourceType.WOOD))) {
							toRemove.add(lit);
						}
					}
					literals.removeAll(toRemove);
					
					//add list (nothing in this case)
					
					try {
						nextState = node.getState().getStateCreator().createState();
					} catch (IOException e) {
						e.printStackTrace();
					}
					
					nextState.addResourceAmount(0, ResourceType.WOOD, peasant.getCargoAmount());
					nextState.getUnit(peasantIds.get(0)).setCargo(null, 0);//TODO is null ok here?
					
					if(peasant.getCargoAmount() + currentWood < 200) {
						Point loc = node.getPeasantLoc();
						closestResourceID = getClosestWoodID(loc, node.getState());
						wood = currentState.getResourceNode(closestResourceID);
						
						estimatedCost = calculateHeuristicDistance(node.getPeasantLoc().x, node.getPeasantLoc().y, 
								wood.getXPosition(), wood.getYPosition());
						
						goal.x = wood.getXPosition();
						goal.y = wood.getYPosition();
					} else if(currentGold < 200) {
						Point loc = new Point(peasant.getXPosition(), peasant.getYPosition());
						closestResourceID = getClosestGoldID(loc, node.getState());
						gold = currentState.getResourceNode(closestResourceID);
						
						estimatedCost = calculateHeuristicDistance(node.getPeasantLoc().x, node.getPeasantLoc().y, 
								gold.getXPosition(), gold.getYPosition());
						
						goal.x = gold.getXPosition();
						goal.y = gold.getYPosition();
					} else {
						estimatedCost = 0;
					}
				}
				
				if(peasant.getCargoType().equals(ResourceType.GOLD)) {
					//TODO check if this removes the right Has objects
					//generate and remove the remove list
					//TODO see above
					ArrayList<Literal> toRemove = new ArrayList<Literal>();
					for(Literal lit: node.getStateLits()) {
						if(lit.equals(new Has(peasantIds.get(0), ResourceType.GOLD))) {
							toRemove.add(lit);
						}
					}
					literals.removeAll(toRemove);
					
					//add list (nothing in this case)
					
					try {
						nextState = node.getState().getStateCreator().createState();
					} catch (IOException e) {
						e.printStackTrace();
					}
					
					nextState.addResourceAmount(0, ResourceType.GOLD, peasant.getCargoAmount());
					nextState.getUnit(peasantIds.get(0)).setCargo(null, 0);
					
					if(peasant.getCargoAmount() + currentGold < 200) {
						Point loc = new Point(peasant.getXPosition(), peasant.getYPosition());
						closestResourceID = getClosestGoldID(loc, node.getState());
						gold = node.getState().getResourceNode(closestResourceID);
						
						estimatedCost = calculateHeuristicDistance(node.getPeasantLoc().x, node.getPeasantLoc().y, 
								gold.getXPosition(), gold.getYPosition());
						
						goal.x = gold.getXPosition();
						goal.y = gold.getYPosition();
					} else if(currentWood < 200) {
						Point loc = new Point(peasant.getXPosition(), peasant.getYPosition());
						closestResourceID = getClosestWoodID(loc, node.getState());
						wood = node.getState().getResourceNode(closestResourceID);
						
						estimatedCost = calculateHeuristicDistance(node.getPeasantLoc().x, node.getPeasantLoc().y, 
								wood.getXPosition(), wood.getYPosition());
						
						goal.x = wood.getXPosition();
						goal.y = wood.getYPosition();
					} else {
						estimatedCost = 0;
					}
				}
				
				Node n = new Node(nextState.getView(0), node, deposit, literals, 
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
			
			hasResource = false;
			for(Literal lit : node.getStateLits()) {
				if(lit.getClass().toString().equals("class Has")) {
					hasResource = true;
					break;
				}
			}
			
			//Gather Gold/Wood
			if(currentWood < 200 && !hasResource) {
				int woodID = getClosestWoodID(node.getPeasantLoc(), node.getState());
				
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
					literals.add(new Has(peasantIds.get(0), ResourceType.WOOD));
					
					Direction dir = getDirectionBetween(node.getPeasantLoc(), currentState.getResourceNode(woodID));
					Gather gather = new Gather(GATHER_AMOUNT, dir);
					
					
					Point goal = new Point();
					goal.x = townhall.getXPosition();
					goal.y = townhall.getYPosition();
					
					int estimatedCost = calculateHeuristicDistance(node.getPeasantLoc().x, node.getPeasantLoc().y, goal.x, goal.y);

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
			} else if(currentGold < 200 && !hasResource) {
				int goldID = getClosestGoldID(node.getPeasantLoc(), node.getState());
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
					literals.add(new Has(peasantIds.get(0), ResourceType.GOLD));
					
					Direction dir = getDirectionBetween(node.getPeasantLoc(), currentState.getResourceNode(goldID));
					Gather gather = new Gather(GATHER_AMOUNT, dir);
					
					Point goal = new Point();
					goal.x = townhall.getXPosition();
					goal.y = townhall.getYPosition();
					
					int estimatedCost = calculateHeuristicDistance(node.getPeasantLoc().x, node.getPeasantLoc().y, goal.x, goal.y);

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
			
			int estimatedCost = calculateHeuristicDistance(peasant.getXPosition() - 1, peasant.getYPosition(), 
					node.getGoal().x, node.getGoal().y);
			Move move = new Move(Direction.WEST);

			int nextX = node.getPeasantLoc().x - 1;
			int nextY = node.getPeasantLoc().y;
			
			if(node.getState().inBounds(nextX, nextY) 
					&& !node.getState().isResourceAt(nextX, nextY)
					&& !node.getState().isUnitAt(nextX, nextY)) {
				literals = new ArrayList<Literal>();
				literals.addAll(node.getStateLits());
				
				//generate remove list
				//TODO remove just one At
				ArrayList<Literal> remove = new ArrayList<Literal>();
				for(Literal lit : node.getStateLits()) {
					if(lit.getClass().toString().equals("class At")
							&& ((At)lit).getObjectID() == peasantIds.get(0)) {
						remove.add(lit);
					}
				}
				literals.removeAll(remove);
				
				//generate add list
				literals.add(new At(peasantIds.get(0), new Point(node.getPeasantLoc().x - 1, node.getPeasantLoc().y)));
				
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
			
			estimatedCost = calculateHeuristicDistance(node.getPeasantLoc().x, node.getPeasantLoc().y - 1, 
					node.getGoal().x, node.getGoal().y);
			move = new Move(Direction.NORTH);
			
			nextX = node.getPeasantLoc().x;
			nextY = node.getPeasantLoc().y - 1;

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
				literals.add(new At(peasantIds.get(0), new Point(node.getPeasantLoc().x, node.getPeasantLoc().y - 1)));

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
			
			estimatedCost = calculateHeuristicDistance(node.getPeasantLoc().x + 1, node.getPeasantLoc().y, 
					node.getGoal().x, node.getGoal().y);
			move = new Move(Direction.EAST);
			
			nextX = node.getPeasantLoc().x + 1;
			nextY = node.getPeasantLoc().y;

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
				literals.add(new At(peasantIds.get(0), new Point(node.getPeasantLoc().x + 1, node.getPeasantLoc().y)));

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
			
			estimatedCost = calculateHeuristicDistance(node.getPeasantLoc().x, node.getPeasantLoc().y + 1, 
					node.getGoal().x, node.getGoal().y);
			move = new Move(Direction.SOUTH);
			
			nextX = node.getPeasantLoc().x;
			nextY = node.getPeasantLoc().y + 1;

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
				literals.add(new At(peasantIds.get(0), new Point(node.getPeasantLoc().x, node.getPeasantLoc().y + 1)));
				
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
	
	//TODO fix this
	public int calculateHeuristicDistance (StateView state, int peasantX, int peasantY, int goalX, int goalY) {
		//relaxation: can carry as many resources as you want at a time
		//and use Chevyshev distance, rather than true distance
		int dist = 0;
		int totalWood = 0;
		int totalGold = 0;
		for(int resourceID: state.getAllResourceIds()) {
			Point peasant = new Point(peasantX, peasantY);
			if(totalWood < 200) {
				int closestWoodID = getClosestWoodID(peasant, state);
				totalWood += state.getResourceNode(closestWoodID).getAmountRemaining();
				
				int woodX = state.getResourceNode(closestWoodID).getXPosition();
				int woodY = state.getResourceNode(closestWoodID).getYPosition();
				dist += chebyshevDistance(peasant, new Point(woodX, woodY));
			}
			
			if(totalGold < 200) {	//TODO this will need to change between 200 and 1000 when the goal changes
				int closestGoldID = getClosestGoldID(new Point(peasantX, peasantY),	state);
				totalGold += state.getResourceNode(closestGoldID).getAmountRemaining();
				
				int goldX = state.getResourceNode(closestGoldID).getXPosition();
				int goldY = state.getResourceNode(closestGoldID).getYPosition();
				dist += chebyshevDistance(peasant, new Point(goldX, goldY));
			}
		}
		
		return dist;
//		return Math.max(Math.abs(peasantX - goalX), Math.abs(peasantY - goalY));
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
		for(Integer woodID : resourceIds) {
			ResourceView resource = currentState.getResourceNode(woodID);
			int xDist = Math.abs(resource.getXPosition() - unit.x);
			int yDist = Math.abs(resource.getYPosition() - unit.y);
			if(xDist + yDist < minDist) {
				minDist = xDist + yDist;
				closestGoldID = woodID;
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

	@Override
	public void savePlayerData(OutputStream os) {
		//this agent lacks learning and so has nothing to persist.
	}
	
	@Override
	public void loadPlayerData(InputStream is) {
		//this agent lacks learning and so has nothing to persist.
	}
}
