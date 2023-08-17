/*
 * Copyright 2019-2023 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.catalog.impl.allocation;

import io.activej.serializer.BinarySerializer;
import io.activej.serializer.annotations.Deserialize;
import io.activej.serializer.annotations.Serialize;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.polypheny.db.catalog.IdBuilder;
import org.polypheny.db.catalog.catalogs.AllocationRelationalCatalog;
import org.polypheny.db.catalog.entity.allocation.AllocationColumn;
import org.polypheny.db.catalog.entity.allocation.AllocationPartition;
import org.polypheny.db.catalog.entity.allocation.AllocationPartitionGroup;
import org.polypheny.db.catalog.entity.allocation.AllocationPlacement;
import org.polypheny.db.catalog.entity.allocation.AllocationTable;
import org.polypheny.db.catalog.entity.logical.LogicalNamespace;
import org.polypheny.db.catalog.logistic.DataPlacementRole;
import org.polypheny.db.catalog.logistic.PartitionType;
import org.polypheny.db.catalog.logistic.PlacementType;
import org.polypheny.db.partition.properties.PartitionProperty;
import org.polypheny.db.type.PolySerializable;
import org.polypheny.db.util.Pair;

@Slf4j
@Value
public class PolyAllocRelCatalog implements AllocationRelationalCatalog, PolySerializable {


    IdBuilder idBuilder = IdBuilder.getInstance();

    @Getter
    @Serialize
    public LogicalNamespace namespace;

    @Getter
    public BinarySerializer<PolyAllocRelCatalog> serializer = PolySerializable.buildSerializer( PolyAllocRelCatalog.class );

    @Serialize
    @Getter
    public ConcurrentHashMap<Long, AllocationTable> tables;

    @Serialize
    @Getter
    public ConcurrentHashMap<Pair<Long, Long>, AllocationColumn> columns; //placementId, columnId

    @Serialize
    @Getter
    public ConcurrentHashMap<Long, PartitionProperty> properties;

    @Serialize
    @Getter
    public ConcurrentHashMap<Long, AllocationPartitionGroup> partitionGroups;

    @Serialize
    @Getter
    public ConcurrentHashMap<Long, AllocationPartition> partitions;

    @Serialize
    @Getter
    public ConcurrentHashMap<Long, AllocationPlacement> placements;


    public PolyAllocRelCatalog( LogicalNamespace namespace ) {
        this(
                namespace,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>() );
    }


    public PolyAllocRelCatalog(
            @Deserialize("namespace") LogicalNamespace namespace,
            @Deserialize("tables") Map<Long, AllocationTable> tables,
            @Deserialize("columns") Map<Pair<Long, Long>, AllocationColumn> columns,
            @Deserialize("partitionGroups") Map<Long, AllocationPartitionGroup> partitionGroups,
            @Deserialize("partitions") Map<Long, AllocationPartition> partitions,
            @Deserialize("properties") Map<Long, PartitionProperty> properties,
            @Deserialize("placements") Map<Long, AllocationPlacement> placements ) {
        this.namespace = namespace;
        this.tables = new ConcurrentHashMap<>( tables );
        this.columns = new ConcurrentHashMap<>( columns );
        this.partitionGroups = new ConcurrentHashMap<>( partitionGroups );
        this.partitions = new ConcurrentHashMap<>( partitions );
        this.properties = new ConcurrentHashMap<>( properties );
        this.placements = new ConcurrentHashMap<>( placements );
    }


    @Override
    public PolySerializable copy() {
        return PolySerializable.deserialize( serialize(), PolyAllocRelCatalog.class );
    }

    // move to Snapshot


    @Override
    public AllocationColumn addColumn( long placementId, long logicalTableId, long columnId, long adapterId, PlacementType placementType, int position ) {
        AllocationColumn column = new AllocationColumn(
                namespace.id,
                placementId,
                logicalTableId,
                columnId,
                placementType,
                position,
                adapterId );

        columns.put( Pair.of( placementId, columnId ), column );
        return column;
    }


    @Override
    public void deleteColumn( long placementId, long columnId ) {
        columns.remove( Pair.of( placementId, columnId ) );
    }


    @Override
    public void updateColumnPlacementType( long placementId, long columnId, PlacementType placementType ) {
        AllocationColumn column = columns.get( Pair.of( placementId, columnId ) ).toBuilder().placementType( placementType ).build();
        columns.put( Pair.of( placementId, columnId ), column );
    }


    @Override
    public AllocationPartitionGroup addPartitionGroup( long tableId, String partitionGroupName, long namespaceId, PartitionType partitionType, long numberOfInternalPartitions, List<String> effectivePartitionGroupQualifier, boolean isUnbound ) {
        long id = idBuilder.getNewGroupId();
        if ( log.isDebugEnabled() ) {
            log.debug( "Creating partitionGroup of type '{}' with id '{}'", partitionType, id );
        }

        AllocationPartitionGroup partitionGroup = new AllocationPartitionGroup(
                id,
                partitionGroupName,
                tableId,
                namespaceId,
                0,
                null,
                isUnbound );

        partitionGroups.put( id, partitionGroup );
        return partitionGroup;
    }


    @Override
    public void deletePartitionGroup( long groupId ) {
        partitionGroups.remove( groupId );
    }


    @Override
    public AllocationPartition addPartition( long tableId, long namespaceId, long partitionGroupId, @Nullable String name, boolean isUnbound, PlacementType placementType, DataPlacementRole role ) {
        long id = idBuilder.getNewPartitionId();
        if ( log.isDebugEnabled() ) {
            log.debug( "Creating partition with id '{}'", id );
        }

        AllocationPartition partition = new AllocationPartition(
                id,
                namespaceId,
                tableId,
                placementType,
                name,
                role,
                isUnbound,
                partitionGroupId );

        partitions.put( id, partition );
        return partition;
    }


    @Override
    public void deletePartition( long partitionId ) {
        partitions.remove( partitionId );
    }


    @Override
    public void addPartitionProperty( long tableId, PartitionProperty partitionProperty ) {
        properties.put( tableId, partitionProperty );
    }


    @Override
    public void partitionTable( long tableId, PartitionProperty partitionProperty ) {
        properties.put( partitionProperty.entityId, partitionProperty );

        /*if ( partitionProperty.reliesOnPeriodicChecks ) {
            addTableToPeriodicProcessing( tableId );
        }*/
    }


    @Override
    public void mergeTable( long tableId ) {

    }


    @Override
    public void updatePartition( long partitionId, Long partitionGroupId ) {

    }


    @Override
    public AllocationPartition addPartition( long namespaceId, long adapterId, long tableId, PlacementType placementType, DataPlacementRole role ) {
        /*long id = idBuilder.getNewPartitionId();
        AllocationPartitionOld partition = new AllocationPartitionOld(
                id,
                namespaceId,
                tableId,
                adapterId,
                placementType,
                role );

        allocationPartitions.put( Pair.of( adapterId, id ), partition );
        return partition;*
         */
        throw new NotImplementedException();
    }


    @Override
    public AllocationTable addAllocation( long adapterId, long placementId, long partitionId, long logicalId ) {
        long id = idBuilder.getNewAllocId();
        AllocationTable table = new AllocationTable( id, placementId, partitionId, logicalId, namespace.id, adapterId );
        tables.put( id, table );
        return table;
    }


    @Override
    public void deleteAllocation( long allocId ) {
        tables.remove( allocId );

    }


    @Override
    public void updateDataPlacement( long adapterId, long tableId, List<Long> columnIds, List<Long> partitionIds ) {

    }


    @Override
    public void deletePartitionPlacement( long adapterId, long partitionId ) {

    }


    @Override
    public AllocationPlacement addPlacement( long logicalEntityId, long namespaceId, long adapterId ) {
        long id = idBuilder.getNewPlacementId();
        AllocationPlacement placement = new AllocationPlacement( id, logicalEntityId, namespaceId, adapterId );

        placements.put( id, placement );
        return placement;
    }


    @Override
    public void deletePlacement( long id ) {
        placements.remove( id );
    }


    @Override
    public void deleteProperty( long id ) {
        properties.remove( id );
    }


}
