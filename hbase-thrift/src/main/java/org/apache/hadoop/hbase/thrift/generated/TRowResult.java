/**
 * Autogenerated by Thrift Compiler (0.12.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package org.apache.hadoop.hbase.thrift.generated;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
/**
 * Holds row name and then a map of columns to cells.
 */
@javax.annotation.Generated(value = "Autogenerated by Thrift Compiler (0.12.0)", date = "2019-02-26")
public class TRowResult implements org.apache.thrift.TBase<TRowResult, TRowResult._Fields>, java.io.Serializable, Cloneable, Comparable<TRowResult> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("TRowResult");

  private static final org.apache.thrift.protocol.TField ROW_FIELD_DESC = new org.apache.thrift.protocol.TField("row", org.apache.thrift.protocol.TType.STRING, (short)1);
  private static final org.apache.thrift.protocol.TField COLUMNS_FIELD_DESC = new org.apache.thrift.protocol.TField("columns", org.apache.thrift.protocol.TType.MAP, (short)2);
  private static final org.apache.thrift.protocol.TField SORTED_COLUMNS_FIELD_DESC = new org.apache.thrift.protocol.TField("sortedColumns", org.apache.thrift.protocol.TType.LIST, (short)3);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new TRowResultStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new TRowResultTupleSchemeFactory();

  public @org.apache.thrift.annotation.Nullable java.nio.ByteBuffer row; // required
  public @org.apache.thrift.annotation.Nullable java.util.Map<java.nio.ByteBuffer,TCell> columns; // optional
  public @org.apache.thrift.annotation.Nullable java.util.List<TColumn> sortedColumns; // optional

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    ROW((short)1, "row"),
    COLUMNS((short)2, "columns"),
    SORTED_COLUMNS((short)3, "sortedColumns");

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
        case 1: // ROW
          return ROW;
        case 2: // COLUMNS
          return COLUMNS;
        case 3: // SORTED_COLUMNS
          return SORTED_COLUMNS;
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
  private static final _Fields optionals[] = {_Fields.COLUMNS,_Fields.SORTED_COLUMNS};
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.ROW, new org.apache.thrift.meta_data.FieldMetaData("row", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING        , "Text")));
    tmpMap.put(_Fields.COLUMNS, new org.apache.thrift.meta_data.FieldMetaData("columns", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.MapMetaData(org.apache.thrift.protocol.TType.MAP, 
            new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING            , "Text"), 
            new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, TCell.class))));
    tmpMap.put(_Fields.SORTED_COLUMNS, new org.apache.thrift.meta_data.FieldMetaData("sortedColumns", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.ListMetaData(org.apache.thrift.protocol.TType.LIST, 
            new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, TColumn.class))));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(TRowResult.class, metaDataMap);
  }

  public TRowResult() {
  }

  public TRowResult(
    java.nio.ByteBuffer row)
  {
    this();
    this.row = org.apache.thrift.TBaseHelper.copyBinary(row);
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public TRowResult(TRowResult other) {
    if (other.isSetRow()) {
      this.row = org.apache.thrift.TBaseHelper.copyBinary(other.row);
    }
    if (other.isSetColumns()) {
      java.util.Map<java.nio.ByteBuffer,TCell> __this__columns = new java.util.HashMap<java.nio.ByteBuffer,TCell>(other.columns.size());
      for (java.util.Map.Entry<java.nio.ByteBuffer, TCell> other_element : other.columns.entrySet()) {

        java.nio.ByteBuffer other_element_key = other_element.getKey();
        TCell other_element_value = other_element.getValue();

        java.nio.ByteBuffer __this__columns_copy_key = org.apache.thrift.TBaseHelper.copyBinary(other_element_key);

        TCell __this__columns_copy_value = new TCell(other_element_value);

        __this__columns.put(__this__columns_copy_key, __this__columns_copy_value);
      }
      this.columns = __this__columns;
    }
    if (other.isSetSortedColumns()) {
      java.util.List<TColumn> __this__sortedColumns = new java.util.ArrayList<TColumn>(other.sortedColumns.size());
      for (TColumn other_element : other.sortedColumns) {
        __this__sortedColumns.add(new TColumn(other_element));
      }
      this.sortedColumns = __this__sortedColumns;
    }
  }

  public TRowResult deepCopy() {
    return new TRowResult(this);
  }

  @Override
  public void clear() {
    this.row = null;
    this.columns = null;
    this.sortedColumns = null;
  }

  public byte[] getRow() {
    setRow(org.apache.thrift.TBaseHelper.rightSize(row));
    return row == null ? null : row.array();
  }

  public java.nio.ByteBuffer bufferForRow() {
    return org.apache.thrift.TBaseHelper.copyBinary(row);
  }

  public TRowResult setRow(byte[] row) {
    this.row = row == null ? (java.nio.ByteBuffer)null   : java.nio.ByteBuffer.wrap(row.clone());
    return this;
  }

  public TRowResult setRow(@org.apache.thrift.annotation.Nullable java.nio.ByteBuffer row) {
    this.row = org.apache.thrift.TBaseHelper.copyBinary(row);
    return this;
  }

  public void unsetRow() {
    this.row = null;
  }

  /** Returns true if field row is set (has been assigned a value) and false otherwise */
  public boolean isSetRow() {
    return this.row != null;
  }

  public void setRowIsSet(boolean value) {
    if (!value) {
      this.row = null;
    }
  }

  public int getColumnsSize() {
    return (this.columns == null) ? 0 : this.columns.size();
  }

  public void putToColumns(java.nio.ByteBuffer key, TCell val) {
    if (this.columns == null) {
      this.columns = new java.util.HashMap<java.nio.ByteBuffer,TCell>();
    }
    this.columns.put(key, val);
  }

  @org.apache.thrift.annotation.Nullable
  public java.util.Map<java.nio.ByteBuffer,TCell> getColumns() {
    return this.columns;
  }

  public TRowResult setColumns(@org.apache.thrift.annotation.Nullable java.util.Map<java.nio.ByteBuffer,TCell> columns) {
    this.columns = columns;
    return this;
  }

  public void unsetColumns() {
    this.columns = null;
  }

  /** Returns true if field columns is set (has been assigned a value) and false otherwise */
  public boolean isSetColumns() {
    return this.columns != null;
  }

  public void setColumnsIsSet(boolean value) {
    if (!value) {
      this.columns = null;
    }
  }

  public int getSortedColumnsSize() {
    return (this.sortedColumns == null) ? 0 : this.sortedColumns.size();
  }

  @org.apache.thrift.annotation.Nullable
  public java.util.Iterator<TColumn> getSortedColumnsIterator() {
    return (this.sortedColumns == null) ? null : this.sortedColumns.iterator();
  }

  public void addToSortedColumns(TColumn elem) {
    if (this.sortedColumns == null) {
      this.sortedColumns = new java.util.ArrayList<TColumn>();
    }
    this.sortedColumns.add(elem);
  }

  @org.apache.thrift.annotation.Nullable
  public java.util.List<TColumn> getSortedColumns() {
    return this.sortedColumns;
  }

  public TRowResult setSortedColumns(@org.apache.thrift.annotation.Nullable java.util.List<TColumn> sortedColumns) {
    this.sortedColumns = sortedColumns;
    return this;
  }

  public void unsetSortedColumns() {
    this.sortedColumns = null;
  }

  /** Returns true if field sortedColumns is set (has been assigned a value) and false otherwise */
  public boolean isSetSortedColumns() {
    return this.sortedColumns != null;
  }

  public void setSortedColumnsIsSet(boolean value) {
    if (!value) {
      this.sortedColumns = null;
    }
  }

  public void setFieldValue(_Fields field, @org.apache.thrift.annotation.Nullable java.lang.Object value) {
    switch (field) {
    case ROW:
      if (value == null) {
        unsetRow();
      } else {
        if (value instanceof byte[]) {
          setRow((byte[])value);
        } else {
          setRow((java.nio.ByteBuffer)value);
        }
      }
      break;

    case COLUMNS:
      if (value == null) {
        unsetColumns();
      } else {
        setColumns((java.util.Map<java.nio.ByteBuffer,TCell>)value);
      }
      break;

    case SORTED_COLUMNS:
      if (value == null) {
        unsetSortedColumns();
      } else {
        setSortedColumns((java.util.List<TColumn>)value);
      }
      break;

    }
  }

  @org.apache.thrift.annotation.Nullable
  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case ROW:
      return getRow();

    case COLUMNS:
      return getColumns();

    case SORTED_COLUMNS:
      return getSortedColumns();

    }
    throw new java.lang.IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new java.lang.IllegalArgumentException();
    }

    switch (field) {
    case ROW:
      return isSetRow();
    case COLUMNS:
      return isSetColumns();
    case SORTED_COLUMNS:
      return isSetSortedColumns();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that == null)
      return false;
    if (that instanceof TRowResult)
      return this.equals((TRowResult)that);
    return false;
  }

  public boolean equals(TRowResult that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_row = true && this.isSetRow();
    boolean that_present_row = true && that.isSetRow();
    if (this_present_row || that_present_row) {
      if (!(this_present_row && that_present_row))
        return false;
      if (!this.row.equals(that.row))
        return false;
    }

    boolean this_present_columns = true && this.isSetColumns();
    boolean that_present_columns = true && that.isSetColumns();
    if (this_present_columns || that_present_columns) {
      if (!(this_present_columns && that_present_columns))
        return false;
      if (!this.columns.equals(that.columns))
        return false;
    }

    boolean this_present_sortedColumns = true && this.isSetSortedColumns();
    boolean that_present_sortedColumns = true && that.isSetSortedColumns();
    if (this_present_sortedColumns || that_present_sortedColumns) {
      if (!(this_present_sortedColumns && that_present_sortedColumns))
        return false;
      if (!this.sortedColumns.equals(that.sortedColumns))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + ((isSetRow()) ? 131071 : 524287);
    if (isSetRow())
      hashCode = hashCode * 8191 + row.hashCode();

    hashCode = hashCode * 8191 + ((isSetColumns()) ? 131071 : 524287);
    if (isSetColumns())
      hashCode = hashCode * 8191 + columns.hashCode();

    hashCode = hashCode * 8191 + ((isSetSortedColumns()) ? 131071 : 524287);
    if (isSetSortedColumns())
      hashCode = hashCode * 8191 + sortedColumns.hashCode();

    return hashCode;
  }

  @Override
  public int compareTo(TRowResult other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.valueOf(isSetRow()).compareTo(other.isSetRow());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetRow()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.row, other.row);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.valueOf(isSetColumns()).compareTo(other.isSetColumns());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetColumns()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.columns, other.columns);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.valueOf(isSetSortedColumns()).compareTo(other.isSetSortedColumns());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetSortedColumns()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.sortedColumns, other.sortedColumns);
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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("TRowResult(");
    boolean first = true;

    sb.append("row:");
    if (this.row == null) {
      sb.append("null");
    } else {
      org.apache.thrift.TBaseHelper.toString(this.row, sb);
    }
    first = false;
    if (isSetColumns()) {
      if (!first) sb.append(", ");
      sb.append("columns:");
      if (this.columns == null) {
        sb.append("null");
      } else {
        sb.append(this.columns);
      }
      first = false;
    }
    if (isSetSortedColumns()) {
      if (!first) sb.append(", ");
      sb.append("sortedColumns:");
      if (this.sortedColumns == null) {
        sb.append("null");
      } else {
        sb.append(this.sortedColumns);
      }
      first = false;
    }
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
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
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class TRowResultStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public TRowResultStandardScheme getScheme() {
      return new TRowResultStandardScheme();
    }
  }

  private static class TRowResultStandardScheme extends org.apache.thrift.scheme.StandardScheme<TRowResult> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, TRowResult struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // ROW
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.row = iprot.readBinary();
              struct.setRowIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // COLUMNS
            if (schemeField.type == org.apache.thrift.protocol.TType.MAP) {
              {
                org.apache.thrift.protocol.TMap _map8 = iprot.readMapBegin();
                struct.columns = new java.util.HashMap<java.nio.ByteBuffer,TCell>(2*_map8.size);
                @org.apache.thrift.annotation.Nullable java.nio.ByteBuffer _key9;
                @org.apache.thrift.annotation.Nullable TCell _val10;
                for (int _i11 = 0; _i11 < _map8.size; ++_i11)
                {
                  _key9 = iprot.readBinary();
                  _val10 = new TCell();
                  _val10.read(iprot);
                  struct.columns.put(_key9, _val10);
                }
                iprot.readMapEnd();
              }
              struct.setColumnsIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 3: // SORTED_COLUMNS
            if (schemeField.type == org.apache.thrift.protocol.TType.LIST) {
              {
                org.apache.thrift.protocol.TList _list12 = iprot.readListBegin();
                struct.sortedColumns = new java.util.ArrayList<TColumn>(_list12.size);
                @org.apache.thrift.annotation.Nullable TColumn _elem13;
                for (int _i14 = 0; _i14 < _list12.size; ++_i14)
                {
                  _elem13 = new TColumn();
                  _elem13.read(iprot);
                  struct.sortedColumns.add(_elem13);
                }
                iprot.readListEnd();
              }
              struct.setSortedColumnsIsSet(true);
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

    public void write(org.apache.thrift.protocol.TProtocol oprot, TRowResult struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.row != null) {
        oprot.writeFieldBegin(ROW_FIELD_DESC);
        oprot.writeBinary(struct.row);
        oprot.writeFieldEnd();
      }
      if (struct.columns != null) {
        if (struct.isSetColumns()) {
          oprot.writeFieldBegin(COLUMNS_FIELD_DESC);
          {
            oprot.writeMapBegin(new org.apache.thrift.protocol.TMap(org.apache.thrift.protocol.TType.STRING, org.apache.thrift.protocol.TType.STRUCT, struct.columns.size()));
            for (java.util.Map.Entry<java.nio.ByteBuffer, TCell> _iter15 : struct.columns.entrySet())
            {
              oprot.writeBinary(_iter15.getKey());
              _iter15.getValue().write(oprot);
            }
            oprot.writeMapEnd();
          }
          oprot.writeFieldEnd();
        }
      }
      if (struct.sortedColumns != null) {
        if (struct.isSetSortedColumns()) {
          oprot.writeFieldBegin(SORTED_COLUMNS_FIELD_DESC);
          {
            oprot.writeListBegin(new org.apache.thrift.protocol.TList(org.apache.thrift.protocol.TType.STRUCT, struct.sortedColumns.size()));
            for (TColumn _iter16 : struct.sortedColumns)
            {
              _iter16.write(oprot);
            }
            oprot.writeListEnd();
          }
          oprot.writeFieldEnd();
        }
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class TRowResultTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public TRowResultTupleScheme getScheme() {
      return new TRowResultTupleScheme();
    }
  }

  private static class TRowResultTupleScheme extends org.apache.thrift.scheme.TupleScheme<TRowResult> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, TRowResult struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      java.util.BitSet optionals = new java.util.BitSet();
      if (struct.isSetRow()) {
        optionals.set(0);
      }
      if (struct.isSetColumns()) {
        optionals.set(1);
      }
      if (struct.isSetSortedColumns()) {
        optionals.set(2);
      }
      oprot.writeBitSet(optionals, 3);
      if (struct.isSetRow()) {
        oprot.writeBinary(struct.row);
      }
      if (struct.isSetColumns()) {
        {
          oprot.writeI32(struct.columns.size());
          for (java.util.Map.Entry<java.nio.ByteBuffer, TCell> _iter17 : struct.columns.entrySet())
          {
            oprot.writeBinary(_iter17.getKey());
            _iter17.getValue().write(oprot);
          }
        }
      }
      if (struct.isSetSortedColumns()) {
        {
          oprot.writeI32(struct.sortedColumns.size());
          for (TColumn _iter18 : struct.sortedColumns)
          {
            _iter18.write(oprot);
          }
        }
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, TRowResult struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      java.util.BitSet incoming = iprot.readBitSet(3);
      if (incoming.get(0)) {
        struct.row = iprot.readBinary();
        struct.setRowIsSet(true);
      }
      if (incoming.get(1)) {
        {
          org.apache.thrift.protocol.TMap _map19 = new org.apache.thrift.protocol.TMap(org.apache.thrift.protocol.TType.STRING, org.apache.thrift.protocol.TType.STRUCT, iprot.readI32());
          struct.columns = new java.util.HashMap<java.nio.ByteBuffer,TCell>(2*_map19.size);
          @org.apache.thrift.annotation.Nullable java.nio.ByteBuffer _key20;
          @org.apache.thrift.annotation.Nullable TCell _val21;
          for (int _i22 = 0; _i22 < _map19.size; ++_i22)
          {
            _key20 = iprot.readBinary();
            _val21 = new TCell();
            _val21.read(iprot);
            struct.columns.put(_key20, _val21);
          }
        }
        struct.setColumnsIsSet(true);
      }
      if (incoming.get(2)) {
        {
          org.apache.thrift.protocol.TList _list23 = new org.apache.thrift.protocol.TList(org.apache.thrift.protocol.TType.STRUCT, iprot.readI32());
          struct.sortedColumns = new java.util.ArrayList<TColumn>(_list23.size);
          @org.apache.thrift.annotation.Nullable TColumn _elem24;
          for (int _i25 = 0; _i25 < _list23.size; ++_i25)
          {
            _elem24 = new TColumn();
            _elem24.read(iprot);
            struct.sortedColumns.add(_elem24);
          }
        }
        struct.setSortedColumnsIsSet(true);
      }
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

