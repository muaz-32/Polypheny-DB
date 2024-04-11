/*
 * Copyright 2019-2024 The Polypheny Project
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
 *
 * This file incorporates code covered by the following terms:
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.algebra.rules;


import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.core.Calc;
import org.polypheny.db.algebra.logical.relational.LogicalCalc;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.algebra.type.AlgDataTypeFactory;
import org.polypheny.db.plan.AlgCluster;
import org.polypheny.db.plan.AlgPlanner;
import org.polypheny.db.plan.AlgTraitSet;
import org.polypheny.db.rex.RexCall;
import org.polypheny.db.rex.RexDynamicParam;
import org.polypheny.db.rex.RexFieldAccess;
import org.polypheny.db.rex.RexIndexRef;
import org.polypheny.db.rex.RexLiteral;
import org.polypheny.db.rex.RexLocalRef;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.rex.RexProgram;
import org.polypheny.db.rex.RexShuttle;
import org.polypheny.db.rex.RexUtil;
import org.polypheny.db.rex.RexVisitorImpl;
import org.polypheny.db.tools.AlgBuilder;
import org.polypheny.db.util.Litmus;
import org.polypheny.db.util.Util;
import org.polypheny.db.util.graph.DefaultDirectedGraph;
import org.polypheny.db.util.graph.DefaultEdge;
import org.polypheny.db.util.graph.DirectedGraph;
import org.polypheny.db.util.graph.TopologicalOrderIterator;
import org.slf4j.Logger;


/**
 * {@link CalcAlgSplitter} operates on a {@link Calc} with multiple {@link RexCall} sub-expressions that cannot all be
 * implemented by a single concrete {@link AlgNode}.
 *
 * For example, the Java and Fennel calculator do not implement an identical set of operators. The Calc can be used to split
 * a single Calc with mixed Java- and Fennel-only operators into a tree of Calc object that can each be individually
 * implemented by either Java or Fennel.and splits it into several Calc instances.
 *
 * Currently, the splitter is only capable of handling two "rel types". That is, it can deal with Java vs. Fennel Calcs,
 * but not Java vs. Fennel vs. some other type of Calc.
 *
 * See {@link ProjectToWindowRule} for an example of how this class is used.
 */
public abstract class CalcAlgSplitter {

    private static final Logger RULE_LOGGER = AlgPlanner.LOGGER;

    protected final RexProgram program;
    private final AlgDataTypeFactory typeFactory;

    private final AlgType[] algTypes;
    private final AlgCluster cluster;
    private final AlgTraitSet traits;
    private final AlgNode child;
    protected final AlgBuilder algBuilder;


    /**
     * Constructs a CalcRelSplitter.
     *
     * @param calc Calc to split
     * @param algTypes Array of alg types, e.g. {Java, Fennel}. Must be distinct.
     */
    CalcAlgSplitter( Calc calc, AlgBuilder algBuilder, AlgType[] algTypes ) {
        this.algBuilder = algBuilder;
        for ( int i = 0; i < algTypes.length; i++ ) {
            assert algTypes[i] != null;
            for ( int j = 0; j < i; j++ ) {
                assert algTypes[i] != algTypes[j] : "Rel types must be distinct";
            }
        }
        this.program = calc.getProgram();
        this.cluster = calc.getCluster();
        this.traits = calc.getTraitSet();
        this.typeFactory = calc.getCluster().getTypeFactory();
        this.child = calc.getInput();
        this.algTypes = algTypes;
    }


