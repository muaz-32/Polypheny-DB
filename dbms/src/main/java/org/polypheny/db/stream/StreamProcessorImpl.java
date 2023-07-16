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

package org.polypheny.db.stream;

import static org.reflections.Reflections.log;

public class StreamProcessorImpl implements StreamProcessor {

    public StreamProcessorImpl() {

    }


    @Override
    //TODO: receive all additional info from Wrapper around MqttStream
    public String processStream( String msg ) {
        String info = extractInfo( msg );
        if ( validateMsg( info ) ) {
            log.info( "Extracted and validated message: {}", msg);
            return info;
        } else {
            log.error( "Message is not valid!" );
            return null;
        }
    }


    private static boolean validateMsg( String msg ) {
        //TODO: Implement
        return true;
    }


    private static String extractInfo( String msg ) {
        //TODO: extract the needed Info only -> based on topic attribut on right side!!};
        return msg;
    }



}
