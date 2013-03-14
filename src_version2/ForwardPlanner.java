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
	private static final int GATHER_AMOUNT = 100;	//TODO find the correct amount

	private int step;
	private int currentGoal;
	
	private List<Integer> peasantIds = new ArrayList<Integer>();
	private List<Integer> townhallIds = new ArrayList<Integer>();
	
	private LookupPriorityQueue<Node> open = new LookupPriorityQueue<Node>();
	private List<Node> closed = new ArrayList<Node>();
	private LinkedList<Node> solution = new LinkedList<Node>();
	private int estimatedCostToGoal;
	
	private StateView currentState;

	public ForwardPlanner(int playernum, String[] arguments) {
		super(playernum);
		currentGoal = 0;
		estimatedCostToGoal = 99999;
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
		At at = new At(peasantIds.get(0), new Point(peasant.getXPosition(), peasant.getYPosition()));
		stateLits.add(at);
		
		
		ResourceView wood = currentState.getResourceNode(getClosestWoodID(peasant));
		
		open.add(new Node(newState, null, null, stateLits, 0, 
				new Point(wood.getXPosition(), wood.getYPosition()), 99999));
		
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
			
			State nextState = null;
			try {
				nextState = node.getState().getStateCreator().createState();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			peasant = currentState.getUnit(peasantIds.get(0));
			UnitView townhall = currentState.getUnit(townhallIds.get(0));

			ArrayList<Literal> literals = node.getStateLits();
			
			//actions to potentially add
			
			//Deposit Good/Wood
			if(node.containsLit(new Has(peasantIds.get(0), ResourceType.GOLD))
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
				
				int estimatedCost;
				int closestResourceID;
				
				if(currentWood < 200) {
					closestResourceID = getClosestWoodID(peasant);
					wood = currentState.getResourceNode(closestResourceID);
					
					estimatedCost = calculateHeuristicDistance(peasant.getXPosition(), peasant.getYPosition(), 
							wood.getXPosition(), wood.getYPosition());
					DepositWood deposit = new DepositWood(peasant.getCargoAmount(), getDirectionBetween(peasant, townhall));
					
					Point goal = new Point();
					goal.x = wood.getXPosition();
					goal.y = wood.getYPosition();
					
					Node n = new Node(nextState.getView(0), node, deposit, node.getStateLits(), 
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
				} else if(currentGold + peasant.getCargoAmount() < 200){
					closestResourceID = getClosestGoldID(peasant);
					ResourceView gold = currentState.getResourceNode(closestResourceID);
					
					estimatedCost = calculateHeuristicDistance(peasant.getXPosition(), peasant.getYPosition(), 
							gold.getXPosition(), gold.getYPosition());
					DepositGold deposit = new DepositGold(peasant.getCargoAmount(), getDirectionBetween(peasant, townhall));
					
					Point goal = new Point();
					goal.x = gold.getXPosition();
					goal.y = gold.getYPosition();
					
					Node n = new Node(nextState.getView(0), node, deposit, node.getStateLits(), 
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
				} else {
					//TODO figure out what to do here
				}
			}
			
			//Gather Gold/Wood
			if(currentWood < 200) {
				int woodID = getClosestWoodID(peasant);
				if(areAdjacent(node, peasantIds.get(0), woodID)) {

					//remove list (nothing in this case)
					
					//add list
					literals.add(new Has(peasantIds.get(0), ResourceType.WOOD));
					
					Direction dir = getDirectionBetween(peasant, currentState.getResourceNode(woodID));
					GatherWood gather = new GatherWood(GATHER_AMOUNT, dir);
					
					int estimatedCost = calculateHeuristicDistance(peasant.getXPosition(), peasant.getYPosition(), 
							townhall.getXPosition(), townhall.getYPosition());
					
					Point goal = new Point();
					goal.x = townhall.getXPosition();
					goal.y = townhall.getYPosition();
					
					Node n = new Node(nextState.getView(0), node, gather, node.getStateLits(), 
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
			} else if(currentGold < 200) {
				int goldID = getClosestGoldID(peasant);
				if(areAdjacent(node, peasantIds.get(0), goldID)) {

					//remove list (nothing in this case)
					
					//add list
					literals.add(new Has(peasantIds.get(0), ResourceType.GOLD));
					
					Direction dir = getDirectionBetween(peasant, currentState.getResourceNode(goldID));
					GatherGold gather = new GatherGold(GATHER_AMOUNT, dir);
					
					int estimatedCost = calculateHeuristicDistance(peasant.getXPosition(), peasant.getYPosition(), 
							townhall.getXPosition(), townhall.getYPosition());
					
					Point goal = new Point();
					goal.x = townhall.getXPosition();
					goal.y = townhall.getYPosition();
					
					Node n = new Node(nextState.getView(0), node, gather, node.getStateLits(), 
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
			} else {
				//TODO decide what to do here
			}
			
			//move west
			nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.WEST);
			
			int estimatedCost = calculateHeuristicDistance(peasant.getXPosition() - 1, peasant.getYPosition(), 
					node.getGoal().x, node.getGoal().y);
			Move move = new Move(Direction.WEST);

			if(node.getState().inBounds(peasant.getXPosition() + 1, peasant.getYPosition())) {
				//generate remove list
				literals.remove(new At(peasantIds.get(0), new Point(peasant.getXPosition(), peasant.getYPosition())));
				
				//generate add list
				literals.add(new At(peasantIds.get(0), new Point(peasant.getXPosition() - 1, peasant.getYPosition())));

				Node n = new Node(nextState.getView(0), node, move, node.getStateLits(), 
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
			nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.NORTH);
			
			estimatedCost = calculateHeuristicDistance(peasant.getXPosition(), peasant.getYPosition() - 1, 
					node.getGoal().x, node.getGoal().y);
			move = new Move(Direction.NORTH);

			if(node.getState().inBounds(peasant.getXPosition(), peasant.getYPosition() - 1)) {

				//generate remove list
				literals.remove(new At(peasantIds.get(0), new Point(peasant.getXPosition(), peasant.getYPosition())));
			
				//generate add list
				literals.add(new At(peasantIds.get(0), new Point(peasant.getXPosition(), peasant.getYPosition() - 1)));

				Node n = new Node(nextState.getView(0), node, move, node.getStateLits(), 
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
			nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.EAST);
			
			estimatedCostToGoal = calculateHeuristicDistance(peasant.getXPosition() + 1, peasant.getYPosition(), 
					node.getGoal().x, node.getGoal().y);
			move = new Move(Direction.EAST);

			if(node.getState().inBounds(peasant.getXPosition() + 1, peasant.getYPosition())) {

				//generate remove list
				literals.remove(new At(peasantIds.get(0), new Point(peasant.getXPosition(), peasant.getYPosition())));
			
				//generate add list
				literals.add(new At(peasantIds.get(0), new Point(peasant.getXPosition() + 1, peasant.getYPosition())));

				Node n = new Node(nextState.getView(0), node, move, node.getStateLits(), 
						node.getCostToNode() + 1, node.getGoal(), estimatedCostToGoal);
				
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
			nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.SOUTH);
			
			estimatedCostToGoal = calculateHeuristicDistance(peasant.getXPosition(), peasant.getYPosition() + 1, 
					node.getGoal().x, node.getGoal().y);
			move = new Move(Direction.SOUTH);

			if(node.getState().inBounds(peasant.getXPosition(), peasant.getYPosition() + 1)) {

				//generate remove list
				literals.remove(new At(peasantIds.get(0), new Point(peasant.getXPosition(), peasant.getYPosition())));
			
				//generate add list
				literals.add(new At(peasantIds.get(0), new Point(peasant.getXPosition(), peasant.getYPosition() + 1)));

				Node n = new Node(nextState.getView(0), node, move, node.getStateLits(), 
						node.getCostToNode() + 1, node.getGoal(), estimatedCostToGoal);
				
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
			
			
//			if(logger.isLoggable(Level.FINE)) {
//				logger.fine("Current Gold: " + currentGold);
//			}
//			if(logger.isLoggable(Level.FINE)) {
//				logger.fine("Current Wood: " + currentWood);
//			}
//			
//			unit = node.getState().getUnit(peasantIds.get(0));
//			UnitView townHall = node.getState().getUnit(townhallIds.get(0));
//			
//			//Goal found
//			if(currentWood >= 200 && currentGold >= 200) {
//				while(node.getParentNode() != null) {
//					solution.addFirst(node);
//					node = node.getParentNode();
//				}
//				break;
//			}
//			
//			closed.add(node);
//			
//			if(currentWood < 200) {
//				int woodId = getClosestWoodID(unit);
//				ResourceView wood = node.getState().getResourceNode(woodId);
//				
//				if(Literals.hasNothing(unit)) {
//					if(Literals.areAdjacent(node.getState(), unit, wood)) {
//						//gather wood
//						
//						nextState.resourceAt(wood.getXPosition(), wood.getYPosition()).reduceAmountRemaining(GATHER_AMOUNT);
//
//						Direction dir = getDirectionBetween(unit, wood);
//						GatherWood gather = new GatherWood(unit.getCargoAmount(), dir);
//						Node n = new Node(nextState.getView(0), node, gather, node.getCostToNode() + 1, 
//								estimatedCostToGoal, unit.getXPosition(), unit.getYPosition());
//						if(n.getState().inBounds(n.getPeasantX(), n.getPeasantY())
//								&& !n.getState().isResourceAt(n.getPeasantX(), n.getPeasantY())
//								&& !n.getState().isUnitAt(n.getPeasantX(), n.getPeasantY())) {
//							if(!closed.contains(n)) {
//								open.add(n);
//							} else if (open.contains(n)){
//								Node toCompare = open.get(n);
//								if(toCompare.getCostToNode() > n.getCostToNode()) {
//									toCompare.setCostToNode(n.getCostToNode());
//									toCompare.setParentNode(n.getParentNode());
////									toCompare.setDirectionToHere(n.getDirectionToHere());
//								}
//							}
//						}
//					} else {
//						//move west
//						nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.WEST);
//						
//						estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX() - 1, node.getPeasantY(), 
//								wood.getXPosition(), wood.getYPosition());
//						Move move = new Move(Direction.WEST);
//						Node n = new Node(nextState.getView(0), node, move, node.getCostToNode() + 1, 
//								estimatedCostToGoal, node.getPeasantX() - 1, node.getPeasantY());
//						if(n.getState().inBounds(n.getPeasantX(), n.getPeasantY())
//								&& !n.getState().isResourceAt(n.getPeasantX(), n.getPeasantY())
//								&& !n.getState().isUnitAt(n.getPeasantX(), n.getPeasantY())) {
//							if(!closed.contains(n)) {
//								open.add(n);
//							} else if (open.contains(n)){
//								Node toCompare = open.get(n);
//								if(toCompare.getCostToNode() > n.getCostToNode()) {
//									toCompare.setCostToNode(n.getCostToNode());
//									toCompare.setParentNode(n.getParentNode());
////									toCompare.setDirectionToHere(n.getDirectionToHere());
//								}
//							}
//						}
//						
//						//move north
//						nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.NORTH);
//						
//						estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX(), node.getPeasantY() - 1,
//								wood.getXPosition(), wood.getYPosition());
//						move = new Move(Direction.NORTH);
//						n = new Node(nextState.getView(0), node, move, node.getCostToNode() + 1, 
//								estimatedCostToGoal, node.getPeasantX(), node.getPeasantY() - 1);
//						if(n.getState().inBounds(n.getPeasantX(), n.getPeasantY())
//								&& !n.getState().isResourceAt(n.getPeasantX(), n.getPeasantY())
//								&& !n.getState().isUnitAt(n.getPeasantX(), n.getPeasantY())) {
//							if(!closed.contains(n)) {
//								open.add(n);
//							} else if (open.contains(n)){
//								Node toCompare = open.get(n);
//								if(toCompare.getCostToNode() > n.getCostToNode()) {
//									toCompare.setCostToNode(n.getCostToNode());
//									toCompare.setParentNode(n.getParentNode());
////									toCompare.setDirectionToHere(n.getDirectionToHere());
//								}
//							}
//						}
//						
//						//move east
//						nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.EAST);
//						
//						estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX() + 1, node.getPeasantY(), 
//								wood.getXPosition(), wood.getYPosition());
//						move = new Move(Direction.EAST);
//						n = new Node(nextState.getView(0), node, move, node.getCostToNode() + 1, 
//								estimatedCostToGoal, node.getPeasantX() + 1, node.getPeasantY());
//						if(n.getState().inBounds(n.getPeasantX(), n.getPeasantY())
//								&& !n.getState().isResourceAt(n.getPeasantX(), n.getPeasantY())
//								&& !n.getState().isUnitAt(n.getPeasantX(), n.getPeasantY())) {
//							if(!closed.contains(n)) {
//								open.add(n);
//							} else if (open.contains(n)){
//								Node toCompare = open.get(n);
//								if(toCompare.getCostToNode() > n.getCostToNode()) {
//									toCompare.setCostToNode(n.getCostToNode());
//									toCompare.setParentNode(n.getParentNode());
////									toCompare.setDirectionToHere(n.getDirectionToHere());
//								}
//							}
//						}
//						
//						//move south
//						nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.SOUTH);
//						
//						estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX(), node.getPeasantY() + 1, 
//								wood.getXPosition(), wood.getYPosition());
//						move = new Move(Direction.SOUTH);
//						n = new Node(nextState.getView(0), node, move, node.getCostToNode() + 1, 
//								estimatedCostToGoal, node.getPeasantX(), node.getPeasantY() + 1);
//						if(n.getState().inBounds(n.getPeasantX(), n.getPeasantY())
//								&& !n.getState().isResourceAt(n.getPeasantX(), n.getPeasantY())
//								&& !n.getState().isUnitAt(n.getPeasantX(), n.getPeasantY())) {
//							if(!closed.contains(n)) {
//								open.add(n);
//							} else if (open.contains(n)){
//								Node toCompare = open.get(n);
//								if(toCompare.getCostToNode() > n.getCostToNode()) {
//									toCompare.setCostToNode(n.getCostToNode());
//									toCompare.setParentNode(n.getParentNode());
////									toCompare.setDirectionToHere(n.getDirectionToHere());
//								}
//							}
//						}
//					}
//				} else {
//					if(Literals.areAdjacent(node.getState(), unit, townHall)) {
//						//deposit resources
//						nextState.getUnit(peasantIds.get(0)).clearCargo();
//						
//						estimatedCostToGoal = 0;
//						
//						Direction dir = getDirectionBetween(unit, townHall);
//						DepositWood deposit = new DepositWood(unit.getCargoAmount(), dir);
//						Node n = new Node(nextState.getView(0), node, deposit, node.getCostToNode() + 1, 
//								estimatedCostToGoal, unit.getXPosition(), unit.getYPosition());
//						if(n.getState().inBounds(n.getPeasantX(), n.getPeasantY())
//								&& !n.getState().isResourceAt(n.getPeasantX(), n.getPeasantY())
//								&& !n.getState().isUnitAt(n.getPeasantX(), n.getPeasantY())) {
//							if(!closed.contains(n)) {
//								open.add(n);
//							} else if (open.contains(n)){
//								Node toCompare = open.get(n);
//								if(toCompare.getCostToNode() > n.getCostToNode()) {
//									toCompare.setCostToNode(n.getCostToNode());
//									toCompare.setParentNode(n.getParentNode());
////									toCompare.setDirectionToHere(n.getDirectionToHere());
//								}
//							}
//						}
//					} else {
//						//move to the town hall
//						
//						UnitView townhall = node.getState().getUnit(townhallIds.get(0));
//						
//						//move west
//						nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.WEST);
//						
//						estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX() - 1, node.getPeasantY(), 
//								townhall.getXPosition(), townhall.getYPosition());
//						Move move = new Move(Direction.WEST);
//						Node n = new Node(nextState.getView(0), node, move, node.getCostToNode() + 1, 
//								estimatedCostToGoal, node.getPeasantX() - 1, node.getPeasantY());
//						if(n.getState().inBounds(n.getPeasantX(), n.getPeasantY())
//								&& !n.getState().isResourceAt(n.getPeasantX(), n.getPeasantY())
//								&& !n.getState().isUnitAt(n.getPeasantX(), n.getPeasantY())) {
//							if(!closed.contains(n)) {
//								open.add(n);
//							} else if (open.contains(n)){
//								Node toCompare = open.get(n);
//								if(toCompare.getCostToNode() > n.getCostToNode()) {
//									toCompare.setCostToNode(n.getCostToNode());
//									toCompare.setParentNode(n.getParentNode());
////									toCompare.setDirectionToHere(n.getDirectionToHere());
//								}
//							}
//						}
//						
//						//move north
//						nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.NORTH);
//						
//						estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX(), node.getPeasantY() - 1, 
//								townhall.getXPosition(), townhall.getYPosition());
//						move = new Move(Direction.NORTH);
//						n = new Node(nextState.getView(0), node, move, node.getCostToNode() + 1, 
//								estimatedCostToGoal, node.getPeasantX(), node.getPeasantY() - 1);
//						if(n.getState().inBounds(n.getPeasantX(), n.getPeasantY())
//								&& !n.getState().isResourceAt(n.getPeasantX(), n.getPeasantY())
//								&& !n.getState().isUnitAt(n.getPeasantX(), n.getPeasantY())) {
//							if(!closed.contains(n)) {
//								open.add(n);
//							} else if (open.contains(n)){
//								Node toCompare = open.get(n);
//								if(toCompare.getCostToNode() > n.getCostToNode()) {
//									toCompare.setCostToNode(n.getCostToNode());
//									toCompare.setParentNode(n.getParentNode());
////									toCompare.setDirectionToHere(n.getDirectionToHere());
//								}
//							}
//						}
//						
//						//move east
//						nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.EAST);
//						
//						estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX() + 1, node.getPeasantY(), 
//								townhall.getXPosition(), townhall.getYPosition());
//						move = new Move(Direction.EAST);
//						n = new Node(nextState.getView(0), node, move, node.getCostToNode() + 1, 
//								estimatedCostToGoal, node.getPeasantX() + 1, node.getPeasantY());
//						if(n.getState().inBounds(n.getPeasantX(), n.getPeasantY())
//								&& !n.getState().isResourceAt(n.getPeasantX(), n.getPeasantY())
//								&& !n.getState().isUnitAt(n.getPeasantX(), n.getPeasantY())) {
//							if(!closed.contains(n)) {
//								open.add(n);
//							} else if (open.contains(n)){
//								Node toCompare = open.get(n);
//								if(toCompare.getCostToNode() > n.getCostToNode()) {
//									toCompare.setCostToNode(n.getCostToNode());
//									toCompare.setParentNode(n.getParentNode());
////									toCompare.setDirectionToHere(n.getDirectionToHere());
//								}
//							}
//						}
//						
//						//move south
//						nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.SOUTH);
//						
//						estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX(), node.getPeasantY() + 1, 
//								townhall.getXPosition(), townhall.getYPosition());
//						move = new Move(Direction.SOUTH);
//						n = new Node(nextState.getView(0), node, move, node.getCostToNode() + 1, 
//								estimatedCostToGoal, node.getPeasantX(), node.getPeasantY() + 1);
//						if(n.getState().inBounds(n.getPeasantX(), n.getPeasantY())
//								&& !n.getState().isResourceAt(n.getPeasantX(), n.getPeasantY())
//								&& !n.getState().isUnitAt(n.getPeasantX(), n.getPeasantY())) {
//							if(!closed.contains(n)) {
//								open.add(n);
//							} else if (open.contains(n)){
//								Node toCompare = open.get(n);
//								if(toCompare.getCostToNode() > n.getCostToNode()) {
//									toCompare.setCostToNode(n.getCostToNode());
//									toCompare.setParentNode(n.getParentNode());
////									toCompare.setDirectionToHere(n.getDirectionToHere());
//								}
//							}
//						}
//					}
//				}
//			} else {
//				if(currentGold < 200) {
//					int goldId = getClosestGoldID(unit);
//					ResourceView gold = node.getState().getResourceNode(goldId);
//					
//					if(Literals.hasNothing(unit)) {
//						if(Literals.areAdjacent(node.getState(), unit, gold)) {
//							//gather gold
//							nextState.resourceAt(gold.getXPosition(), gold.getYPosition()).reduceAmountRemaining(GATHER_AMOUNT);
//							
//							estimatedCostToGoal = 0;
//							
//							Direction dir = getDirectionBetween(unit, gold);
//							GatherGold gather = new GatherGold(200, dir);
//							Node n = new Node(nextState.getView(0), node, gather, node.getCostToNode() + 1, 
//									estimatedCostToGoal, unit.getXPosition(), unit.getYPosition());
//							if(n.getState().inBounds(n.getPeasantX(), n.getPeasantY())
//									&& !n.getState().isResourceAt(n.getPeasantX(), n.getPeasantY())
//									&& !n.getState().isUnitAt(n.getPeasantX(), n.getPeasantY())) {
//								if(!closed.contains(n)) {
//									open.add(n);
//								} else if (open.contains(n)){
//									Node toCompare = open.get(n);
//									if(toCompare.getCostToNode() > n.getCostToNode()) {
//										toCompare.setCostToNode(n.getCostToNode());
//										toCompare.setParentNode(n.getParentNode());
////										toCompare.setDirectionToHere(n.getDirectionToHere());
//									}
//								}
//							}
//						} else {
//							//move west
//							nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.WEST);
//							
//							estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX() - 1, node.getPeasantY(), 
//									gold.getXPosition(), gold.getYPosition());
//							Move move = new Move(Direction.WEST);
//							Node n = new Node(nextState.getView(0), node, move, node.getCostToNode() + 1, 
//									estimatedCostToGoal, node.getPeasantX() - 1, node.getPeasantY());
//							if(n.getState().inBounds(n.getPeasantX(), n.getPeasantY())
//									&& !n.getState().isResourceAt(n.getPeasantX(), n.getPeasantY())
//									&& !n.getState().isUnitAt(n.getPeasantX(), n.getPeasantY())) {
//								if(!closed.contains(n)) {
//									open.add(n);
//								} else if (open.contains(n)){
//									Node toCompare = open.get(n);
//									if(toCompare.getCostToNode() > n.getCostToNode()) {
//										toCompare.setCostToNode(n.getCostToNode());
//										toCompare.setParentNode(n.getParentNode());
////										toCompare.setDirectionToHere(n.getDirectionToHere());
//									}
//								}
//							}
//							
//							//move north
//							nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.NORTH);
//							
//							estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX(), node.getPeasantY() - 1, 
//									gold.getXPosition(), gold.getYPosition());
//							move = new Move(Direction.NORTH);
//							n = new Node(nextState.getView(0), node, move, node.getCostToNode() + 1, 
//									estimatedCostToGoal, node.getPeasantX(), node.getPeasantY() - 1);
//							if(n.getState().inBounds(n.getPeasantX(), n.getPeasantY())
//									&& !n.getState().isResourceAt(n.getPeasantX(), n.getPeasantY())
//									&& !n.getState().isUnitAt(n.getPeasantX(), n.getPeasantY())) {
//								if(!closed.contains(n)) {
//									open.add(n);
//								} else if (open.contains(n)){
//									Node toCompare = open.get(n);
//									if(toCompare.getCostToNode() > n.getCostToNode()) {
//										toCompare.setCostToNode(n.getCostToNode());
//										toCompare.setParentNode(n.getParentNode());
////										toCompare.setDirectionToHere(n.getDirectionToHere());
//									}
//								}
//							}
//							
//							//move east
//							nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.EAST);
//							
//							estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX() + 1, node.getPeasantY(), 
//									gold.getXPosition(), gold.getYPosition());
//							move = new Move(Direction.EAST);
//							n = new Node(nextState.getView(0), node, move, node.getCostToNode() + 1, 
//									estimatedCostToGoal, node.getPeasantX() + 1, node.getPeasantY());
//							if(n.getState().inBounds(n.getPeasantX(), n.getPeasantY())
//									&& !n.getState().isResourceAt(n.getPeasantX(), n.getPeasantY())
//									&& !n.getState().isUnitAt(n.getPeasantX(), n.getPeasantY())) {
//								if(!closed.contains(n)) {
//									open.add(n);
//								} else if (open.contains(n)){
//									Node toCompare = open.get(n);
//									if(toCompare.getCostToNode() > n.getCostToNode()) {
//										toCompare.setCostToNode(n.getCostToNode());
//										toCompare.setParentNode(n.getParentNode());
////										toCompare.setDirectionToHere(n.getDirectionToHere());
//									}
//								}
//							}
//							
//							//move south
//							nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.SOUTH);
//							
//							estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX(), node.getPeasantY() + 1, 
//									gold.getXPosition(), gold.getYPosition());
//							move = new Move(Direction.SOUTH);
//							n = new Node(nextState.getView(0), node, move, node.getCostToNode() + 1, 
//									estimatedCostToGoal, node.getPeasantX(), node.getPeasantY() + 1);
//							if(n.getState().inBounds(n.getPeasantX(), n.getPeasantY())
//									&& !n.getState().isResourceAt(n.getPeasantX(), n.getPeasantY())
//									&& !n.getState().isUnitAt(n.getPeasantX(), n.getPeasantY())) {
//								if(!closed.contains(n)) {
//									open.add(n);
//								} else if (open.contains(n)){
//									Node toCompare = open.get(n);
//									if(toCompare.getCostToNode() > n.getCostToNode()) {
//										toCompare.setCostToNode(n.getCostToNode());
//										toCompare.setParentNode(n.getParentNode());
////										toCompare.setDirectionToHere(n.getDirectionToHere());
//									}
//								}
//							}
//						}
//					} else {
//						if(Literals.areAdjacent(node.getState(), unit, townHall)) {
//							//deposit resources
//							nextState.getUnit(peasantIds.get(0)).clearCargo();
//							estimatedCostToGoal = 0;
//							
//							Direction dir = getDirectionBetween(unit, townHall);
//							DepositWood deposit = new DepositWood(unit.getCargoAmount(), dir);
//							Node n = new Node(nextState.getView(0), node, deposit, node.getCostToNode() + 1, 
//									estimatedCostToGoal, node.getPeasantX(), node.getPeasantY());
//							if(n.getState().inBounds(n.getPeasantX(), n.getPeasantY())
//									&& !n.getState().isResourceAt(n.getPeasantX(), n.getPeasantY())
//									&& !n.getState().isUnitAt(n.getPeasantX(), n.getPeasantY())) {
//								if(!closed.contains(n)) {
//									open.add(n);
//								} else if (open.contains(n)){
//									Node toCompare = open.get(n);
//									if(toCompare.getCostToNode() > n.getCostToNode()) {
//										toCompare.setCostToNode(n.getCostToNode());
//										toCompare.setParentNode(n.getParentNode());
////										toCompare.setDirectionToHere(n.getDirectionToHere());
//									}
//								}
//							}
//						} else {
//							//move towards the town hall
//							
//							UnitView townhall = node.getState().getUnit(townhallIds.get(0));
//							
//							//move west
//							nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.WEST);
//							
//							estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX() - 1, node.getPeasantY(), 
//									townhall.getXPosition(), townhall.getYPosition());
//							Move move = new Move(Direction.WEST);
//							Node n = new Node(nextState.getView(0), node, move, node.getCostToNode() + 1, 
//									estimatedCostToGoal, node.getPeasantX() - 1, node.getPeasantY());
//							if(n.getState().inBounds(n.getPeasantX(), n.getPeasantY())
//									&& !n.getState().isResourceAt(n.getPeasantX(), n.getPeasantY())
//									&& !n.getState().isUnitAt(n.getPeasantX(), n.getPeasantY())) {
//								if(!closed.contains(n)) {
//									open.add(n);
//								} else if (open.contains(n)){
//									Node toCompare = open.get(n);
//									if(toCompare.getCostToNode() > n.getCostToNode()) {
//										toCompare.setCostToNode(n.getCostToNode());
//										toCompare.setParentNode(n.getParentNode());
////										toCompare.setDirectionToHere(n.getDirectionToHere());
//									}
//								}
//							}
//							
//							//move north
//							nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.NORTH);
//							
//							estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX(), node.getPeasantY() - 1, 
//									townhall.getXPosition(), townhall.getYPosition());
//							move = new Move(Direction.NORTH);
//							n = new Node(nextState.getView(0), node, move, node.getCostToNode() + 1, 
//									estimatedCostToGoal, node.getPeasantX(), node.getPeasantY() - 1);
//							if(n.getState().inBounds(n.getPeasantX(), n.getPeasantY())
//									&& !n.getState().isResourceAt(n.getPeasantX(), n.getPeasantY())
//									&& !n.getState().isUnitAt(n.getPeasantX(), n.getPeasantY())) {
//								if(!closed.contains(n)) {
//									open.add(n);
//								} else if (open.contains(n)){
//									Node toCompare = open.get(n);
//									if(toCompare.getCostToNode() > n.getCostToNode()) {
//										toCompare.setCostToNode(n.getCostToNode());
//										toCompare.setParentNode(n.getParentNode());
////										toCompare.setDirectionToHere(n.getDirectionToHere());
//									}
//								}
//							}
//							
//							//move east
//							nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.EAST);
//							
//							estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX() + 1, node.getPeasantY(), 
//									townhall.getXPosition(), townhall.getYPosition());
//							move = new Move(Direction.EAST);
//							n = new Node(nextState.getView(0), node, move, node.getCostToNode() + 1, 
//									estimatedCostToGoal, node.getPeasantX() + 1, node.getPeasantY());
//							if(n.getState().inBounds(n.getPeasantX(), n.getPeasantY())
//									&& !n.getState().isResourceAt(n.getPeasantX(), n.getPeasantY())
//									&& !n.getState().isUnitAt(n.getPeasantX(), n.getPeasantY())) {
//								if(!closed.contains(n)) {
//									open.add(n);
//								} else if (open.contains(n)){
//									Node toCompare = open.get(n);
//									if(toCompare.getCostToNode() > n.getCostToNode()) {
//										toCompare.setCostToNode(n.getCostToNode());
//										toCompare.setParentNode(n.getParentNode());
////										toCompare.setDirectionToHere(n.getDirectionToHere());
//									}
//								}
//							}
//							
//							//move south
//							nextState.moveUnit(nextState.getUnit(peasantIds.get(0)), Direction.SOUTH);
//							
//							estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX(), node.getPeasantY() + 1, 
//									townhall.getXPosition(), townhall.getYPosition());
//							move = new Move(Direction.SOUTH);
//							n = new Node(nextState.getView(0), node, move, node.getCostToNode() + 1, 
//									estimatedCostToGoal, node.getPeasantX(), node.getPeasantY() + 1);
//							if(n.getState().inBounds(n.getPeasantX(), n.getPeasantY())
//									&& !n.getState().isResourceAt(n.getPeasantX(), n.getPeasantY())
//									&& !n.getState().isUnitAt(n.getPeasantX(), n.getPeasantY())) {
//								if(!closed.contains(n)) {
//									open.add(n);
//								} else if (open.contains(n)){
//									Node toCompare = open.get(n);
//									if(toCompare.getCostToNode() > n.getCostToNode()) {
//										toCompare.setCostToNode(n.getCostToNode());
//										toCompare.setParentNode(n.getParentNode());
////										toCompare.setDirectionToHere(n.getDirectionToHere());
//									}
//								}
//							}
//						}
//					}
//				} else {
//					currentGoal++;
//				}
//			}
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