    AlgNode execute() {
        // Check that program is valid. In particular, this means that every expression is trivial (either an atom, or a
        // function applied to references to atoms) and every expression depends only on expressions to the left.
        assert program.isValid( Litmus.THROW, null );
        final List<RexNode> exprList = program.getExprList();
        final RexNode[] exprs = exprList.toArray( new RexNode[0] );
        assert !RexUtil.containComplexExprs( exprList );

        // Figure out what level each expression belongs to.
        int[] exprLevels = new int[exprs.length];

        // The type of a level is given by relTypes[levelTypeOrdinals[level]].
        int[] levelTypeOrdinals = new int[exprs.length];

        int levelCount = chooseLevels( exprs, -1, exprLevels, levelTypeOrdinals );

        // For each expression, figure out which is the highest level where it is used.
        int[] exprMaxUsingLevelOrdinals = new HighestUsageFinder( exprs, exprLevels ).getMaxUsingLevelOrdinals();

        // If expressions are used as outputs, mark them as higher than that.
        final List<RexLocalRef> projectRefList = program.getProjectList();
        final RexLocalRef conditionRef = program.getCondition();
        for ( RexLocalRef projectRef : projectRefList ) {
            exprMaxUsingLevelOrdinals[projectRef.getIndex()] = levelCount;
        }
        if ( conditionRef != null ) {
            exprMaxUsingLevelOrdinals[conditionRef.getIndex()] = levelCount;
        }

        // Print out what we've got.
        if ( RULE_LOGGER.isTraceEnabled() ) {
            traceLevelExpressions( exprs, exprLevels, levelTypeOrdinals, levelCount );
        }

        // Now build the calcs.
        AlgNode alg = child;
        final int inputFieldCount = program.getInputRowType().getFieldCount();
        int[] inputExprOrdinals = identityArray( inputFieldCount );
        boolean doneCondition = false;
        for ( int level = 0; level < levelCount; level++ ) {
            final int[] projectExprOrdinals;
            final AlgDataType outputRowType;
            if ( level == (levelCount - 1) ) {
                outputRowType = program.getOutputRowType();
                projectExprOrdinals = new int[projectRefList.size()];
                for ( int i = 0; i < projectExprOrdinals.length; i++ ) {
                    projectExprOrdinals[i] = projectRefList.get( i ).getIndex();
                }
            } else {
                outputRowType = null;

                // Project the expressions which are computed at this level or before, and will be used at later levels.
                List<Integer> projectExprOrdinalList = new ArrayList<>();
                for ( int i = 0; i < exprs.length; i++ ) {
                    RexNode expr = exprs[i];
                    if ( expr instanceof RexLiteral ) {
                        // Don't project literals. They are always created in the level where they are used.
                        exprLevels[i] = -1;
                        continue;
                    }
                    if ( (exprLevels[i] <= level) && (exprMaxUsingLevelOrdinals[i] > level) ) {
                        projectExprOrdinalList.add( i );
                    }
                }
                projectExprOrdinals = Ints.toArray( projectExprOrdinalList );
            }

            final AlgType algType = algTypes[levelTypeOrdinals[level]];

            // Can we do the condition this level?
            int conditionExprOrdinal = -1;
            if ( (conditionRef != null) && !doneCondition ) {
                conditionExprOrdinal = conditionRef.getIndex();
                if ( (exprLevels[conditionExprOrdinal] > level) || !algType.supportsCondition() ) {
                    // stand down -- we're not ready to do the condition yet
                    conditionExprOrdinal = -1;
                } else {
                    doneCondition = true;
                }
            }

            RexProgram program1 =
                    createProgramForLevel(
                            level,
                            levelCount,
                            alg.getTupleType(),
                            exprs,
                            exprLevels,
                            inputExprOrdinals,
                            projectExprOrdinals,
                            conditionExprOrdinal,
                            outputRowType );
            alg = algType.makeRel( cluster, traits, algBuilder, alg, program1 );

            // Sometimes a level's program merely projects its inputs. We don't want these. They cause an explosion in the search space.
            if ( alg instanceof LogicalCalc && ((LogicalCalc) alg).getProgram().isTrivial() ) {
                alg = alg.getInput( 0 );
            }

            alg = handle( alg );

            // The outputs of this level will be the inputs to the next level.
            inputExprOrdinals = projectExprOrdinals;
        }

        Preconditions.checkArgument( doneCondition || (conditionRef == null), "unhandled condition" );
        return alg;
    }


    /**
     * Opportunity to further refine the relational expression created for a given level. The default implementation returns
     * the relational expression unchanged.
     */
    protected AlgNode handle( AlgNode alg ) {
        return alg;
    }


