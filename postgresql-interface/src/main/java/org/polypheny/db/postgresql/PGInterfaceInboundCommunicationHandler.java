/*
 * Copyright 2019-2022 The Polypheny Project
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

package org.polypheny.db.postgresql;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.polypheny.db.transaction.TransactionManager;

/**
 * Manages all incoming communication, not using the netty framework (but being a handler in netty)
 */
public class PGInterfaceInboundCommunicationHandler {

    String type;
    ChannelHandlerContext ctx;
    TransactionManager transactionManager;


    public PGInterfaceInboundCommunicationHandler( String type, ChannelHandlerContext ctx, TransactionManager transactionManager ) {
        this.type = type;
        this.ctx = ctx;
        this.transactionManager = transactionManager;
    }


    /**
     * Decides in what cycle from postgres the client is (startup-phase, query-phase, etc.)
     *
     * @param oMsg the incoming message from the client (unchanged)
     * @return
     */
    public void decideCycle( Object oMsg ) {
        String msgWithZeroBits = ((String) oMsg);
        String wholeMsg = msgWithZeroBits.replace( "\u0000", "" );

        //TODO(FF): simple query phase is not implemented
        switch ( wholeMsg.substring( 0, 1 ) ) {
            case "C":   //TODO(FF):was gnau passiert do??
                PGInterfaceMessage msg = null;
                msg.setHeader( PGInterfaceHeaders.C );
                break;
            case "r":
                startUpPhase();
                break;
            case "P":
                extendedQueryPhase( wholeMsg );
                break;
            case "X":
                terminateConnection();
                break;

        }
    }


    /**
     * Performs necessary steps on the first connection with the client (mostly sends necessary replies, but doesn't really set anything on the server side).
     * Sends authenticationOk (without checking authentication), sets server version, sends readyForQuery
     */
    public void startUpPhase() {
        //authenticationOk
        PGInterfaceMessage authenticationOk = new PGInterfaceMessage( PGInterfaceHeaders.R, "0", 8, false );
        PGInterfaceServerWriter authenticationOkWriter = new PGInterfaceServerWriter( "i", authenticationOk, ctx );
        ctx.writeAndFlush( authenticationOkWriter.writeOnByteBuf() );

        //server_version (Parameter Status message)
        PGInterfaceMessage parameterStatusServerVs = new PGInterfaceMessage(PGInterfaceHeaders.S, "server_version" + PGInterfaceMessage.getDelimiter() + "14", 4, true);
        PGInterfaceServerWriter parameterStatusServerVsWriter = new PGInterfaceServerWriter( "ss", parameterStatusServerVs, ctx );
        ctx.writeAndFlush( parameterStatusServerVsWriter.writeOnByteBuf() );

        //ReadyForQuery
        sendReadyForQuery( "I" );
    }


    public void simpleQueryPhase() {
        //TODO(FF): (low priority) The simple query phase is handled a bit differently than the extended query phase. The most important difference is that the simple query phase accepts several queries at once and sends some different response messages (e.g. no parse/bindComplete).
        //Several queries seperated with ";"
    }


    /**
     * Sends necessary responses to client (without really setting anything in backend) and prepares the incoming query for usage. Continues query forward to QueryHandler
     * @param incomingMsg unchanged incoming message (transformed to string by netty)
     */
    public void extendedQueryPhase( String incomingMsg ) {

        if ( incomingMsg.substring( 2, 5 ).equals( "SET" ) ) {

            sendParseBindComplete();

            sendCommandComplete("SET", -1);

            sendReadyForQuery( "I" );

        } else {
            //Query does not have ";" at the end!!
            String query = extractQuery( incomingMsg );
            PGInterfaceQueryHandler queryHandler = new PGInterfaceQueryHandler( query, ctx, this, transactionManager );
            queryHandler.start();

        }
    }


    /**
     * Creates and sends (flushes on ctx) a readyForQuery message with a tag. The tag is choosable (see below for options).
     *
     * @param msgBody tag - current transaction status indicator (possible vals: I (idle, not in transaction block),
     * T (in transaction block), E (in failed transaction block, queries will be rejected until block is ended (TODO: what exactly happens in transaction blocks.)
     */
    public void sendReadyForQuery( String msgBody ) {
        PGInterfaceMessage readyForQuery = new PGInterfaceMessage( PGInterfaceHeaders.Z, msgBody, 5, false );
        PGInterfaceServerWriter readyForQueryWriter = new PGInterfaceServerWriter( "c", readyForQuery, ctx );
        ctx.writeAndFlush( readyForQueryWriter.writeOnByteBuf() );
    }


    /**
     * Prepares (parses) the incoming message from the client, so it can be used in the context of polypheny
     * NOTE: Some incoming messages from the client are disregarded (they are sent the same way all the time, if something unusual occurs, this is not handled yet, i.e. hardcoded to find the end of the query itself).
     *
     * @param incomingMsg unchanged incoming message from the client
     * @return "normally" readable and usable query string
     */
    public String extractQuery( String incomingMsg ) {
        String query = "";
        //cut header
        query = incomingMsg.substring( 2, incomingMsg.length() - 1 );

        //find end of query --> normally it ends with combination of BDPES (are headers (some indicators from client), with some weird other bits in between)
        //B starts immediately after query --> find position of correct B and end of query is found
        byte[] byteSequence = { 66, 0, 0, 0, 12, 0, 0, 0, 0, 0, 0, 0, 0, 68, 0, 0, 0, 6, 80, 0, 69, 0, 0, 0, 9 };
        String msgWithZeroBits = new String( byteSequence, StandardCharsets.UTF_8 );
        String endSequence = msgWithZeroBits.replace( "\u0000", "" );

        String endOfQuery = query.substring( incomingMsg.length() - 20 );

        int idx = incomingMsg.indexOf( endSequence );
        if ( idx != -1 ) {
            query = query.substring( 0, idx - 2 );
        } else {
            //TODO(FF) something went wrong!! --> trow exception (in polypheny), send errormessage to client
            int lol = 2;
        }

        return query;
    }


