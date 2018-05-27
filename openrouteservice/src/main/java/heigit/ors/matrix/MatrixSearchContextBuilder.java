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
package heigit.ors.matrix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.ByteArrayBuffer;
import com.graphhopper.util.shapes.GHPoint3D;
import com.vividsolutions.jts.geom.Coordinate;

public class MatrixSearchContextBuilder {
	private Map<Coordinate, LocationEntry> _locationCache;
	private boolean _resolveNames;
	private LocationIndex _locIndex;
	private EdgeFilter _edgeFilter;
	private ByteArrayBuffer _buffer;

	class LocationEntry
	{
		public int nodeId;
		public ResolvedLocation location;
		public QueryResult queryResult;
	}

	public MatrixSearchContextBuilder(LocationIndex index, EdgeFilter edgeFilter, ByteArrayBuffer buffer, boolean resolveNames)
	{
		_locIndex = index;
		_edgeFilter = edgeFilter;
		_buffer = buffer;
		_resolveNames = resolveNames;
	}

	public MatrixSearchContext create(Graph graph, Coordinate[] sources, Coordinate[] destinations, double maxSearchRadius) throws Exception
	{
		if (_locationCache == null)
			_locationCache = new HashMap<Coordinate, LocationEntry>();
		else
			_locationCache.clear();
	
		QueryGraph queryGraph = new QueryGraph(graph);
		List<QueryResult> queryResults = new ArrayList<QueryResult>(sources.length + destinations.length);
		
		resolveLocations(sources, queryResults, maxSearchRadius);
		resolveLocations(destinations, queryResults, maxSearchRadius);

		queryGraph.lookup(queryResults, _buffer);
		
		MatrixLocations mlSources = createLocations(sources);
		MatrixLocations mlDestinations = createLocations(destinations);
		
		return new  MatrixSearchContext(queryGraph, mlSources, mlDestinations);
	}
	
	private void resolveLocations(Coordinate[] coords, List<QueryResult> queryResults, double maxSearchRadius)
	{
		Coordinate p = null;
		
		for (int i = 0; i < coords.length; i++)
		{
			p = coords[i];

			LocationEntry ld = _locationCache.get(p);
			if (ld == null)
			{  
				QueryResult qr = _locIndex.findClosest(p.y, p.x, _edgeFilter, _buffer);
				
				ld = new LocationEntry();
				ld.queryResult = qr;
				
				if (qr.isValid() && qr.getQueryDistance() < maxSearchRadius)
				{
					GHPoint3D pt = qr.getSnappedPoint();
					ld.nodeId = qr.getClosestNode();
					ld.location = new ResolvedLocation(new Coordinate(pt.getLon(), pt.getLat()), _resolveNames ? qr.getClosestEdge().getName(): null, qr.getQueryDistance());

					queryResults.add(qr);
				}
				else
				{
					ld.nodeId = -1;
				}

				_locationCache.put(p, ld);
			}
		}
	}
 	
	private MatrixLocations createLocations(Coordinate[] coords) throws Exception
	{
		MatrixLocations mlRes = new MatrixLocations(coords.length, _resolveNames);
		
		Coordinate p = null;
		
		for (int i = 0; i < coords.length; i++)
		{
			p = coords[i];

			LocationEntry ld = _locationCache.get(p);
			if (ld != null)
				mlRes.setData(i, ld.nodeId == -1 ? -1 : ld.queryResult.getClosestNode(), ld.location);
			else
			{  
				throw new Exception("Oops!");
			}
		}
		
		return mlRes;
	}
}