    /**
     * Figures out which expressions to calculate at which level.
     *
     * @param exprs Array of expressions
     * @param conditionOrdinal Ordinal of the condition expression, or -1 if no condition
     * @param exprLevels Level ordinal for each expression (output)
     * @param levelTypeOrdinals The type of each level (output)
     * @return Number of levels required
     */
    private int chooseLevels( final RexNode[] exprs, int conditionOrdinal, int[] exprLevels, int[] levelTypeOrdinals ) {
        final int inputFieldCount = program.getInputRowType().getFieldCount();

        int levelCount = 0;
        final MaxInputFinder maxInputFinder = new MaxInputFinder( exprLevels );
        boolean[] algTypesPossibleForTopLevel = new boolean[algTypes.length];
        Arrays.fill( algTypesPossibleForTopLevel, true );

        // Compute the order in which to visit expressions.
        final List<Set<Integer>> cohorts = getCohorts();
        final List<Integer> permutation = computeTopologicalOrdering( exprs, cohorts );

        for ( int i : permutation ) {
            RexNode expr = exprs[i];
            final boolean condition = i == conditionOrdinal;

            if ( i < inputFieldCount ) {
                assert expr instanceof RexIndexRef;
                exprLevels[i] = -1;
                continue;
            }

            // Deduce the minimum level of the expression. An expression must be at a level greater than or equal to all of its inputs.
            int level = maxInputFinder.maxInputFor( expr );

            // If the expression is in a cohort, it can occur no lower than the levels of other expressions in the same cohort.
            Set<Integer> cohort = findCohort( cohorts, i );
            if ( cohort != null ) {
                for ( Integer exprOrdinal : cohort ) {
                    if ( exprOrdinal == i ) {
                        // Already did this member of the cohort. It's a waste of effort to repeat.
                        continue;
                    }
                    final RexNode cohortExpr = exprs[exprOrdinal];
                    int cohortLevel = maxInputFinder.maxInputFor( cohortExpr );
                    if ( cohortLevel > level ) {
                        level = cohortLevel;
                    }
                }
            }

            // Try to implement this expression at this level.
            // If that is not possible, try to implement it at higher levels.
            levelLoop:
            for ( ; ; ++level ) {
                if ( level >= levelCount ) {
                    // This is a new level. We can use any type we like.
                    for ( int algTypeOrdinal = 0; algTypeOrdinal < algTypes.length; algTypeOrdinal++ ) {
                        if ( !algTypesPossibleForTopLevel[algTypeOrdinal] ) {
                            continue;
                        }
                        if ( algTypes[algTypeOrdinal].canImplement( expr, condition ) ) {
                            // Success. We have found a type where we can implement this expression.
                            exprLevels[i] = level;
                            levelTypeOrdinals[level] = algTypeOrdinal;
                            assert (level == 0) || (levelTypeOrdinals[level - 1] != levelTypeOrdinals[level]) : "successive levels of same type";

                            // Figure out which of the other reltypes are still possible for this level.
                            // Previous reltypes are not possible.
                            for ( int j = 0; j < algTypeOrdinal; ++j ) {
                                algTypesPossibleForTopLevel[j] = false;
                            }

                            // Successive reltypes may be possible.
                            for ( int j = algTypeOrdinal + 1; j < algTypes.length; ++j ) {
                                if ( algTypesPossibleForTopLevel[j] ) {
                                    algTypesPossibleForTopLevel[j] = algTypes[j].canImplement( expr, condition );
                                }
                            }

                            // Move to next level.
                            levelTypeOrdinals[levelCount] = firstSet( algTypesPossibleForTopLevel );
                            ++levelCount;
                            Arrays.fill( algTypesPossibleForTopLevel, true );
                            break levelLoop;
                        }
                    }

                    // None of the reltypes still active for this level could implement expr. But maybe we could succeed with a new level, with all options execute?
                    if ( count( algTypesPossibleForTopLevel ) >= algTypes.length ) {
                        // Cannot implement for any type.
                        throw new AssertionError( "cannot implement " + expr );
                    }
                    levelTypeOrdinals[levelCount] = firstSet( algTypesPossibleForTopLevel );
                    ++levelCount;
                    Arrays.fill( algTypesPossibleForTopLevel, true );
                } else {
                    final int levelTypeOrdinal = levelTypeOrdinals[level];
                    if ( !algTypes[levelTypeOrdinal].canImplement( expr, condition ) ) {
                        // Cannot implement this expression in this type; continue to next level.
                        continue;
                    }
                    exprLevels[i] = level;
                    break;
                }
            }
        }
        if ( levelCount > 0 ) {
            // The latest level should be CalcRelType otherwise literals cannot be implemented.
            assert "CalcRelType".equals( algTypes[0].name ) : "The first RelType should be CalcRelType for proper RexLiteral implementation at the last level, got " + algTypes[0].name;
            if ( levelTypeOrdinals[levelCount - 1] != 0 ) {
                levelCount++;
            }
        }
        return levelCount;
    }


