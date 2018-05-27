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
package heigit.ors.mapmatching;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.EdgeFilter;

public abstract class AbstractMapMatcher implements MapMatcher {
	protected double _searchRadius = 50;
	protected EdgeFilter _edgeFilter;
	protected GraphHopper _graphHopper;
	
	public void setSearchRadius(double radius)
	{
		_searchRadius = radius;
	}
	
	public void setEdgeFilter(EdgeFilter edgeFilter)
	{
		_edgeFilter = edgeFilter;
	}
	
	public void setGraphHopper(GraphHopper gh)
	{
		_graphHopper = gh;
	}
	
	public RouteSegmentInfo match(double lat0, double lon0, double lat1, double lon1)
	{
		return null;
	}
}
