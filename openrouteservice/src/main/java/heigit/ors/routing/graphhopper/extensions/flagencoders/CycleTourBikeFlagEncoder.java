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
package heigit.ors.routing.graphhopper.extensions.flagencoders;

import static com.graphhopper.routing.util.PriorityCode.AVOID_AT_ALL_COSTS;
import static com.graphhopper.routing.util.PriorityCode.AVOID_IF_POSSIBLE;
import static com.graphhopper.routing.util.PriorityCode.PREFER;
import static com.graphhopper.routing.util.PriorityCode.REACH_DEST;
import static com.graphhopper.routing.util.PriorityCode.UNCHANGED;
import static com.graphhopper.routing.util.PriorityCode.VERY_NICE;
import static com.graphhopper.routing.util.PriorityCode.BEST;

import java.util.TreeMap;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;

public class CycleTourBikeFlagEncoder extends BikeCommonFlagEncoder {
	public CycleTourBikeFlagEncoder() {
		super(4, 2, 0, false);
	}

	public CycleTourBikeFlagEncoder(PMap configuration) {
		super(configuration.getInt("speed_bits", 4) + (configuration.getBool("consider_elevation", false) ? 1 : 0),
				configuration.getDouble("speed_factor", 2),
				configuration.getBool("turn_costs", false) ? 3 : 0, 
						configuration.getBool("consider_elevation", false));

		setBlockFords(false);
		this.setBlockFords(configuration.getBool("block_fords", true));

		setCyclingNetworkPreference("icn", BEST.getValue());
		setCyclingNetworkPreference("ncn", BEST.getValue());
		setCyclingNetworkPreference("rcn", BEST.getValue());
		setCyclingNetworkPreference("lcn", BEST.getValue());

		// addPushingSection("path"); // Runge Assume that paths are suitable
		// for cycle tours.
		addPushingSection("footway");
		addPushingSection("pedestrian");
		addPushingSection("steps");

		avoidHighwayTags.clear();
		avoidHighwayTags.add("motorway");
		avoidHighwayTags.add("motorway_link");
		avoidHighwayTags.add("trunk");
		avoidHighwayTags.add("trunk_link");
		avoidHighwayTags.add("primary");
		avoidHighwayTags.add("primary_link");
		avoidHighwayTags.add("secondary");
		avoidHighwayTags.add("secondary_link");

		// preferHighwayTags.add("road");
		preferHighwayTags.add("path");
		preferHighwayTags.add("service");
		preferHighwayTags.add("residential");
		preferHighwayTags.add("unclassified");
		preferHighwayTags.add("tertiary");
		preferHighwayTags.add("tertiary_link");

		setAvoidSpeedLimit(61);

		setSpecificClassBicycle("touring");

		init();
	}

	@Override
	protected boolean isPushingSection(ReaderWay way) {
		String highway = way.getTag("highway");
		String trackType = way.getTag("tracktype");
		return way.hasTag("highway", pushingSectionsHighways) || way.hasTag("railway", "platform")  || way.hasTag("route", ferries) 
				|| "track".equals(highway) && trackType != null
				&&  !("grade1".equals(trackType) || "grade2".equals(trackType) || "grade3".equals(trackType));
	}

	protected void collect(ReaderWay way, double wayTypeSpeed, TreeMap<Double, Integer> weightToPrioMap) { // Runge
		String service = way.getTag("service");
		String highway = way.getTag("highway");
		if (way.hasTag("bicycle", "designated"))
			weightToPrioMap.put(100d, VERY_NICE.getValue());
		if ("cycleway".equals(highway))
			weightToPrioMap.put(100d, BEST.getValue());

		double maxSpeed = getMaxSpeed(way);

		String cycleway = getCycleway(way); // Runge
		if (!Helper.isEmpty(cycleway) && (cycleway.equals("track") || cycleway.equals("lane")))
		{
			if (maxSpeed <= 50)
				weightToPrioMap.put(90d, VERY_NICE.getValue());
			else if (maxSpeed > 50 && maxSpeed < avoidSpeedLimit)
				weightToPrioMap.put(50d, AVOID_IF_POSSIBLE.getValue());
			else if (maxSpeed >= AVOID_AT_ALL_COSTS.getValue())
				weightToPrioMap.put(50d, REACH_DEST.getValue());
		}

		if (preferHighwayTags.contains(highway) || maxSpeed > 0 && maxSpeed <= 30) {
			if (maxSpeed >= avoidSpeedLimit) // Runge
				weightToPrioMap.put(55d, AVOID_AT_ALL_COSTS.getValue());
			else if (maxSpeed >= 50 && avoidSpeedLimit <= 70)
				weightToPrioMap.put(40d, AVOID_IF_POSSIBLE.getValue());
			else {
				// special case for highway=path
				if ("path".equals(highway))
					weightToPrioMap.put(40d, AVOID_IF_POSSIBLE.getValue());
				else
				{
					if (maxSpeed >= 50)
						weightToPrioMap.put(40d, UNCHANGED.getValue());
					else
						weightToPrioMap.put(40d, PREFER.getValue());
				}
			}

			if (way.hasTag("tunnel", intendedValues))
				weightToPrioMap.put(40d, AVOID_IF_POSSIBLE.getValue());
		} else {
			if ("track".equals(highway)) {
				String trackType = way.getTag("tracktype");

				if (trackType == null || "grade1".equals(trackType) || "grade2".equals(trackType)) {
					weightToPrioMap.put(40d, UNCHANGED.getValue());
				} else if ("grade2".equals(trackType) || "grade3".equals(trackType)) {
					weightToPrioMap.put(40d, AVOID_IF_POSSIBLE.getValue());
				} else {
					weightToPrioMap.put(40d, REACH_DEST.getValue());
				}
			}
		}

		if (pushingSectionsHighways.contains(highway) || way.hasTag("bicycle", "use_sidepath")
				|| "parking_aisle".equals(service)) {
			weightToPrioMap.put(50d, AVOID_IF_POSSIBLE.getValue());
		}
		if (avoidHighwayTags.contains(highway) || ((maxSpeed >= avoidSpeedLimit) && (highway != "track"))) {
			weightToPrioMap.put(50d, REACH_DEST.getValue());
			if (way.hasTag("tunnel", intendedValues))
				weightToPrioMap.put(50d, AVOID_AT_ALL_COSTS.getValue());
		}
		if (way.hasTag("railway", "tram"))
			weightToPrioMap.put(50d, AVOID_AT_ALL_COSTS.getValue());

		String classBicycleSpecific = way.getTag(classBicycleKey);
		if (classBicycleSpecific != null) {
			// We assume that humans are better in classifying preferences
			// compared to our algorithm above -> weight = 100
			weightToPrioMap.put(100d, convertClassValueToPriority(classBicycleSpecific).getValue());
		} else {
			String classBicycle = way.getTag("class:bicycle");
			if (classBicycle != null) {
				weightToPrioMap.put(100d, convertClassValueToPriority(classBicycle).getValue());
			}
		}
	}

	@Override
	public int getVersion() {
		return 2;
	}

	@Override
	protected double getDownhillMaxSpeed()
	{
		return 50;
	}

	@Override
	public String toString() {
		return "cycletourbike";
	}
}