    /**
     * Computes the order in which to visit expressions, so that we decide the level of an expression only after the levels
     * of lower expressions have been decided.
     *
     * First, we need to ensure that an expression is visited after all of its inputs.
     *
     * Further, if the expression is a member of a cohort, we need to visit it after the inputs of all other expressions in
     * that cohort. With this condition, expressions in the same cohort will
     * very likely end up in the same level.
     *
     * Note that if there are no cohorts, the expressions from the {@link RexProgram} are already in a suitable order.
     * We perform the topological sort just to ensure that the code path is well-trodden.
     *
     * @param exprs Expressions
     * @param cohorts List of cohorts, each of which is a set of expr ordinals
     * @return Expression ordinals in topological order
     */
    private List<Integer> computeTopologicalOrdering( RexNode[] exprs, List<Set<Integer>> cohorts ) {
        final DirectedGraph<Integer, DefaultEdge> graph = DefaultDirectedGraph.create();
        for ( int i = 0; i < exprs.length; i++ ) {
            graph.addVertex( i );
        }
        for ( int i = 0; i < exprs.length; i++ ) {
            final RexNode expr = exprs[i];
            final Set<Integer> cohort = findCohort( cohorts, i );
            final Set<Integer> targets;
            if ( cohort == null ) {
                targets = Collections.singleton( i );
            } else {
                targets = cohort;
            }
            expr.accept(
                    new RexVisitorImpl<Void>( true ) {
                        @Override
                        public Void visitLocalRef( RexLocalRef localRef ) {
                            for ( Integer target : targets ) {
                                graph.addEdge( localRef.getIndex(), target );
                            }
                            return null;
                        }
                    } );
        }
        TopologicalOrderIterator<Integer, DefaultEdge> iter = new TopologicalOrderIterator<>( graph );
        final List<Integer> permutation = new ArrayList<>();
        while ( iter.hasNext() ) {
            permutation.add( iter.next() );
        }
        return permutation;
    }


    /**
     * Finds the cohort that contains the given integer, or returns null.
     *
     * @param cohorts List of cohorts, each a set of integers
     * @param ordinal Integer to search for
     * @return Cohort that contains the integer, or null if not found
     */
    private static Set<Integer> findCohort( List<Set<Integer>> cohorts, int ordinal ) {
        for ( Set<Integer> cohort : cohorts ) {
            if ( cohort.contains( ordinal ) ) {
                return cohort;
            }
        }
        return null;
    }


    private int[] identityArray( int length ) {
        final int[] ints = new int[length];
        for ( int i = 0; i < ints.length; i++ ) {
            ints[i] = i;
        }
        return ints;
    }