    public void sendParseBindComplete() {
        //TODO(FF): This should work with the normal PGInterfaceServerWriter type "i" (called like in the commented out part),
        // but it does not --> roundabout solution that works, but try to figure out what went wrong...

        /*
        //parseComplete
        PGInterfaceMessage parseComplete = new PGInterfaceMessage(PGInterfaceHeaders.ONE, "0", 4, true);
        PGInterfaceServerWriter parseCompleteWriter = new PGInterfaceServerWriter("i", parseComplete, ctx);
        ctx.writeAndFlush(parseCompleteWriter.writeOnByteBuf());

        //bindComplete
        PGInterfaceMessage bindComplete = new PGInterfaceMessage(PGInterfaceHeaders.TWO, "0", 4, true);
        PGInterfaceServerWriter bindCompleteWriter = new PGInterfaceServerWriter("i", bindComplete, ctx);
        ctx.writeAndFlush(bindCompleteWriter.writeOnByteBuf());
         */

        ByteBuf buffer = ctx.alloc().buffer();
        PGInterfaceMessage mockMessage = new PGInterfaceMessage( PGInterfaceHeaders.ONE, "0", 4, true );
        PGInterfaceServerWriter headerWriter = new PGInterfaceServerWriter( "i", mockMessage, ctx );
        buffer = headerWriter.writeIntHeaderOnByteBuf( '1' );
        buffer.writeBytes( headerWriter.writeIntHeaderOnByteBuf( '2' ) );
        ctx.writeAndFlush( buffer );

    }



    public void sendNoData() {
        //TODO(FF): not entirely sure in which case this would be needed?
        PGInterfaceMessage noData = new PGInterfaceMessage( PGInterfaceHeaders.n, "0", 4, true );
        PGInterfaceServerWriter noDataWriter = new PGInterfaceServerWriter( "i", noData, ctx );
        ctx.writeAndFlush( noDataWriter.writeOnByteBuf() );
    }


    /**
     * Sends CommandComplete to client, with choosable command type
     * @param command which command is completed (no space afterwards, space is added here)
     * @param rowsAffected number of rows affected (if it is not necessary to send a number, put -1)
     */
    public void sendCommandComplete( String command, int rowsAffected ) {
        String body = "";
        PGInterfaceMessage commandComplete;
        PGInterfaceServerWriter commandCompleteWriter;

        if ( rowsAffected == -1) {
            body = command;

        } else {
            body = command + " " + String.valueOf( rowsAffected );
        }

        commandComplete = new PGInterfaceMessage( PGInterfaceHeaders.C, body, 4, true );
        commandCompleteWriter = new PGInterfaceServerWriter( "s", commandComplete, ctx );
        ctx.writeAndFlush( commandCompleteWriter.writeOnByteBuf() );
    }


    /**
     * Prepares everything to send rowDescription
     * @param numberOfFields how many fields are in a row of the result
     * @param valuesPerCol The values that should be sent for each field (information about each column)
     */
    public void sendRowDescription( int numberOfFields, ArrayList<Object[]> valuesPerCol ) {
        String body = String.valueOf( numberOfFields );
        PGInterfaceMessage rowDescription = new PGInterfaceMessage( PGInterfaceHeaders.T, body, 4, true );    //the length here doesn't really matter, because it is calculated seperately in writeRowDescription
        PGInterfaceServerWriter rowDescriptionWriter = new PGInterfaceServerWriter( "i", rowDescription, ctx );
        ctx.writeAndFlush(rowDescriptionWriter.writeRowDescription(valuesPerCol));
    }


    /**
     * Prepares everything to send DataRows, with its corresponding needed information
     * @param data data that should be sent
     */
    public void sendDataRow( ArrayList<String[]> data ) {
        int noCols = data.size();   //number of rows returned
        String colVal = "";         //The value of the result
        int colValLength = 0;       //length of the colVal - can be 0 and -1 (-1= NULL is colVal)
        String body = "";           //combination of colVal and colValLength
        int nbrFollowingColVal = data.get( 0 ).length;

        PGInterfaceMessage dataRow;
        PGInterfaceServerWriter dataRowWriter;

        for ( int i = 0; i < noCols; i++ ) {

            for ( int j = 0; j < nbrFollowingColVal; j++ ) {

                colVal = data.get( i )[j];

                //TODO(FF): How is null safed in polypheny exactly?? is it correctly checked?
                if ( colVal == "NULL" ) {
                    colValLength = -1;
                    //no body should be sent
                    break;
                } else {
                    colValLength += colVal.length();
                    body += colVal.length() + PGInterfaceMessage.getDelimiter() + colVal + PGInterfaceMessage.getDelimiter();
                }
            }
            dataRow = new PGInterfaceMessage( PGInterfaceHeaders.D, body, colValLength, false );
            dataRowWriter = new PGInterfaceServerWriter( "dr", dataRow, ctx );
            ctx.writeAndFlush( dataRowWriter.writeOnByteBuf() );
            body = "";
        }


    }


    public void terminateConnection() {
        ctx.close();
    }
}
