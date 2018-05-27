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
package heigit.ors.routing.graphhopper.extensions.storages.builders;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import heigit.ors.routing.graphhopper.extensions.storages.GreenIndexGraphStorage;

/**
 * Created by lliu on 13/03/2017.
 */
public class GreenIndexGraphStorageBuilder extends AbstractGraphStorageBuilder {
    private GreenIndexGraphStorage _storage;
    private Map<Long, Double> _greenIndices = new HashMap<>();
    private static int TOTAL_LEVEL = 64;
    private static int DEFAULT_LEVEL = TOTAL_LEVEL - 1;
    private Map<Byte, SlotRange> _slots = new HashMap<>(TOTAL_LEVEL);

    public GreenIndexGraphStorageBuilder() {

    }

    @Override
    public GraphExtension init(GraphHopper graphhopper) throws Exception {
        if (_storage != null)
            throw new Exception("GraphStorageBuilder has been already initialized.");

        // TODO Check if the _greenIndexFile exists
        String csvFile = _parameters.get("filepath");
        readGreenIndicesFromCSV(csvFile);
        prepareGreenIndexSlots();
        _storage = new GreenIndexGraphStorage();

        return _storage;
    }

    private void prepareGreenIndexSlots() {
        double max = Collections.max(_greenIndices.values());
        double min = Collections.min(_greenIndices.values());
        double step = (max - min) / TOTAL_LEVEL;
        // Divide the range of raw green index values into TOTAL_LEVEL,
        // then map the raw value to [0..TOTAL_LEVEL - 1]
        for (byte i = 0; i < TOTAL_LEVEL; i++) {
            _slots.put(i, new SlotRange(min + i * step, min + (i + 1) * step));
        }
    }

    private void readGreenIndicesFromCSV(String csvFile) throws IOException {
        BufferedReader csvBuffer = null;
        
        try {
            String row;
            csvBuffer = new BufferedReader(new FileReader(csvFile));
            // Jump the header line
            row = csvBuffer.readLine();
            char separator = row.contains(";") ? ';': ',';
            String[] rowValues = new String[2];
            
            while ((row = csvBuffer.readLine()) != null) 
            {
                if (!parseCSVrow(row, separator, rowValues)) 
                	continue;
                
                _greenIndices.put(Long.parseLong(rowValues[0]), Double.parseDouble(rowValues[1]));
            }

        } catch (IOException openFileEx) {
            openFileEx.printStackTrace();
            throw openFileEx;
        } finally {
            if (csvBuffer != null) 
            	csvBuffer.close();
        }
    }

    private boolean parseCSVrow(String row, char separator,  String[] rowValues) {
        if (Helper.isEmpty(row))
        	return false;
        
        int pos = row.indexOf(separator);
        if (pos > 0)
        {
        	rowValues[0] = row.substring(0, pos).trim();
        	rowValues[1] = row.substring(pos+1, row.length()).trim();
        	// read, check and push "osm_id" and "ungreen_factor" values
        	if (Helper.isEmpty(rowValues[0]) || Helper.isEmpty(rowValues[1])) 
        		return false;
        	
        	return true;
        }
        else
        	return false;
    }

    @Override
    public void processWay(ReaderWay way) {

    }

    @Override
    public void processEdge(ReaderWay way, EdgeIteratorState edge) {
        _storage.setEdgeValue(edge.getEdge(), calcGreenIndex(way.getId()));
    }

    private class SlotRange {
        double left = 0.0;
        double right = 0.0;

        SlotRange(double l, double r) {
            this.left = l;
            this.right = r;
        }

        boolean within(double val) {
            // check if the @val falls in [left, right] range
            if ((val < left) || (val > right)) return false;
            return true;
        }
    }

    private byte calcGreenIndex(long id) {
        Double gi = _greenIndices.get(id);

        // No such @id key in the _greenIndices, or the value of it is null
        // We set its green level to TOTAL_LEVEL/2 indicating the middle value for such cases
        // TODO this DEFAULT_LEVEL should be put in the app.config file and
        // injected back in the code
        if (gi == null)
            return (byte) (DEFAULT_LEVEL);

        for (Map.Entry<Byte, SlotRange> s : _slots.entrySet()) {
            if (s.getValue().within(gi))
                return s.getKey();
        }
        return (byte) (DEFAULT_LEVEL);
    }

    @Override
    public String getName() {
        return "GreenIndex";
    }
}