    /**
     * Creates a program containing the expressions for a given level.
     *
     * The expression list of the program will consist of all entries in the expression list <code>allExprs[i]</code> for
     * which the corresponding level ordinal <code>exprLevels[i]</code> is equal to <code>level</code>.
     * Expressions are mapped according to <code>inputExprOrdinals</code>.
     *
     * @param level Level ordinal
     * @param levelCount Number of levels
     * @param inputRowType Input row type
     * @param allExprs Array of all expressions
     * @param exprLevels Array of the level ordinal of each expression
     * @param inputExprOrdinals Ordinals in the expression list of input expressions. Input expression <code>i</code> will be found at position <code>inputExprOrdinals[i]</code>.
     * @param projectExprOrdinals Ordinals of the expressions to be output this level.
     * @param conditionExprOrdinal Ordinal of the expression to form the condition for this level, or -1 if there is no condition.
     * @param outputRowType Output row type
     * @return Relational expression
     */
    private RexProgram createProgramForLevel( int level, int levelCount, AlgDataType inputRowType, RexNode[] allExprs, int[] exprLevels, int[] inputExprOrdinals, final int[] projectExprOrdinals, int conditionExprOrdinal, AlgDataType outputRowType ) {
        // Build a list of expressions to form the calc.
        List<RexNode> exprs = new ArrayList<>();

        // exprInverseOrdinals describes where an expression in allExprs comes from -- from an input, from a calculated expression, or -1 if not available at this level.
        int[] exprInverseOrdinals = new int[allExprs.length];
        Arrays.fill( exprInverseOrdinals, -1 );
        int j = 0;

        // First populate the inputs. They were computed at some previous level and are used here.
        for ( int i = 0; i < inputExprOrdinals.length; i++ ) {
            final int inputExprOrdinal = inputExprOrdinals[i];
            exprs.add( new RexIndexRef( i, allExprs[inputExprOrdinal].getType() ) );
            exprInverseOrdinals[inputExprOrdinal] = j;
            ++j;
        }

        // Next populate the computed expressions.
        final RexShuttle shuttle = new InputToCommonExprConverter( exprInverseOrdinals, exprLevels, level, inputExprOrdinals, allExprs );
        for ( int i = 0; i < allExprs.length; i++ ) {
            if ( exprLevels[i] == level || exprLevels[i] == -1 && level == (levelCount - 1) && allExprs[i] instanceof RexLiteral ) {
                RexNode expr = allExprs[i];
                final RexNode translatedExpr = expr.accept( shuttle );
                exprs.add( translatedExpr );
                assert exprInverseOrdinals[i] == -1;
                exprInverseOrdinals[i] = j;
                ++j;
            }
        }

        // Form the projection and condition list. Project and condition ordinals are offsets into allExprs, so we need to map them into exprs.
        final List<RexLocalRef> projectRefs = new ArrayList<>( projectExprOrdinals.length );
        final List<String> fieldNames = new ArrayList<>( projectExprOrdinals.length );
        for ( int i = 0; i < projectExprOrdinals.length; i++ ) {
            final int projectExprOrdinal = projectExprOrdinals[i];
            final int index = exprInverseOrdinals[projectExprOrdinal];
            assert index >= 0;
            RexNode expr = allExprs[projectExprOrdinal];
            projectRefs.add( new RexLocalRef( index, expr.getType() ) );

            // Inherit meaningful field name if possible.
            fieldNames.add( deriveFieldName( expr, i ) );
        }
        RexLocalRef conditionRef;
        if ( conditionExprOrdinal >= 0 ) {
            final int index = exprInverseOrdinals[conditionExprOrdinal];
            conditionRef = new RexLocalRef( index, allExprs[conditionExprOrdinal].getType() );
        } else {
            conditionRef = null;
        }
        if ( outputRowType == null ) {
            outputRowType = RexUtil.createStructType( typeFactory, projectRefs, fieldNames, null );
        }
        final RexProgram program = new RexProgram( inputRowType, exprs, projectRefs, conditionRef, outputRowType );
        // Program is NOT normalized here (e.g. can contain literals in call operands), since literals should be inlined.
        return program;
    }


    private String deriveFieldName( RexNode expr, int ordinal ) {
        if ( expr instanceof RexIndexRef ) {
            int inputIndex = ((RexIndexRef) expr).getIndex();
            String fieldName = child.getTupleType().getFields().get( inputIndex ).getName();
            // Don't inherit field names like '$3' from child: that's confusing.
            if ( !fieldName.startsWith( "$" ) || fieldName.startsWith( "$EXPR" ) ) {
                return fieldName;
            }
        }
        return "$" + ordinal;
    }


    /**
     * Traces the given array of level expression lists at the finer level.
     *
     * @param exprs Array expressions
     * @param exprLevels For each expression, the ordinal of its level
     * @param levelTypeOrdinals For each level, the ordinal of its type in the {@link #algTypes} array
     * @param levelCount The number of levels
     */
    private void traceLevelExpressions( RexNode[] exprs, int[] exprLevels, int[] levelTypeOrdinals, int levelCount ) {
        StringWriter traceMsg = new StringWriter();
        PrintWriter traceWriter = new PrintWriter( traceMsg );
        traceWriter.println( "FarragoAutoCalcRule result expressions for: " );
        traceWriter.println( program.toString() );

        for ( int level = 0; level < levelCount; level++ ) {
            traceWriter.println( "Rel Level " + level + ", type " + algTypes[levelTypeOrdinals[level]] );

            for ( int i = 0; i < exprs.length; i++ ) {
                RexNode expr = exprs[i];
                assert (exprLevels[i] >= -1) && (exprLevels[i] < levelCount) : "expression's level is out of range";
                if ( exprLevels[i] == level ) {
                    traceWriter.println( "\t" + i + ": " + expr );
                }
            }
            traceWriter.println();
        }
        String msg = traceMsg.toString();
        RULE_LOGGER.trace( msg );
    }


    /**
     * Returns the number of bits set in an array.
     */
    private static int count( boolean[] booleans ) {
        int count = 0;
        for ( boolean b : booleans ) {
            if ( b ) {
                ++count;
            }
        }
        return count;
    }


