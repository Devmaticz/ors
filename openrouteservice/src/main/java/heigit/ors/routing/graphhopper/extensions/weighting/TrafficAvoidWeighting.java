/*
 *  Licensed to GIScience Research Group, Heidelberg University (GIScience)
 *
 *   http://www.giscience.uni-hd.de
 *   http://www.heigit.org
 *
 *  under one or more contributor license agreements. See the NOTICE file 
 *  distributed with this work for additional information regarding copyright 
 *  ownership. The GIScience licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in compliance 
 *  with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package heigit.ors.routing.graphhopper.extensions.weighting;


import java.util.HashMap;


import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;

import heigit.ors.routing.traffic.AvoidEdgeInfo;
import heigit.ors.routing.traffic.TmcEventCodesTable;
import heigit.ors.routing.traffic.TmcMode;
import heigit.ors.routing.traffic.TrafficEventInfo;

public class TrafficAvoidWeighting extends AbstractWeighting {

    /**
     * Converting to seconds is not necessary but makes adding other penalities easier (e.g. turn
     * costs or traffic light costs etc)
     */
    protected final static double SPEED_CONV = 1;
    private double maxSpeed;
	private HashMap<Integer, AvoidEdgeInfo> forbiddenEdges;

	private int encoderIndex = -1;

    public TrafficAvoidWeighting( FlagEncoder encoder, PMap map)
    {
    	super(encoder);
    	
        if (!encoder.isRegistered())
            throw new IllegalStateException("Make sure you add the FlagEncoder " + encoder + " to an EncodingManager before using it elsewhere");

        encoderIndex = encoder.getIndex();
        maxSpeed = encoder.getMaxSpeed() / SPEED_CONV;
    }

    public TrafficAvoidWeighting( FlagEncoder encoder )
    {
        this(encoder, new PMap(0));
    }


    public TrafficAvoidWeighting(Weighting defultWeighting, FlagEncoder encoder, HashMap<Integer, AvoidEdgeInfo> forbiddenEdges)
    {
        this(encoder, new PMap(0));
		this.forbiddenEdges = forbiddenEdges;     
    }

    
    @Override
    public double getMinWeight( double distance )
    {
        return distance / maxSpeed;
    }

    @Override
    public double calcWeight( EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId )
    {
        double normal_speed = reverse ? getFlagEncoder().getReverseSpeed(edge.getFlags(encoderIndex)) : getFlagEncoder().getSpeed(edge.getFlags(encoderIndex));
        if (normal_speed == 0)
            return Double.POSITIVE_INFINITY;

    
	    AvoidEdgeInfo ei = forbiddenEdges.get(edge.getEdge());
		if (ei!= null){

			short[] codes = ei.getCodes();
			TrafficEventInfo tec = null;
			double givenSpeed = Double.MAX_VALUE;
			double speedFactor = 1;
			double givenDelay = -1;
			for (int i = 0; i < codes.length; i++) {
				int code = codes[i];
				tec = TmcEventCodesTable.getEventInfo(code);
				
				if ((tec.getTmcMode() == TmcMode.HEAVY_VEHICLE) && (flagEncoder instanceof CarFlagEncoder))
					continue; 				
				
				if (tec!=null) {
					if (tec.isDelay()) {
                        // use the max delay in the routing 
						givenDelay = Math.max(givenDelay, tec.getDelay());
					}  

					if(tec.getSpeedFactor()>1){

						givenSpeed = Math.min(givenSpeed, tec.getSpeedFactor());

					} else {
   
						speedFactor = Math.min(speedFactor, tec.getSpeedFactor()); 

					}

				} // end for tec null 
			} // end for codes
			

		
			if (givenDelay > 0 ){
				
	            return givenDelay * 60 + calcTravelTimeInSec(edge.getDistance(), normal_speed); 			
	       
			} else if (givenSpeed < Double.MAX_VALUE){
			
				return calcTravelTimeInSec(edge.getDistance(), givenSpeed); 		
					
			} else if (speedFactor <= 1){
				
				return calcTravelTimeInSec(edge.getDistance(), speedFactor * normal_speed); 	

			} else {
				
				 System.err.println("traffic weighting method didn't give the weight");
				 throw new IllegalStateException("edge " + edge.getOriginalEdge() + 
						   "has no considered event codes " + ei.getCodesAsString());
			}
		}
		
		// if AovidFeatureInfo is null
		double weight  = calcTravelTimeInSec(edge.getDistance(), normal_speed);
		return weight; 
		
    }
    
    private double calcTravelTimeInSec (double distance, double speed) {
    	return distance *3600 / (1000 * speed);
    }

	@Override
	public String getName() {
		return "traffic_avoiding_weighting";
	}
}