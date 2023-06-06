// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: TableListMessage.proto

package org.apache.hadoop.hbase.rest.protobuf.generated;

public final class TableListMessage {
  private TableListMessage() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  public interface TableListOrBuilder extends
      // @@protoc_insertion_point(interface_extends:org.apache.hadoop.hbase.rest.protobuf.generated.TableList)
      com.google.protobuf.MessageOrBuilder {

    /**
     * <code>repeated string name = 1;</code>
     * @return A list containing the name.
     */
    java.util.List<java.lang.String>
        getNameList();
    /**
     * <code>repeated string name = 1;</code>
     * @return The count of name.
     */
    int getNameCount();
    /**
     * <code>repeated string name = 1;</code>
     * @param index The index of the element to return.
     * @return The name at the given index.
     */
    java.lang.String getName(int index);
    /**
     * <code>repeated string name = 1;</code>
     * @param index The index of the value to return.
     * @return The bytes of the name at the given index.
     */
    com.google.protobuf.ByteString
        getNameBytes(int index);
  }
  /**
   * Protobuf type {@code org.apache.hadoop.hbase.rest.protobuf.generated.TableList}
   */
  public  static final class TableList extends
      com.google.protobuf.GeneratedMessageV3 implements
      // @@protoc_insertion_point(message_implements:org.apache.hadoop.hbase.rest.protobuf.generated.TableList)
      TableListOrBuilder {
  private static final long serialVersionUID = 0L;
    // Use TableList.newBuilder() to construct.
    private TableList(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
      super(builder);
    }
    private TableList() {
      name_ = com.google.protobuf.LazyStringArrayList.EMPTY;
    }

    @java.lang.Override
    @SuppressWarnings({"unused"})
    protected java.lang.Object newInstance(
        UnusedPrivateParameter unused) {
      return new TableList();
    }

    @java.lang.Override
    public final com.google.protobuf.UnknownFieldSet
    getUnknownFields() {
      return this.unknownFields;
    }
    private TableList(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      this();
      if (extensionRegistry == null) {
        throw new java.lang.NullPointerException();
      }
      int mutable_bitField0_ = 0;
      com.google.protobuf.UnknownFieldSet.Builder unknownFields =
          com.google.protobuf.UnknownFieldSet.newBuilder();
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            case 10: {
              com.google.protobuf.ByteString bs = input.readBytes();
              if (!((mutable_bitField0_ & 0x00000001) != 0)) {
                name_ = new com.google.protobuf.LazyStringArrayList();
                mutable_bitField0_ |= 0x00000001;
              }
              name_.add(bs);
              break;
            }
            default: {
              if (!parseUnknownField(
                  input, unknownFields, extensionRegistry, tag)) {
                done = true;
              }
              break;
            }
          }
        }
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(this);
      } catch (java.io.IOException e) {
        throw new com.google.protobuf.InvalidProtocolBufferException(
            e).setUnfinishedMessage(this);
      } finally {
        if (((mutable_bitField0_ & 0x00000001) != 0)) {
          name_ = name_.getUnmodifiableView();
        }
        this.unknownFields = unknownFields.build();
        makeExtensionsImmutable();
      }
    }
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.internal_static_org_apache_hadoop_hbase_rest_protobuf_generated_TableList_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.internal_static_org_apache_hadoop_hbase_rest_protobuf_generated_TableList_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList.class, org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList.Builder.class);
    }

    public static final int NAME_FIELD_NUMBER = 1;
    private com.google.protobuf.LazyStringList name_;
    /**
     * <code>repeated string name = 1;</code>
     * @return A list containing the name.
     */
    public com.google.protobuf.ProtocolStringList
        getNameList() {
      return name_;
    }
    /**
     * <code>repeated string name = 1;</code>
     * @return The count of name.
     */
    public int getNameCount() {
      return name_.size();
    }
    /**
     * <code>repeated string name = 1;</code>
     * @param index The index of the element to return.
     * @return The name at the given index.
     */
    public java.lang.String getName(int index) {
      return name_.get(index);
    }
    /**
     * <code>repeated string name = 1;</code>
     * @param index The index of the value to return.
     * @return The bytes of the name at the given index.
     */
    public com.google.protobuf.ByteString
        getNameBytes(int index) {
      return name_.getByteString(index);
    }

    private byte memoizedIsInitialized = -1;
    @java.lang.Override
    public final boolean isInitialized() {
      byte isInitialized = memoizedIsInitialized;
      if (isInitialized == 1) return true;
      if (isInitialized == 0) return false;

      memoizedIsInitialized = 1;
      return true;
    }

    @java.lang.Override
    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      for (int i = 0; i < name_.size(); i++) {
        com.google.protobuf.GeneratedMessageV3.writeString(output, 1, name_.getRaw(i));
      }
      unknownFields.writeTo(output);
    }

    @java.lang.Override
    public int getSerializedSize() {
      int size = memoizedSize;
      if (size != -1) return size;

      size = 0;
      {
        int dataSize = 0;
        for (int i = 0; i < name_.size(); i++) {
          dataSize += computeStringSizeNoTag(name_.getRaw(i));
        }
        size += dataSize;
        size += 1 * getNameList().size();
      }
      size += unknownFields.getSerializedSize();
      memoizedSize = size;
      return size;
    }

    @java.lang.Override
    public boolean equals(final java.lang.Object obj) {
      if (obj == this) {
       return true;
      }
      if (!(obj instanceof org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList)) {
        return super.equals(obj);
      }
      org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList other = (org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList) obj;

      if (!getNameList()
          .equals(other.getNameList())) return false;
      if (!unknownFields.equals(other.unknownFields)) return false;
      return true;
    }

    @java.lang.Override
    public int hashCode() {
      if (memoizedHashCode != 0) {
        return memoizedHashCode;
      }
      int hash = 41;
      hash = (19 * hash) + getDescriptor().hashCode();
      if (getNameCount() > 0) {
        hash = (37 * hash) + NAME_FIELD_NUMBER;
        hash = (53 * hash) + getNameList().hashCode();
      }
      hash = (29 * hash) + unknownFields.hashCode();
      memoizedHashCode = hash;
      return hash;
    }

    public static org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList parseFrom(
        java.nio.ByteBuffer data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList parseFrom(
        java.nio.ByteBuffer data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }
    public static org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input);
    }
    public static org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
    }
    public static org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }

    @java.lang.Override
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder() {
      return DEFAULT_INSTANCE.toBuilder();
    }
    public static Builder newBuilder(org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList prototype) {
      return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
    }
    @java.lang.Override
    public Builder toBuilder() {
      return this == DEFAULT_INSTANCE
          ? new Builder() : new Builder().mergeFrom(this);
    }

    @java.lang.Override
    protected Builder newBuilderForType(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      Builder builder = new Builder(parent);
      return builder;
    }
    /**
     * Protobuf type {@code org.apache.hadoop.hbase.rest.protobuf.generated.TableList}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
        // @@protoc_insertion_point(builder_implements:org.apache.hadoop.hbase.rest.protobuf.generated.TableList)
        org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableListOrBuilder {
      public static final com.google.protobuf.Descriptors.Descriptor
          getDescriptor() {
        return org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.internal_static_org_apache_hadoop_hbase_rest_protobuf_generated_TableList_descriptor;
      }

      @java.lang.Override
      protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
          internalGetFieldAccessorTable() {
        return org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.internal_static_org_apache_hadoop_hbase_rest_protobuf_generated_TableList_fieldAccessorTable
            .ensureFieldAccessorsInitialized(
                org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList.class, org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList.Builder.class);
      }

      // Construct using org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList.newBuilder()
      private Builder() {
        maybeForceBuilderInitialization();
      }

      private Builder(
          com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
        super(parent);
        maybeForceBuilderInitialization();
      }
      private void maybeForceBuilderInitialization() {
        if (com.google.protobuf.GeneratedMessageV3
                .alwaysUseFieldBuilders) {
        }
      }
      @java.lang.Override
      public Builder clear() {
        super.clear();
        name_ = com.google.protobuf.LazyStringArrayList.EMPTY;
        bitField0_ = (bitField0_ & ~0x00000001);
        return this;
      }

      @java.lang.Override
      public com.google.protobuf.Descriptors.Descriptor
          getDescriptorForType() {
        return org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.internal_static_org_apache_hadoop_hbase_rest_protobuf_generated_TableList_descriptor;
      }

      @java.lang.Override
      public org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList getDefaultInstanceForType() {
        return org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList.getDefaultInstance();
      }

      @java.lang.Override
      public org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList build() {
        org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList result = buildPartial();
        if (!result.isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return result;
      }

      @java.lang.Override
      public org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList buildPartial() {
        org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList result = new org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList(this);
        int from_bitField0_ = bitField0_;
        if (((bitField0_ & 0x00000001) != 0)) {
          name_ = name_.getUnmodifiableView();
          bitField0_ = (bitField0_ & ~0x00000001);
        }
        result.name_ = name_;
        onBuilt();
        return result;
      }

      @java.lang.Override
      public Builder clone() {
        return super.clone();
      }
      @java.lang.Override
      public Builder setField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          java.lang.Object value) {
        return super.setField(field, value);
      }
      @java.lang.Override
      public Builder clearField(
          com.google.protobuf.Descriptors.FieldDescriptor field) {
        return super.clearField(field);
      }
      @java.lang.Override
      public Builder clearOneof(
          com.google.protobuf.Descriptors.OneofDescriptor oneof) {
        return super.clearOneof(oneof);
      }
      @java.lang.Override
      public Builder setRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          int index, java.lang.Object value) {
        return super.setRepeatedField(field, index, value);
      }
      @java.lang.Override
      public Builder addRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          java.lang.Object value) {
        return super.addRepeatedField(field, value);
      }
      @java.lang.Override
      public Builder mergeFrom(com.google.protobuf.Message other) {
        if (other instanceof org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList) {
          return mergeFrom((org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList)other);
        } else {
          super.mergeFrom(other);
          return this;
        }
      }

      public Builder mergeFrom(org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList other) {
        if (other == org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList.getDefaultInstance()) return this;
        if (!other.name_.isEmpty()) {
          if (name_.isEmpty()) {
            name_ = other.name_;
            bitField0_ = (bitField0_ & ~0x00000001);
          } else {
            ensureNameIsMutable();
            name_.addAll(other.name_);
          }
          onChanged();
        }
        this.mergeUnknownFields(other.unknownFields);
        onChanged();
        return this;
      }

      @java.lang.Override
      public final boolean isInitialized() {
        return true;
      }

      @java.lang.Override
      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList parsedMessage = null;
        try {
          parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
          parsedMessage = (org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList) e.getUnfinishedMessage();
          throw e.unwrapIOException();
        } finally {
          if (parsedMessage != null) {
            mergeFrom(parsedMessage);
          }
        }
        return this;
      }
      private int bitField0_;

      private com.google.protobuf.LazyStringList name_ = com.google.protobuf.LazyStringArrayList.EMPTY;
      private void ensureNameIsMutable() {
        if (!((bitField0_ & 0x00000001) != 0)) {
          name_ = new com.google.protobuf.LazyStringArrayList(name_);
          bitField0_ |= 0x00000001;
         }
      }
      /**
       * <code>repeated string name = 1;</code>
       * @return A list containing the name.
       */
      public com.google.protobuf.ProtocolStringList
          getNameList() {
        return name_.getUnmodifiableView();
      }
      /**
       * <code>repeated string name = 1;</code>
       * @return The count of name.
       */
      public int getNameCount() {
        return name_.size();
      }
      /**
       * <code>repeated string name = 1;</code>
       * @param index The index of the element to return.
       * @return The name at the given index.
       */
      public java.lang.String getName(int index) {
        return name_.get(index);
      }
      /**
       * <code>repeated string name = 1;</code>
       * @param index The index of the value to return.
       * @return The bytes of the name at the given index.
       */
      public com.google.protobuf.ByteString
          getNameBytes(int index) {
        return name_.getByteString(index);
      }
      /**
       * <code>repeated string name = 1;</code>
       * @param index The index to set the value at.
       * @param value The name to set.
       * @return This builder for chaining.
       */
      public Builder setName(
          int index, java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  ensureNameIsMutable();
        name_.set(index, value);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string name = 1;</code>
       * @param value The name to add.
       * @return This builder for chaining.
       */
      public Builder addName(
          java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  ensureNameIsMutable();
        name_.add(value);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string name = 1;</code>
       * @param values The name to add.
       * @return This builder for chaining.
       */
      public Builder addAllName(
          java.lang.Iterable<java.lang.String> values) {
        ensureNameIsMutable();
        com.google.protobuf.AbstractMessageLite.Builder.addAll(
            values, name_);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string name = 1;</code>
       * @return This builder for chaining.
       */
      public Builder clearName() {
        name_ = com.google.protobuf.LazyStringArrayList.EMPTY;
        bitField0_ = (bitField0_ & ~0x00000001);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string name = 1;</code>
       * @param value The bytes of the name to add.
       * @return This builder for chaining.
       */
      public Builder addNameBytes(
          com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  ensureNameIsMutable();
        name_.add(value);
        onChanged();
        return this;
      }
      @java.lang.Override
      public final Builder setUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.setUnknownFields(unknownFields);
      }

      @java.lang.Override
      public final Builder mergeUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.mergeUnknownFields(unknownFields);
      }


      // @@protoc_insertion_point(builder_scope:org.apache.hadoop.hbase.rest.protobuf.generated.TableList)
    }

    // @@protoc_insertion_point(class_scope:org.apache.hadoop.hbase.rest.protobuf.generated.TableList)
    private static final org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList DEFAULT_INSTANCE;
    static {
      DEFAULT_INSTANCE = new org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList();
    }

    public static org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList getDefaultInstance() {
      return DEFAULT_INSTANCE;
    }

    @java.lang.Deprecated public static final com.google.protobuf.Parser<TableList>
        PARSER = new com.google.protobuf.AbstractParser<TableList>() {
      @java.lang.Override
      public TableList parsePartialFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws com.google.protobuf.InvalidProtocolBufferException {
        return new TableList(input, extensionRegistry);
      }
    };

    public static com.google.protobuf.Parser<TableList> parser() {
      return PARSER;
    }

    @java.lang.Override
    public com.google.protobuf.Parser<TableList> getParserForType() {
      return PARSER;
    }

    @java.lang.Override
    public org.apache.hadoop.hbase.rest.protobuf.generated.TableListMessage.TableList getDefaultInstanceForType() {
      return DEFAULT_INSTANCE;
    }

  }

  private static final com.google.protobuf.Descriptors.Descriptor
    internal_static_org_apache_hadoop_hbase_rest_protobuf_generated_TableList_descriptor;
  private static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_org_apache_hadoop_hbase_rest_protobuf_generated_TableList_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\026TableListMessage.proto\022/org.apache.had" +
      "oop.hbase.rest.protobuf.generated\"\031\n\tTab" +
      "leList\022\014\n\004name\030\001 \003(\t"
    };
    descriptor = com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        });
    internal_static_org_apache_hadoop_hbase_rest_protobuf_generated_TableList_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_org_apache_hadoop_hbase_rest_protobuf_generated_TableList_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_org_apache_hadoop_hbase_rest_protobuf_generated_TableList_descriptor,
        new java.lang.String[] { "Name", });
  }

  // @@protoc_insertion_point(outer_class_scope)
}