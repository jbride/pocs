// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: src/main/proto/addressbook.proto

package org.acme.proto3;

public interface AddressBookOrBuilder extends
    // @@protoc_insertion_point(interface_extends:addressbook.AddressBook)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>repeated .addressbook.Person people = 1;</code>
   */
  java.util.List<org.acme.proto3.Person> 
      getPeopleList();
  /**
   * <code>repeated .addressbook.Person people = 1;</code>
   */
  org.acme.proto3.Person getPeople(int index);
  /**
   * <code>repeated .addressbook.Person people = 1;</code>
   */
  int getPeopleCount();
  /**
   * <code>repeated .addressbook.Person people = 1;</code>
   */
  java.util.List<? extends org.acme.proto3.PersonOrBuilder> 
      getPeopleOrBuilderList();
  /**
   * <code>repeated .addressbook.Person people = 1;</code>
   */
  org.acme.proto3.PersonOrBuilder getPeopleOrBuilder(
      int index);
}
