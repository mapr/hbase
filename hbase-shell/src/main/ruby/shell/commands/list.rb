#
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

module Shell
  module Commands
    class List < Command
      def help
        return <<-EOF
List all tables in hbase. Optional regular expression parameter could
be used to filter the output. Examples:

  hbase> list
  hbase> list '/tables/t.*'
EOF
      end

      def command(regex = nil)
        if regex == nil && m7admin.m7_available? && !m7admin.is_m7_default?
          $stderr.puts "Listing HBase tables. Specify a path or configure namespace mappings to list M7 tables."
          begin
            masterRunning = admin.isMasterRunning
          rescue => e
            if e.cause != nil && e.cause.cause != nil && e.cause.cause.kind_of?(javax.security.sasl.SaslException)
              raise e
            end
          end
          if !masterRunning
            $stderr.puts "Unable to connect to HBase services."
          end
          regex = ".*"
        end

        now = Time.now
        formatter.header([ "TABLE" ])

        list = admin.list(regex)
        list.each do |table|
          formatter.row([ table ])
        end

        formatter.footer(now, list.size)
        return list
      end
    end
  end
end