    /**
     * Returns the index of the first set bit in an array.
     */
    private static int firstSet( boolean[] booleans ) {
        for ( int i = 0; i < booleans.length; i++ ) {
            if ( booleans[i] ) {
                return i;
            }
        }
        return -1;
    }


    /**
     * Searches for a value in a map, and returns the position where it was found, or -1.
     *
     * @param value Value to search for
     * @param map Map to search in
     * @return Ordinal of value in map, or -1 if not found
     */
    private static int indexOf( int value, int[] map ) {
        for ( int i = 0; i < map.length; i++ ) {
            if ( value == map[i] ) {
                return i;
            }
        }
        return -1;
    }


    /**
     * Returns whether a relational expression can be implemented solely in a given {@link AlgType}.
     *
     * @param alg Calculation relational expression
     * @param algTypeName Name of a {@link AlgType}
     * @return Whether relational expression can be implemented
     */
    protected boolean canImplement( LogicalCalc alg, String algTypeName ) {
        for ( AlgType algType : algTypes ) {
            if ( algType.name.equals( algTypeName ) ) {
                return algType.canImplement( alg.getProgram() );
            }
        }
        throw new AssertionError( "unknown type " + algTypeName );
    }


    /**
     * Returns a list of sets of expressions that should be on the same level.
     *
     * For example, if this method returns { {3, 5}, {4, 7} }, it means that expressions 3 and 5, should be on the same level,
     * and expressions 4 and 7 should be on the same level. The two cohorts do not need to be on the same level.
     *
     * The list is best effort. If it is not possible to arrange that the expressions in a cohort are on the same level,
     * the {@link #execute()} method will still succeed.
     *
     * The default implementation of this method returns the empty list; expressions will be put on the most suitable level.
     * This is generally the lowest possible level, except for literals, which are placed at the level where they are used.
     *
     * @return List of cohorts, that is sets of expressions, that the splitting algorithm should attempt to place on the same level
     */
    protected List<Set<Integer>> getCohorts() {
        return Collections.emptyList();
    }


    /**
     * Type of relational expression. Determines which kinds of expressions it can handle.
     */
    public abstract static class AlgType {

        private final String name;


        public AlgType( String name ) {
            this.name = name;
        }


        public String toString() {
            return name;
        }


        protected abstract boolean canImplement( RexFieldAccess field );

        protected abstract boolean canImplement( RexDynamicParam param );

        protected abstract boolean canImplement( RexLiteral literal );

        protected abstract boolean canImplement( RexCall call );


        protected boolean supportsCondition() {
            return true;
        }


        protected AlgNode makeRel( AlgCluster cluster, AlgTraitSet traitSet, AlgBuilder algBuilder, AlgNode input, RexProgram program ) {
            return LogicalCalc.create( input, program );
        }


        /**
         * Returns whether this <code>RelType</code> can implement a given expression.
         *
         * @param expr Expression
         * @param condition Whether expression is a condition
         * @return Whether this <code>RelType</code> can implement a given expression.
         */
        public boolean canImplement( RexNode expr, boolean condition ) {
            if ( condition && !supportsCondition() ) {
                return false;
            }
            try {
                expr.accept( new ImplementTester( this ) );
                return true;
            } catch ( CannotImplement e ) {
                Util.swallow( e, null );
                return false;
            }
        }


        /**
         * Returns whether this tester's <code>RelType</code> can implement a given program.
         *
         * @param program Program
         * @return Whether this tester's <code>RelType</code> can implement a given program.
         */
        public boolean canImplement( RexProgram program ) {
            if ( (program.getCondition() != null) && !canImplement( program.getCondition(), true ) ) {
                return false;
            }
            for ( RexNode expr : program.getExprList() ) {
                if ( !canImplement( expr, false ) ) {
                    return false;
                }
            }
            return true;
        }

    }


    /**
     * Visitor which returns whether an expression can be implemented in a given type of relational expression.
     */
    private static class ImplementTester extends RexVisitorImpl<Void> {

        private final AlgType algType;


        ImplementTester( AlgType algType ) {
            super( false );
            this.algType = algType;
        }


        @Override
        public Void visitCall( RexCall call ) {
            if ( !algType.canImplement( call ) ) {
                throw CannotImplement.INSTANCE;
            }
            return null;
        }


        @Override
        public Void visitDynamicParam( RexDynamicParam dynamicParam ) {
            if ( !algType.canImplement( dynamicParam ) ) {
                throw CannotImplement.INSTANCE;
            }
            return null;
        }


