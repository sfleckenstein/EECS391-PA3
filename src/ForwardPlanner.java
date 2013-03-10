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
		
		UnitView unit = currentState.getUnit(peasantIds.get(0));
		open.add(new Node(null, null, 0, 0, unit.getXPosition(), unit.getYPosition()));
		
		while(true) {
			Node node = open.poll();

			if(node == null) {
				terminalStep(newState, stateHistory);
				break;
			}
			
//			Map<Integer, Action> builder = new HashMap<Integer, Action>();
			
			int currentGold = currentState.getResourceAmount(0, ResourceType.GOLD);
			int currentWood = currentState.getResourceAmount(0, ResourceType.WOOD);
			if(logger.isLoggable(Level.FINE)) {
				logger.fine("Current Gold: " + currentGold);
			}
			if(logger.isLoggable(Level.FINE)) {
				logger.fine("Current Wood: " + currentWood);
			}
			
			unit = currentState.getUnit(peasantIds.get(0));
			UnitView townHall = currentState.getUnit(townhallIds.get(0));
			
			//Goal found
			if(currentWood >= 200 && currentGold >= 200) {
				while(node.getParentNode() != null) {
					solution.addFirst(node);
					node = node.getParentNode();
				}
				break;
			}
			
			closed.add(node);
			
			if(currentWood < 200) {
				int woodId = getClosestWoodID(unit);
				ResourceView wood = currentState.getResourceNode(woodId);
				
				if(Literals.hasNothing(unit)) {
					if(Literals.areAdjacent(currentState, unit, wood)) {
						//gather wood

						Direction dir = getDirectionBetween(unit, wood);
						GatherWood gather = new GatherWood(unit.getCargoAmount(), dir);
						Node n = new Node(node, gather, node.getCostToNode() + 1, estimatedCostToGoal, unit.getXPosition(), unit.getYPosition());
						if(currentState.inBounds(n.getPeasantX(), n.getPeasantY())
								&& !currentState.isResourceAt(n.getPeasantX(), n.getPeasantY())
								&& !currentState.isUnitAt(n.getPeasantX(), n.getPeasantY())) {
							if(!closed.contains(n)) {
								open.add(n);
							} else if (open.contains(n)){
								Node toCompare = open.get(n);
								if(toCompare.getCostToNode() > n.getCostToNode()) {
									toCompare.setCostToNode(n.getCostToNode());
									toCompare.setParentNode(n.getParentNode());
//									toCompare.setDirectionToHere(n.getDirectionToHere());
								}
							}
						}
					} else {
						//move west
						estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX() - 1, node.getPeasantY(), 
								wood.getXPosition(), wood.getYPosition());
						Move move = new Move(Direction.WEST);
						Node n = new Node(node, move, node.getCostToNode() + 1, estimatedCostToGoal, node.getPeasantX() - 1, node.getPeasantY());
						if(currentState.inBounds(n.getPeasantX(), n.getPeasantY())
								&& !currentState.isResourceAt(n.getPeasantX(), n.getPeasantY())
								&& !currentState.isUnitAt(n.getPeasantX(), n.getPeasantY())) {
							if(!closed.contains(n)) {
								open.add(n);
							} else if (open.contains(n)){
								Node toCompare = open.get(n);
								if(toCompare.getCostToNode() > n.getCostToNode()) {
									toCompare.setCostToNode(n.getCostToNode());
									toCompare.setParentNode(n.getParentNode());
//									toCompare.setDirectionToHere(n.getDirectionToHere());
								}
							}
						}
						
						//move north
						estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX(), node.getPeasantY() - 1,
								wood.getXPosition(), wood.getYPosition());
						move = new Move(Direction.NORTH);
						n = new Node(node, move, node.getCostToNode() + 1, estimatedCostToGoal, node.getPeasantX(), node.getPeasantY() - 1);
						if(currentState.inBounds(n.getPeasantX(), n.getPeasantY())
								&& !currentState.isResourceAt(n.getPeasantX(), n.getPeasantY())
								&& !currentState.isUnitAt(n.getPeasantX(), n.getPeasantY())) {
							if(!closed.contains(n)) {
								open.add(n);
							} else if (open.contains(n)){
								Node toCompare = open.get(n);
								if(toCompare.getCostToNode() > n.getCostToNode()) {
									toCompare.setCostToNode(n.getCostToNode());
									toCompare.setParentNode(n.getParentNode());
//									toCompare.setDirectionToHere(n.getDirectionToHere());
								}
							}
						}
						
						//move east
						estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX() + 1, node.getPeasantY(), 
								wood.getXPosition(), wood.getYPosition());
						move = new Move(Direction.EAST);
						n = new Node(node, move, node.getCostToNode() + 1, estimatedCostToGoal, node.getPeasantX() + 1, node.getPeasantY());
						if(currentState.inBounds(n.getPeasantX(), n.getPeasantY())
								&& !currentState.isResourceAt(n.getPeasantX(), n.getPeasantY())
								&& !currentState.isUnitAt(n.getPeasantX(), n.getPeasantY())) {
							if(!closed.contains(n)) {
								open.add(n);
							} else if (open.contains(n)){
								Node toCompare = open.get(n);
								if(toCompare.getCostToNode() > n.getCostToNode()) {
									toCompare.setCostToNode(n.getCostToNode());
									toCompare.setParentNode(n.getParentNode());
//									toCompare.setDirectionToHere(n.getDirectionToHere());
								}
							}
						}
						
						//move south
						estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX(), node.getPeasantY() + 1, 
								wood.getXPosition(), wood.getYPosition());
						move = new Move(Direction.SOUTH);
						n = new Node(node, move, node.getCostToNode() + 1, estimatedCostToGoal, node.getPeasantX(), node.getPeasantY() + 1);
						if(currentState.inBounds(n.getPeasantX(), n.getPeasantY())
								&& !currentState.isResourceAt(n.getPeasantX(), n.getPeasantY())
								&& !currentState.isUnitAt(n.getPeasantX(), n.getPeasantY())) {
							if(!closed.contains(n)) {
								open.add(n);
							} else if (open.contains(n)){
								Node toCompare = open.get(n);
								if(toCompare.getCostToNode() > n.getCostToNode()) {
									toCompare.setCostToNode(n.getCostToNode());
									toCompare.setParentNode(n.getParentNode());
//									toCompare.setDirectionToHere(n.getDirectionToHere());
								}
							}
						}
					}
				} else {
					if(Literals.areAdjacent(currentState, unit, townHall)) {
						//deposit resources
						estimatedCostToGoal = 0;
						
						Direction dir = getDirectionBetween(unit, townHall);
						DepositWood deposit = new DepositWood(unit.getCargoAmount(), dir);
						Node n = new Node(node, deposit, node.getCostToNode() + 1, estimatedCostToGoal, unit.getXPosition(), unit.getYPosition());
						if(currentState.inBounds(n.getPeasantX(), n.getPeasantY())
								&& !currentState.isResourceAt(n.getPeasantX(), n.getPeasantY())
								&& !currentState.isUnitAt(n.getPeasantX(), n.getPeasantY())) {
							if(!closed.contains(n)) {
								open.add(n);
							} else if (open.contains(n)){
								Node toCompare = open.get(n);
								if(toCompare.getCostToNode() > n.getCostToNode()) {
									toCompare.setCostToNode(n.getCostToNode());
									toCompare.setParentNode(n.getParentNode());
//									toCompare.setDirectionToHere(n.getDirectionToHere());
								}
							}
						}
					} else {
						//move to the town hall
						
						UnitView townhall = currentState.getUnit(townhallIds.get(0));
						
						//move west
						estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX() - 1, node.getPeasantY(), 
								townhall.getXPosition(), townhall.getYPosition());
						Move move = new Move(Direction.WEST);
						Node n = new Node(node, move, node.getCostToNode() + 1, estimatedCostToGoal, node.getPeasantX() - 1, node.getPeasantY());
						if(currentState.inBounds(n.getPeasantX(), n.getPeasantY())
								&& !currentState.isResourceAt(n.getPeasantX(), n.getPeasantY())
								&& !currentState.isUnitAt(n.getPeasantX(), n.getPeasantY())) {
							if(!closed.contains(n)) {
								open.add(n);
							} else if (open.contains(n)){
								Node toCompare = open.get(n);
								if(toCompare.getCostToNode() > n.getCostToNode()) {
									toCompare.setCostToNode(n.getCostToNode());
									toCompare.setParentNode(n.getParentNode());
//									toCompare.setDirectionToHere(n.getDirectionToHere());
								}
							}
						}
						
						//move north
						estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX(), node.getPeasantY() - 1, 
								townhall.getXPosition(), townhall.getYPosition());
						move = new Move(Direction.NORTH);
						n = new Node(node, move, node.getCostToNode() + 1, estimatedCostToGoal, node.getPeasantX(), node.getPeasantY() - 1);
						if(currentState.inBounds(n.getPeasantX(), n.getPeasantY())
								&& !currentState.isResourceAt(n.getPeasantX(), n.getPeasantY())
								&& !currentState.isUnitAt(n.getPeasantX(), n.getPeasantY())) {
							if(!closed.contains(n)) {
								open.add(n);
							} else if (open.contains(n)){
								Node toCompare = open.get(n);
								if(toCompare.getCostToNode() > n.getCostToNode()) {
									toCompare.setCostToNode(n.getCostToNode());
									toCompare.setParentNode(n.getParentNode());
//									toCompare.setDirectionToHere(n.getDirectionToHere());
								}
							}
						}
						
						//move east
						estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX() + 1, node.getPeasantY(), 
								townhall.getXPosition(), townhall.getYPosition());
						move = new Move(Direction.EAST);
						n = new Node(node, move, node.getCostToNode() + 1, estimatedCostToGoal, node.getPeasantX() + 1, node.getPeasantY());
						if(currentState.inBounds(n.getPeasantX(), n.getPeasantY())
								&& !currentState.isResourceAt(n.getPeasantX(), n.getPeasantY())
								&& !currentState.isUnitAt(n.getPeasantX(), n.getPeasantY())) {
							if(!closed.contains(n)) {
								open.add(n);
							} else if (open.contains(n)){
								Node toCompare = open.get(n);
								if(toCompare.getCostToNode() > n.getCostToNode()) {
									toCompare.setCostToNode(n.getCostToNode());
									toCompare.setParentNode(n.getParentNode());
//									toCompare.setDirectionToHere(n.getDirectionToHere());
								}
							}
						}
						
						//move south
						estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX(), node.getPeasantY() + 1, 
								townhall.getXPosition(), townhall.getYPosition());
						move = new Move(Direction.SOUTH);
						n = new Node(node, move, node.getCostToNode() + 1, estimatedCostToGoal, node.getPeasantX(), node.getPeasantY() + 1);
						if(currentState.inBounds(n.getPeasantX(), n.getPeasantY())
								&& !currentState.isResourceAt(n.getPeasantX(), n.getPeasantY())
								&& !currentState.isUnitAt(n.getPeasantX(), n.getPeasantY())) {
							if(!closed.contains(n)) {
								open.add(n);
							} else if (open.contains(n)){
								Node toCompare = open.get(n);
								if(toCompare.getCostToNode() > n.getCostToNode()) {
									toCompare.setCostToNode(n.getCostToNode());
									toCompare.setParentNode(n.getParentNode());
//									toCompare.setDirectionToHere(n.getDirectionToHere());
								}
							}
						}
					}
				}
			} else {
				if(currentGold < 200) {
					int goldId = getClosestGoldID(unit);
					ResourceView gold = currentState.getResourceNode(goldId);
					
					if(Literals.hasNothing(unit)) {
						if(Literals.areAdjacent(currentState, unit, gold)) {
							//gather gold
							
							estimatedCostToGoal = 0;
							
							Direction dir = getDirectionBetween(unit, gold);
							GatherGold gather = new GatherGold(200, dir);
							Node n = new Node(node, gather, node.getCostToNode() + 1, estimatedCostToGoal, unit.getXPosition(), unit.getYPosition());
							if(currentState.inBounds(n.getPeasantX(), n.getPeasantY())
									&& !currentState.isResourceAt(n.getPeasantX(), n.getPeasantY())
									&& !currentState.isUnitAt(n.getPeasantX(), n.getPeasantY())) {
								if(!closed.contains(n)) {
									open.add(n);
								} else if (open.contains(n)){
									Node toCompare = open.get(n);
									if(toCompare.getCostToNode() > n.getCostToNode()) {
										toCompare.setCostToNode(n.getCostToNode());
										toCompare.setParentNode(n.getParentNode());
//										toCompare.setDirectionToHere(n.getDirectionToHere());
									}
								}
							}
						} else {
							//move west
							estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX() - 1, node.getPeasantY(), 
									gold.getXPosition(), gold.getYPosition());
							Move move = new Move(Direction.WEST);
							Node n = new Node(node, move, node.getCostToNode() + 1, estimatedCostToGoal, node.getPeasantX() - 1, node.getPeasantY());
							if(currentState.inBounds(n.getPeasantX(), n.getPeasantY())
									&& !currentState.isResourceAt(n.getPeasantX(), n.getPeasantY())
									&& !currentState.isUnitAt(n.getPeasantX(), n.getPeasantY())) {
								if(!closed.contains(n)) {
									open.add(n);
								} else if (open.contains(n)){
									Node toCompare = open.get(n);
									if(toCompare.getCostToNode() > n.getCostToNode()) {
										toCompare.setCostToNode(n.getCostToNode());
										toCompare.setParentNode(n.getParentNode());
//										toCompare.setDirectionToHere(n.getDirectionToHere());
									}
								}
							}
							
							//move north
							estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX(), node.getPeasantY() - 1, 
									gold.getXPosition(), gold.getYPosition());
							move = new Move(Direction.NORTH);
							n = new Node(node, move, node.getCostToNode() + 1, estimatedCostToGoal, node.getPeasantX(), node.getPeasantY() - 1);
							if(currentState.inBounds(n.getPeasantX(), n.getPeasantY())
									&& !currentState.isResourceAt(n.getPeasantX(), n.getPeasantY())
									&& !currentState.isUnitAt(n.getPeasantX(), n.getPeasantY())) {
								if(!closed.contains(n)) {
									open.add(n);
								} else if (open.contains(n)){
									Node toCompare = open.get(n);
									if(toCompare.getCostToNode() > n.getCostToNode()) {
										toCompare.setCostToNode(n.getCostToNode());
										toCompare.setParentNode(n.getParentNode());
//										toCompare.setDirectionToHere(n.getDirectionToHere());
									}
								}
							}
							
							//move east
							estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX() + 1, node.getPeasantY(), 
									gold.getXPosition(), gold.getYPosition());
							move = new Move(Direction.EAST);
							n = new Node(node, move, node.getCostToNode() + 1, estimatedCostToGoal, node.getPeasantX() + 1, node.getPeasantY());
							if(currentState.inBounds(n.getPeasantX(), n.getPeasantY())
									&& !currentState.isResourceAt(n.getPeasantX(), n.getPeasantY())
									&& !currentState.isUnitAt(n.getPeasantX(), n.getPeasantY())) {
								if(!closed.contains(n)) {
									open.add(n);
								} else if (open.contains(n)){
									Node toCompare = open.get(n);
									if(toCompare.getCostToNode() > n.getCostToNode()) {
										toCompare.setCostToNode(n.getCostToNode());
										toCompare.setParentNode(n.getParentNode());
//										toCompare.setDirectionToHere(n.getDirectionToHere());
									}
								}
							}
							
							//move south
							estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX(), node.getPeasantY() + 1, 
									gold.getXPosition(), gold.getYPosition());
							move = new Move(Direction.SOUTH);
							n = new Node(node, move, node.getCostToNode() + 1, estimatedCostToGoal, node.getPeasantX(), node.getPeasantY() + 1);
							if(currentState.inBounds(n.getPeasantX(), n.getPeasantY())
									&& !currentState.isResourceAt(n.getPeasantX(), n.getPeasantY())
									&& !currentState.isUnitAt(n.getPeasantX(), n.getPeasantY())) {
								if(!closed.contains(n)) {
									open.add(n);
								} else if (open.contains(n)){
									Node toCompare = open.get(n);
									if(toCompare.getCostToNode() > n.getCostToNode()) {
										toCompare.setCostToNode(n.getCostToNode());
										toCompare.setParentNode(n.getParentNode());
//										toCompare.setDirectionToHere(n.getDirectionToHere());
									}
								}
							}
						}
					} else {
						if(Literals.areAdjacent(currentState, unit, townHall)) {
							//deposit resources
							estimatedCostToGoal = 0;
							
							Direction dir = getDirectionBetween(unit, townHall);
							DepositWood deposit = new DepositWood(unit.getCargoAmount(), dir);
							Node n = new Node(node, deposit, node.getCostToNode() + 1, estimatedCostToGoal, node.getPeasantX(), node.getPeasantY());
							if(currentState.inBounds(n.getPeasantX(), n.getPeasantY())
									&& !currentState.isResourceAt(n.getPeasantX(), n.getPeasantY())
									&& !currentState.isUnitAt(n.getPeasantX(), n.getPeasantY())) {
								if(!closed.contains(n)) {
									open.add(n);
								} else if (open.contains(n)){
									Node toCompare = open.get(n);
									if(toCompare.getCostToNode() > n.getCostToNode()) {
										toCompare.setCostToNode(n.getCostToNode());
										toCompare.setParentNode(n.getParentNode());
//										toCompare.setDirectionToHere(n.getDirectionToHere());
									}
								}
							}
						} else {
							//move towards the town hall
							
							UnitView townhall = currentState.getUnit(townhallIds.get(0));
							
							//move west
							estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX() - 1, node.getPeasantY(), 
									townhall.getXPosition(), townhall.getYPosition());
							Move move = new Move(Direction.WEST);
							Node n = new Node(node, move, node.getCostToNode() + 1, estimatedCostToGoal, node.getPeasantX() - 1, node.getPeasantY());
							if(currentState.inBounds(n.getPeasantX(), n.getPeasantY())
									&& !currentState.isResourceAt(n.getPeasantX(), n.getPeasantY())
									&& !currentState.isUnitAt(n.getPeasantX(), n.getPeasantY())) {
								if(!closed.contains(n)) {
									open.add(n);
								} else if (open.contains(n)){
									Node toCompare = open.get(n);
									if(toCompare.getCostToNode() > n.getCostToNode()) {
										toCompare.setCostToNode(n.getCostToNode());
										toCompare.setParentNode(n.getParentNode());
//										toCompare.setDirectionToHere(n.getDirectionToHere());
									}
								}
							}
							
							//move north
							estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX(), node.getPeasantY() - 1, 
									townhall.getXPosition(), townhall.getYPosition());
							move = new Move(Direction.NORTH);
							n = new Node(node, move, node.getCostToNode() + 1, estimatedCostToGoal, node.getPeasantX(), node.getPeasantY() - 1);
							if(currentState.inBounds(n.getPeasantX(), n.getPeasantY())
									&& !currentState.isResourceAt(n.getPeasantX(), n.getPeasantY())
									&& !currentState.isUnitAt(n.getPeasantX(), n.getPeasantY())) {
								if(!closed.contains(n)) {
									open.add(n);
								} else if (open.contains(n)){
									Node toCompare = open.get(n);
									if(toCompare.getCostToNode() > n.getCostToNode()) {
										toCompare.setCostToNode(n.getCostToNode());
										toCompare.setParentNode(n.getParentNode());
//										toCompare.setDirectionToHere(n.getDirectionToHere());
									}
								}
							}
							
							//move east
							estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX() + 1, node.getPeasantY(), 
									townhall.getXPosition(), townhall.getYPosition());
							move = new Move(Direction.EAST);
							n = new Node(node, move, node.getCostToNode() + 1, estimatedCostToGoal, node.getPeasantX() + 1, node.getPeasantY());
							if(currentState.inBounds(n.getPeasantX(), n.getPeasantY())
									&& !currentState.isResourceAt(n.getPeasantX(), n.getPeasantY())
									&& !currentState.isUnitAt(n.getPeasantX(), n.getPeasantY())) {
								if(!closed.contains(n)) {
									open.add(n);
								} else if (open.contains(n)){
									Node toCompare = open.get(n);
									if(toCompare.getCostToNode() > n.getCostToNode()) {
										toCompare.setCostToNode(n.getCostToNode());
										toCompare.setParentNode(n.getParentNode());
//										toCompare.setDirectionToHere(n.getDirectionToHere());
									}
								}
							}
							
							//move south
							estimatedCostToGoal = calculateHeuristicDistance(node.getPeasantX(), node.getPeasantY() + 1, 
									townhall.getXPosition(), townhall.getYPosition());
							move = new Move(Direction.SOUTH);
							n = new Node(node, move, node.getCostToNode() + 1, estimatedCostToGoal, node.getPeasantX(), node.getPeasantY() + 1);
							if(currentState.inBounds(n.getPeasantX(), n.getPeasantY())
									&& !currentState.isResourceAt(n.getPeasantX(), n.getPeasantY())
									&& !currentState.isUnitAt(n.getPeasantX(), n.getPeasantY())) {
								if(!closed.contains(n)) {
									open.add(n);
								} else if (open.contains(n)){
									Node toCompare = open.get(n);
									if(toCompare.getCostToNode() > n.getCostToNode()) {
										toCompare.setCostToNode(n.getCostToNode());
										toCompare.setParentNode(n.getParentNode());
//										toCompare.setDirectionToHere(n.getDirectionToHere());
									}
								}
							}
						}
					}
				} else {
					currentGoal++;
				}
			}
			
		}
		return middleStep(newState, stateHistory);
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
	
	@Override
	public void savePlayerData(OutputStream os) {
		//this agent lacks learning and so has nothing to persist.
	}
	
	@Override
	public void loadPlayerData(InputStream is) {
		//this agent lacks learning and so has nothing to persist.
	}
}
