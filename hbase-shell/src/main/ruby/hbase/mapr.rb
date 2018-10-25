#
# Copyright The Apache Software Foundation
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
include Java

module Hbase
  class MapRAdmin
    include HBaseConstants

    TYPE_TABLE = "table"
    TYPE_FAMILY = "column family"
    TYPE_QUALIFIER = "column qualifier"

    HEADER_FORMAT_STRING = "%-20s%-30s%-60s"
    ROW_FORMAT_STRING = " %-19s%-30s%-60s"

    def initialize(shell, configuration, formatter)
      @formatter = formatter
      @shell = shell
      begin
        @mapping_rules = com.mapr.fs.hbase.MapRTableMappingRules.new(configuration)
      rescue NameError => e
        @mapping_rules = nil
        return
      end

      @security_available = false
      begin
        @perm_maps = {
          TYPE_TABLE => com.mapr.fs.AceHelper::TABLE_PERMISSIONS,
          TYPE_FAMILY => com.mapr.fs.AceHelper::FAMILY_PERMISSIONS,
          TYPE_QUALIFIER => com.mapr.fs.AceHelper::COLUMN_PERMISSIONS
        }
        @admin = com.mapr.fs.hbase.HBaseAdminImpl.new(configuration, @mapping_rules)
        @security_available = true
      rescue => e
      end
    end

    def set_perm(table_path, *args)
      # validate args
      security_available? && exists_and_is_mapr?(table_path)
      raise(ArgumentError, "There should be at least one argument after the table name") if args.length < 1

      htd = @admin.getTableDescriptor(table_path)
      # parse args
      tbl_permissions = java.util.HashMap.new
      cfs_permissions = Hash.new
      arg_index = 0
      while arg_index < args.length
        arg = args[arg_index]
        unless arg.kind_of?(String) || arg.kind_of?(Hash)
          raise(ArgumentError, "#{arg.class} of #{arg.inspect} is not of Hash or String type")
        end

        if arg.kind_of?(String) # table permission
          expression = args[arg_index+=1]
          validate_permissions(TYPE_TABLE, arg, expression)
          tbl_permissions.put(arg, expression)
        elsif !arg.empty? # column family / qualifier permission
          raise(ArgumentError, "Missing COLUMN parameter") unless arg.has_key?(COLUMN)
          raise(ArgumentError, "Missing PERM parameter") unless arg.has_key?(PERM)
          raise(ArgumentError, "Missing EXPR parameter") unless arg.has_key?(EXPR)
          column = arg[COLUMN]
          perm = arg[PERM]
          expr = arg[EXPR]

          parts = column.split(":")
          family = parts[0]
          raise(ArgumentError, "Can't find family: #{family}") unless htd.hasFamily(family.to_java_bytes)
          cf_perms = cfs_permissions[family]
          if cf_perms.nil?
            cf_perms = com.mapr.fs.hbase.CFPermissions.new(family)
            cfs_permissions[family] = cf_perms
          end

          if (parts.length > 1)
            qualifier = parts[1]
            validate_permissions(TYPE_QUALIFIER, perm, expr)
            raise(ArgumentError, "Duplicate permission '#{perm}' for column qualifier '#{family}:#{qualifier}'") if cf_perms.hasColPerm(qualifier, perm)
            cf_perms.addColPermission(qualifier, perm, expr)
          else
            validate_permissions(TYPE_FAMILY, perm, expr)
            raise(ArgumentError, "Duplicate permission '#{perm}' for column family '#{family}'") if cf_perms.hasCFPerm(perm)
            cf_perms.addCFPermission(perm, expr)
          end
        end

        arg_index+=1
      end

      # execute
      if cfs_permissions.size > 0 # set on family first, will validate if the column families exist
        cfs_permissions.each do |family, cf_perms|
          @admin.setFamilyPermissions(table_path, family, cf_perms)
        end
      end
      if tbl_permissions.size > 0 # and then on table
        @admin.setTablePermissions(table_path, tbl_permissions)
      end
    end

    def list_perm(table_path)
      # validate args
      security_available? && exists_and_is_mapr?(table_path)

      count = 0
      now = Time.now
      puts HEADER_FORMAT_STRING % ["Scope", "Permission", "Access Control Expression"]

      # list table permissions
      @admin.getTablePermissions(table_path).each do |permission, expression|
        count += 1
        puts ROW_FORMAT_STRING % ["", permission, expression]
      end

      @admin.getFamilyPermissions(table_path).each do |cf_perms|
        # column family permissions
        cf_perms.getCfPermissions.each do |permission, expression|
          count += 1
          puts ROW_FORMAT_STRING % [cf_perms.getFamily, permission, expression]
        end

        # column qualifier permissions
        cf_perms.getColumnNames.each do |column|
          cf_perms.getColPermission(column).each do |permission, expression|
            count += 1
            puts ROW_FORMAT_STRING % [cf_perms.getFamily + ":" + column, permission, expression]
          end
        end
      end

      @formatter.footer(now, count)
    end

    def del_perm(permission, table_path, *args)
      # validate args
      security_available? && exists_and_is_mapr?(table_path)
      raise(ArgumentError, "There can be at most one argument after the table name") if args.length > 1

      if args.nil? || args.length == 0
        validate_permissions(TYPE_TABLE, permission)
        @admin.deleteTablePermission(table_path, permission)
      else
        if args[0].include? ?:
          validate_permissions(TYPE_QUALIFIER, permission)
        else
          validate_permissions(TYPE_FAMILY, permission)
        end
        @admin.deleteColumnPermission(table_path, args[0], permission)
      end

    end

    def validate_permissions(type, permission, expression=nil)
      raise(ArgumentError, "The permission '#{permission}' is not valid for #{type}.") if !@perm_maps[type].contains(permission)
      begin
        com.mapr.fs.AceHelper::toPostfix(expression) unless expression.nil?
      rescue => e
        puts e.message
        puts "Backtrace: #{e.backtrace.join("\n           ")}" if @shell.debug
        raise ArgumentError, "The permission expression '#{expression}' is not valid."
      end
    end

    def is_m7_default?()
      @mapping_rules != nil && @mapping_rules.isMapRDefault
    end

    def m7_available?()
      @mapping_rules != nil
    end

    # Is it an existing MapR table?
    def exists_and_is_mapr?(table_path)
      # Table name should be a string
      raise(ArgumentError, "Table name must be of type String") unless table_path.kind_of?(String)
      # Must resolve to a MapR table
      raise(ArgumentError, "Table: '#{table_path}' is not a MapR table") unless @mapping_rules.isMapRTable(table_path)
      # Table should exist
      raise(ArgumentError, "Can't find a table: '#{table_path}'") unless @admin.tableExists(table_path)
      true;
    end

    # Make sure that security classes are available
    def security_available?()
      raise(ArgumentError, "Security features for MapR tables are not available in this version.") unless @security_available
      true;
    end
  end
end
