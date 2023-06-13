/**
 * Autogenerated by Thrift Compiler (0.16.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package org.apache.hadoop.hbase.thrift2.generated;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
/**
 * Represents a single cell and its value.
 */
@javax.annotation.Generated(value = "Autogenerated by Thrift Compiler (0.16.0)", date = "2023-06-13")
public class TColumnValue implements org.apache.thrift.TBase<TColumnValue, TColumnValue._Fields>, java.io.Serializable, Cloneable, Comparable<TColumnValue> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("TColumnValue");

  private static final org.apache.thrift.protocol.TField FAMILY_FIELD_DESC = new org.apache.thrift.protocol.TField("family", org.apache.thrift.protocol.TType.STRING, (short)1);
  private static final org.apache.thrift.protocol.TField QUALIFIER_FIELD_DESC = new org.apache.thrift.protocol.TField("qualifier", org.apache.thrift.protocol.TType.STRING, (short)2);
  private static final org.apache.thrift.protocol.TField VALUE_FIELD_DESC = new org.apache.thrift.protocol.TField("value", org.apache.thrift.protocol.TType.STRING, (short)3);
  private static final org.apache.thrift.protocol.TField TIMESTAMP_FIELD_DESC = new org.apache.thrift.protocol.TField("timestamp", org.apache.thrift.protocol.TType.I64, (short)4);
  private static final org.apache.thrift.protocol.TField TAGS_FIELD_DESC = new org.apache.thrift.protocol.TField("tags", org.apache.thrift.protocol.TType.STRING, (short)5);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new TColumnValueStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new TColumnValueTupleSchemeFactory();

  public @org.apache.thrift.annotation.Nullable java.nio.ByteBuffer family; // required
  public @org.apache.thrift.annotation.Nullable java.nio.ByteBuffer qualifier; // required
  public @org.apache.thrift.annotation.Nullable java.nio.ByteBuffer value; // required
  public long timestamp; // optional
  public @org.apache.thrift.annotation.Nullable java.nio.ByteBuffer tags; // optional

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    FAMILY((short)1, "family"),
    QUALIFIER((short)2, "qualifier"),
    VALUE((short)3, "value"),
    TIMESTAMP((short)4, "timestamp"),
    TAGS((short)5, "tags");

    private static final java.util.Map<java.lang.String, _Fields> byName = new java.util.HashMap<java.lang.String, _Fields>();

    static {
      for (_Fields field : java.util.EnumSet.allOf(_Fields.class)) {
        byName.put(field.getFieldName(), field);
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, or null if its not found.
     */
    @org.apache.thrift.annotation.Nullable
    public static _Fields findByThriftId(int fieldId) {
      switch(fieldId) {
        case 1: // FAMILY
          return FAMILY;
        case 2: // QUALIFIER
          return QUALIFIER;
        case 3: // VALUE
          return VALUE;
        case 4: // TIMESTAMP
          return TIMESTAMP;
        case 5: // TAGS
          return TAGS;
        default:
          return null;
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, throwing an exception
     * if it is not found.
     */
    public static _Fields findByThriftIdOrThrow(int fieldId) {
      _Fields fields = findByThriftId(fieldId);
      if (fields == null) throw new java.lang.IllegalArgumentException("Field " + fieldId + " doesn't exist!");
      return fields;
    }

    /**
     * Find the _Fields constant that matches name, or null if its not found.
     */
    @org.apache.thrift.annotation.Nullable
    public static _Fields findByName(java.lang.String name) {
      return byName.get(name);
    }

    private final short _thriftId;
    private final java.lang.String _fieldName;

    _Fields(short thriftId, java.lang.String fieldName) {
      _thriftId = thriftId;
      _fieldName = fieldName;
    }

    public short getThriftFieldId() {
      return _thriftId;
    }

    public java.lang.String getFieldName() {
      return _fieldName;
    }
  }

  // isset id assignments
  private static final int __TIMESTAMP_ISSET_ID = 0;
  private byte __isset_bitfield = 0;
  private static final _Fields optionals[] = {_Fields.TIMESTAMP,_Fields.TAGS};
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.FAMILY, new org.apache.thrift.meta_data.FieldMetaData("family", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING        , true)));
    tmpMap.put(_Fields.QUALIFIER, new org.apache.thrift.meta_data.FieldMetaData("qualifier", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING        , true)));
    tmpMap.put(_Fields.VALUE, new org.apache.thrift.meta_data.FieldMetaData("value", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING        , true)));
    tmpMap.put(_Fields.TIMESTAMP, new org.apache.thrift.meta_data.FieldMetaData("timestamp", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64)));
    tmpMap.put(_Fields.TAGS, new org.apache.thrift.meta_data.FieldMetaData("tags", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING        , true)));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(TColumnValue.class, metaDataMap);
  }

  public TColumnValue() {
  }

  public TColumnValue(
    java.nio.ByteBuffer family,
    java.nio.ByteBuffer qualifier,
    java.nio.ByteBuffer value)
  {
    this();
    this.family = org.apache.thrift.TBaseHelper.copyBinary(family);
    this.qualifier = org.apache.thrift.TBaseHelper.copyBinary(qualifier);
    this.value = org.apache.thrift.TBaseHelper.copyBinary(value);
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public TColumnValue(TColumnValue other) {
    __isset_bitfield = other.__isset_bitfield;
    if (other.isSetFamily()) {
      this.family = org.apache.thrift.TBaseHelper.copyBinary(other.family);
    }
    if (other.isSetQualifier()) {
      this.qualifier = org.apache.thrift.TBaseHelper.copyBinary(other.qualifier);
    }
    if (other.isSetValue()) {
      this.value = org.apache.thrift.TBaseHelper.copyBinary(other.value);
    }
    this.timestamp = other.timestamp;
    if (other.isSetTags()) {
      this.tags = org.apache.thrift.TBaseHelper.copyBinary(other.tags);
    }
  }

  public TColumnValue deepCopy() {
    return new TColumnValue(this);
  }

  @Override
  public void clear() {
    this.family = null;
    this.qualifier = null;
    this.value = null;
    setTimestampIsSet(false);
    this.timestamp = 0;
    this.tags = null;
  }

  public byte[] getFamily() {
    setFamily(org.apache.thrift.TBaseHelper.rightSize(family));
    return family == null ? null : family.array();
  }

  public java.nio.ByteBuffer bufferForFamily() {
    return org.apache.thrift.TBaseHelper.copyBinary(family);
  }

  public TColumnValue setFamily(byte[] family) {
    this.family = family == null ? (java.nio.ByteBuffer)null   : java.nio.ByteBuffer.wrap(family.clone());
    return this;
  }

  public TColumnValue setFamily(@org.apache.thrift.annotation.Nullable java.nio.ByteBuffer family) {
    this.family = org.apache.thrift.TBaseHelper.copyBinary(family);
    return this;
  }

  public void unsetFamily() {
    this.family = null;
  }

  /** Returns true if field family is set (has been assigned a value) and false otherwise */
  public boolean isSetFamily() {
    return this.family != null;
  }

  public void setFamilyIsSet(boolean value) {
    if (!value) {
      this.family = null;
    }
  }

  public byte[] getQualifier() {
    setQualifier(org.apache.thrift.TBaseHelper.rightSize(qualifier));
    return qualifier == null ? null : qualifier.array();
  }

  public java.nio.ByteBuffer bufferForQualifier() {
    return org.apache.thrift.TBaseHelper.copyBinary(qualifier);
  }

  public TColumnValue setQualifier(byte[] qualifier) {
    this.qualifier = qualifier == null ? (java.nio.ByteBuffer)null   : java.nio.ByteBuffer.wrap(qualifier.clone());
    return this;
  }

  public TColumnValue setQualifier(@org.apache.thrift.annotation.Nullable java.nio.ByteBuffer qualifier) {
    this.qualifier = org.apache.thrift.TBaseHelper.copyBinary(qualifier);
    return this;
  }

  public void unsetQualifier() {
    this.qualifier = null;
  }

  /** Returns true if field qualifier is set (has been assigned a value) and false otherwise */
  public boolean isSetQualifier() {
    return this.qualifier != null;
  }

  public void setQualifierIsSet(boolean value) {
    if (!value) {
      this.qualifier = null;
    }
  }

  public byte[] getValue() {
    setValue(org.apache.thrift.TBaseHelper.rightSize(value));
    return value == null ? null : value.array();
  }

  public java.nio.ByteBuffer bufferForValue() {
    return org.apache.thrift.TBaseHelper.copyBinary(value);
  }

  public TColumnValue setValue(byte[] value) {
    this.value = value == null ? (java.nio.ByteBuffer)null   : java.nio.ByteBuffer.wrap(value.clone());
    return this;
  }

  public TColumnValue setValue(@org.apache.thrift.annotation.Nullable java.nio.ByteBuffer value) {
    this.value = org.apache.thrift.TBaseHelper.copyBinary(value);
    return this;
  }

  public void unsetValue() {
    this.value = null;
  }

  /** Returns true if field value is set (has been assigned a value) and false otherwise */
  public boolean isSetValue() {
    return this.value != null;
  }

  public void setValueIsSet(boolean value) {
    if (!value) {
      this.value = null;
    }
  }

  public long getTimestamp() {
    return this.timestamp;
  }

  public TColumnValue setTimestamp(long timestamp) {
    this.timestamp = timestamp;
    setTimestampIsSet(true);
    return this;
  }

  public void unsetTimestamp() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __TIMESTAMP_ISSET_ID);
  }

  /** Returns true if field timestamp is set (has been assigned a value) and false otherwise */
  public boolean isSetTimestamp() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __TIMESTAMP_ISSET_ID);
  }

  public void setTimestampIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __TIMESTAMP_ISSET_ID, value);
  }

  public byte[] getTags() {
    setTags(org.apache.thrift.TBaseHelper.rightSize(tags));
    return tags == null ? null : tags.array();
  }

  public java.nio.ByteBuffer bufferForTags() {
    return org.apache.thrift.TBaseHelper.copyBinary(tags);
  }

  public TColumnValue setTags(byte[] tags) {
    this.tags = tags == null ? (java.nio.ByteBuffer)null   : java.nio.ByteBuffer.wrap(tags.clone());
    return this;
  }

  public TColumnValue setTags(@org.apache.thrift.annotation.Nullable java.nio.ByteBuffer tags) {
    this.tags = org.apache.thrift.TBaseHelper.copyBinary(tags);
    return this;
  }

  public void unsetTags() {
    this.tags = null;
  }

  /** Returns true if field tags is set (has been assigned a value) and false otherwise */
  public boolean isSetTags() {
    return this.tags != null;
  }

  public void setTagsIsSet(boolean value) {
    if (!value) {
      this.tags = null;
    }
  }

  public void setFieldValue(_Fields field, @org.apache.thrift.annotation.Nullable java.lang.Object value) {
    switch (field) {
    case FAMILY:
      if (value == null) {
        unsetFamily();
      } else {
        if (value instanceof byte[]) {
          setFamily((byte[])value);
        } else {
          setFamily((java.nio.ByteBuffer)value);
        }
      }
      break;

    case QUALIFIER:
      if (value == null) {
        unsetQualifier();
      } else {
        if (value instanceof byte[]) {
          setQualifier((byte[])value);
        } else {
          setQualifier((java.nio.ByteBuffer)value);
        }
      }
      break;

    case VALUE:
      if (value == null) {
        unsetValue();
      } else {
        if (value instanceof byte[]) {
          setValue((byte[])value);
        } else {
          setValue((java.nio.ByteBuffer)value);
        }
      }
      break;

    case TIMESTAMP:
      if (value == null) {
        unsetTimestamp();
      } else {
        setTimestamp((java.lang.Long)value);
      }
      break;

    case TAGS:
      if (value == null) {
        unsetTags();
      } else {
        if (value instanceof byte[]) {
          setTags((byte[])value);
        } else {
          setTags((java.nio.ByteBuffer)value);
        }
      }
      break;

    }
  }

  @org.apache.thrift.annotation.Nullable
  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case FAMILY:
      return getFamily();

    case QUALIFIER:
      return getQualifier();

    case VALUE:
      return getValue();

    case TIMESTAMP:
      return getTimestamp();

    case TAGS:
      return getTags();

    }
    throw new java.lang.IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new java.lang.IllegalArgumentException();
    }

    switch (field) {
    case FAMILY:
      return isSetFamily();
    case QUALIFIER:
      return isSetQualifier();
    case VALUE:
      return isSetValue();
    case TIMESTAMP:
      return isSetTimestamp();
    case TAGS:
      return isSetTags();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that instanceof TColumnValue)
      return this.equals((TColumnValue)that);
    return false;
  }

  public boolean equals(TColumnValue that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_family = true && this.isSetFamily();
    boolean that_present_family = true && that.isSetFamily();
    if (this_present_family || that_present_family) {
      if (!(this_present_family && that_present_family))
        return false;
      if (!this.family.equals(that.family))
        return false;
    }

    boolean this_present_qualifier = true && this.isSetQualifier();
    boolean that_present_qualifier = true && that.isSetQualifier();
    if (this_present_qualifier || that_present_qualifier) {
      if (!(this_present_qualifier && that_present_qualifier))
        return false;
      if (!this.qualifier.equals(that.qualifier))
        return false;
    }

    boolean this_present_value = true && this.isSetValue();
    boolean that_present_value = true && that.isSetValue();
    if (this_present_value || that_present_value) {
      if (!(this_present_value && that_present_value))
        return false;
      if (!this.value.equals(that.value))
        return false;
    }

    boolean this_present_timestamp = true && this.isSetTimestamp();
    boolean that_present_timestamp = true && that.isSetTimestamp();
    if (this_present_timestamp || that_present_timestamp) {
      if (!(this_present_timestamp && that_present_timestamp))
        return false;
      if (this.timestamp != that.timestamp)
        return false;
    }

    boolean this_present_tags = true && this.isSetTags();
    boolean that_present_tags = true && that.isSetTags();
    if (this_present_tags || that_present_tags) {
      if (!(this_present_tags && that_present_tags))
        return false;
      if (!this.tags.equals(that.tags))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + ((isSetFamily()) ? 131071 : 524287);
    if (isSetFamily())
      hashCode = hashCode * 8191 + family.hashCode();

    hashCode = hashCode * 8191 + ((isSetQualifier()) ? 131071 : 524287);
    if (isSetQualifier())
      hashCode = hashCode * 8191 + qualifier.hashCode();

    hashCode = hashCode * 8191 + ((isSetValue()) ? 131071 : 524287);
    if (isSetValue())
      hashCode = hashCode * 8191 + value.hashCode();

    hashCode = hashCode * 8191 + ((isSetTimestamp()) ? 131071 : 524287);
    if (isSetTimestamp())
      hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(timestamp);

    hashCode = hashCode * 8191 + ((isSetTags()) ? 131071 : 524287);
    if (isSetTags())
      hashCode = hashCode * 8191 + tags.hashCode();

    return hashCode;
  }

  @Override
  public int compareTo(TColumnValue other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.compare(isSetFamily(), other.isSetFamily());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetFamily()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.family, other.family);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.compare(isSetQualifier(), other.isSetQualifier());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetQualifier()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.qualifier, other.qualifier);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.compare(isSetValue(), other.isSetValue());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetValue()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.value, other.value);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.compare(isSetTimestamp(), other.isSetTimestamp());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetTimestamp()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.timestamp, other.timestamp);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.compare(isSetTags(), other.isSetTags());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetTags()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.tags, other.tags);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    return 0;
  }

  @org.apache.thrift.annotation.Nullable
  public _Fields fieldForId(int fieldId) {
    return _Fields.findByThriftId(fieldId);
  }

  public void read(org.apache.thrift.protocol.TProtocol iprot) throws org.apache.thrift.TException {
    scheme(iprot).read(iprot, this);
  }

  public void write(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
    scheme(oprot).write(oprot, this);
  }

  @Override
  public java.lang.String toString() {
    java.lang.StringBuilder sb = new java.lang.StringBuilder("TColumnValue(");
    boolean first = true;

    sb.append("family:");
    if (this.family == null) {
      sb.append("null");
    } else {
      org.apache.thrift.TBaseHelper.toString(this.family, sb);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("qualifier:");
    if (this.qualifier == null) {
      sb.append("null");
    } else {
      org.apache.thrift.TBaseHelper.toString(this.qualifier, sb);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("value:");
    if (this.value == null) {
      sb.append("null");
    } else {
      org.apache.thrift.TBaseHelper.toString(this.value, sb);
    }
    first = false;
    if (isSetTimestamp()) {
      if (!first) sb.append(", ");
      sb.append("timestamp:");
      sb.append(this.timestamp);
      first = false;
    }
    if (isSetTags()) {
      if (!first) sb.append(", ");
      sb.append("tags:");
      if (this.tags == null) {
        sb.append("null");
      } else {
        org.apache.thrift.TBaseHelper.toString(this.tags, sb);
      }
      first = false;
    }
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    if (family == null) {
      throw new org.apache.thrift.protocol.TProtocolException("Required field 'family' was not present! Struct: " + toString());
    }
    if (qualifier == null) {
      throw new org.apache.thrift.protocol.TProtocolException("Required field 'qualifier' was not present! Struct: " + toString());
    }
    if (value == null) {
      throw new org.apache.thrift.protocol.TProtocolException("Required field 'value' was not present! Struct: " + toString());
    }
    // check for sub-struct validity
  }

  private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    try {
      write(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(out)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, java.lang.ClassNotFoundException {
    try {
      // it doesn't seem like you should have to do this, but java serialization is wacky, and doesn't call the default constructor.
      __isset_bitfield = 0;
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class TColumnValueStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public TColumnValueStandardScheme getScheme() {
      return new TColumnValueStandardScheme();
    }
  }

  private static class TColumnValueStandardScheme extends org.apache.thrift.scheme.StandardScheme<TColumnValue> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, TColumnValue struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // FAMILY
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.family = iprot.readBinary();
              struct.setFamilyIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // QUALIFIER
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.qualifier = iprot.readBinary();
              struct.setQualifierIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 3: // VALUE
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.value = iprot.readBinary();
              struct.setValueIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 4: // TIMESTAMP
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.timestamp = iprot.readI64();
              struct.setTimestampIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 5: // TAGS
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.tags = iprot.readBinary();
              struct.setTagsIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          default:
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();

      // check for required fields of primitive type, which can't be checked in the validate method
      struct.validate();
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot, TColumnValue struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.family != null) {
        oprot.writeFieldBegin(FAMILY_FIELD_DESC);
        oprot.writeBinary(struct.family);
        oprot.writeFieldEnd();
      }
      if (struct.qualifier != null) {
        oprot.writeFieldBegin(QUALIFIER_FIELD_DESC);
        oprot.writeBinary(struct.qualifier);
        oprot.writeFieldEnd();
      }
      if (struct.value != null) {
        oprot.writeFieldBegin(VALUE_FIELD_DESC);
        oprot.writeBinary(struct.value);
        oprot.writeFieldEnd();
      }
      if (struct.isSetTimestamp()) {
        oprot.writeFieldBegin(TIMESTAMP_FIELD_DESC);
        oprot.writeI64(struct.timestamp);
        oprot.writeFieldEnd();
      }
      if (struct.tags != null) {
        if (struct.isSetTags()) {
          oprot.writeFieldBegin(TAGS_FIELD_DESC);
          oprot.writeBinary(struct.tags);
          oprot.writeFieldEnd();
        }
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class TColumnValueTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public TColumnValueTupleScheme getScheme() {
      return new TColumnValueTupleScheme();
    }
  }

  private static class TColumnValueTupleScheme extends org.apache.thrift.scheme.TupleScheme<TColumnValue> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, TColumnValue struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      oprot.writeBinary(struct.family);
      oprot.writeBinary(struct.qualifier);
      oprot.writeBinary(struct.value);
      java.util.BitSet optionals = new java.util.BitSet();
      if (struct.isSetTimestamp()) {
        optionals.set(0);
      }
      if (struct.isSetTags()) {
        optionals.set(1);
      }
      oprot.writeBitSet(optionals, 2);
      if (struct.isSetTimestamp()) {
        oprot.writeI64(struct.timestamp);
      }
      if (struct.isSetTags()) {
        oprot.writeBinary(struct.tags);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, TColumnValue struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.family = iprot.readBinary();
      struct.setFamilyIsSet(true);
      struct.qualifier = iprot.readBinary();
      struct.setQualifierIsSet(true);
      struct.value = iprot.readBinary();
      struct.setValueIsSet(true);
      java.util.BitSet incoming = iprot.readBitSet(2);
      if (incoming.get(0)) {
        struct.timestamp = iprot.readI64();
        struct.setTimestampIsSet(true);
      }
      if (incoming.get(1)) {
        struct.tags = iprot.readBinary();
        struct.setTagsIsSet(true);
      }
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

