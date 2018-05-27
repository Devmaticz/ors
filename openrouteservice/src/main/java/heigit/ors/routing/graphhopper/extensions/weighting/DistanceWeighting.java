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

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;

public class DistanceWeighting extends AbstractWeighting
{
    protected final FlagEncoder flagEncoder;
    private int encoderIndex = -1;
    
    public DistanceWeighting( FlagEncoder encoder, PMap pMap )
    {
        super(encoder);

        this.flagEncoder = encoder;
        this.encoderIndex = encoder.getIndex();
    }

    public DistanceWeighting( FlagEncoder encoder )
    {
        this(encoder, new PMap(0));
    }

    public DistanceWeighting(double userMaxSpeed, FlagEncoder encoder)
    {
    	this(encoder);
    }
    
    @Override
    public double calcWeight(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId)
    {
        double speed = reverse ? flagEncoder.getReverseSpeed(edge.getFlags(encoderIndex)) : flagEncoder.getSpeed(edge.getFlags(encoderIndex));
        if (speed == 0)
            return Double.POSITIVE_INFINITY;

       return edge.getDistance();
    }

	@Override
	public double getMinWeight(double distance) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getName() {
		return "distance";
	}
}
