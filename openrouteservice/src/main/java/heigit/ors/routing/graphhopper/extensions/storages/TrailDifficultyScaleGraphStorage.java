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
package heigit.ors.routing.graphhopper.extensions.storages;

import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphExtension;

public class TrailDifficultyScaleGraphStorage implements GraphExtension {
	protected final int NO_ENTRY = -1;
	protected final int EF_DIFFICULTY_SCALE;

	protected DataAccess edges;
	protected int edgeEntryIndex = 0;
	protected int edgeEntryBytes;
	protected int edgesCount; 
	private byte[] byteValues;

	public TrailDifficultyScaleGraphStorage() 
	{
		EF_DIFFICULTY_SCALE = nextBlockEntryIndex (2);

		edgeEntryBytes = edgeEntryIndex;
		edgesCount = 0;
		byteValues = new byte[2];
	}

	public void init(Graph graph, Directory dir) {
		if (edgesCount > 0)
			throw new AssertionError("The ext_traildifficulty storage must be initialized only once.");

		this.edges = dir.find("ext_traildifficulty");
	}

	protected final int nextBlockEntryIndex(int size) {
		int res = edgeEntryIndex;
		edgeEntryIndex += size;
		return res;
	}

	public void setSegmentSize(int bytes) {
		edges.setSegmentSize(bytes);
	}

	public GraphExtension create(long initBytes) {
		edges.create((long) initBytes * edgeEntryBytes);
		return this;
	}

	public void flush() {
		edges.setHeader(0, edgeEntryBytes);
		edges.setHeader(1 * 4, edgesCount);
		edges.flush();
	}

	public void close() {
		edges.close();
	}

	public long getCapacity() {
		return edges.getCapacity();
	}

	public int entries() {
		return edgesCount;
	}

	public boolean loadExisting() {
		if (!edges.loadExisting())
			throw new IllegalStateException("Unable to load storage 'ext_traildifficulty'. corrupt file or directory? ");

		edgeEntryBytes = edges.getHeader(0);
		edgesCount = edges.getHeader(4);
		return true;
	}

	void ensureEdgesIndex(int edgeIndex) {
		edges.ensureCapacity(((long) edgeIndex + 1) * edgeEntryBytes);
	}

	public void setEdgeValue(int edgeId, int sacScale, int mtbScale, int mtbUphillScale) {
		edgesCount++;
		ensureEdgesIndex(edgeId);

		long edgePointer = (long) edgeId * edgeEntryBytes;
 
		byteValues[0] = (byte)sacScale;
		byteValues[1] = (byte)(mtbScale << 4 | (0x0F & mtbUphillScale));
		
		edges.setBytes(edgePointer + EF_DIFFICULTY_SCALE, byteValues, 2);
	}

	public int getHikingScale(int edgeId, byte[] buffer) {
		long edgeBase = (long) edgeId * edgeEntryBytes;
		
		edges.getBytes(edgeBase + EF_DIFFICULTY_SCALE, buffer, 1);

		return buffer[0];
	}
	
	public int getMtbScale(int edgeId, byte[] buffer, boolean uphill) {
		long edgeBase = (long) edgeId * edgeEntryBytes;
		
		edges.getBytes(edgeBase + EF_DIFFICULTY_SCALE + 1, buffer, 1);
		
		if (uphill)
			return  (byte)(buffer[0] & 0x0F);
		else
			return (byte)((buffer[0] >> 4) & (byte) 0x0F);
	}

	public boolean isRequireNodeField() {
		return true;
	}

	public boolean isRequireEdgeField() {
		// we require the additional field in the graph to point to the first
		// entry in the node table
		return true;
	}

	public int getDefaultNodeFieldValue() {
		return -1;
		//		throw new UnsupportedOperationException("Not supported by this storage");
	}

	public int getDefaultEdgeFieldValue() {
		return -1;
	}

	public GraphExtension copyTo(GraphExtension clonedStorage) {
		if (!(clonedStorage instanceof TrailDifficultyScaleGraphStorage)) {
			throw new IllegalStateException("the extended storage to clone must be the same");
		}

		TrailDifficultyScaleGraphStorage clonedTC = (TrailDifficultyScaleGraphStorage) clonedStorage;

		edges.copyTo(clonedTC.edges);
		clonedTC.edgesCount = edgesCount;

		return clonedStorage;
	}

	@Override
	public boolean isClosed() {
		// TODO Auto-generated method stub
		return false;
	}
}
