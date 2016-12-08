/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.storage;

import com.graphhopper.util.EdgeIteratorState;

/**
 * Holds road attributes for each edge. The additional field of an edge will be used to point
 * towards the first entry within a road attribute table to identify applicable road attributes.
 * <p>
 *
 * @author Tuan Nguyen
 */
public class RoadAttributeExtension implements GraphExtension {

    public enum Type {
        OSMID(0),
        HEIGHT(1),
        WIDTH(2),
        LENGTH(3),
        WEIGHT(4);

        private int code;

        private Type(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        public Type fromCode(int code) {
            for (Type type: values()) {
                if (code == type.getCode()) return type;
            }

            return null;
        }
    }

    /* pointer for no attribute entry */
    private static final int NO_ATTRIBUTE_ENTRY = -1;
    private static final int EMPTY_ATTRIBUTE = 0;

    /*
     * items in turn cost tables: edge from, edge to, getCosts, pointer to next
     * cost entry of same node
     */
    private final int RA_TYPE, RA_VALUE, RA_NEXT;

    private DataAccess roadAttributes;
    private int roadAttributesEntryIndex = -4;
    private int roadAttributesEntryBytes;
    private int roadAttributesCount;

    public RoadAttributeExtension() {
        RA_TYPE = nextRoadAttributeEntryIndex();
        RA_VALUE = nextRoadAttributeEntryIndex();
        RA_NEXT = nextRoadAttributeEntryIndex();
        roadAttributesEntryBytes = roadAttributesEntryIndex + 4;
        roadAttributesCount = 0;
    }

    @Override
    public void init(Graph graph, Directory dir) {
        if (roadAttributesCount > 0)
            throw new AssertionError("The road attribute storage must be initialized only once.");

        this.roadAttributes = dir.find("road_attributes");
    }

    private int nextRoadAttributeEntryIndex() {
        roadAttributesEntryIndex += 4;
        return roadAttributesEntryIndex;
    }

    @Override
    public void setSegmentSize(int bytes) {
        roadAttributes.setSegmentSize( bytes );
    }

    @Override
    public RoadAttributeExtension create(long initBytes) {
        roadAttributes.create( (long) initBytes * roadAttributesEntryBytes );
        return this;
    }

    @Override
    public void flush() {
        roadAttributes.setHeader(0, roadAttributesEntryBytes);
        roadAttributes.setHeader(1 * 4, roadAttributesCount);
        roadAttributes.flush();
    }

    @Override
    public void close() {
        roadAttributes.close();
    }

    @Override
    public long getCapacity() {
        return roadAttributes.getCapacity();
    }

    @Override
    public boolean loadExisting() {
        if (!roadAttributes.loadExisting())
            return false;

        roadAttributesEntryBytes = roadAttributes.getHeader(0);
        roadAttributesCount = roadAttributes.getHeader(4);
        return true;
    }

    /**
     * This method adds a new entry which is a road attribute
     */
    public void addRoadAttribute(EdgeIteratorState edge, Type type, int attribute) {
        // no need to store attribute
        if (attribute == EMPTY_ATTRIBUTE)
            return;

        // append
        int newEntryIndex = roadAttributesCount;
        roadAttributesCount++;
        ensureRoadAttributeIndex(newEntryIndex);

        // determine if we already have an cost entry for this node
        int previousEntryIndex = edge.getAdditionalField();
        if (previousEntryIndex == NO_ATTRIBUTE_ENTRY) {
            // set value to this new road entry
            edge.setAdditionalField( newEntryIndex );
        } else {
            int i = 0;
            int tmp = previousEntryIndex;
            while ((tmp = roadAttributes.getInt((long) tmp * roadAttributesEntryBytes + RA_NEXT)) != NO_ATTRIBUTE_ENTRY) {
                previousEntryIndex = tmp;
                // search for the last added cost entry
                if (i++ > 1000) {
                    throw new IllegalStateException("Something unexpected happened. An edge probably will not have 1000+ attributes.");
                }
            }
            // set next-pointer to this new attribute entry
            roadAttributes.setInt((long) previousEntryIndex * roadAttributesEntryBytes + RA_NEXT, newEntryIndex);
        }

        // add entry
        long attributesBase = (long) newEntryIndex * roadAttributesEntryBytes;
        roadAttributes.setInt(attributesBase + RA_TYPE, type.getCode());
        roadAttributes.setInt(attributesBase + RA_VALUE, attribute);
        // next-pointer is NO_ATTRIBUTE_ENTRY
        roadAttributes.setInt(attributesBase + RA_NEXT, NO_ATTRIBUTE_ENTRY);
    }

    /**
     * @return turn flags of the specified node and edge properties.
     */
    public long getRoadAttribute(EdgeIteratorState edge, Type type) {
        int attributeIndex = edge.getAdditionalField();
        int i = 0;
        for (; i < 1000; i++) {
            if (attributeIndex == NO_ATTRIBUTE_ENTRY)
                break;
            long attributePtr = (long) attributeIndex * roadAttributesEntryBytes;
            if ( type.getCode() == roadAttributes.getInt(attributePtr + RA_TYPE) ) {
                    return roadAttributes.getInt(attributePtr + RA_VALUE);
            }

            int nextRoadAttributeIndex = roadAttributes.getInt(attributePtr + RA_NEXT);
            if (nextRoadAttributeIndex == attributeIndex)
                throw new IllegalStateException("something went wrong: next entry would be the same");

            attributeIndex = nextRoadAttributeIndex;
        }
        // so many turn restrictions on one node? here is something wrong
        if (i > 1000)
            throw new IllegalStateException("something went wrong: there seems to be no end of the road attribute list!?");
        return EMPTY_ATTRIBUTE;
    }

    private void ensureRoadAttributeIndex(int nodeIndex) {
        roadAttributes.ensureCapacity(((long) nodeIndex + 4) * roadAttributesEntryBytes);
    }

    @Override
    public boolean isRequireNodeField() {
        return false;
    }

    @Override
    public boolean isRequireEdgeField() {
        return true;
    }

    @Override
    public int getDefaultNodeFieldValue() {
        throw new UnsupportedOperationException("Not supported by this storage");
    }

    @Override
    public int getDefaultEdgeFieldValue() {
        return NO_ATTRIBUTE_ENTRY;
    }

    @Override
    public GraphExtension copyTo(GraphExtension clonedStorage) {
        if (!(clonedStorage instanceof RoadAttributeExtension)) {
            throw new IllegalStateException("the extended storage to clone must be the same");
        }

        RoadAttributeExtension clonedTC = (RoadAttributeExtension) clonedStorage;

        roadAttributes.copyTo(clonedTC.roadAttributes);
        clonedTC.roadAttributesCount = roadAttributesCount;

        return clonedStorage;
    }

    @Override
    public boolean isClosed() {
        return roadAttributes.isClosed();
    }

    @Override
    public String toString() {
        return "road_attribute";
    }
}
