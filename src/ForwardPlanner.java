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
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionType;
import edu.cwru.sepia.action.TargetedAction;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.ResourceNode.ResourceView;
import edu.cwru.sepia.environment.model.state.ResourceNode.Type;
import edu.cwru.sepia.environment.model.state.ResourceType;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Template.TemplateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;

public class ForwardPlanner extends Agent {
	private static final long serialVersionUID = -4047208702628325380L;
	private static final Logger logger = Logger.getLogger(ForwardPlanner.class.getCanonicalName());

	private int step;
	private int currentGoal;
	
	StateView currentState;

	public ForwardPlanner(int playernum, String[] arguments) {
		super(playernum);
		currentGoal = 0;
	}

	
	@Override
	public Map<Integer, Action> initialStep(StateView newstate, History.HistoryView statehistory) {
		step = 0;
		return middleStep(newstate, statehistory);
	}

	@Override
	public Map<Integer, Action> middleStep(StateView newState, History.HistoryView statehistory) {
		step++;
		if(logger.isLoggable(Level.FINE)) {
			logger.fine("=> Step: " + step);
		}
		
		Map<Integer, Action> builder = new HashMap<Integer, Action>();
		currentState = newState;
		
		int currentGold = currentState.getResourceAmount(0, ResourceType.GOLD);
		int currentWood = currentState.getResourceAmount(0, ResourceType.WOOD);
		if(logger.isLoggable(Level.FINE)) {
			logger.fine("Current Gold: " + currentGold);
		}
		if(logger.isLoggable(Level.FINE)) {
			logger.fine("Current Wood: " + currentWood);
		}
		
		List<Integer> allUnitIds = currentState.getAllUnitIds();
		List<Integer> peasantIds = new ArrayList<Integer>();
		List<Integer> townhallIds = new ArrayList<Integer>();
		List<Integer> footmanIds = new ArrayList<Integer>();
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
			if(unitTypeName.equals("Footman")) {
				footmanIds.add(id);
			}
		}
		
		switch(currentGoal) {
		case 0:
			UnitView unit = currentState.getUnit(peasantIds.get(0));
			if(currentWood < 200) {
				int woodId = getClosestWoodID(unit);
				ResourceView wood = currentState.getResourceNode(woodId);
				UnitView townHall = currentState.getUnit(townhallIds.get(0));
				
				Action b = null; 
				if(Literals.hasNothing(unit)) {
					if(Literals.areAdjacent(currentState, unit, wood)) {
						b = new TargetedAction(peasantIds.get(0), ActionType.PRIMITIVEGATHER, woodId);
					} else {
						//move to the nearest wood
					}
				} else {
					if(Literals.areAdjacent(currentState, unit, townHall)) {
						//deposit resources
						b = new TargetedAction(peasantIds.get(0), ActionType.PRIMITIVEDEPOSIT, townhallIds.get(0));
					} else {
						//move to the town hall
					}
				}
			} else {
				if(currentGold < 200) {
					int goldId = getClosestGoldID(unit);
					ResourceView gold = currentState.getResourceNode(goldId);
					UnitView townHall = currentState.getUnit(townhallIds.get(0));
					
					Action b = null; 
					if(Literals.hasNothing(unit)) {
						if(Literals.areAdjacent(currentState, unit, gold)) {
							b = new TargetedAction(peasantIds.get(0), ActionType.PRIMITIVEGATHER, goldId);
						} else {
							//move towards the nearest gold
						}
					} else {
						if(Literals.areAdjacent(currentState, unit, townHall)) {
							//deposit resources
							b = new TargetedAction(peasantIds.get(0), ActionType.PRIMITIVEDEPOSIT, townhallIds.get(0));
						} else {
							//move towards the town hall
						}
					}
				} else {
					currentGoal++;
				}
			}
			
			
			int peasantId = peasantIds.get(0);
			int townhallId = townhallIds.get(0);
			Action b = null;
			if(currentState.getUnit(peasantId).getCargoType() == ResourceType.GOLD 
					&& currentState.getUnit(peasantId).getCargoAmount() > 0) {
				b = new TargetedAction(peasantId, ActionType.COMPOUNDDEPOSIT, townhallId);
			} else {
				List<Integer> resourceIds = currentState.getResourceNodeIds(Type.GOLD_MINE);
				b = new TargetedAction(peasantId, ActionType.COMPOUNDGATHER, resourceIds.get(0));
			}
			builder.put(peasantId, b);
			
			if(currentGold >= 800) {
				currentGoal++;
			}
			break;
		}
		return builder;
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