        @Override
        public Void visitFieldAccess( RexFieldAccess fieldAccess ) {
            if ( !algType.canImplement( fieldAccess ) ) {
                throw CannotImplement.INSTANCE;
            }
            return null;
        }


        @Override
        public Void visitLiteral( RexLiteral literal ) {
            if ( !algType.canImplement( literal ) ) {
                throw CannotImplement.INSTANCE;
            }
            return null;
        }

    }


    /**
     * Control exception for {@link ImplementTester}.
     */
    private static class CannotImplement extends RuntimeException {

        static final CannotImplement INSTANCE = new CannotImplement();

    }


    /**
     * Shuttle which converts every reference to an input field in an expression to a reference to a common sub-expression.
     */
    private static class InputToCommonExprConverter extends RexShuttle {

        private final int[] exprInverseOrdinals;
        private final int[] exprLevels;
        private final int level;
        private final int[] inputExprOrdinals;
        private final RexNode[] allExprs;


        InputToCommonExprConverter( int[] exprInverseOrdinals, int[] exprLevels, int level, int[] inputExprOrdinals, RexNode[] allExprs ) {
            this.exprInverseOrdinals = exprInverseOrdinals;
            this.exprLevels = exprLevels;
            this.level = level;
            this.inputExprOrdinals = inputExprOrdinals;
            this.allExprs = allExprs;
        }


        @Override
        public RexNode visitIndexRef( RexIndexRef input ) {
            final int index = exprInverseOrdinals[input.getIndex()];
            assert index >= 0;
            return new RexLocalRef( index, input.getType() );
        }


        @Override
        public RexNode visitLocalRef( RexLocalRef local ) {
            // A reference to a local variable becomes a reference to an input if the local was computed at a previous level.
            final int localIndex = local.getIndex();
            final int exprLevel = exprLevels[localIndex];
            if ( exprLevel < level ) {
                if ( allExprs[localIndex] instanceof RexLiteral ) {
                    // Expression is to be inlined. Use the original expression.
                    return allExprs[localIndex];
                }
                int inputIndex = indexOf( localIndex, inputExprOrdinals );
                assert inputIndex >= 0;
                return new RexLocalRef( inputIndex, local.getType() );
            } else {
                // It's a reference to what was a local expression at the previous level, and was then projected.
                final int exprIndex = exprInverseOrdinals[localIndex];
                return new RexLocalRef( exprIndex, local.getType() );
            }
        }

    }


    /**
     * Finds the highest level used by any of the inputs of a given expression.
     */
    private static class MaxInputFinder extends RexVisitorImpl<Void> {

        int level;
        private final int[] exprLevels;


        MaxInputFinder( int[] exprLevels ) {
            super( true );
            this.exprLevels = exprLevels;
        }


        @Override
        public Void visitLocalRef( RexLocalRef localRef ) {
            int inputLevel = exprLevels[localRef.getIndex()];
            level = Math.max( level, inputLevel );
            return null;
        }


        /**
         * Returns the highest level of any of the inputs of an expression.
         */
        public int maxInputFor( RexNode expr ) {
            level = 0;
            expr.accept( this );
            return level;
        }

    }


    /**
     * Builds an array of the highest level which contains an expression which uses each expression as an input.
     */
    private static class HighestUsageFinder extends RexVisitorImpl<Void> {

        private final int[] maxUsingLevelOrdinals;
        private int currentLevel;


        HighestUsageFinder( RexNode[] exprs, int[] exprLevels ) {
            super( true );
            this.maxUsingLevelOrdinals = new int[exprs.length];
            Arrays.fill( maxUsingLevelOrdinals, -1 );
            for ( int i = 0; i < exprs.length; i++ ) {
                if ( exprs[i] instanceof RexLiteral ) {
                    // Literals are always used directly. It never makes sense to compute them at a lower level and project them to where they are used.
                    maxUsingLevelOrdinals[i] = -1;
                    continue;
                }
                currentLevel = exprLevels[i];
                exprs[i].accept( this );
            }
        }


        public int[] getMaxUsingLevelOrdinals() {
            return maxUsingLevelOrdinals;
        }


        @Override
        public Void visitLocalRef( RexLocalRef ref ) {
            final int index = ref.getIndex();
            maxUsingLevelOrdinals[index] = Math.max( maxUsingLevelOrdinals[index], currentLevel );
            return null;
        }

    }

}

