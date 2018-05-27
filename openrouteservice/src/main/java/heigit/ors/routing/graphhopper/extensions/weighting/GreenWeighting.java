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
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;

import heigit.ors.routing.graphhopper.extensions.storages.GraphStorageUtils;
import heigit.ors.routing.graphhopper.extensions.storages.GreenIndexGraphStorage;

/**
 * Created by lliu on 15/03/2017.
 */
public class GreenWeighting extends FastestWeighting {
    private GreenIndexGraphStorage _gsGreenIndex;
    private byte[] _buffer = new byte[1];
    private double[] _factors = new double[totalLevel]; 

    private static final int totalLevel = 64;

    public GreenWeighting(FlagEncoder encoder, PMap map, GraphStorage graphStorage) {
        super(encoder, map);
        
        _gsGreenIndex = GraphStorageUtils.getGraphExtension(graphStorage, GreenIndexGraphStorage.class);
        double factor = map.getDouble("factor", 1);
        
        for (int i = 0; i < totalLevel; i++)
        	_factors[i] = calcGreenWeightFactor(i, factor);
    }

    private double calcGreenWeightFactor(int level, double factor) {
        // There is an implicit convention here:
        // the green level range is [0, total - 1].
        // And the @level will be transformed to a float number
        // falling in (0, 2] linearly
        // However, for the final green weighting,
        // a weighting factor will be taken into account
        // to control the impact of the "green consideration"
        // just like an amplifier
        double wf = (double) (level + 1) * 2.0 / totalLevel;
        return 1.0 - (1.0 - wf) * factor;
    }

    @Override
    public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        if (_gsGreenIndex != null) {
            int greenLevel = _gsGreenIndex.getEdgeValue(edgeState.getOriginalEdge(), _buffer);
            return _factors[greenLevel];
        }

        return 1.0;
    }
}
