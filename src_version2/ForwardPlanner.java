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
		//TODO 98 could be the proble line
		Point peasantLoc = new Point(peasant.getXPosition(), peasant.getYPosition());
		At at = new At(peasantIds.get(0), peasantLoc);
		stateLits.add(at);
		ResourceView wood = currentState.getResourceNode(getClosestWoodID(peasant));
		ResourceView gold = currentState.getResourceNode(getClosestGoldID(peasant));
		
//		open.add(new Node(newState, null, null, stateLits, 0, 
//				new Point(wood.getXPosition(), wood.getYPosition()), 99999, peasantLoc));
		
		open.add(new Node(null, null, stateLits, 0, 
				new Point(wood.getXPosition(), wood.getYPosition()), 99999, peasantLoc));
		
		while(true) {
			Node node = open.poll();

			if(node == null) {
				terminalStep(newState, stateHistory);
				break;
			}
			
			//Goal found
			if(currentWood >= 200 && currentGold >= 200) {
				while(node.getParentNode() != null) {
					solution.addFirst(node);
					node = node.getParentNode();
				}
				break;
			}
			
			closed.add(node);
			
//			State nextState = null;
//			try {
//				nextState = node.getCopy().getState().getStateCreator().createState();
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
			
			peasant = currentState.getUnit(peasantIds.get(0));
			UnitView townhall = currentState.getUnit(townhallIds.get(0));

			ArrayList<Literal> literals = node.getStateLits();
			
			//actions to potentially add
			
			//Deposit Good/Wood
			if((node.containsLit(new Has(peasantIds.get(0), ResourceType.GOLD))
					|| node.containsLit(new Has(peasantIds.get(0), ResourceType.WOOD)))
					&& areAdjacent(node, peasantIds.get(0), townhallIds.get(0))) { //preconditions
				
				//generate and remove the remove list
				ArrayList<Literal> toRemove = new ArrayList<Literal>();
				for(Literal lit: node.getStateLits()) {
					if(lit.equals(new Has(peasantIds.get(0), ResourceType.GOLD))) {
						toRemove.add(lit);
					}
				}
				literals.removeAll(toRemove);
				
				//add list (nothing in this case)

				Deposit deposit = new Deposit(peasant.getCargoAmount(), getDirectionBetween(peasant, townhall));
				
				Point goal = node.getGoal();
				int estimatedCost = 99999;
				int closestResourceID;
				
				if(peasant.getCargoType().equals(ResourceType.WOOD)) {
					
//					try {
//						nextState = node.getCopy().getState().getStateCreator().createState();
//					} catch (IOException e) {
//						e.printStackTrace();
//					}
					
					if(peasant.getCargoAmount() + currentWood < 200) {
						closestResourceID = getClosestWoodID(peasant);
						wood = currentState.getResourceNode(closestResourceID);
						
						estimatedCost = calculateHeuristicDistance(node.getPeasantLoc().x, node.getPeasantLoc().y, 
								wood.getXPosition(), wood.getYPosition());
						
						goal.x = wood.getXPosition();
						goal.y = wood.getYPosition();
						
						peasantLoc.x = node.getPeasantLoc().x;
						peasantLoc.y = node.getPeasantLoc().y;
					} else if(currentGold < 200) {
						closestResourceID = getClosestGoldID(peasant);
						gold = currentState.getResourceNode(closestResourceID);
						
						estimatedCost = calculateHeuristicDistance(node.getPeasantLoc().x, node.getPeasantLoc().y, 
								gold.getXPosition(), gold.getYPosition());
						
						goal.x = gold.getXPosition();
						goal.y = gold.getYPosition();
						
						peasantLoc.x = node.getPeasantLoc().x;
						peasantLoc.y = node.getPeasantLoc().y;
					} else {
						estimatedCost = 0;
					}
				}
				
				if(peasant.getCargoType().equals(ResourceType.GOLD)) {
					
//					try {
//						nextState = node.getCopy().getState().getStateCreator().createState();
//					} catch (IOException e) {
//						e.printStackTrace();
//					}
					
					if(peasant.getCargoAmount() + currentGold < 200) {
						closestResourceID = getClosestGoldID(peasant);
						gold = currentState.getResourceNode(closestResourceID);
						
						estimatedCost = calculateHeuristicDistance(node.getPeasantLoc().x, node.getPeasantLoc().y, 
								gold.getXPosition(), gold.getYPosition());
						
						goal.x = gold.getXPosition();
						goal.y = gold.getYPosition();
						
						peasantLoc.x = node.getPeasantLoc().x;
						peasantLoc.y = node.getPeasantLoc().y;
					} else if(currentWood < 200) {
						closestResourceID = getClosestWoodID(peasant);
						wood = currentState.getResourceNode(closestResourceID);
						
						estimatedCost = calculateHeuristicDistance(node.getPeasantLoc().x, node.getPeasantLoc().y, 
								wood.getXPosition(), wood.getYPosition());
						
						goal.x = wood.getXPosition();
						goal.y = wood.getYPosition();
						
						peasantLoc.x = node.getPeasantLoc().x;
						peasantLoc.y = node.getPeasantLoc().y;
					} else {
						estimatedCost = 0;
					}
				}
				
//				Node n = new Node(nextState.getView(0), node, deposit, node.getStateLits(), 
//						node.getCostToNode() + 1, goal, estimatedCost, peasantLoc);
				
				Node n = new Node(node, deposit, node.getStateLits(), 
						node.getCostToNode() + 1, goal, estimatedCost, peasantLoc);
				
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
			if(currentWood < 200) {
				int woodID = getClosestWoodID(peasant);
				if(areAdjacent(node, peasantIds.get(0), woodID)) {
//					try {
//						nextState = node.getCopy().getState().getStateCreator().createState();
//					} catch (IOException e) {
//						e.printStackTrace();
//					}
					
					literals = node.getStateLits();

					//remove list (nothing in this case)
					
					//add list
					literals.add(new Has(peasantIds.get(0), ResourceType.WOOD));
					
					//TODO check if peasant is in the right location. I bet it's not
					Direction dir = getDirectionBetween(peasant, currentState.getResourceNode(woodID));
					Gather gather = new Gather(GATHER_AMOUNT, dir);
					
					int estimatedCost = calculateHeuristicDistance(node.getPeasantLoc().x, node.getPeasantLoc().y, 
							townhall.getXPosition(), townhall.getYPosition());
					
					Point goal = new Point();
					goal.x = townhall.getXPosition();
					goal.y = townhall.getYPosition();
					
					peasantLoc.x = node.getPeasantLoc().x;
					peasantLoc.y = node.getPeasantLoc().y;
					
//					Node n = new Node(nextState.getView(0), node, gather, node.getStateLits(), 
//							node.getCostToNode() + 1, node.getGoal(), estimatedCost, peasantLoc);
					Node n = new Node(node, gather, node.getStateLits(), 
							node.getCostToNode() + 1, node.getGoal(), estimatedCost, peasantLoc);
					
					
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
			} else if(currentGold < 200) {
				int goldID = getClosestGoldID(peasant);
				if(areAdjacent(node, peasantIds.get(0), goldID)) {
//					try {
//						nextState = node.getCopy().getState().getStateCreator().createState();
//					} catch (IOException e) {
//						e.printStackTrace();
//					}
					
					literals = node.getStateLits();

					//remove list (nothing in this case)
					
					//add list
					literals.add(new Has(peasantIds.get(0), ResourceType.GOLD));
					
					Direction dir = getDirectionBetween(peasant, currentState.getResourceNode(goldID));
					Gather gather = new Gather(GATHER_AMOUNT, dir);
					
					int estimatedCost = calculateHeuristicDistance(node.getPeasantLoc().x, node.getPeasantLoc().y, 
							townhall.getXPosition(), townhall.getYPosition());
					
					Point goal = new Point();
					goal.x = townhall.getXPosition();
					goal.y = townhall.getYPosition();
					
					peasantLoc.x = node.getPeasantLoc().x;
					peasantLoc.y = node.getPeasantLoc().y;
					
//					Node n = new Node(nextState.getView(0), node, gather, node.getStateLits(), 
//							node.getCostToNode() + 1, node.getGoal(), estimatedCost, peasantLoc);
					Node n = new Node(node, gather, node.getStateLits(), 
							node.getCostToNode() + 1, node.getGoal(), estimatedCost, peasantLoc);
					
					
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
//			try {
//				nextState = node.getCopy().getState().getStateCreator().createState();
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
			
//			nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.WEST);
			
			int estimatedCost = calculateHeuristicDistance(peasant.getXPosition() - 1, peasant.getYPosition(), 
					node.getGoal().x, node.getGoal().y);
			Move move = new Move(Direction.WEST);

//			if(node.getState().inBounds(node.getPeasantLoc().x - 1, node.getPeasantLoc().y)) {
			if(currentState.inBounds(node.getPeasantLoc().x - 1, node.getPeasantLoc().y)) {
				//TODO check for collisions
			
				literals = node.getStateLits();
				
				//generate remove list
				literals.remove(new At(peasantIds.get(0), new Point(node.getPeasantLoc().x, node.getPeasantLoc().y)));
				
				//generate add list
				literals.add(new At(peasantIds.get(0), new Point(node.getPeasantLoc().x - 1, node.getPeasantLoc().y)));
				
				//TODO after the next line is executed, node.getPeasantLoc().x is decremented
				peasantLoc.x = node.getPeasantLoc().x - 1;
				peasantLoc.y = node.getPeasantLoc().y;
				
//				Node n = new Node(nextState.getView(0), node, move, node.getStateLits(), 
//						node.getCostToNode() + 1, node.getGoal(), estimatedCost, peasantLoc);
				Node n = new Node(node, move, node.getStateLits(), 
						node.getCostToNode() + 1, node.getGoal(), estimatedCost, peasantLoc);
				
				
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
//			try {
//				nextState = node.getCopy().getState().getStateCreator().createState();
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
			
//			nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.NORTH);
			
			estimatedCost = calculateHeuristicDistance(node.getPeasantLoc().x, node.getPeasantLoc().y - 1, 
					node.getGoal().x, node.getGoal().y);
			move = new Move(Direction.NORTH);

//			if(node.getState().inBounds(node.getPeasantLoc().x, node.getPeasantLoc().y - 1)) {
			if(currentState.inBounds(node.getPeasantLoc().x, node.getPeasantLoc().y - 1)) {
				literals = node.getStateLits();
				
				//generate remove list
				literals.remove(new At(peasantIds.get(0), new Point(node.getPeasantLoc().x, node.getPeasantLoc().y)));
			
				//generate add list
				literals.add(new At(peasantIds.get(0), new Point(node.getPeasantLoc().x, node.getPeasantLoc().y - 1)));

				peasantLoc.x = node.getPeasantLoc().x;
				peasantLoc.y = node.getPeasantLoc().y - 1;
				
//				Node n = new Node(nextState.getView(0), node, move, node.getStateLits(), 
//						node.getCostToNode() + 1, node.getGoal(), estimatedCost, peasantLoc);
				Node n = new Node(node, move, node.getStateLits(), 
						node.getCostToNode() + 1, node.getGoal(), estimatedCost, peasantLoc);
				
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
//			try {
//				nextState = node.getCopy().getState().getStateCreator().createState();
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
			
//			nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.EAST);
			
			estimatedCost = calculateHeuristicDistance(node.getPeasantLoc().x + 1, node.getPeasantLoc().y, 
					node.getGoal().x, node.getGoal().y);
			move = new Move(Direction.EAST);

//			if(node.getState().inBounds(node.getPeasantLoc().x + 1, node.getPeasantLoc().y)) {
			if(currentState.inBounds(node.getPeasantLoc().x + 1, node.getPeasantLoc().y)) {
				literals = node.getStateLits();
				
				//generate remove list
				literals.remove(new At(peasantIds.get(0), new Point(node.getPeasantLoc().x, node.getPeasantLoc().y)));
			
				//generate add list
				literals.add(new At(peasantIds.get(0), new Point(node.getPeasantLoc().x + 1, node.getPeasantLoc().y)));

				peasantLoc.x = peasant.getXPosition() + 1;
				peasantLoc.y = peasant.getYPosition();
				
//				Node n = new Node(nextState.getView(0), node, move, node.getStateLits(), 
//						node.getCostToNode() + 1, node.getGoal(), estimatedCost, peasantLoc);
				Node n = new Node(node, move, node.getStateLits(), 
						node.getCostToNode() + 1, node.getGoal(), estimatedCost, peasantLoc);
				
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
//			try {
//				nextState = node.getCopy().getState().getStateCreator().createState();
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//			
//			nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.SOUTH);
			
			estimatedCost = calculateHeuristicDistance(node.getPeasantLoc().x, node.getPeasantLoc().y + 1, 
					node.getGoal().x, node.getGoal().y);
			move = new Move(Direction.SOUTH);

//			if(node.getState().inBounds(node.getPeasantLoc().x, node.getPeasantLoc().y + 1)) {
			if(currentState.inBounds(node.getPeasantLoc().x, node.getPeasantLoc().y + 1)) {
				literals = node.getStateLits();

				//generate remove list
				literals.remove(new At(peasantIds.get(0), new Point(node.getPeasantLoc().x, node.getPeasantLoc().y)));
			
				//generate add list
				literals.add(new At(peasantIds.get(0), new Point(node.getPeasantLoc().x, node.getPeasantLoc().y + 1)));

				peasantLoc.x = peasant.getXPosition();
				peasantLoc.y = peasant.getYPosition() ;
				
//				Node n = new Node(nextState.getView(0), node, move, node.getStateLits(), 
//						node.getCostToNode() + 1, node.getGoal(), estimatedCost, peasantLoc);
				Node n = new Node(node, move, node.getStateLits(), 
						node.getCostToNode() + 1, node.getGoal(), estimatedCost, peasantLoc);
				
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
	
	public int calculateHeuristicDistance (int peasantX, int peasantY, int goalX, int goalY) {
		return Math.max(Math.abs(peasantX - goalX), Math.abs(peasantY - goalY));
	}

	private int getClosestWoodID(UnitView unit) {
		List<Integer> resourceIds = currentState.getResourceNodeIds(Type.TREE);
		int closestWoodID = resourceIds.get(0);
		int minDist = 999;
		for(Integer woodID : resourceIds) {
			ResourceView resource = currentState.getResourceNode(woodID);
			int xDist = Math.abs(resource.getXPosition() - unit.getXPosition());
			int yDist = Math.abs(resource.getYPosition() - unit.getYPosition());
			if(xDist + yDist < minDist) {
				minDist = xDist + yDist;
				closestWoodID = woodID;
			}
		}
		return closestWoodID;
	}
	
	private int getClosestGoldID(UnitView unit) {
		List<Integer> resourceIds = currentState.getResourceNodeIds(Type.GOLD_MINE);
		int closestGoldID = resourceIds.get(0);
		int minDist = 999;
		for(Integer woodID : resourceIds) {
			ResourceView resource = currentState.getResourceNode(woodID);
			int xDist = Math.abs(resource.getXPosition() - unit.getXPosition());
			int yDist = Math.abs(resource.getYPosition() - unit.getYPosition());
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
			return Direction.WEST;
		} else if(unit.getXPosition() < townHall.getXPosition()
				&& unit.getYPosition() < townHall.getYPosition()) {
			return Direction.NORTHWEST;
		} else if(unit.getXPosition() == townHall.getXPosition()
				&& unit.getYPosition() < townHall.getYPosition()) {
			return Direction.NORTH;
		} else if(unit.getXPosition() > townHall.getXPosition()
				&& unit.getYPosition() < townHall.getYPosition()) {
			return Direction.NORTHEAST;
		} else if(unit.getXPosition() > townHall.getXPosition()
				&& unit.getYPosition() == townHall.getYPosition()) {
			return Direction.EAST;
		} else if(unit.getXPosition() > townHall.getXPosition()
				&& unit.getYPosition() > townHall.getYPosition()) {
			return Direction.SOUTHEAST;
		} else if(unit.getXPosition() == townHall.getXPosition()
				&& unit.getYPosition() > townHall.getYPosition()) {
			return Direction.SOUTH;
		} else {
			return Direction.SOUTHWEST;
		}
	}
	
	private Direction getDirectionBetween(UnitView unit, ResourceView resource) {
		if(unit.getXPosition() < resource.getXPosition()
				&& unit.getYPosition() == resource.getYPosition()) {
			return Direction.WEST;
		} else if(unit.getXPosition() < resource.getXPosition()
				&& unit.getYPosition() < resource.getYPosition()) {
			return Direction.NORTHWEST;
		} else if(unit.getXPosition() == resource.getXPosition()
				&& unit.getYPosition() < resource.getYPosition()) {
			return Direction.NORTH;
		} else if(unit.getXPosition() > resource.getXPosition()
				&& unit.getYPosition() < resource.getYPosition()) {
			return Direction.NORTHEAST;
		} else if(unit.getXPosition() > resource.getXPosition()
				&& unit.getYPosition() == resource.getYPosition()) {
			return Direction.EAST;
		} else if(unit.getXPosition() > resource.getXPosition()
				&& unit.getYPosition() > resource.getYPosition()) {
			return Direction.SOUTHEAST;
		} else if(unit.getXPosition() == resource.getXPosition()
				&& unit.getYPosition() > resource.getYPosition()) {
			return Direction.SOUTH;
		} else {
			return Direction.SOUTHWEST;
		}
	}
	
	/**
	 * 
	 * @param unit - The ID of the unit you are concerned with.
	 * @param resource - The ID of the resource you are concerned with.
	 * @return True if the specified unit is adjacent to the specified resource.
	 */
	public boolean areAdjacent(Node node, int objOneId, int objTwoId) {
		for(Literal lit1 : node.getStateLits()) {
			for(Literal lit2 : node.getStateLits()) {
				if(lit1.getClass().toString().equals("At")
						&& lit2.getClass().toString().equals("At")) {
					if(((At)lit1).getObjectID() == objOneId
							&& ((At)lit2).getObjectID() == objTwoId) {
						
						Point p1 = ((At)lit1).getPosition();
						Point p2 = ((At)lit2).getPosition();
						
						if(Math.abs(p1.getX() - p2.getX()) <=1
								&& Math.abs(p1.getY() - p2.getY()) <=1 ) {
							return true;
						}
					}
				} else {
					continue;
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
