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

package org.polypheny.db.adapter.ethereum;


import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.polypheny.db.adapter.DataSource.ExportedColumn;
import org.polypheny.db.ddl.DdlManager.FieldInformation;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.datatypes.Event;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.EthLog.LogResult;
import org.web3j.protocol.http.HttpService;

// TODO evtl.: Man könnte es noch weiter abtrennen. Jedes Event hat einen Cache. Bzw. jedes Event macht sein eigenes caching (hat seine eigen URL)
// könnte evtl. logistisch für Java einfacher sein.
@Slf4j // library to use logging annotations
public class EventCache {


    private final int batchSizeInBlocks;
    private final Map<Event, List<EthLog.LogResult>> cache = new ConcurrentHashMap<>(); // a cache for each event
    private final List<Event> events; // maintain a list of events
    private final String smartContractAddress;
    private final BigInteger fromBlock;
    private final BigInteger toBlock;
    protected final Web3j web3j;

    public final int sourceAdapterId;
    private final Map<String, List<ExportedColumn>> columns;
    private final int targetAdapterId;


    public EventCache( int sourceAdapterId, int targetAdapterId, String clientUrl, int batchSizeInBlocks, String smartContractAddress, BigInteger fromBlock, BigInteger toBlock, List<Event> events, Map<String, List<ExportedColumn>> columns ) {
        this.sourceAdapterId = sourceAdapterId;
        this.targetAdapterId = targetAdapterId;
        this.columns = columns;
        this.batchSizeInBlocks = batchSizeInBlocks;
        this.smartContractAddress = smartContractAddress;
        this.fromBlock = fromBlock;
        this.toBlock = toBlock;
        this.events = events;
        events.forEach( event -> this.cache.put( event, new ArrayList<>() ) );
        this.web3j = Web3j.build( new HttpService( clientUrl ) );
    }

    // jede event hat ein cache evtl -> evtl einfacher für logik


    public void initializeCaching() {
        // register table in schema
        this.createSchema();
        // start caching
        this.startCaching();
    }


    // In this method, we create the appropriate schemas and tables in the catalog. (see also createTable)
    private void createSchema() {
        columns.remove("block");
        columns.remove("transaction");
        // todo: block and trx columns are also included. Remove?
        Map<String, List<FieldInformation>> columnInformations = columns.entrySet()
                .stream()
                .collect(
                        Collectors.toMap(
                                table -> EthereumPlugin.HIDDEN_PREFIX + table.getKey(), // we prepend this to hide the table to the user
                                table -> table.getValue()
                                        .stream()
                                        .map( ExportedColumn::toFieldInformation )
                                        .collect( Collectors.toList() ) ) );

        EventCacheManager.getInstance().createTables( sourceAdapterId, columnInformations, targetAdapterId );
    }


    public void startCaching() {
        log.warn( "start to cache" );
        BigInteger currentBlock = fromBlock;

        while ( currentBlock.compareTo( toBlock ) <= 0 ) {
            BigInteger endBlock = currentBlock.add( BigInteger.valueOf( batchSizeInBlocks ) );
            if ( endBlock.compareTo( toBlock ) > 0 ) {
                endBlock = toBlock;
            }

            log.warn( "from-to: " + currentBlock + " to " + endBlock ); // in production: instead of .warn take .debug

            // for each event fetch logs from block x to block y according to batchSizeInBlocks
            for ( Event event : events ) {
                addToCache( event, currentBlock, endBlock );
            }

            // just another loop for debugging reasons. I will put it in the first loop later on.
            for ( Event event : events ) {
                if (cache.get( event ).size() == 0) {
                    continue;
                }
                String tableName = event.getName().toLowerCase();
                EventCacheManager.getInstance().writeToStore( event, tableName ); // write the event into the store // todo add table name // (T): Question -> e.g "delegateChanged"?
                cache.get( event ).clear(); // clear cache batch
            }

            currentBlock = endBlock.add( BigInteger.ONE ); // avoid overlapping block numbers
        }
    }


    public synchronized void addToCache( Event event, BigInteger startBlock, BigInteger endBlock ) {
        EthFilter filter = new EthFilter(
                DefaultBlockParameter.valueOf( startBlock ),
                DefaultBlockParameter.valueOf( endBlock ),
                smartContractAddress
        );

        filter.addSingleTopic( EventEncoder.encode( event ) );

        try {
            List<EthLog.LogResult> logs = web3j.ethGetLogs( filter ).send().getLogs(); // I think I don't need this: .stream().map( log -> (LogResult<?>) log ).collect( Collectors.toList() );

            // We are still writing to memory with logs & .addAll. Right now we will use the memory space.
            cache.get( event ).addAll( logs );

            // Without using the memory:
            // Directly write to store. How?
            // 1. call getLogs method which returns logs
            // 2. write it directly to the store: writeToStore( getLogs() )
            // This can be done synchronously. David thinks this method is good for my project. This means we don't need the cash Hashmap anymore.

            // or (again using a little bit of memory)
            // use also a hashmap like above, write them into the map (like right now) but this time use multithreading
            // so when one value is put into the map another is written to the store asynchronously

        } catch ( IOException e ) {
            // Handle exception here. Maybe log an error and re-throw, or set `logs` to an empty list.
        }
    }


    public CachingStatus getStatus() {
        throw new NotImplementedException();
    }

}

