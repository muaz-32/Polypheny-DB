// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: Polypheny-DB/plugins/proto-interface/src/main/proto/protointerface.proto

package org.polypheny.db.protointerface.proto;

public interface ConnectionReplyOrBuilder extends
    // @@protoc_insertion_point(interface_extends:polypheny.protointerface.ConnectionReply)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>bool isCompatible = 1;</code>
   */
  boolean getIsCompatible();

  /**
   * <code>uint32 majorApiVersion = 2;</code>
   */
  int getMajorApiVersion();

  /**
   * <code>uint32 minorApiVersion = 3;</code>
   */
  int getMinorApiVersion();
}