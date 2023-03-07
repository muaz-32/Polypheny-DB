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

package org.polypheny.db.languages;

import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.polypheny.db.algebra.AlgDecorrelator;
import org.polypheny.db.algebra.AlgRoot;
import org.polypheny.db.algebra.constant.ExplainFormat;
import org.polypheny.db.algebra.constant.ExplainLevel;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.logistic.Pattern;
import org.polypheny.db.languages.mql.MqlCollectionStatement;
import org.polypheny.db.languages.mql.MqlCreateCollection;
import org.polypheny.db.languages.mql.MqlNode;
import org.polypheny.db.languages.mql.MqlQueryParameters;
import org.polypheny.db.languages.mql.parser.MqlParser;
import org.polypheny.db.languages.mql.parser.MqlParser.MqlParserConfig;
import org.polypheny.db.languages.mql2alg.MqlToAlgConverter;
import org.polypheny.db.nodes.Node;
import org.polypheny.db.plan.AlgOptCluster;
import org.polypheny.db.plan.AlgOptUtil;
import org.polypheny.db.processing.AutomaticDdlProcessor;
import org.polypheny.db.rex.RexBuilder;
import org.polypheny.db.tools.AlgBuilder;
import org.polypheny.db.transaction.Lock.LockMode;
import org.polypheny.db.transaction.LockManager;
import org.polypheny.db.transaction.Statement;
import org.polypheny.db.transaction.Transaction;
import org.polypheny.db.transaction.TransactionException;
import org.polypheny.db.transaction.TransactionImpl;
import org.polypheny.db.util.DeadlockException;
import org.polypheny.db.util.Pair;
import org.polypheny.db.util.SourceStringReader;


@Slf4j
public class MqlProcessorImpl extends AutomaticDdlProcessor {

    private static final MqlParserConfig parserConfig;


    static {
        MqlParser.ConfigBuilder configConfigBuilder = MqlParser.configBuilder();
        parserConfig = configConfigBuilder.build();
    }


    @Override
    public List<? extends Node> parse( String mql ) {
        final StopWatch stopWatch = new StopWatch();
        if ( log.isDebugEnabled() ) {
            log.debug( "Parsing PolyMQL statement ..." );
        }
        stopWatch.start();
        MqlNode parsed;
        if ( log.isDebugEnabled() ) {
            log.debug( "MQL: {}", mql );
        }

        try {
            final MqlParser parser = MqlParser.create( new SourceStringReader( mql ), parserConfig );
            parsed = parser.parseStmt();
        } catch ( NodeParseException e ) {
            log.error( "Caught exception", e );
            throw new RuntimeException( e );
        }
        stopWatch.stop();
        if ( log.isTraceEnabled() ) {
            log.trace( "Parsed query: [{}]", parsed );
        }
        if ( log.isDebugEnabled() ) {
            log.debug( "Parsing PolyMQL statement ... done. [{}]", stopWatch );
        }
        return ImmutableList.of( parsed );
    }


    @Override
    public Pair<Node, AlgDataType> validate( Transaction transaction, Node parsed, boolean addDefaultValues ) {
        throw new RuntimeException( "The MQL implementation does not support validation." );
    }


    @Override
    public boolean needsDdlGeneration( Node query, QueryParameters parameters ) {
        if ( query instanceof MqlCollectionStatement ) {
            return Catalog.getInstance()
                    .getNamespaces( Pattern.of( ((MqlQueryParameters) parameters).getDatabase() ) )
                    .stream().flatMap( n -> Catalog.getInstance().getLogicalDoc( n.id ).getCollections( null ).stream() )
                    .noneMatch( t -> t.name.equals( ((MqlCollectionStatement) query).getCollection() ) );
        }
        return false;
    }


    public void autoGenerateDDL( Statement statement, Node query, QueryParameters parameters ) {
        if ( ((MqlCollectionStatement) query).getCollection() == null ) {
            try {
                statement.getTransaction().commit();
            } catch ( TransactionException e ) {
                throw new RuntimeException( "There was a problem auto-generating the needed collection." );
            }

            throw new RuntimeException( "No collections is used." );
        }
        new MqlCreateCollection(
                ParserPos.sum( Collections.singletonList( query ) ),
                ((MqlCollectionStatement) query).getCollection(),
                null
        )
                .execute( statement.getPrepareContext(), statement, parameters );
        try {
            statement.getTransaction().commit();
        } catch ( TransactionException e ) {
            throw new RuntimeException( "There was a problem auto-generating the needed collection." );
        }
    }


    @Override
    public AlgRoot translate( Statement statement, Node mql, QueryParameters parameters ) {
        final StopWatch stopWatch = new StopWatch();
        if ( log.isDebugEnabled() ) {
            log.debug( "Planning Statement ..." );
        }
        stopWatch.start();

        final RexBuilder rexBuilder = new RexBuilder( statement.getTransaction().getTypeFactory() );
        final AlgOptCluster cluster = AlgOptCluster.createDocument( statement.getQueryProcessor().getPlanner(), rexBuilder, statement.getTransaction().getSnapshot() );

        final MqlToAlgConverter mqlToAlgConverter = new MqlToAlgConverter( this, statement.getTransaction().getSnapshot(), cluster );
        AlgRoot logicalRoot = mqlToAlgConverter.convert( mql, parameters );

        // Decorrelate
        final AlgBuilder algBuilder = AlgBuilder.create( statement );
        logicalRoot = logicalRoot.withAlg( AlgDecorrelator.decorrelateQuery( logicalRoot.alg, algBuilder ) );

        if ( log.isTraceEnabled() ) {
            log.trace( "Logical query plan: [{}]", AlgOptUtil.dumpPlan( "-- Logical Plan", logicalRoot.alg, ExplainFormat.TEXT, ExplainLevel.DIGEST_ATTRIBUTES ) );
        }
        stopWatch.stop();
        if ( log.isDebugEnabled() ) {
            log.debug( "Planning Statement ... done. [{}]", stopWatch );
        }

        return logicalRoot;
    }


    @Override
    public void unlock( Statement statement ) {
        LockManager.INSTANCE.unlock( Collections.singletonList( LockManager.GLOBAL_LOCK ), (TransactionImpl) statement.getTransaction() );
    }


    @Override
    public void lock( Statement statement ) throws DeadlockException {
        LockManager.INSTANCE.lock( Collections.singletonList( Pair.of( LockManager.GLOBAL_LOCK, LockMode.EXCLUSIVE ) ), (TransactionImpl) statement.getTransaction() );
    }


    @Override
    public String getQuery( Node parsed, QueryParameters parameters ) {
        return parameters.getQuery();
    }


    @Override
    public AlgDataType getParameterRowType( Node left ) {
        return null;
    }

}
